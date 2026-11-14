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
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;

/**
 * Teste: com a tela apagada, abre o app de tempos em tempos para que uma rotina da Samsung
 * ("se Ciclo Dados estiver aberto → desligar dados móveis") desligue os dados.
 *
 * Tela apaga → espera → abre a janela (dados devem desligar) → fecha a janela (a rotina termina
 * e os dados voltam) → espera → abre de novo... Acender a tela fecha a janela e para tudo.
 * Um minuto depois de abrir e de fechar, anota no histórico o estado real dos dados.
 */
public class CicloService extends Service {

    static final String ACTION_STOP = "com.douglasribeiro.ciclodados.STOP";
    private static final String ACTION_TICK = "com.douglasribeiro.ciclodados.TICK";
    private static final String CHANNEL = "ciclo";
    private static final int NOTIF_ID = 1;

    private static final int IDLE = 0, ESPERA = 1, ABERTO_CONF = 2, ABERTO = 3, FECHADO_CONF = 4, FECHADO = 5;

    private SharedPreferences prefs;
    private boolean receiverOn;
    /** true enquanto a tela está acesa porque o próprio app acendeu (não é você usando o celular). */
    static volatile boolean gatilhoAtivo;

    private AudioTrack audio;
    private MediaSession sessao;

    /** "midia" (áudio silencioso) ou "tela" (acende a tela e deixa o app aberto). */
    private boolean modoMidia() {
        return !"tela".equals(prefs.getString("modo", "midia"));
    }

    private final BroadcastReceiver telaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(a)) {
                // Desbloqueio é sempre você, nunca o gatilho.
                gatilhoAtivo = false;
                telaAcendeu();
            } else if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                if (gatilhoAtivo) {
                    gatilhoAtivo = false;
                    Registro.add(CicloService.this, "Tela apagou de novo (fim do gatilho). App continua aberto.");
                } else {
                    telaApagou();
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                if (gatilhoAtivo) Registro.add(CicloService.this, "Tela acesa pelo gatilho.");
                else telaAcendeu();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Registro.prefs(this);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Ciclo Dados", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        // Sempre entra em primeiro plano antes de qualquer coisa: o alarme inicia o serviço como foreground.
        primeiroPlano("Aguardando a tela apagar");
        if (ACTION_STOP.equals(action) || !prefs.getBoolean("enabled", false)) {
            parar(ACTION_STOP.equals(action));
            return START_NOT_STICKY;
        }
        if (!receiverOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(telaReceiver, f);
            receiverOn = true;
        }
        if (ACTION_TICK.equals(action)) tick();
        return START_STICKY;
    }

    private void telaApagou() {
        int espera = prefs.getInt("wait_min", 1);
        Registro.add(this, "Tela apagou. Abre o app em " + espera + " min.");
        setStep(ESPERA);
        agendar(espera);
        notificar("Tela apagada: aguardando " + espera + " min");
    }

    private void telaAcendeu() {
        gatilhoAtivo = false;
        int step = prefs.getInt("step", IDLE);
        cancelar();
        if (step == ABERTO_CONF || step == ABERTO) {
            encerrarGatilho();
            Registro.add(this, "Tela acendeu: gatilho encerrado, a rotina deve religar os dados.");
        } else if (step != IDLE) {
            Registro.add(this, "Tela acendeu: ciclo parado.");
        }
        setStep(IDLE);
        notificar("Aguardando a tela apagar");
    }

    private void tick() {
        PowerManager pm = getSystemService(PowerManager.class);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ciclodados:tick");
        wl.acquire(30_000);
        try {
            if (pm.isInteractive() && !gatilhoAtivo) {
                telaAcendeu();
                return;
            }
            int step = prefs.getInt("step", IDLE);
            int aberto = prefs.getInt("open_min", 5);
            int fechado = prefs.getInt("closed_min", 2);
            switch (step) {
                case ESPERA:
                case FECHADO:
                    if (modoMidia()) tocarSilencio(); else abrirJanela();
                    setStep(ABERTO_CONF);
                    agendar(1);
                    notificar(modoMidia() ? "Tocando silêncio: dados devem estar desligados"
                            : "App aberto: dados devem estar desligados");
                    break;
                case ABERTO_CONF:
                    Registro.add(this, "1 min após abrir → " + estado() + "   (esperado: DESLIGADOS)");
                    setStep(ABERTO);
                    agendar(aberto - 1);
                    break;
                case ABERTO:
                    encerrarGatilho();
                    Registro.add(this, modoMidia() ? "Parando o áudio silencioso." : "Fechando o app.");
                    setStep(FECHADO_CONF);
                    agendar(1);
                    notificar("App fechado: dados devem voltar");
                    break;
                case FECHADO_CONF:
                    Registro.add(this, "1 min após fechar → " + estado() + "   (esperado: LIGADOS)");
                    setStep(FECHADO);
                    agendar(fechado - 1);
                    break;
                default:
                    break;
            }
        } finally {
            if (wl.isHeld()) wl.release();
        }
    }

    private void abrirJanela() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Registro.add(this, "ERRO: falta a permissão \"Aparecer sobre outros apps\". O Android pode bloquear a abertura.");
        }
        gatilhoAtivo = true;
        try {
            Intent i = new Intent(this, JanelaActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
            Registro.add(this, "Abrindo o app (pedido enviado ao Android).");
        } catch (RuntimeException e) {
            gatilhoAtivo = false;
            Registro.add(this, "ERRO ao abrir o app: " + e.getClass().getSimpleName());
        }
    }

    /** Estado real: a chave de dados móveis e a rede que está em uso. */
    private String estado() {
        String dados;
        try {
            TelephonyManager tm = getSystemService(TelephonyManager.class);
            dados = tm.isDataEnabled() ? "LIGADOS" : "DESLIGADOS";
        } catch (RuntimeException e) {
            int v = Settings.Global.getInt(getContentResolver(), "mobile_data", -1);
            dados = v == 1 ? "LIGADOS" : v == 0 ? "DESLIGADOS" : "desconhecido";
        }
        String rede = "nenhuma";
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities nc = n != null ? cm.getNetworkCapabilities(n) : null;
        if (nc != null) {
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) rede = "Wi-Fi";
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) rede = "dados móveis";
            else rede = "outra";
        }
        String g = modoMidia() ? "áudio: " + (audio != null && audio.getPlayState() == AudioTrack.PLAYSTATE_PLAYING ? "tocando" : "parado")
                : "janela: " + JanelaActivity.estado;
        return "dados móveis: " + dados + " | rede em uso: " + rede + " | " + g;
    }

    private void parar(boolean anotar) {
        cancelar();
        encerrarGatilho();
        setStep(IDLE);
        if (receiverOn) {
            unregisterReceiver(telaReceiver);
            receiverOn = false;
        }
        if (anotar) Registro.add(this, "Ciclo desligado.");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** Fecha a janela e para o áudio, o que estiver ativo. */
    private void encerrarGatilho() {
        JanelaActivity.fechar();
        pararSilencio();
    }

    /**
     * Toca silêncio (zeros) em loop como mídia, com uma sessão de mídia "tocando", para a condição
     * "A mídia está sendo reproduzida" da Samsung. Não pede foco de áudio: não interrompe sua música.
     */
    private void tocarSilencio() {
        pararSilencio();
        try {
            int rate = 8000;
            byte[] zeros = new byte[rate * 2]; // 1 s, mono, 16 bits
            audio = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(zeros.length)
                    .build();
            audio.write(zeros, 0, zeros.length);
            audio.setLoopPoints(0, rate, -1);
            audio.play();

            sessao = new MediaSession(this, "ciclodados");
            sessao.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .setActions(PlaybackState.ACTION_STOP)
                    .build());
            sessao.setActive(true);
            Registro.add(this, "Tocando áudio silencioso.");
        } catch (RuntimeException e) {
            Registro.add(this, "ERRO ao tocar o áudio: " + e.getClass().getSimpleName());
            pararSilencio();
        }
    }

    private void pararSilencio() {
        if (audio != null) {
            try { audio.stop(); } catch (RuntimeException ignored) {}
            audio.release();
            audio = null;
        }
        if (sessao != null) {
            sessao.setActive(false);
            sessao.release();
            sessao = null;
        }
    }

    @Override
    public void onDestroy() {
        pararSilencio();
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

    private Notification notificacao(String texto) {
        PendingIntent abrir = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Ciclo Dados (teste)")
                .setContentText(texto)
                .setContentIntent(abrir)
                .setOngoing(true)
                .build();
    }

    private void primeiroPlano(String texto) {
        Notification n = notificacao(texto);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void notificar(String texto) {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, notificacao(texto));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
