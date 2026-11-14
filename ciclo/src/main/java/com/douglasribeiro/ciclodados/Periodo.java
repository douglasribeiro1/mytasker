package com.douglasribeiro.ciclodados;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Um horário configurado pelo usuário. O primeiro da lista que estiver valendo é o que manda. */
final class Periodo {

    static final String CICLO = "ciclo";       // ciclo com tempos próprios
    static final String ECONOMIA = "economia"; // "Desligar" o tempo todo, com a tela acesa ou apagada
    static final String NORMAL = "normal";     // sem ciclo

    static final String[] LETRAS = {"S", "T", "Q", "Q", "S", "S", "D"}; // seg..dom

    String nome = "Horário";
    int dias = 0b1111111; // bit 0 = segunda ... bit 6 = domingo
    int ini = 23 * 60, fim = 6 * 60; // minutos do dia
    String modo = ECONOMIA;
    int off = 30, on = 2;
    boolean ativo = true;

    // ---- valer agora ----

    static int bitDia(Calendar c) {
        int d = c.get(Calendar.DAY_OF_WEEK); // domingo = 1 ... sábado = 7
        return 1 << ((d + 5) % 7);
    }

    boolean vale(Calendar c) {
        if (!ativo || dias == 0) return false;
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        boolean hoje = (dias & bitDia(c)) != 0;
        if (ini == fim) return hoje; // o dia todo
        if (ini < fim) return hoje && m >= ini && m < fim;
        Calendar ontem = (Calendar) c.clone();
        ontem.add(Calendar.DAY_OF_YEAR, -1);
        boolean veioDeOntem = (dias & bitDia(ontem)) != 0;
        return (hoje && m >= ini) || (veioDeOntem && m < fim); // atravessa a meia-noite
    }

    static Periodo atual(List<Periodo> lista, long agora) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(agora);
        for (Periodo p : lista) if (p.vale(c)) return p;
        return null;
    }

    /** Próximo instante em que algum horário começa ou termina (para reavaliar na hora certa). */
    static long proximaMudanca(List<Periodo> lista, long agora) {
        long melhor = Long.MAX_VALUE;
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(agora);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);
        for (Periodo p : lista) {
            if (!p.ativo) continue;
            for (int d = 0; d <= 8; d++) {
                for (int m : new int[]{p.ini, p.fim}) {
                    Calendar k = (Calendar) base.clone();
                    k.add(Calendar.DAY_OF_YEAR, d);
                    k.set(Calendar.HOUR_OF_DAY, m / 60);
                    k.set(Calendar.MINUTE, m % 60);
                    long t = k.getTimeInMillis();
                    if (t > agora && t < melhor) melhor = t;
                }
            }
        }
        return melhor;
    }

    /** Quando este horário termina, a partir de agora (0 se não souber). */
    long fimApos(long agora) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(agora);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        k.set(Calendar.HOUR_OF_DAY, fim / 60);
        k.set(Calendar.MINUTE, fim % 60);
        if (k.getTimeInMillis() <= agora) k.add(Calendar.DAY_OF_YEAR, 1);
        return ini == fim ? 0 : k.getTimeInMillis();
    }

    // ---- textos ----

    static String hhmm(int m) {
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    String textoDias() {
        if (dias == 0b1111111) return "Todos os dias";
        if (dias == 0b0011111) return "Seg a sex";
        if (dias == 0b1100000) return "Sáb e dom";
        StringBuilder sb = new StringBuilder();
        String[] nomes = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
        for (int i = 0; i < 7; i++) {
            if ((dias & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(nomes[i]);
            }
        }
        return sb.length() == 0 ? "Nenhum dia" : sb.toString();
    }

    String textoHorario() {
        return ini == fim ? "o dia todo" : hhmm(ini) + "–" + hhmm(fim);
    }

    String textoModo() {
        switch (modo) {
            case CICLO: return "Ciclo: " + off + " min economia / " + on + " min normal";
            case NORMAL: return "Sem ciclo (normal)";
            default: return "Economia o tempo todo";
        }
    }

    // ---- salvar ----

    JSONObject json() throws JSONException {
        return new JSONObject().put("nome", nome).put("dias", dias).put("ini", ini).put("fim", fim)
                .put("modo", modo).put("off", off).put("on", on).put("ativo", ativo);
    }

    static Periodo de(JSONObject o) {
        Periodo p = new Periodo();
        p.nome = o.optString("nome", p.nome);
        p.dias = o.optInt("dias", p.dias);
        p.ini = o.optInt("ini", p.ini);
        p.fim = o.optInt("fim", p.fim);
        p.modo = o.optString("modo", p.modo);
        p.off = o.optInt("off", p.off);
        p.on = o.optInt("on", p.on);
        p.ativo = o.optBoolean("ativo", true);
        return p;
    }

    static List<Periodo> deArray(JSONArray a) {
        List<Periodo> l = new ArrayList<>();
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) l.add(de(o));
        }
        return l;
    }

    static JSONArray paraArray(List<Periodo> l) {
        JSONArray a = new JSONArray();
        try {
            for (Periodo p : l) a.put(p.json());
        } catch (JSONException ignored) {
        }
        return a;
    }

    /** Formato antigo (uma lista só, antes das várias automações). */
    static List<Periodo> carregar(SharedPreferences prefs) {
        List<Periodo> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("periodos", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(de(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return l;
    }

    static void salvar(SharedPreferences prefs, List<Periodo> l) {
        JSONArray a = new JSONArray();
        try {
            for (Periodo p : l) a.put(p.json());
        } catch (JSONException ignored) {
        }
        prefs.edit().putString("periodos", a.toString()).apply();
    }
}
