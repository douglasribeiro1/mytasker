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
import java.util.Locale;

/**
 * Pulso: avisa as rotinas da Samsung por uma notificação fixa que muda de texto.
 *   palavra de desligar ("Desligar") → economia: a rotina desliga o que você escolheu (dados, Wi-Fi...)
 *   palavra de ligar ("Ativar")      → normal: a rotina religa.
 *
 * Quem manda, em ordem: pausa → carregando → Wi-Fi conectado → horário da lista → tela → ciclo padrão.
 * Ciclo: tela apaga → espera → economia (X min) → normal (Y min) → economia...
 * Só manda "Ativar" depois de ter mandado "Desligar"; se você mesmo desligou os dados, não religa.
 */
public class CicloService extends Service {

    static final String ACTION_STOP = "com.douglasribeiro.ciclodados.STOP";
    static final String ACTION_TESTE_OFF = "com.douglasribeiro.ciclodados.TESTE_OFF";
    static final String ACTION_TESTE_ON = "com.douglasribeiro.ciclodados.TESTE_ON";
    static final String ACTION_PAUSAR = "com.douglasribeiro.ciclodados.PAUSAR";
    static final String ACTION_RETOMAR = "com.douglasribeiro.ciclodados.RETOMAR";
    static final String ACTION_REAVALIAR = "com.douglasribeiro.ciclodados.REAVALIAR";
    private static final String ACTION_TICK = "com.douglasribeiro.ciclodados.TICK";
    private static final String ACTION_CHECK = "com.douglasribeiro.ciclodados.CHECK";

    private static final String CHANNEL = "sinal";
    private static final String CHANNEL_ALERTA = "alertas";
    private static final int NOTIF_ID = 1, ALERTA_ID = 2;
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

    private final Runnable reavaliarDepois = this::reavaliar;

    private final BroadcastReceiver receptor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) Registro.add(CicloService.this, "Tela apagou.");
            else if (Intent.ACTION_SCREEN_ON.equals(a) && prefs.getInt("step", IDLE) != IDLE)
                Registro.add(CicloService.this, "Tela acendeu.");
            reavaliar();
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

    static String palavraOff(SharedPreferences p) { return p.getString("palavra_off", "Desligar"); }
    static String palavraOn(SharedPreferences p) { return p.getString("palavra_on", "Ativar"); }

    static String hora(long t) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Registro.prefs(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        // Canal visível, sem som nem vibração: é o "sinal" que a rotina lê.
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Sinal para as rotinas", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        NotificationChannel al = new NotificationChannel(CHANNEL_ALERTA, "Avisos de problema", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(al);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (!emPrimeiroPlano) {
            // Reaproveita o último texto mostrado, para não trocar o sinal à toa.
            primeiroPlano(construir(prefs.getString("notif_titulo", "Pulso"),
                    prefs.getString("notif_texto", "Aguardando a tela apagar")));
            emPrimeiroPlano = true;
        }
        if (ACTION_STOP.equals(action) || !prefs.getBoolean("enabled", false)) {
            parar(ACTION_STOP.equals(action));
            return START_NOT_STICKY;
        }
        registrar();
        if (ACTION_TICK.equals(action)) {
            tick();
        } else if (ACTION_CHECK.equals(action)) {
            conferir();
        } else if (ACTION_PAUSAR.equals(action)) {
            prefs.edit().putLong("pausa_ate", System.currentTimeMillis() + PAUSA_MIN * 60_000L).apply();
            Registro.add(this, "Pausado por " + PAUSA_MIN + " min.");
            reavaliar();
        } else if (ACTION_RETOMAR.equals(action)) {
            prefs.edit().remove("pausa_ate").apply();
            Registro.add(this, "Pausa encerrada.");
            reavaliar();
        } else if (ACTION_TESTE_OFF.equals(action)) {
            Registro.add(this, "Teste: \"" + palavraOff(prefs) + "\".");
            enviarDesligar("Sinal de teste");
        } else if (ACTION_TESTE_ON.equals(action)) {
            Registro.add(this, "Teste: \"" + palavraOn(prefs) + "\".");
            enviarAtivar("Sinal de teste");
        } else {
            reavaliar();
        }
        return START_STICKY;
    }

    private void registrar() {
        if (registrado) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
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

    private void calcular() {
        long agora = System.currentTimeMillis();
        offMin = prefs.getInt("off_min", 15);
        onMin = prefs.getInt("on_min", 2);
        periodo = Periodo.atual(Periodo.carregar(prefs), agora);
        long pausa = prefs.getLong("pausa_ate", 0);
        if (pausa > agora) {
            modo = M_NORMAL;
            motivo = "Pausado até " + hora(pausa);
        } else if (prefs.getBoolean("pular_carregando", true) && carregando()) {
            modo = M_NORMAL;
            motivo = "Carregando: sem economia";
        } else if (prefs.getBoolean("pular_wifi", true) && wifiConectado()) {
            modo = M_NORMAL;
            motivo = "No Wi-Fi: sem economia";
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
            if (telaLigada() && prefs.getBoolean("tela_interrompe", true)) {
                modo = M_NORMAL;
                motivo = "Aguardando a tela apagar";
            } else {
                modo = M_CICLO;
                motivo = "";
            }
        }
    }

    /** Ajusta tudo ao estado atual. Pode ser chamado a qualquer momento, quantas vezes for. */
    private void reavaliar() {
        if (!prefs.getBoolean("enabled", false)) return;
        calcular();
        int step = prefs.getInt("step", IDLE);
        switch (modo) {
            case M_NORMAL:
                if (step != IDLE) {
                    cancelar(ACTION_TICK);
                    setStep(IDLE);
                }
                if (sinalOff()) {
                    Registro.add(this, motivo + ": enviando \"" + palavraOn(prefs) + "\".");
                    enviarAtivar("Normal");
                    agendarCheck("on");
                }
                fase("normal", 0);
                atualizarNeutro();
                break;

            case M_ECONOMIA:
                cancelar(ACTION_TICK);
                if (step != FIXA) {
                    setStep(FIXA);
                    if (!sinalOff()) {
                        if (dadosLigados()) {
                            Registro.add(this, motivo + ": enviando \"" + palavraOff(prefs) + "\".");
                            enviarDesligar("Economia: " + motivo);
                            agendarCheck("off");
                        } else {
                            Registro.add(this, motivo + ": dados já desligados por você, nada a fazer.");
                        }
                    }
                }
                long fim = periodo != null ? periodo.fimApos(System.currentTimeMillis()) : 0;
                faseAte("economia", fim);
                break;

            case M_CICLO:
                if (step == FIXA) {
                    step = IDLE;
                    setStep(IDLE);
                }
                if (step == IDLE) {
                    if (sinalOff()) {
                        // Já em economia (veio de um horário fixo): segue o ciclo daqui.
                        setStep(OFF);
                        agendarTick(offMin);
                        fase("off", offMin);
                    } else if (telaLigada()) {
                        // Tela acesa e ela não interrompe: o ciclo começa quando a tela apagar.
                        fase("normal", 0);
                        atualizarNeutro();
                    } else {
                        int espera = prefs.getInt("wait_min", 3);
                        Registro.add(this, "Economia começa em " + espera + " min.");
                        setStep(ESPERA);
                        agendarTick(espera);
                        fase("espera", espera);
                        notificar("Pulso", "Tela apagada: economia às " + ate(espera), false);
                    }
                }
                break;
        }
        agendarMudanca();
    }

    private void tick() {
        PowerManager pm = getSystemService(PowerManager.class);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulso:tick");
        wl.acquire(30_000);
        try {
            calcular();
            if (modo != M_CICLO) {
                reavaliar();
                return;
            }
            switch (prefs.getInt("step", IDLE)) {
                case ESPERA:
                case ON:
                    if (!dadosLigados()) {
                        Registro.add(this, "Dados desligados por você: o app não mexe. Tenta de novo às "
                                + ate(offMin + onMin) + ".");
                        setStep(ON);
                        agendarTick(offMin + onMin);
                        fase("pausa_usuario", offMin + onMin);
                        notificar("Pulso", "Dados desligados por você", false);
                        break;
                    }
                    Registro.add(this, "Enviando \"" + palavraOff(prefs) + "\" (" + offMin + " min).");
                    enviarDesligar("Economia até " + ate(offMin));
                    setStep(OFF);
                    agendarTick(offMin);
                    fase("off", offMin);
                    agendarCheck("off");
                    break;
                case OFF:
                    Registro.add(this, "Enviando \"" + palavraOn(prefs) + "\" (" + onMin + " min).");
                    enviarAtivar("Normal até " + ate(onMin));
                    setStep(ON);
                    agendarTick(onMin);
                    fase("on", onMin);
                    agendarCheck("on");
                    break;
                default:
                    reavaliar();
                    break;
            }
            agendarMudanca();
        } finally {
            if (wl.isHeld()) wl.release();
        }
    }

    /** 1 min depois de cada sinal: confere se a rotina obedeceu. Se não religou, reenvia e depois avisa. */
    private void conferir() {
        String tipo = prefs.getString("check", "");
        prefs.edit().remove("check").apply();
        if ("off".equals(tipo)) {
            if (!sinalOff()) return;
            Registro.add(this, "Conferência após \"" + palavraOff(prefs) + "\" → " + estado());
        } else if ("on".equals(tipo) || "on2".equals(tipo)) {
            if (sinalOff()) return;
            if (dadosLigados()) {
                Registro.add(this, "Conferência após \"" + palavraOn(prefs) + "\" → " + estado());
                getSystemService(NotificationManager.class).cancel(ALERTA_ID);
            } else if ("on".equals(tipo)) {
                Registro.add(this, "Dados ainda desligados: reenviando \"" + palavraOn(prefs) + "\".");
                enviarAtivar("Normal (reenviado)");
                agendarCheck("on2");
            } else {
                Registro.add(this, "A rotina não religou os dados.");
                alerta();
            }
        }
    }

    // ---------------- Sinais ----------------

    private boolean sinalOff() {
        return "off".equals(prefs.getString("sinal", "on"));
    }

    private void enviarDesligar(String texto) {
        prefs.edit().putString("sinal", "off").apply();
        Estatistica.inicio(prefs, System.currentTimeMillis());
        notificar(palavraOff(prefs), texto, true);
    }

    private void enviarAtivar(String texto) {
        prefs.edit().putString("sinal", "on").apply();
        Estatistica.fim(prefs, System.currentTimeMillis());
        notificar(palavraOn(prefs), texto, true);
    }

    /** Texto neutro (sem as palavras de sinal), só quando não há sinal de economia ativo. */
    private void atualizarNeutro() {
        if (sinalOff()) return;
        notificar("Pulso", motivo.isEmpty() ? "Ativo" : motivo, false);
    }

    // ---------------- Notificações ----------------

    private Notification construir(String titulo, String texto) {
        PendingIntent abrir = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        boolean pausado = prefs.getLong("pausa_ate", 0) > System.currentTimeMillis();
        Intent acao = new Intent(this, CicloService.class).setAction(pausado ? ACTION_RETOMAR : ACTION_PAUSAR);
        PendingIntent pAcao = PendingIntent.getForegroundService(this, 10, acao,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setContentIntent(abrir)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .addAction(new Notification.Action.Builder(null,
                        pausado ? "Retomar" : "Pausar " + PAUSA_MIN / 60 + " h", pAcao).build())
                .build();
    }

    /** {@code forcar}: publica mesmo se o texto for igual (necessário para os sinais). */
    private void notificar(String titulo, String texto, boolean forcar) {
        if (!forcar && titulo.equals(prefs.getString("notif_titulo", ""))
                && texto.equals(prefs.getString("notif_texto", ""))) return;
        prefs.edit().putString("notif_titulo", titulo).putString("notif_texto", texto).apply();
        getSystemService(NotificationManager.class).notify(NOTIF_ID, construir(titulo, texto));
    }

    private void primeiroPlano(Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void alerta() {
        PendingIntent abrir = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ALERTA)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("A rotina não religou os dados")
                .setContentText("Confira a rotina do \"" + palavraOn(prefs) + "\" em Modos e Rotinas.")
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(ALERTA_ID, n);
    }

    // ---------------- Estado do aparelho ----------------

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

    private void fase(String f, int minutos) {
        faseAte(f, minutos > 0 ? System.currentTimeMillis() + minutos * 60_000L : 0);
    }

    private void faseAte(String f, long ate) {
        prefs.edit().putString("fase", f).putLong("fase_ate", ate).putString("fase_motivo", motivo).apply();
    }

    // ---------------- Alarmes ----------------

    private PendingIntent alarme(String action, int code) {
        Intent i = new Intent(this, CicloService.class).setAction(action);
        return PendingIntent.getForegroundService(this, code, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private int codigo(String action) {
        return ACTION_TICK.equals(action) ? 1 : ACTION_CHECK.equals(action) ? 2 : 3;
    }

    private void agendarElapsed(String action, long emMs) {
        AlarmManager am = getSystemService(AlarmManager.class);
        long at = SystemClock.elapsedRealtime() + emMs;
        PendingIntent pi = alarme(action, codigo(action));
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        }
    }

    private void agendarTick(int minutos) {
        agendarElapsed(ACTION_TICK, Math.max(1, minutos) * 60_000L);
    }

    private void agendarCheck(String tipo) {
        prefs.edit().putString("check", tipo).apply();
        agendarElapsed(ACTION_CHECK, 60_000L);
    }

    /** Reavalia na hora em que um horário começa/termina ou a pausa acaba. */
    private void agendarMudanca() {
        long agora = System.currentTimeMillis();
        long prox = Periodo.proximaMudanca(Periodo.carregar(prefs), agora);
        long pausa = prefs.getLong("pausa_ate", 0);
        if (pausa > agora) prox = Math.min(prox, pausa);
        AlarmManager am = getSystemService(AlarmManager.class);
        PendingIntent pi = alarme(ACTION_REAVALIAR, 3);
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

    private void cancelar(String action) {
        getSystemService(AlarmManager.class).cancel(alarme(action, codigo(action)));
    }

    private void setStep(int s) {
        prefs.edit().putInt("step", s).apply();
    }

    // ---------------- Ciclo de vida ----------------

    private void parar(boolean anotar) {
        cancelar(ACTION_TICK);
        cancelar(ACTION_CHECK);
        cancelar(ACTION_REAVALIAR);
        desregistrar();
        setStep(IDLE);
        motivo = "";
        fase("idle", 0);
        if (anotar) Registro.add(this, "Desligado.");
        if (sinalOff()) {
            Registro.add(this, "Enviando \"" + palavraOn(prefs) + "\" antes de parar.");
            enviarAtivar("Normal");
            h.postDelayed(this::sair, 5000);
        } else {
            sair();
        }
    }

    private void sair() {
        if (prefs.getBoolean("enabled", false)) return; // religado nesse meio tempo
        prefs.edit().remove("notif_titulo").remove("notif_texto").apply();
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
