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
 * Sinaliza a rotina da Samsung por uma notificação persistente que muda de texto:
 * "Desligar" no tempo sem internet, "Ativar" no tempo com internet (as palavras são configuráveis).
 * Rotinas: "Notificação recebida" com a palavra de desligar → dados desligados;
 *          com a palavra de ligar → dados ligados.
 *
 * Tela apaga → espera → Desligar → (tempo sem internet) → Ativar → (tempo com internet) → Desligar...
 * Se a opção estiver marcada, acender a tela interrompe o ciclo e manda "Ativar" na hora.
 * Só manda "Ativar" depois de ter mandado "Desligar": se você mesmo desligou os dados, não religa.
 */
public class CicloService extends Service {

    static final String ACTION_STOP = "com.douglasribeiro.ciclodados.STOP";
    static final String ACTION_TESTE_OFF = "com.douglasribeiro.ciclodados.TESTE_OFF";
    static final String ACTION_TESTE_ON = "com.douglasribeiro.ciclodados.TESTE_ON";
    private static final String ACTION_TICK = "com.douglasribeiro.ciclodados.TICK";
    private static final String CHANNEL = "sinal";
    private static final int NOTIF_ID = 1;

    private static final int IDLE = 0, ESPERA = 1, OFF_CONF = 2, OFF = 3, ON_CONF = 4, ON = 5;

    private SharedPreferences prefs;
    private boolean receiverOn;
    private boolean emPrimeiroPlano;

    private final BroadcastReceiver telaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) telaApagou();
            else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) telaAcendeu();
        }
    };

    static String palavraOff(SharedPreferences p) { return p.getString("palavra_off", "Desligar"); }
    static String palavraOn(SharedPreferences p) { return p.getString("palavra_on", "Ativar"); }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Registro.prefs(this);
        // Canal visível, mas sem som nem vibração.
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Sinal para a rotina", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        // Sempre em primeiro plano antes de tudo: o alarme inicia o serviço como foreground.
        // Reaproveita o último texto mostrado, para não trocar o sinal à toa.
        if (!emPrimeiroPlano) {
            primeiroPlano(sinal(prefs.getString("notif_titulo", "Ciclo Dados"),
                    prefs.getString("notif_texto", "Aguardando a tela apagar")));
            emPrimeiroPlano = true;
        }
        if (ACTION_STOP.equals(action) || !prefs.getBoolean("enabled", false)) {
            parar(ACTION_STOP.equals(action));
            return START_NOT_STICKY;
        }
        if (!receiverOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(telaReceiver, f);
            receiverOn = true;
        }
        if (ACTION_TICK.equals(action)) tick();
        else if (ACTION_TESTE_OFF.equals(action)) {
            Registro.add(this, "Teste: notificação \"" + palavraOff(prefs) + "\".");
            sinalDesligar(0);
        } else if (ACTION_TESTE_ON.equals(action)) {
            Registro.add(this, "Teste: notificação \"" + palavraOn(prefs) + "\".");
            sinalLigar(0);
        }
        return START_STICKY;
    }

    /** Guarda a fase atual para a tela do app mostrar o status. */
    private void fase(String f, int minutos) {
        prefs.edit().putString("fase", f)
                .putLong("fase_ate", minutos > 0 ? System.currentTimeMillis() + minutos * 60_000L : 0).apply();
    }

    static String hora(long t) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    private static String ate(int minutos) {
        return hora(System.currentTimeMillis() + minutos * 60_000L);
    }

    private boolean telaInterrompe() {
        return prefs.getBoolean("tela_interrompe", true);
    }

    private void telaApagou() {
        if (prefs.getInt("step", IDLE) != IDLE) return; // ciclo já rodando (tela não interrompe)
        int espera = prefs.getInt("wait_min", 3);
        Registro.add(this, "Tela apagou. \"" + palavraOff(prefs) + "\" em " + espera + " min.");
        setStep(ESPERA);
        agendar(espera);
        fase("espera", espera);
        notificar(neutro("Tela apagada: começa às " + ate(espera)));
    }

    private void telaAcendeu() {
        if (!telaInterrompe()) return;
        int step = prefs.getInt("step", IDLE);
        cancelar();
        setStep(IDLE);
        if (step == OFF_CONF || step == OFF) {
            Registro.add(this, "Tela acendeu: \"" + palavraOn(prefs) + "\" para religar os dados.");
            sinalLigar(0);
        } else {
            if (step != IDLE) Registro.add(this, "Tela acendeu: ciclo parado.");
            notificar(neutro("Aguardando a tela apagar"));
        }
        fase("idle", 0);
    }

    private void tick() {
        PowerManager pm = getSystemService(PowerManager.class);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ciclodados:tick");
        wl.acquire(30_000);
        try {
            if (pm.isInteractive() && telaInterrompe()) {
                telaAcendeu();
                return;
            }
            int off = prefs.getInt("off_min", 15);
            int on = prefs.getInt("on_min", 2);
            switch (prefs.getInt("step", IDLE)) {
                case ESPERA:
                case ON:
                    if (!dadosLigados()) {
                        // Você (ou outra coisa) desligou os dados: respeita e só tenta no próximo ciclo.
                        Registro.add(this, "Dados já estavam desligados: nada a fazer agora.");
                        setStep(ON);
                        agendar(off + on);
                        fase("pausa", off + on);
                        notificar(neutro("Dados desligados por você: tenta de novo às " + ate(off + on)));
                        break;
                    }
                    Registro.add(this, "Enviando \"" + palavraOff(prefs) + "\".");
                    sinalDesligar(off);
                    fase("off", off);
                    setStep(OFF_CONF);
                    agendar(1);
                    break;
                case OFF_CONF:
                    Registro.add(this, "1 min após \"" + palavraOff(prefs) + "\" → " + estado() + "   (esperado: DESLIGADOS)");
                    setStep(OFF);
                    agendar(off - 1);
                    break;
                case OFF:
                    Registro.add(this, "Enviando \"" + palavraOn(prefs) + "\".");
                    sinalLigar(on);
                    fase("on", on);
                    setStep(ON_CONF);
                    agendar(1);
                    break;
                case ON_CONF:
                    Registro.add(this, "1 min após \"" + palavraOn(prefs) + "\" → " + estado() + "   (esperado: LIGADOS)");
                    setStep(ON);
                    agendar(on - 1);
                    break;
                default:
                    break;
            }
        } finally {
            if (wl.isHeld()) wl.release();
        }
    }

    // ---- Notificação (o sinal para a rotina) ----

    private Notification sinal(String titulo, String texto) {
        prefs.edit().putString("notif_titulo", titulo).putString("notif_texto", texto).apply();
        PendingIntent abrir = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setContentIntent(abrir)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build();
    }

    /** Textos neutros nunca contêm as palavras de sinal, para não disparar rotina por engano. */
    private Notification neutro(String texto) {
        return sinal("Ciclo Dados", texto);
    }

    private void sinalDesligar(int minutos) {
        String t = minutos > 0 ? "Sem internet até " + ate(minutos) : "Sinal de teste";
        notificar(sinal(palavraOff(prefs), t));
    }

    private void sinalLigar(int minutos) {
        String t = minutos > 0 ? "Com internet até " + ate(minutos) : "Dados de volta";
        notificar(sinal(palavraOn(prefs), t));
    }

    private void primeiroPlano(Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void notificar(Notification n) {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, n);
    }

    // ---- Estado real dos dados (para o histórico) ----

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
        return "dados móveis: " + (dadosLigados() ? "LIGADOS" : "DESLIGADOS") + " | rede em uso: " + rede;
    }

    // ---- Ciclo de vida ----

    private void parar(boolean anotar) {
        cancelar();
        int step = prefs.getInt("step", IDLE);
        setStep(IDLE);
        fase("idle", 0);
        if (receiverOn) {
            unregisterReceiver(telaReceiver);
            receiverOn = false;
        }
        if (anotar) Registro.add(this, "Ciclo desligado.");
        if (step == OFF_CONF || step == OFF) {
            // Estava sem internet: manda o sinal de ligar e espera um pouco antes de tirar a notificação.
            Registro.add(this, "Enviando \"" + palavraOn(prefs) + "\" antes de parar.");
            sinalLigar(0);
            new Handler(Looper.getMainLooper()).postDelayed(this::sair, 5000);
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
        if (receiverOn) {
            unregisterReceiver(telaReceiver);
            receiverOn = false;
        }
        super.onDestroy();
    }

    private void setStep(int s) {
        prefs.edit().putInt("step", s).apply();
    }

    private PendingIntent tickIntent() {
        Intent i = new Intent(this, CicloService.class).setAction(ACTION_TICK);
        return PendingIntent.getForegroundService(this, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void agendar(int minutos) {
        AlarmManager am = getSystemService(AlarmManager.class);
        long at = SystemClock.elapsedRealtime() + Math.max(1, minutos) * 60_000L;
        PendingIntent pi = tickIntent();
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        }
    }

    private void cancelar() {
        getSystemService(AlarmManager.class).cancel(tickIntent());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
