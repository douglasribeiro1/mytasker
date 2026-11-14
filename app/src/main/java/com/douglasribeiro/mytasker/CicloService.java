package com.douglasribeiro.mytasker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.telephony.SubscriptionManager;
import android.util.Log;

import rikka.shizuku.Shizuku;

/**
 * Ciclo com a tela apagada:
 *   tela apaga -> espera o tempo de segurança -> anota o que estava LIGADO (dados e/ou Wi-Fi, conforme o que foi
 *   escolhido no app) -> desliga -> espera "desligado" -> liga -> espera "ligado" -> repete.
 * Nunca liga algo que estava desligado antes. Tela acesa: cancela e religa na hora só o que o ciclo desligou.
 * Os tempos usam AlarmManager, que funciona mesmo com o celular em sono profundo (Doze).
 */
public class CicloService extends Service {

    private static final String TAG = "MyTasker";
    private static final String CHANNEL_ID = "ciclo";
    private static final int NOTIF_ID = 1;
    private static final String ACTION_TICK = "com.douglasribeiro.mytasker.TICK";

    // Próximo passo agendado.
    private static final int STEP_IDLE = 0;
    private static final int STEP_START = 1;   // fim do tempo de segurança
    private static final int STEP_ON = 2;      // fim do período desligado
    private static final int STEP_OFF = 3;     // fim do período ligado

    private static final long RETRY_MS = 20_000L;

    private HandlerThread worker;
    private Handler handler;
    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;
    private volatile IPrivilegedService privileged;

    private boolean bound;
    private boolean receiverRegistered;
    private Shizuku.UserServiceArgs userArgs;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                handler.post(CicloService.this::onScreenOff);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                handler.post(CicloService.this::onScreenOn);
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            privileged = IPrivilegedService.Stub.asInterface(service);
            Log.i(TAG, "Shizuku conectado");
            // Se a tela já está acesa e algo ficou desligado pelo ciclo, religa agora.
            handler.post(() -> {
                if (isScreenOn()) restoreAndClear();
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            privileged = null;
            status("Shizuku desconectado");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        worker = new HandlerThread("mytasker-ciclo");
        worker.start();
        handler = new Handler(worker.getLooper());
        wakeLock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyTasker:ciclo");
        wakeLock.setReferenceCounted(false);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Ciclo de conexão", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        foreground("Ativo");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        foreground(prefs.getString("status", "Ativo"));

        if (!prefs.getBoolean("active", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenReceiver, filter);
            receiverRegistered = true;
        }

        if (!bound && !bindShizuku()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        boolean tick = intent != null && ACTION_TICK.equals(intent.getAction());
        if (tick) {
            // Segura o processador acordado enquanto troca a conexão (no máximo 1 minuto).
            wakeLock.acquire(60_000L);
            handler.post(this::onTick);
        } else {
            handler.post(this::onStartOrSettingsChanged);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        cancelAlarm();
        handler.removeCallbacksAndMessages(null);
        handler.post(() -> {
            // Ao desligar o ciclo, devolve o que o ciclo tinha desligado.
            restoreAndClear();
            unbindShizuku();
            if (wakeLock.isHeld()) wakeLock.release();
            worker.quitSafely();
        });
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- máquina de estados (tudo roda na thread "worker") ----------

    private void onStartOrSettingsChanged() {
        if (isScreenOn()) {
            restoreAndClear();
            status("Tela acesa: conexão mantida");
        } else if (step() == STEP_IDLE) {
            onScreenOff();
        }
    }

    private void onScreenOff() {
        long grace = minutes("grace_min", 3);
        setStep(STEP_START);
        schedule(grace);
        status("Tela apagada: ciclo começa em " + grace / 60_000 + " min");
    }

    private void onScreenOn() {
        cancelAlarm();
        restoreAndClear();
        status("Tela acesa: conexão mantida");
    }

    private void onTick() {
        try {
            if (isScreenOn()) {
                onScreenOn();
                return;
            }
            if (privileged == null) {
                status("Aguardando Shizuku");
                schedule(RETRY_MS);
                return;
            }
            switch (step()) {
                case STEP_START:
                case STEP_OFF:
                    turnOff();
                    break;
                case STEP_ON:
                    turnOn();
                    break;
                default:
                    break;
            }
        } finally {
            if (wakeLock.isHeld()) wakeLock.release();
        }
    }

    /** Desliga só o que está ligado agora e foi escolhido no app. */
    private void turnOff() {
        boolean mobile = prefs.getBoolean("cycle_mobile", true) && isMobileDataOn();
        boolean wifi = prefs.getBoolean("cycle_wifi", false) && isWifiOn();

        if (!mobile && !wifi) {
            // Usuário (ou o sistema) já deixou tudo desligado: respeita e para até a próxima vez que a tela apagar.
            setTargets(false, false);
            setStep(STEP_IDLE);
            status("Nada ligado para ciclar");
            return;
        }

        boolean ok = true;
        if (mobile) ok &= call(true, false);
        if (wifi) ok &= call(false, false);
        setTargets(mobile, wifi);
        setStep(STEP_ON);
        schedule(minutes("offline_min", 15));
        status((ok ? "Desligado: " : "Falha ao desligar: ") + describe(mobile, wifi));
    }

    /** Religa só o que o ciclo desligou. */
    private void turnOn() {
        boolean mobile = prefs.getBoolean("off_mobile", false);
        boolean wifi = prefs.getBoolean("off_wifi", false);
        boolean ok = true;
        if (mobile) ok &= call(true, true);
        if (wifi) ok &= call(false, true);
        setTargets(false, false);
        setStep(STEP_OFF);
        schedule(minutes("online_min", 2));
        status((ok ? "Ligado para sincronizar: " : "Falha ao ligar: ") + describe(mobile, wifi));
    }

    private void restoreAndClear() {
        setStep(STEP_IDLE);
        boolean mobile = prefs.getBoolean("off_mobile", false);
        boolean wifi = prefs.getBoolean("off_wifi", false);
        if (!mobile && !wifi) return;
        if (privileged == null) return; // mantém anotado; religa quando o Shizuku voltar
        if (mobile) call(true, true);
        if (wifi) call(false, true);
        setTargets(false, false);
    }

    // ---------- auxiliares ----------

    private boolean call(boolean mobile, boolean enable) {
        IPrivilegedService p = privileged;
        if (p == null) return false;
        try {
            return mobile ? p.setMobileData(enable) : p.setWifi(enable);
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Sem resposta do Shizuku", e);
            return false;
        }
    }

    private boolean isMobileDataOn() {
        IPrivilegedService p = privileged;
        if (p == null) return false;
        try {
            return p.isMobileDataEnabled(SubscriptionManager.getDefaultDataSubscriptionId());
        } catch (RemoteException | RuntimeException e) {
            return false;
        }
    }

    private boolean isWifiOn() {
        WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
        return wm != null && wm.isWifiEnabled();
    }

    private boolean isScreenOn() {
        return getSystemService(PowerManager.class).isInteractive();
    }

    private long minutes(String key, int def) {
        return Math.max(1, prefs.getInt(key, def)) * 60_000L;
    }

    private int step() {
        return prefs.getInt("step", STEP_IDLE);
    }

    private void setStep(int step) {
        prefs.edit().putInt("step", step).apply();
    }

    private void setTargets(boolean mobile, boolean wifi) {
        prefs.edit().putBoolean("off_mobile", mobile).putBoolean("off_wifi", wifi).commit();
    }

    private static String describe(boolean mobile, boolean wifi) {
        if (mobile && wifi) return "dados e Wi-Fi";
        return mobile ? "dados móveis" : "Wi-Fi";
    }

    private PendingIntent tickIntent() {
        Intent i = new Intent(this, CicloService.class).setAction(ACTION_TICK);
        return PendingIntent.getService(this, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void schedule(long delayMs) {
        AlarmManager am = getSystemService(AlarmManager.class);
        long at = SystemClock.elapsedRealtime() + delayMs;
        PendingIntent pi = tickIntent();
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        }
    }

    private void cancelAlarm() {
        getSystemService(AlarmManager.class).cancel(tickIntent());
    }

    private boolean bindShizuku() {
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                status("Shizuku indisponível");
                return false;
            }
            userArgs = new Shizuku.UserServiceArgs(
                    new ComponentName(getPackageName(), PrivilegedService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("privileged")
                    .debuggable(false)
                    .version(2);
            Shizuku.bindUserService(userArgs, connection);
            bound = true;
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Falha ao conectar ao Shizuku", e);
            status("Falha ao conectar ao Shizuku");
            return false;
        }
    }

    private void unbindShizuku() {
        if (!bound) return;
        try {
            IPrivilegedService p = privileged;
            if (p != null) p.destroy();
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "destroy do serviço privilegiado", e);
        }
        try {
            Shizuku.unbindUserService(userArgs, connection, true);
        } catch (RuntimeException e) {
            Log.w(TAG, "unbind", e);
        }
        bound = false;
        privileged = null;
    }

    private void foreground(String text) {
        Notification notification = buildNotification(text);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void status(String text) {
        prefs.edit().putString("status", text).apply();
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("MyTasker · ciclo de conexão")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }
}
