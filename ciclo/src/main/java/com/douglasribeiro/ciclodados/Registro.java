package com.douglasribeiro.ciclodados;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Histórico com horário, guardado no aparelho, para você conferir depois o que aconteceu com a tela apagada. */
final class Registro {
    static final String PREFS = "ciclodados";
    private static final String KEY = "log";
    private static final int MAX_LINHAS = 300;

    private Registro() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized void add(Context c, String msg) {
        String hora = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(new Date());
        SharedPreferences p = prefs(c);
        String atual = p.getString(KEY, "");
        String novo = hora + "  " + msg + (atual.isEmpty() ? "" : "\n" + atual);
        String[] linhas = novo.split("\n");
        if (linhas.length > MAX_LINHAS) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_LINHAS; i++) {
                if (i > 0) sb.append('\n');
                sb.append(linhas[i]);
            }
            novo = sb.toString();
        }
        p.edit().putString(KEY, novo).apply();
    }

    /** Mais recente primeiro. */
    static String texto(Context c) {
        return prefs(c).getString(KEY, "");
    }

    static void limpar(Context c) {
        prefs(c).edit().remove(KEY).apply();
    }
}
