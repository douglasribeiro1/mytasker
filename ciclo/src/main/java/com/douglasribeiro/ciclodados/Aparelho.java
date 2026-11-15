package com.douglasribeiro.ciclodados;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;

/** Leituras do estado do celular, usadas pelo serviço e pela tela de diagnóstico. */
final class Aparelho {

    /** Broadcast do sistema (fixo) que diz se o roteador Wi-Fi está ligado. 13 = ligado, 12 = ligando. */
    static final String ROTEADOR_ACAO = "android.net.wifi.WIFI_AP_STATE_CHANGED";

    private Aparelho() {
    }

    static boolean telaLigada(Context c) {
        return c.getSystemService(PowerManager.class).isInteractive();
    }

    static boolean bloqueado(Context c) {
        return c.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    static boolean carregando(Context c) {
        return c.getSystemService(BatteryManager.class).isCharging();
    }

    static boolean wifiConectado(Context c) {
        NetworkCapabilities nc = capacidades(c);
        return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    static boolean dadosLigados(Context c) {
        try {
            return c.getSystemService(TelephonyManager.class).isDataEnabled();
        } catch (RuntimeException e) {
            return Settings.Global.getInt(c.getContentResolver(), "mobile_data", 1) == 1;
        }
    }

    /** Chamada em andamento ou tocando: ligação comum, WhatsApp, Meet, Telegram... (qualquer app de voz). */
    static boolean emChamada(Context c) {
        int m = c.getSystemService(AudioManager.class).getMode();
        if (m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION
                || m == AudioManager.MODE_RINGTONE) return true;
        if (Build.VERSION.SDK_INT >= 30 && m == AudioManager.MODE_CALL_SCREENING) return true;
        return Build.VERSION.SDK_INT >= 33
                && (m == AudioManager.MODE_CALL_REDIRECT || m == AudioManager.MODE_COMMUNICATION_REDIRECT);
    }

    /** Roteador Wi-Fi (compartilhar internet) ligado. Não há API pública: lê o aviso fixo do sistema. */
    static boolean roteadorLigado(Context c) {
        try {
            IntentFilter f = new IntentFilter(ROTEADOR_ACAO);
            Intent i = Build.VERSION.SDK_INT >= 33
                    ? c.getApplicationContext().registerReceiver(null, f, Context.RECEIVER_EXPORTED)
                    : c.getApplicationContext().registerReceiver(null, f);
            if (i != null) {
                int s = i.getIntExtra("wifi_state", -1);
                if (s >= 0) return s == 12 || s == 13;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            WifiManager wm = c.getApplicationContext().getSystemService(WifiManager.class);
            Method m = wm.getClass().getDeclaredMethod("isWifiApEnabled");
            m.setAccessible(true);
            return Boolean.TRUE.equals(m.invoke(wm));
        } catch (Exception ignored) {
            return false;
        }
    }

    static String rede(Context c) {
        NetworkCapabilities nc = capacidades(c);
        if (nc == null) return "nenhuma";
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "dados móveis";
        return "outra";
    }

    private static NetworkCapabilities capacidades(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        return n != null ? cm.getNetworkCapabilities(n) : null;
    }
}
