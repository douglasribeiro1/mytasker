package com.douglasribeiro.mytasker;

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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import rikka.shizuku.Shizuku;

/**
 * Enquanto a tela estiver apagada, desliga os dados móveis e liga de tempos em tempos.
 * Quando a tela acende, liga os dados imediatamente.
 */
public class CicloService extends Service {

    private static final String TAG = "MyTasker";
    private static final String CHANNEL_ID = "ciclo";
    private static final int NOTIF_ID = 1;

    private HandlerThread worker;
    private Handler handler;
    private volatile IPrivilegedService privileged;
    private volatile String lastStatus = "Iniciando";
    private volatile long offlineMs = 15 * 60_000L;
    private volatile long onlineMs = 2 * 60_000L;

    private boolean bound;
    private boolean cycling;
    private boolean receiverRegistered;
    private Shizuku.UserServiceArgs userArgs;

    private final Runnable offTick = this::tickOff;
    private final Runnable onTick = this::tickOn;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                handler.post(CicloService.this::startCycle);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                handler.post(CicloService.this::resumeData);
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            privileged = IPrivilegedService.Stub.asInterface(service);
            status("Shizuku conectado");
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
        worker = new HandlerThread("mytasker-ciclo");
        worker.start();
        handler = new Handler(worker.getLooper());

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Ciclo de dados", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        foreground(lastStatus);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        offlineMs = prefs.getInt("offline_min", 15) * 60_000L;
        onlineMs = prefs.getInt("online_min", 2) * 60_000L;

        foreground(lastStatus);

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

        if (!getSystemService(PowerManager.class).isInteractive()) {
            handler.post(this::startCycle);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver);
            receiverRegistered = false;
        }
        cycling = false;
        handler.removeCallbacksAndMessages(null);
        handler.post(() -> {
            // Ao desligar o ciclo, deixa os dados ligados e libera o serviço do Shizuku.
            IPrivilegedService p = privileged;
            if (p != null) {
                try {
                    p.setMobileData(true);
                } catch (RemoteException e) {
                    Log.w(TAG, "Falha ao religar dados", e);
                }
            }
            unbindShizuku();
            worker.quitSafely();
        });
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startCycle() {
        if (cycling) return;
        cycling = true;
        handler.post(offTick);
    }

    private void resumeData() {
        cycling = false;
        handler.removeCallbacks(offTick);
        handler.removeCallbacks(onTick);
        setData(true);
    }

    private void tickOff() {
        if (!cycling) return;
        if (isOnWifi()) {
            status("Wi-Fi ativo: dados mantidos");
        } else {
            setData(false);
        }
        handler.postDelayed(onTick, offlineMs);
    }

    private void tickOn() {
        if (!cycling) return;
        setData(true);
        handler.postDelayed(offTick, onlineMs);
    }

    private void setData(boolean enabled) {
        IPrivilegedService p = privileged;
        if (p == null) {
            status("Aguardando Shizuku");
            return;
        }
        try {
            boolean ok = p.setMobileData(enabled);
            if (!ok) {
                status("Falha no comando svc data");
            } else {
                status(enabled ? "Dados ligados" : "Dados desligados");
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Sem resposta do Shizuku", e);
            status("Sem resposta do Shizuku");
        }
    }

    private boolean isOnWifi() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
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
                    .version(1);
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
        lastStatus = text;
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("MyTasker · ciclo de dados")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }
}
