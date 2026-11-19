package com.douglasribeiro.ciclodados;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pulso: cada automação tem uma notificação própria cujo título é a palavra de sinal.
 *   palavra de economia ("Desligar") → a rotina da Samsung desliga o que você escolheu;
 *   palavra de normal   ("Ativar")   → a rotina religa.
 * Um título só é publicado quando o sinal daquela automação muda, para não redisparar as outras rotinas.
 *
 * Quem manda, em ordem: pausa → chamada → roteador → carregando → Wi-Fi → horário da lista → tela → ciclo padrão.
 */
public class CicloService extends Service {

    static final String ACTION_STOP = "com.douglasribeiro.ciclodados.STOP";
    static final String ACTION_TESTE_OFF = "com.douglasribeiro.ciclodados.TESTE_OFF";
    static final String ACTION_TESTE_ON = "com.douglasribeiro.ciclodados.TESTE_ON";
    static final String ACTION_PAUSAR = "com.douglasribeiro.ciclodados.PAUSAR";
    static final String ACTION_RETOMAR = "com.douglasribeiro.ciclodados.RETOMAR";
    static final String ACTION_REAVALIAR = "com.douglasribeiro.ciclodados.REAVALIAR";
    static final String ACTION_ALTERNAR = "com.douglasribeiro.ciclodados.ALTERNAR";
    /** A pessoa apagou uma notificação (Android 14+ deixa): recoloca. */
    private static final String ACTION_REPOR = "com.douglasribeiro.ciclodados.REPOR";
    static final String EXTRA_ID = "id";
    private static final String ACTION_TICK = "com.douglasribeiro.ciclodados.TICK";
    private static final String ACTION_CHECK = "com.douglasribeiro.ciclodados.CHECK";
    /** Fim da espera depois de reiniciar/atualizar: só então manda os sinais (as rotinas já carregaram). */
    static final String ACTION_INICIO = "com.douglasribeiro.ciclodados.INICIO";
    /** Botão do aviso de falha: manda o sinal atual de novo e confere. */
    private static final String ACTION_REENVIAR = "com.douglasribeiro.ciclodados.REENVIAR";
    /** Respostas à pergunta "dados desligados por fora". */
    private static final String ACTION_CONTINUAR = "com.douglasribeiro.ciclodados.CONTINUAR";
    private static final String ACTION_PARAR_ASSIM = "com.douglasribeiro.ciclodados.PARAR_ASSIM";

    /** Depois de ligar o celular, espera o Modos e Rotinas carregar antes do primeiro sinal. */
    static final long ESPERA_BOOT_MS = 3 * 60_000L;
    /** Depois de atualizar o app, as rotinas já estão de pé: espera pouco. */
    static final long ESPERA_ATUALIZACAO_MS = 15_000L;

    /** Entre um sinal e o contrário, pelo menos isto (apagar e acender a tela rápido não dispara as duas rotinas juntas). */
    static final long INTERVALO_MIN_MS = 10_000L;
    /** Publicações adiadas pelo intervalo mínimo, por automação. */
    private final HashMap<Integer, Runnable> adiados = new HashMap<>();

    private static final String CHANNEL = "sinal";
    private static final String CHANNEL_ALERTA = "alertas";
    private static final String GRUPO = "pulso";
    private static final int RESUMO_ID = 1, ALERTA_BASE = 5000, NOTIF_BASE = 1000, PERGUNTA_BASE = 7000;
    private static final int PAUSA_MIN = 60;

    private static final int IDLE = 0, ESPERA = 1, OFF = 3, ON = 5, FIXA = 6;
    private static final int M_NORMAL = 0, M_ECONOMIA = 1, M_CICLO = 2;

    private SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean registrado;
    private boolean emPrimeiroPlano;

    // resultado de calcular()
    private int modo;
    private String motivo = "";
    private int offMin, onMin;
    private Periodo periodo;

    private final Runnable reavaliarDepois = this::reavaliarTodas;

    private final BroadcastReceiver receptor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reavaliarTodas();
        }
    };

    /** Início/fim de chamada (Android 12+): reavalia logo, sem esperar o próximo ciclo. */
    private AudioManager.OnModeChangedListener modoAudio;

    private final ConnectivityManager.NetworkCallback rede = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { depois(); }
        @Override public void onLost(Network n) { depois(); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { depois(); }
        private void depois() {
            // Espera a rede assentar (evita reagir à própria troca de dados).
            h.removeCallbacks(reavaliarDepois);
            h.postDelayed(reavaliarDepois, 5000);
        }
    };

    static String hora(long t) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Registro.prefs(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        // Canal visível, sem som nem vibração: é o "sinal" que as rotinas leem.
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Sinal para as rotinas", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_ALERTA, "Avisos de problema",
                NotificationManager.IMPORTANCE_HIGH));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        int id = intent != null ? intent.getIntExtra(EXTRA_ID, -1) : -1;
        if (!emPrimeiroPlano) {
            Notification n = resumo();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(RESUMO_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(RESUMO_ID, n);
            }
            emPrimeiroPlano = true;
        }
        if (ACTION_PARAR_ASSIM.equals(action) && prefs.getBoolean("enabled", false)) {
            // Resposta "Parar o Pulso" à pergunta: desliga sem religar os dados que você desligou.
            prefs.edit().putBoolean("enabled", false).apply();
            Registro.add(this, "Você escolheu parar o Pulso (os dados ficam como você deixou).");
            parar(false, false);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action) || !prefs.getBoolean("enabled", false)) {
            parar(ACTION_STOP.equals(action), true);
            return START_NOT_STICKY;
        }
        if (ACTION_ALTERNAR.equals(action)) {
            action = prefs.getLong("pausa_ate", 0) > System.currentTimeMillis() ? ACTION_RETOMAR : ACTION_PAUSAR;
        }
        registrar();
        List<Automacao> todas = Automacao.carregar(prefs);
        Automacao a = Automacao.porId(todas, id);
        long inicioAte = prefs.getLong("inicio_ate", 0);
        if (inicioAte > 0) {
            // Acabou de reiniciar/atualizar: segura os sinais até o Modos e Rotinas estar pronto.
            if (ACTION_INICIO.equals(action) || System.currentTimeMillis() >= inicioAte) {
                retomarAposInicio(todas);
            } else {
                agendarInicio(inicioAte);
                atualizarResumo();
                return START_STICKY;
            }
        } else if (ACTION_INICIO.equals(action)) {
            action = null; // alarme atrasado de um início já tratado: só reavalia
        }
        if (ACTION_CONTINUAR.equals(action)) {
            getSystemService(NotificationManager.class).cancel(PERGUNTA_BASE + id);
            Registro.add(this, (a != null ? a.nome : "Pulso") + ": você escolheu manter o Pulso funcionando.");
        } else if (ACTION_REENVIAR.equals(action)) {
            if (a != null && a.ativo) reenviar(a);
        } else if (ACTION_REPOR.equals(action)) {
            repor(a, id);
        } else if (ACTION_TICK.equals(action)) {
            if (a != null && a.ativo) tick(a);
        } else if (ACTION_CHECK.equals(action)) {
            if (a != null && a.ativo) conferir(a);
        } else if (ACTION_PAUSAR.equals(action)) {
            prefs.edit().putLong("pausa_ate", System.currentTimeMillis() + PAUSA_MIN * 60_000L).apply();
            Registro.add(this, "Pausado por " + PAUSA_MIN + " min.");
            reavaliarTodas();
        } else if (ACTION_RETOMAR.equals(action)) {
            prefs.edit().remove("pausa_ate").apply();
            Registro.add(this, "Pausa encerrada.");
            reavaliarTodas();
        } else if (ACTION_TESTE_OFF.equals(action) && a != null) {
            Registro.add(this, a.nome + ": teste \"" + a.off + "\".");
            enviarDesligar(a, "teste");
        } else if (ACTION_TESTE_ON.equals(action) && a != null) {
            Registro.add(this, a.nome + ": teste \"" + a.on + "\".");
            enviarAtivar(a, "teste");
        } else {
            reavaliarTodas();
        }
        atualizarResumo();
        return START_STICKY;
    }

    private void registrar() {
        if (registrado) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT); // desbloqueio
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction(Aparelho.ROTEADOR_ACAO);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receptor, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(receptor, f);
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(rede);
        if (Build.VERSION.SDK_INT >= 31) {
            modoAudio = m -> {
                h.removeCallbacks(reavaliarDepois);
                h.postDelayed(reavaliarDepois, 2000);
            };
            getSystemService(AudioManager.class).addOnModeChangedListener(getMainExecutor(), modoAudio);
        }
        registrado = true;
    }

    private void desregistrar() {
        if (!registrado) return;
        unregisterReceiver(receptor);
        try {
            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(rede);
        } catch (RuntimeException ignored) {
        }
        if (Build.VERSION.SDK_INT >= 31 && modoAudio != null) {
            try {
                getSystemService(AudioManager.class).removeOnModeChangedListener(modoAudio);
            } catch (RuntimeException ignored) {
            }
            modoAudio = null;
        }
        h.removeCallbacks(reavaliarDepois);
        registrado = false;
    }

    // ---------------- Decisão ----------------

    private void calcular(Automacao a) {
        long agora = System.currentTimeMillis();
        offMin = a.offMin;
        onMin = a.onMin;
        periodo = Periodo.atual(a.periodos, agora);
        long pausa = prefs.getLong("pausa_ate", 0);
        if (pausa > agora) {
            modo = M_NORMAL;
            motivo = "Pausado até " + hora(pausa);
        } else if (a.chamada && Aparelho.emChamada(this)) {
            modo = M_NORMAL;
            motivo = "Em chamada";
        } else if (a.roteador && Aparelho.roteadorLigado(this)) {
            modo = M_NORMAL;
            motivo = "Roteador Wi-Fi ligado";
        } else if (a.carregando && carregando()) {
            modo = M_NORMAL;
            motivo = "Carregando";
        } else if (a.wifi && wifiConectado()) {
            modo = M_NORMAL;
            motivo = "No Wi-Fi";
        } else if (periodo != null && Periodo.NORMAL.equals(periodo.modo)) {
            modo = M_NORMAL;
            motivo = "Horário \"" + periodo.nome + "\": sem ciclo";
        } else if (periodo != null && Periodo.ECONOMIA.equals(periodo.modo)) {
            modo = M_ECONOMIA;
            long fim = periodo.fimApos(agora);
            motivo = "Horário \"" + periodo.nome + "\"" + (fim > 0 ? " até " + hora(fim) : "");
        } else {
            if (periodo != null) {
                offMin = periodo.off;
                onMin = periodo.on;
            }
            if (interrompe(a)) {
                modo = M_NORMAL;
                motivo = Automacao.SAIR_DESBLOQUEIO.equals(a.sair) ? "Celular desbloqueado" : "Aguardando a tela apagar";
            } else {
                modo = M_CICLO;
                motivo = "";
            }
        }
    }

    /** Ajusta todas as automações ao estado atual. Pode ser chamado a qualquer momento. */
    private void reavaliarTodas() {
        if (!prefs.getBoolean("enabled", false)) return;
        if (prefs.getLong("inicio_ate", 0) > 0) return; // ainda esperando as rotinas carregarem
        List<Automacao> todas = Automacao.carregar(prefs);
        Set<String> vivas = new HashSet<>();
        for (Automacao a : todas) {
            if (a.ativo) {
                reavaliar(a);
                vivas.add(String.valueOf(a.id));
            } else {
                encerrar(a);
            }
        }
        // Automações excluídas: encerra o que tiver ficado para trás.
        for (String s : prefs.getStringSet("ids_vivos", new HashSet<>())) {
            if (Automacao.porId(todas, Integer.parseInt(s)) == null) {
                Automacao fantasma = new Automacao();
                fantasma.id = Integer.parseInt(s);
                fantasma.nome = "Automação excluída";
                fantasma.on = prefs.getString(fantasma.k() + "on_salvo", "");
                if (!fantasma.on.isEmpty()) encerrar(fantasma);
                else cancelarNotificacao(fantasma);
            }
        }
        prefs.edit().putStringSet("ids_vivos", vivas).apply();
        agendarMudanca(todas);
        atualizarResumo();
    }

    private void reavaliar(Automacao a) {
        prefs.edit().putString(a.k() + "on_salvo", a.on).apply();
        calcular(a);
        String k = a.k();
        int step = prefs.getInt(k + "step", IDLE);
        switch (modo) {
            case M_NORMAL:
                if (step != IDLE) {
                    cancelar(ACTION_TICK, a.id);
                    setStep(a, IDLE);
                }
                if (sinalOff(a)) {
                    Registro.add(this, a.nome + ": " + motivo + " → \"" + a.on + "\".");
                    enviarAtivar(a, null);
                    if (a.dados) agendarCheck(a, "on", 10 * 60_000L);
                }
                fase(a, "normal", 0);
                // Antes do Android 12 não há aviso de fim de chamada: confere de novo em 1 min.
                if (Build.VERSION.SDK_INT < 31 && "Em chamada".equals(motivo)) agendarTick(a, 1);
                break;

            case M_ECONOMIA:
                cancelar(ACTION_TICK, a.id);
                if (step != FIXA) {
                    setStep(a, FIXA);
                    if (!sinalOff(a)) {
                        // O Pulso manda: o sinal sai mesmo que os dados já estejam desligados.
                        Registro.add(this, a.nome + ": " + motivo + " → \"" + a.off + "\".");
                        long fim = periodo != null ? periodo.fimApos(System.currentTimeMillis()) : 0;
                        enviarDesligar(a, fim > 0 ? "até " + hora(fim) : null);
                        if (a.dados) agendarCheck(a, "off", 10 * 60_000L);
                    }
                }
                faseAte(a, "economia", periodo != null ? periodo.fimApos(System.currentTimeMillis()) : 0);
                break;

            case M_CICLO:
                if (step == FIXA) {
                    step = IDLE;
                    setStep(a, IDLE);
                }
                if (step == IDLE) {
                    if (sinalOff(a)) {
                        // Já em economia (veio de um horário fixo): segue o ciclo daqui.
                        setStep(a, OFF);
                        agendarTick(a, offMin);
                        fase(a, "off", offMin);
                    } else if (emUso(a)) {
                        fase(a, "normal", 0); // celular em uso e ele não interrompe: começa quando apagar
                    } else if (a.espera <= 0) {
                        Registro.add(this, a.nome + ": tela apagada, economia já (espera 0).");
                        setStep(a, ESPERA);
                        tick(a);
                    } else {
                        Registro.add(this, a.nome + ": economia em " + a.espera + " min.");
                        setStep(a, ESPERA);
                        agendarTick(a, a.espera);
                        fase(a, "espera", a.espera);
                    }
                }
                break;
        }
    }

    private void tick(Automacao a) {
        PowerManager pm = getSystemService(PowerManager.class);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulso:tick");
        wl.acquire(30_000);
        try {
            calcular(a);
            if (modo != M_CICLO) {
                reavaliar(a);
                return;
            }
            String k = a.k();
            // Conferência ainda pendente (cairia junto com este passo): ela é que decide; não alarma duas vezes.
            // Se o alarme da conferência se perdeu (atrasou mais de 10 s), a rede de segurança assume.
            boolean conferindo = !prefs.getString(k + "check", "").isEmpty()
                    && prefs.getLong(k + "check_em", 0) > System.currentTimeMillis() - 10_000L;
            switch (prefs.getInt(k + "step", IDLE)) {
                case ESPERA:
                case ON:
                    // Rede de segurança sem custo: aproveita o despertar do ciclo para conferir a fase que acabou.
                    if (a.dados && !conferindo && !dadosLigados()) {
                        if ("on".equals(prefs.getString(k + "ok", ""))) {
                            // Estavam ligados (conferido) e caíram: alguém desligou por fora.
                            Registro.add(this, a.nome + ": dados desligados fora do Pulso. O Pulso segue no comando.");
                            perguntar(a);
                        } else if (!sinalOff(a)) {
                            Registro.add(this, a.nome + ": ERRO: os dados ficaram desligados na fase normal"
                                    + " (a rotina de \"" + a.on + "\" não obedeceu).");
                            alerta(a, false);
                        }
                    }
                    Registro.add(this, a.nome + ": \"" + a.off + "\" (" + offMin + " min).");
                    enviarDesligar(a, "até " + ate(offMin));
                    setStep(a, OFF);
                    agendarTick(a, offMin);
                    fase(a, "off", offMin);
                    if (a.dados) agendarCheck(a, "off", offMin * 60_000L);
                    break;
                case OFF:
                    if (a.dados && !conferindo && dadosLigados() && !"off".equals(prefs.getString(k + "ok", ""))) {
                        Registro.add(this, a.nome + ": ERRO: os dados ficaram ligados na economia"
                                + " (a rotina de \"" + a.off + "\" não obedeceu).");
                        alerta(a, true);
                    }
                    Registro.add(this, a.nome + ": \"" + a.on + "\" (" + onMin + " min).");
                    enviarAtivar(a, "até " + ate(onMin));
                    setStep(a, ON);
                    agendarTick(a, onMin);
                    fase(a, "on", onMin);
                    if (a.dados) agendarCheck(a, "on", onMin * 60_000L);
                    break;
                default:
                    reavaliar(a);
                    break;
            }
            atualizarResumo();
        } finally {
            if (wl.isHeld()) wl.release();
        }
    }

    /**
     * Só para quem controla os dados: pouco depois de cada sinal (de desligar E de religar), confere.
     * Não obedeceu → reenvia como notificação nova e confere de novo. Ainda não → aviso com som.
     * Nunca falha calado.
     */
    private void conferir(Automacao a) {
        String k = a.k();
        String tipo = prefs.getString(k + "check", "");
        prefs.edit().remove(k + "check").apply();
        if (tipo.isEmpty()) return;
        boolean querOff = tipo.startsWith("off");
        if (querOff != sinalOff(a)) return; // o sinal já mudou desde então
        String palavra = querOff ? a.off : a.on;
        boolean obedeceu = querOff ? !dadosLigados() : dadosLigados();
        if (obedeceu) {
            Registro.add(this, a.nome + ": conferência após \"" + palavra + "\" → " + estado());
            prefs.edit().putString(k + "ok", querOff ? "off" : "on").remove(k + "alertado").apply();
            getSystemService(NotificationManager.class).cancel(ALERTA_BASE + a.id);
        } else if (!tipo.endsWith("2")) {
            Registro.add(this, a.nome + ": a rotina não obedeceu \"" + palavra + "\" (" + estado()
                    + "). Reenviando.");
            publicar(a, palavra, "reenviado");
            agendarCheck(a, querOff ? "off2" : "on2", prefs.getLong(k + "check_resto", 60_000L));
        } else {
            Registro.add(this, a.nome + ": ERRO: a rotina não obedeceu \"" + palavra + "\" nem reenviando ("
                    + estado() + ").");
            alerta(a, querOff);
        }
    }

    /** Botão "Tentar de novo" do aviso: manda o sinal atual outra vez e confere. */
    private void reenviar(Automacao a) {
        getSystemService(NotificationManager.class).cancel(ALERTA_BASE + a.id);
        prefs.edit().remove(a.k() + "alertado").apply();
        boolean off = sinalOff(a);
        String palavra = off ? a.off : a.on;
        Registro.add(this, a.nome + ": tentando de novo \"" + palavra + "\".");
        publicar(a, palavra, "reenviado");
        if (a.dados) agendarCheck(a, off ? "off" : "on", 2 * 60_000L);
    }

    /**
     * Depois de reiniciar o celular ou atualizar o app: a notificação sumiu e não se sabe se o último
     * sinal pegou. Manda de novo o sinal certo para o momento, como notificação nova, e confere.
     */
    private void retomarAposInicio(List<Automacao> todas) {
        prefs.edit().remove("inicio_ate").apply();
        cancelar(ACTION_INICIO, 0);
        Registro.add(this, "Rotinas prontas: sincronizando os sinais.");
        for (Automacao a : todas) {
            if (!a.ativo) continue;
            String k = a.k();
            prefs.edit().putInt(k + "step", IDLE).remove(k + "ok").remove(k + "alertado").remove(k + "check").apply();
            calcular(a);
            if (modo == M_ECONOMIA) {
                // reavaliar() manda o sinal de economia como novo.
                prefs.edit().putString(k + "sinal", "on").apply();
            } else {
                Registro.add(this, a.nome + ": após reiniciar → \"" + a.on + "\".");
                enviarAtivar(a, "após reiniciar");
                if (a.dados) agendarCheck(a, "on", 10 * 60_000L);
            }
        }
        reavaliarTodas();
    }

    private void agendarInicio(long rtc) {
        AlarmManager am = getSystemService(AlarmManager.class);
        PendingIntent pi = alarme(ACTION_INICIO, 0);
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rtc, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rtc, pi);
        }
    }

    /** Automação desligada ou excluída: volta ao normal e some com a notificação dela. */
    private void encerrar(Automacao a) {
        encerrar(a, true);
    }

    /** religar = false: some sem mandar a palavra de normal (você desligou por fora e pediu para parar). */
    private void encerrar(Automacao a, boolean religar) {
        cancelar(ACTION_TICK, a.id);
        cancelar(ACTION_CHECK, a.id);
        setStep(a, IDLE);
        fase(a, "idle", 0);
        getSystemService(NotificationManager.class).cancel(PERGUNTA_BASE + a.id);
        getSystemService(NotificationManager.class).cancel(ALERTA_BASE + a.id);
        Runnable adiado = adiados.remove(a.id);
        if (adiado != null) h.removeCallbacks(adiado);
        prefs.edit().remove(a.k() + "publicar_em").apply();
        if (!religar) {
            prefs.edit().putString(a.k() + "sinal", "on").apply();
            Estatistica.fim(prefs, a.k(), System.currentTimeMillis());
            cancelarNotificacao(a);
        } else if (sinalOff(a)) {
            Registro.add(this, (a.nome.isEmpty() ? "Automação" : a.nome) + ": encerrada → \"" + a.on + "\".");
            // Encerrar não espera o intervalo mínimo: a notificação some logo depois.
            prefs.edit().putString(a.k() + "sinal", "on").apply();
            Estatistica.fim(prefs, a.k(), System.currentTimeMillis());
            publicarAgora(a, a.on, null);
            h.postDelayed(() -> cancelarNotificacao(a), 5000);
        } else {
            cancelarNotificacao(a);
        }
    }

    // ---------------- Sinais ----------------

    private boolean sinalOff(Automacao a) {
        return "off".equals(prefs.getString(a.k() + "sinal", "on"));
    }

    private void enviarDesligar(Automacao a, String detalhe) {
        prefs.edit().putString(a.k() + "sinal", "off").remove(a.k() + "ok").remove(a.k() + "alertado").apply();
        if (a.dados) Estatistica.inicio(prefs, a.k(), System.currentTimeMillis());
        publicar(a, a.off, detalhe);
    }

    private void enviarAtivar(Automacao a, String detalhe) {
        prefs.edit().putString(a.k() + "sinal", "on").remove(a.k() + "ok").remove(a.k() + "alertado").apply();
        Estatistica.fim(prefs, a.k(), System.currentTimeMillis());
        publicar(a, a.on, detalhe);
    }

    /**
     * Título = palavra de sinal. Texto = só textos fixos (nunca nomes), para não disparar outra rotina.
     * Sempre uma notificação NOVA (apaga a anterior antes): só trocar o título da mesma notificação
     * nem sempre faz a Samsung rodar a rotina de novo.
     */
    private void publicar(Automacao a, String palavra, String detalhe) {
        String k = a.k();
        long agora = System.currentTimeMillis();
        Runnable antes = adiados.remove(a.id);
        if (antes != null) h.removeCallbacks(antes);
        String ultima = prefs.getString(k + "publicado", "");
        if (antes != null && palavra.equals(ultima)) {
            // Voltou ao sinal que já estava na tela antes de o adiado sair: nada a mandar.
            prefs.edit().remove(k + "publicar_em").apply();
            Registro.add(this, a.nome + ": mudou de ideia em menos de " + INTERVALO_MIN_MS / 1000
                    + " s; continua \"" + palavra + "\".");
            return;
        }
        long falta = prefs.getLong(k + "publicado_em", 0) + INTERVALO_MIN_MS - agora;
        if (!palavra.equals(ultima) && falta > 0) {
            // Sinal contrário logo depois do anterior: espera completar o intervalo mínimo.
            PowerManager.WakeLock wl = getSystemService(PowerManager.class)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulso:intervalo");
            wl.acquire(falta + 5_000L);
            Runnable r = () -> {
                adiados.remove(a.id);
                prefs.edit().remove(k + "publicar_em").apply();
                publicarAgora(a, palavra, detalhe);
                if (wl.isHeld()) wl.release();
            };
            adiados.put(a.id, r);
            prefs.edit().putLong(k + "publicar_em", agora + falta).apply();
            h.postDelayed(r, falta);
            return;
        }
        publicarAgora(a, palavra, detalhe);
    }

    private void publicarAgora(Automacao a, String palavra, String detalhe) {
        prefs.edit().putString(a.k() + "publicado", palavra)
                .putLong(a.k() + "publicado_em", System.currentTimeMillis()).apply();
        String texto = "Pulso" + (detalhe != null ? " · " + detalhe : "");
        PendingIntent abrir = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(palavra)
                .setContentText(texto)
                .setContentIntent(abrir)
                .setDeleteIntent(servico(ACTION_REPOR, a.id, 4))
                .setGroup(GRUPO)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .addAction(new Notification.Action.Builder(null, "Pausar / retomar",
                        servico(ACTION_ALTERNAR, 0, 5)).build())
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.cancel(NOTIF_BASE + a.id);
        nm.notify(NOTIF_BASE + a.id, n);
    }

    /** PendingIntent para uma ação deste serviço (um por automação e tipo). */
    private PendingIntent servico(String action, int id, int tipo) {
        Intent i = new Intent(this, CicloService.class).setAction(action).putExtra(EXTRA_ID, id)
                .setData(Uri.parse("pulso://" + tipo + "/" + id));
        return PendingIntent.getForegroundService(this, id * 10 + tipo, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** A notificação foi apagada: põe de volta. Só some quando você desliga o Pulso (o Pulso manda). */
    private void repor(Automacao a, int id) {
        if (id <= 0) {
            prefs.edit().remove("resumo_estado").apply();
            atualizarResumo();
            return;
        }
        if (a == null || !a.ativo) return;
        boolean off = sinalOff(a);
        Registro.add(this, a.nome + ": notificação apagada, recolocada.");
        publicar(a, off ? a.off : a.on, null);
    }

    private void cancelarNotificacao(Automacao a) {
        getSystemService(NotificationManager.class).cancel(NOTIF_BASE + a.id);
    }

    // ---------------- Notificação principal (resumo do grupo) ----------------

    private Notification resumo() {
        PendingIntent abrir = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        long pausa = prefs.getLong("pausa_ate", 0);
        boolean pausado = pausa > System.currentTimeMillis();
        Intent acao = new Intent(this, CicloService.class).setAction(pausado ? ACTION_RETOMAR : ACTION_PAUSAR);
        PendingIntent pAcao = PendingIntent.getForegroundService(this, 10, acao,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Pulso")
                .setContentText(pausado ? "Pausado até " + hora(pausa)
                        : prefs.getLong("inicio_ate", 0) > 0 ? "Iniciando · sinais às " + hora(prefs.getLong("inicio_ate", 0))
                        : "Em funcionamento")
                .setContentIntent(abrir)
                .setGroup(GRUPO)
                .setGroupSummary(true)
                .setDeleteIntent(servico(ACTION_REPOR, 0, 4))
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(null,
                        pausado ? "Retomar" : "Pausar " + PAUSA_MIN / 60 + " h", pAcao).build())
                .build();
    }

    private void atualizarResumo() {
        long pausa = prefs.getLong("pausa_ate", 0);
        long inicio = prefs.getLong("inicio_ate", 0);
        String estado = pausa > System.currentTimeMillis() ? "p" + pausa : inicio > 0 ? "i" + inicio : "ok";
        if (estado.equals(prefs.getString("resumo_estado", ""))) return;
        prefs.edit().putString("resumo_estado", estado).apply();
        getSystemService(NotificationManager.class).notify(RESUMO_ID, resumo());
    }

    static final String ALERTA_TITULO = "A rotina não obedeceu";
    static final String ALERTA_OFF = "A economia não aconteceu: os dados continuam ligados. "
            + "Confira a rotina em Modos e Rotinas.";
    static final String ALERTA_ON = "Os dados não voltaram: continuam desligados. "
            + "Confira a rotina em Modos e Rotinas.";
    static final String PERGUNTA_TITULO = "Dados móveis desligados por fora";
    static final String PERGUNTA_TEXTO = "O Pulso continua no comando e volta a ligar no próximo ciclo. "
            + "Quer parar o Pulso?";

    /**
     * Aviso com som quando a rotina não obedeceu. Um por sinal (não repete o mesmo aviso).
     * Os textos não levam o nome da automação, para nunca conter uma palavra de sinal.
     */
    private void alerta(Automacao a, boolean off) {
        String k = a.k();
        String tipo = off ? "off" : "on";
        if (tipo.equals(prefs.getString(k + "alertado", ""))) return;
        prefs.edit().putString(k + "alertado", tipo).apply();
        PendingIntent abrir = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ALERTA)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(ALERTA_TITULO)
                .setContentText(off ? ALERTA_OFF : ALERTA_ON)
                .setStyle(new Notification.BigTextStyle().bigText(off ? ALERTA_OFF : ALERTA_ON))
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Tentar de novo",
                        servico(ACTION_REENVIAR, a.id, 6)).build())
                .build();
        getSystemService(NotificationManager.class).notify(ALERTA_BASE + a.id, n);
    }

    /** Os dados caíram sem o Pulso mandar: o ciclo continua, mas pergunta se você quer parar. */
    private void perguntar(Automacao a) {
        PendingIntent abrir = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ALERTA)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(PERGUNTA_TITULO)
                .setContentText(PERGUNTA_TEXTO)
                .setStyle(new Notification.BigTextStyle().bigText(PERGUNTA_TEXTO))
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Continuar",
                        servico(ACTION_CONTINUAR, a.id, 7)).build())
                .addAction(new Notification.Action.Builder(null, "Parar o Pulso",
                        servico(ACTION_PARAR_ASSIM, a.id, 8)).build())
                .build();
        getSystemService(NotificationManager.class).notify(PERGUNTA_BASE + a.id, n);
    }

    // ---------------- Estado do aparelho ----------------

    /** O uso do celular tira esta automação da economia agora? */
    private boolean interrompe(Automacao a) {
        if (Automacao.SAIR_NUNCA.equals(a.sair)) return false;
        return emUso(a);
    }

    /** "Em uso": tela acesa; no modo "só ao desbloquear", tela acesa E desbloqueada. */
    private boolean emUso(Automacao a) {
        if (!telaLigada()) return false;
        if (Automacao.SAIR_DESBLOQUEIO.equals(a.sair)) return !bloqueado();
        return true;
    }

    private boolean bloqueado() {
        return Aparelho.bloqueado(this);
    }

    private boolean telaLigada() {
        return Aparelho.telaLigada(this);
    }

    private boolean carregando() {
        return Aparelho.carregando(this);
    }

    private boolean wifiConectado() {
        return Aparelho.wifiConectado(this);
    }

    private boolean dadosLigados() {
        return Aparelho.dadosLigados(this);
    }

    private String estado() {
        return "dados móveis: " + (dadosLigados() ? "LIGADOS" : "DESLIGADOS") + " | rede: " + Aparelho.rede(this);
    }

    // ---------------- Fase (para a tela do app) ----------------

    private static String ate(int minutos) {
        return hora(System.currentTimeMillis() + minutos * 60_000L);
    }

    private void fase(Automacao a, String f, int minutos) {
        faseAte(a, f, minutos > 0 ? System.currentTimeMillis() + minutos * 60_000L : 0);
    }

    private void faseAte(Automacao a, String f, long ate) {
        String k = a.k();
        prefs.edit().putString(k + "fase", f).putLong(k + "fase_ate", ate).putString(k + "fase_motivo", motivo).apply();
    }

    private void setStep(Automacao a, int s) {
        prefs.edit().putInt(a.k() + "step", s).apply();
    }

    // ---------------- Alarmes ----------------

    private PendingIntent alarme(String action, int id) {
        int tipo = ACTION_TICK.equals(action) ? 1 : ACTION_CHECK.equals(action) ? 2
                : ACTION_INICIO.equals(action) ? 9 : 3;
        Intent i = new Intent(this, CicloService.class).setAction(action).putExtra(EXTRA_ID, id)
                .setData(Uri.parse("pulso://" + tipo + "/" + id)); // um alarme distinto por automação
        return PendingIntent.getForegroundService(this, id * 10 + tipo, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void agendarElapsed(String action, int id, long emMs) {
        AlarmManager am = getSystemService(AlarmManager.class);
        long at = SystemClock.elapsedRealtime() + emMs;
        PendingIntent pi = alarme(action, id);
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        }
    }

    private void agendarTick(Automacao a, int minutos) {
        agendarElapsed(ACTION_TICK, a.id, Math.max(1, minutos) * 60_000L);
    }

    /**
     * Confere na metade da fase (no máximo 1 min depois), para nunca coincidir com o próximo sinal.
     * Ex.: normal de 1 min → confere aos 30 s; se reenviar, confere de novo aos 45 s.
     */
    private void agendarCheck(Automacao a, String tipo, long faseMs) {
        long d = Math.max(10_000L, Math.min(60_000L, faseMs / 2));
        // Se o sinal está esperando o intervalo mínimo, a conferência conta a partir de quando ele sair.
        d += Math.max(0, prefs.getLong(a.k() + "publicar_em", 0) - System.currentTimeMillis());
        prefs.edit().putString(a.k() + "check", tipo).putLong(a.k() + "check_resto", faseMs - d)
                .putLong(a.k() + "check_em", System.currentTimeMillis() + d).apply();
        agendarElapsed(ACTION_CHECK, a.id, d);
    }

    /** Reavalia na hora em que algum horário começa/termina ou a pausa acaba. */
    private void agendarMudanca(List<Automacao> todas) {
        long agora = System.currentTimeMillis();
        long prox = Long.MAX_VALUE;
        for (Automacao a : todas) {
            if (a.ativo) prox = Math.min(prox, Periodo.proximaMudanca(a.periodos, agora));
        }
        long pausa = prefs.getLong("pausa_ate", 0);
        if (pausa > agora) prox = Math.min(prox, pausa);
        AlarmManager am = getSystemService(AlarmManager.class);
        PendingIntent pi = alarme(ACTION_REAVALIAR, 0);
        if (prox == Long.MAX_VALUE) {
            am.cancel(pi);
            return;
        }
        long at = prox + 1000;
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private void cancelar(String action, int id) {
        getSystemService(AlarmManager.class).cancel(alarme(action, id));
    }

    // ---------------- Ciclo de vida ----------------

    /** religar = false: não manda a palavra de normal (os dados ficam como estão). */
    private void parar(boolean anotar, boolean religar) {
        cancelar(ACTION_REAVALIAR, 0);
        cancelar(ACTION_INICIO, 0);
        prefs.edit().remove("inicio_ate").apply();
        desregistrar();
        motivo = "";
        if (anotar) Registro.add(this, "Pulso desligado.");
        boolean algumOff = false;
        for (Automacao a : Automacao.carregar(prefs)) {
            if (religar && sinalOff(a)) algumOff = true;
            encerrar(a, religar);
        }
        prefs.edit().remove("resumo_estado").apply();
        if (algumOff) h.postDelayed(this::sair, 6000);
        else sair();
    }

    private void sair() {
        if (prefs.getBoolean("enabled", false)) return; // religado nesse meio tempo
        emPrimeiroPlano = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        desregistrar();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
