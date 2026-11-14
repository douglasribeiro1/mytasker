package com.douglasribeiro.ciclodados;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Soma, por dia, o tempo em economia (do sinal "Desligar" até o "Ativar"). Guarda os últimos 8 dias. */
final class Estatistica {

    private Estatistica() {}

    static void inicio(SharedPreferences p, String k, long t) {
        if (p.getLong(k + "eco_desde", 0) <= 0) p.edit().putLong(k + "eco_desde", t).apply();
    }

    static void fim(SharedPreferences p, String k, long t) {
        long s = p.getLong(k + "eco_desde", 0);
        p.edit().remove(k + "eco_desde").apply();
        if (s <= 0 || t <= s) return;
        JSONObject dias = ler(p, k);
        while (s < t) {
            long meiaNoite = meiaNoiteApos(s);
            long e = Math.min(t, meiaNoite);
            String dia = chave(s);
            try {
                dias.put(dia, dias.optLong(dia, 0) + (e - s));
            } catch (JSONException ignored) {
            }
            s = e;
        }
        podar(dias);
        p.edit().putString(k + "eco_dias", dias.toString()).apply();
    }

    /** Milissegundos em economia no dia de {@code dia} (inclui o trecho em andamento). */
    static long noDia(SharedPreferences p, String k, long dia, long agora) {
        long total = ler(p, k).optLong(chave(dia), 0);
        long s = p.getLong(k + "eco_desde", 0);
        if (s > 0 && chave(dia).equals(chave(agora))) {
            total += agora - Math.max(s, meiaNoiteAntes(agora));
        }
        return total;
    }

    /** Texto dos últimos 7 dias, do mais recente ao mais antigo. */
    static List<String> semana(SharedPreferences p, String k, long agora) {
        List<String> l = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("EEE dd/MM", new Locale("pt", "BR"));
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(agora);
        for (int i = 0; i < 7; i++) {
            l.add(f.format(c.getTime()) + ": " + duracao(noDia(p, k, c.getTimeInMillis(), agora)));
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return l;
    }

    static String duracao(long ms) {
        long min = ms / 60_000;
        if (min < 60) return min + " min";
        return (min / 60) + " h " + String.format(Locale.ROOT, "%02d", min % 60) + " min";
    }

    private static JSONObject ler(SharedPreferences p, String k) {
        try {
            return new JSONObject(p.getString(k + "eco_dias", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void podar(JSONObject dias) {
        TreeSet<String> ks = new TreeSet<>();
        for (Iterator<String> it = dias.keys(); it.hasNext(); ) ks.add(it.next());
        while (ks.size() > 8) dias.remove(ks.pollFirst());
    }

    private static String chave(long t) {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(t));
    }

    private static long meiaNoiteAntes(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long meiaNoiteApos(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(meiaNoiteAntes(t));
        c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }
}
