package com.douglasribeiro.ciclodados;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Confere no GitHub se saiu uma versão nova (Releases do repositório público). */
final class Atualizacao {

    static final String PAGINA = "https://github.com/douglasribeiro1/mytasker/releases/latest";
    private static final String API = "https://api.github.com/repos/douglasribeiro1/mytasker/releases/latest";
    private static final long INTERVALO = 12 * 3_600_000L;

    interface Fim {
        /** erro != null se não deu para consultar. */
        void pronto(String erro);
    }

    private Atualizacao() {
    }

    /** Número da versão deste app ("1.0.21" → 21). */
    static int atual(Context c) {
        try {
            return numero(c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName);
        } catch (Exception e) {
            return 0;
        }
    }

    static String nomeAtual(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    static int numero(String v) {
        if (v == null) return 0;
        String[] partes = v.replaceAll("[^0-9.]", "").split("\\.");
        try {
            return Integer.parseInt(partes[partes.length - 1]);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Versão nova disponível (nome), ou null. */
    static String disponivel(Context c) {
        SharedPreferences p = Registro.prefs(c);
        String v = p.getString("upd_versao", null);
        return v != null && numero(v) > atual(c) ? v : null;
    }

    static String link(Context c) {
        return Registro.prefs(c).getString("upd_url", PAGINA);
    }

    /** Consulta em segundo plano. Sem forcar, no máximo a cada 12 h. */
    static void verificar(Context c, boolean forcar, Fim fim) {
        SharedPreferences p = Registro.prefs(c);
        long agora = System.currentTimeMillis();
        if (!forcar && agora - p.getLong("upd_quando", 0) < INTERVALO) {
            if (fim != null) fim.pronto(null);
            return;
        }
        Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String erro = null;
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(API).openConnection();
                con.setConnectTimeout(10_000);
                con.setReadTimeout(10_000);
                con.setRequestProperty("Accept", "application/vnd.github+json");
                int code = con.getResponseCode();
                if (code == 404) {
                    erro = "Nenhuma versão publicada ainda.";
                } else if (code != 200) {
                    erro = "O GitHub respondeu " + code + ".";
                } else {
                    JSONObject o = new JSONObject(ler(con.getInputStream()));
                    String versao = o.optString("tag_name", "").replaceFirst("^v", "");
                    String url = o.optString("html_url", PAGINA);
                    JSONArray assets = o.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            String u = assets.getJSONObject(i).optString("browser_download_url", "");
                            if (u.endsWith(".apk")) url = u;
                        }
                    }
                    p.edit().putString("upd_versao", versao).putString("upd_url", url)
                            .putLong("upd_quando", System.currentTimeMillis()).apply();
                }
            } catch (Exception e) {
                erro = "Sem conexão com o GitHub.";
            } finally {
                if (con != null) con.disconnect();
            }
            String e = erro;
            if (fim != null) ui.post(() -> fim.pronto(e));
        }).start();
    }

    private static String ler(InputStream in) throws Exception {
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = is.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        }
    }
}
