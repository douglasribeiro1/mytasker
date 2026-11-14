package com.douglasribeiro.ciclodados;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Uma automação: um par de palavras na notificação ("Desligar" / "Ativar") com ciclo, condições e horários
 * próprios. Cada uma tem a sua notificação; uma rotina da Samsung lê cada palavra.
 */
final class Automacao {

    /** Textos fixos que o app escreve nas notificações. Nenhuma palavra pode estar contida neles. */
    static final String TEXTOS_FIXOS = "pulso · até 00:00 · teste · reenviado · em funcionamento · "
            + "pausado até · pausar 1 h · retomar";

    int id;
    String nome = "Dados móveis";
    String off = "Desligar", on = "Ativar";
    boolean ativo = true;
    boolean dados = true; // controla os dados móveis: respeitar, conferir, resumo
    int espera = 3, offMin = 15, onMin = 2;
    boolean tela = true, wifi = true, carregando = true;
    List<Periodo> periodos = new ArrayList<>();

    /** Prefixo das chaves de estado desta automação. */
    String k() {
        return "a" + id + "_";
    }

    // ---------------- salvar ----------------

    JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("nome", nome).put("off", off).put("on", on)
                .put("ativo", ativo).put("dados", dados).put("espera", espera).put("offMin", offMin)
                .put("onMin", onMin).put("tela", tela).put("wifi", wifi).put("carregando", carregando)
                .put("periodos", Periodo.paraArray(periodos));
    }

    static Automacao de(JSONObject o) {
        Automacao a = new Automacao();
        a.id = o.optInt("id", 1);
        a.nome = o.optString("nome", a.nome);
        a.off = o.optString("off", a.off);
        a.on = o.optString("on", a.on);
        a.ativo = o.optBoolean("ativo", true);
        a.dados = o.optBoolean("dados", false);
        a.espera = o.optInt("espera", a.espera);
        a.offMin = o.optInt("offMin", a.offMin);
        a.onMin = o.optInt("onMin", a.onMin);
        a.tela = o.optBoolean("tela", true);
        a.wifi = o.optBoolean("wifi", true);
        a.carregando = o.optBoolean("carregando", true);
        a.periodos = Periodo.deArray(o.optJSONArray("periodos"));
        return a;
    }

    static synchronized List<Automacao> carregar(SharedPreferences p) {
        if (!p.contains("automacoes")) migrar(p);
        List<Automacao> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("automacoes", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(de(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return l;
    }

    static synchronized void salvar(SharedPreferences p, List<Automacao> l) {
        JSONArray a = new JSONArray();
        try {
            for (Automacao x : l) a.put(x.json());
        } catch (JSONException ignored) {
        }
        p.edit().putString("automacoes", a.toString()).apply();
    }

    static Automacao porId(List<Automacao> l, int id) {
        for (Automacao a : l) if (a.id == id) return a;
        return null;
    }

    static int novoId(List<Automacao> l) {
        int m = 0;
        for (Automacao a : l) m = Math.max(m, a.id);
        return m + 1;
    }

    /** Converte a configuração de antes (uma automação só) na automação "Dados móveis". */
    private static void migrar(SharedPreferences p) {
        Automacao a = new Automacao();
        a.id = 1;
        a.dados = true;
        a.off = p.getString("palavra_off", "Desligar");
        a.on = p.getString("palavra_on", "Ativar");
        a.espera = p.getInt("wait_min", 3);
        a.offMin = p.getInt("off_min", 15);
        a.onMin = p.getInt("on_min", 2);
        a.tela = p.getBoolean("tela_interrompe", true);
        a.wifi = p.getBoolean("pular_wifi", true);
        a.carregando = p.getBoolean("pular_carregando", true);
        a.periodos = Periodo.carregar(p);
        SharedPreferences.Editor e = p.edit();
        String k = a.k();
        e.putInt(k + "step", 0);
        e.putString(k + "sinal", p.getString("sinal", "on"));
        if (p.contains("eco_dias")) e.putString(k + "eco_dias", p.getString("eco_dias", "{}"));
        if (p.getLong("eco_desde", 0) > 0) e.putLong(k + "eco_desde", p.getLong("eco_desde", 0));
        e.apply();
        List<Automacao> l = new ArrayList<>();
        l.add(a);
        salvar(p, l);
    }

    // ---------------- validação ----------------

    /** Devolve o problema com as palavras (entre todas as automações), ou null se estiver tudo certo. */
    static String conflito(List<Automacao> l) {
        List<String> ws = new ArrayList<>(), donos = new ArrayList<>();
        for (Automacao a : l) {
            ws.add(a.off);
            donos.add(a.nome);
            ws.add(a.on);
            donos.add(a.nome);
        }
        for (int i = 0; i < ws.size(); i++) {
            String w = ws.get(i).trim().toLowerCase(Locale.ROOT);
            if (w.isEmpty()) return "\"" + donos.get(i) + "\": as duas palavras são obrigatórias.";
            if (TEXTOS_FIXOS.contains(w)) {
                return "\"" + ws.get(i) + "\" aparece nos textos que o app já escreve. Use outra palavra.";
            }
            for (int j = 0; j < ws.size(); j++) {
                if (i == j) continue;
                String o = ws.get(j).trim().toLowerCase(Locale.ROOT);
                if (o.contains(w)) {
                    return "\"" + ws.get(i) + "\" (" + donos.get(i) + ") está dentro de \"" + ws.get(j) + "\" ("
                            + donos.get(j) + "). As duas rotinas disparariam juntas.";
                }
            }
        }
        return null;
    }
}
