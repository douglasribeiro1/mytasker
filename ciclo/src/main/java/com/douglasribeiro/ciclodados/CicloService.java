package com.douglasribeiro.ciclodados;

import android.app.AlarmManager;
import android.app.KeyguardManager;
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
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import java.text.SimpleDateFormat;
import java.util.Date;
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
 * Quem manda, em ordem: pausa → carregando → Wi-Fi → horário da lista → tela → ciclo padrão.
 */
public class CicloService extends Service {

    static final String ACTION_STOP = "com.douglasribeiro.ciclodados.STOP";
    static final String ACTION_TESTE_OFF = "com.douglasribeiro.ciclodados.TESTE_OFF";
    static final String ACTION_TESTE_ON = "com.douglasribeiro.ciclodados.TESTE_ON";
    static final String ACTION_PAUSAR = "com.douglasribeiro.ciclodados.PAUSAR";
    static final String ACTION_RETOMAR = "com.douglasribeiro.ciclodados.RETOMAR";
    static final String ACTION_REAVALIAR = "com.douglasribeiro.ciclodados.REAVALIAR";
    static final String EXTRA_ID = "id";
    private static final String ACTION_TICK = "com.douglasribeiro.ciclodados.TICK";
    private static final String ACTION_CHECK = "com.douglasribeiro.ciclodados.CHECK";

    private static final String CHANNEL = "sinal";
    private static final String CHANNEL_ALERTA = "alertas";
    private static final String GRUPO = "pulso";
    private static final int RESUMO_ID = 1, ALERTA_BASE = 5000, NOTIF_BASE = 1000;
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
        if (ACTION_STOP.equals(action) || !prefs.getBoolean("enabled", false)) {
            parar(ACTION_STOP.equals(action));
            return START_NOT_STICKY;
        }
        registrar();
        List<Automacao> todas = Automacao.carregar(prefs);
        Automacao a = Automacao.porId(todas, id);
        if (ACTION_TICK.equals(action)) {
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
        registerReceiver(receptor, f);
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(rede);
        registrado = true;
    }

    private void desregistrar() {
        if (!registrado) return;
        unregisterReceiver(receptor);
        try {
            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(rede);
        } catch (RuntimeException ignored) {
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
                    if (a.dados) agendarCheck(a, "on");
                }
                fase(a, "normal", 0);
                break;

            case M_ECONOMIA:
                cancelar(ACTION_TICK, a.id);
                if (step != FIXA) {
                    setStep(a, FIXA);
                    if (!sinalOff(a)) {
                        if (a.dados && !dadosLigados()) {
                            Registro.add(this, a.nome + ": " + motivo + ", mas os dados já estavam desligados por você.");
                        } else {
                            Registro.add(this, a.nome + ": " + motivo + " → \"" + a.off + "\".");
                            long fim = periodo != null ? periodo.fimApos(System.currentTimeMillis()) : 0;
                            enviarDesligar(a, fim > 0 ? "até " + hora(fim) : null);
                            if (a.dados) agendarCheck(a, "off");
                        }
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
            switch (prefs.getInt(k + "step", IDLE)) {
                case ESPERA:
                case ON:
                    if (a.dados && !dadosLigados()) {
                        Registro.add(this, a.nome + ": dados desligados por você, o app não mexe.");
                        setStep(a, ON);
                        agendarTick(a, offMin + onMin);
                        fase(a, "pausa_usuario", offMin + onMin);
                        break;
                    }
                    Registro.add(this, a.nome + ": \"" + a.off + "\" (" + offMin + " min).");
                    enviarDesligar(a, "até " + ate(offMin));
                    setStep(a, OFF);
                    agendarTick(a, offMin);
                    fase(a, "off", offMin);
                    if (a.dados) agendarCheck(a, "off");
                    break;
                case OFF:
                    Registro.add(this, a.nome + ": \"" + a.on + "\" (" + onMin + " min).");
                    enviarAtivar(a, "até " + ate(onMin));
                    setStep(a, ON);
                    agendarTick(a, onMin);
                    fase(a, "on", onMin);
                    if (a.dados) agendarCheck(a, "on");
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

    /** Só para quem controla os dados: 1 min após o sinal, confere. Se não religou, reenvia e depois avisa. */
    private void conferir(Automacao a) {
        String k = a.k();
        String tipo = prefs.getString(k + "check", "");
        prefs.edit().remove(k + "check").apply();
        if ("off".equals(tipo)) {
            if (sinalOff(a)) Registro.add(this, a.nome + ": conferência após \"" + a.off + "\" → " + estado());
        } else if ("on".equals(tipo) || "on2".equals(tipo)) {
            if (sinalOff(a)) return;
            if (dadosLigados()) {
                Registro.add(this, a.nome + ": conferência após \"" + a.on + "\" → " + estado());
                getSystemService(NotificationManager.class).cancel(ALERTA_BASE + a.id);
            } else if ("on".equals(tipo)) {
                Registro.add(this, a.nome + ": dados ainda desligados, reenviando \"" + a.on + "\".");
                enviarAtivar(a, "reenviado");
                agendarCheck(a, "on2");
            } else {
                Registro.add(this, a.nome + ": a rotina não religou os dados.");
                alerta(a);
            }
        }
    }

    /** Automação desligada ou excluída: volta ao normal e some com a notificação dela. */
    private void encerrar(Automacao a) {
        cancelar(ACTION_TICK, a.id);
        cancelar(ACTION_CHECK, a.id);
        setStep(a, IDLE);
        fase(a, "idle", 0);
        if (sinalOff(a)) {
            Registro.add(this, (a.nome.isEmpty() ? "Automação" : a.nome) + ": encerrada → \"" + a.on + "\".");
            enviarAtivar(a, null);
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
        prefs.edit().putString(a.k() + "sinal", "off").apply();
        if (a.dados) Estatistica.inicio(prefs, a.k(), System.currentTimeMillis());
        publicar(a, a.off, detalhe);
    }

    private void enviarAtivar(Automacao a, String detalhe) {
        prefs.edit().putString(a.k() + "sinal", "on").apply();
        Estatistica.fim(prefs, a.k(), System.currentTimeMillis());
        publicar(a, a.on, detalhe);
    }

    /** Título = palavra de sinal. Texto = só textos fixos (nunca nomes), para não disparar outra rotina. */
    private void publicar(Automacao a, String palavra, String detalhe) {
        String texto = "Pulso" + (detalhe != null ? " · " + detalhe : "");
        PendingIntent abrir = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(palavra)
                .setContentText(texto)
                .setContentIntent(abrir)
                .setGroup(GRUPO)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build();
        getSystemService(NotificationManager.class).notify(NOTIF_BASE + a.id, n);
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
                .setContentText(pausado ? "Pausado até " + hora(pausa) : "Em funcionamento")
                .setContentIntent(abrir)
                .setGroup(GRUPO)
                .setGroupSummary(true)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(null,
                        pausado ? "Retomar" : "Pausar " + PAUSA_MIN / 60 + " h", pAcao).build())
                .build();
    }

    private void atualizarResumo() {
        long pausa = prefs.getLong("pausa_ate", 0);
        String estado = pausa > System.currentTimeMillis() ? "p" + pausa : "ok";
        if (estado.equals(prefs.getString("resumo_estado", ""))) return;
        prefs.edit().putString("resumo_estado", estado).apply();
        getSystemService(NotificationManager.class).notify(RESUMO_ID, resumo());
    }

    private void alerta(Automacao a) {
        PendingIntent abrir = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ALERTA)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("A rotina não religou os dados")
                .setContentText("Confira em Modos e Rotinas a rotina da automação " + a.nome + ".")
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(ALERTA_BASE + a.id, n);
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
        return getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private boolean telaLigada() {
        return getSystemService(PowerManager.class).isInteractive();
    }

    private boolean carregando() {
        return getSystemService(BatteryManager.class).isCharging();
    }

    private boolean wifiConectado() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities nc = n != null ? cm.getNetworkCapabilities(n) : null;
        return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private boolean dadosLigados() {
        try {
            return getSystemService(TelephonyManager.class).isDataEnabled();
        } catch (RuntimeException e) {
            return Settings.Global.getInt(getContentResolver(), "mobile_data", 1) == 1;
        }
    }

    private String estado() {
        String rede = "nenhuma";
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities nc = n != null ? cm.getNetworkCapabilities(n) : null;
        if (nc != null) {
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) rede = "Wi-Fi";
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) rede = "dados móveis";
            else rede = "outra";
        }
        return "dados móveis: " + (dadosLigados() ? "LIGADOS" : "DESLIGADOS") + " | rede: " + rede;
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
        int tipo = ACTION_TICK.equals(action) ? 1 : ACTION_CHECK.equals(action) ? 2 : 3;
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

    private void agendarCheck(Automacao a, String tipo) {
        prefs.edit().putString(a.k() + "check", tipo).apply();
        agendarElapsed(ACTION_CHECK, a.id, 60_000L);
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

    private void parar(boolean anotar) {
        cancelar(ACTION_REAVALIAR, 0);
        desregistrar();
        motivo = "";
        if (anotar) Registro.add(this, "Pulso desligado.");
        boolean algumOff = false;
        for (Automacao a : Automacao.carregar(prefs)) {
            if (sinalOff(a)) algumOff = true;
            encerrar(a);
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
