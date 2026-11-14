package com.douglasribeiro.ciclodados;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 1;

    private SharedPreferences prefs;
    private boolean escuro;
    private int corFundo, corCartao, corTexto, corSuave, corDestaque, corAlerta;

    private TextView statusTitulo, statusDetalhe, logView;
    private LinearLayout avisos;
    private EditText waitField, offField, onField, palavraOffField, palavraOnField;
    private CheckBox telaBox;
    private Switch cicloSwitch;
    private boolean suprimir;

    private final SharedPreferences.OnSharedPreferenceChangeListener mudou = (p, k) -> {
        if ("fase".equals(k) || "fase_ate".equals(k) || "enabled".equals(k) || "log".equals(k)) {
            runOnUiThread(this::atualizar);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Registro.prefs(this);
        escuro = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        corFundo = escuro ? 0xFF000000 : 0xFFF2F2F7;
        corCartao = escuro ? 0xFF1C1C1E : 0xFFFFFFFF;
        corTexto = escuro ? 0xFFF2F2F2 : 0xFF111111;
        corSuave = escuro ? 0xFFA0A0A5 : 0xFF6B6B70;
        corDestaque = escuro ? 0xFF5AA2FF : 0xFF1F5FA8;
        corAlerta = escuro ? 0xFFFF8A65 : 0xFFC0392B;
        montarTela();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.registerOnSharedPreferenceChangeListener(mudou);
        atualizar();
    }

    @Override
    protected void onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(mudou);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        atualizar();
    }

    // ---------------- Tela ----------------

    private void montarTela() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        root.setBackgroundColor(corFundo);

        TextView titulo = texto("Ciclo Dados", 28, corTexto);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulo.setPadding(dp(4), dp(8), 0, dp(16));
        root.addView(titulo);

        // Status + liga/desliga
        LinearLayout status = cartao(root);
        statusTitulo = texto("", 22, corTexto);
        statusTitulo.setTypeface(Typeface.DEFAULT_BOLD);
        status.addView(statusTitulo);
        statusDetalhe = texto("", 14, corSuave);
        statusDetalhe.setPadding(0, dp(4), 0, dp(8));
        status.addView(statusDetalhe);
        cicloSwitch = new Switch(this);
        cicloSwitch.setText("Ciclo ligado");
        cicloSwitch.setTextColor(corTexto);
        cicloSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        cicloSwitch.setPadding(0, dp(8), 0, dp(4));
        cicloSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!suprimir) ligarCiclo(on);
        });
        status.addView(cicloSwitch);

        // Avisos (só aparecem quando falta algo)
        avisos = new LinearLayout(this);
        avisos.setOrientation(LinearLayout.VERTICAL);
        root.addView(avisos);

        // Tempos
        rotulo(root, "Tempos");
        LinearLayout tempos = cartao(root);
        waitField = campoNumero(tempos, "Espera depois de apagar a tela (min)", prefs.getInt("wait_min", 3));
        offField = campoNumero(tempos, "Sem internet (min)", prefs.getInt("off_min", 15));
        onField = campoNumero(tempos, "Com internet (min)", prefs.getInt("on_min", 2));
        telaBox = new CheckBox(this);
        telaBox.setText("Ao acender a tela, parar o ciclo e religar os dados na hora");
        telaBox.setTextColor(corTexto);
        telaBox.setChecked(prefs.getBoolean("tela_interrompe", true));
        telaBox.setPadding(0, dp(8), 0, dp(8));
        tempos.addView(telaBox);
        tempos.addView(botao("Salvar", this::salvar, true));

        // Como configurar
        LinearLayout ajuda = secao(root, "Como configurar as rotinas", false);
        ajuda.addView(texto(
                "Em Modos e Rotinas, crie duas rotinas com a condição \"Notificação recebida\", "
                + "escolhendo o app Ciclo Dados:\n\n"
                + "1. Com a palavra \"" + CicloService.palavraOff(prefs) + "\" → Dados móveis: desligado\n"
                + "2. Com a palavra \"" + CicloService.palavraOn(prefs) + "\" → Dados móveis: ligado\n\n"
                + "Deixe a bateria do Ciclo Dados sem restrições e não bloqueie as notificações dele: "
                + "é a notificação que avisa a rotina.\n\n"
                + "Se você mesmo desligar os dados, o app respeita e não religa.", 14, corTexto));

        // Avançado
        LinearLayout avancado = secao(root, "Avançado", false);
        avancado.addView(texto("Palavras da notificação (uma não pode estar dentro da outra):", 13, corSuave));
        palavraOffField = campoTexto(avancado, "Para desligar", CicloService.palavraOff(prefs));
        palavraOnField = campoTexto(avancado, "Para ligar", CicloService.palavraOn(prefs));
        avancado.addView(botao("Salvar palavras", this::salvar, false));
        avancado.addView(texto("\nTestar as rotinas agora (com o ciclo ligado):", 13, corSuave));
        LinearLayout teste = new LinearLayout(this);
        teste.addView(botao("Enviar desligar", () -> testar(CicloService.ACTION_TESTE_OFF), false));
        teste.addView(botao("Enviar ligar", () -> testar(CicloService.ACTION_TESTE_ON), false));
        avancado.addView(teste);
        LinearLayout hist = new LinearLayout(this);
        hist.setPadding(0, dp(8), 0, 0);
        TextView h = texto("Histórico", 13, corSuave);
        h.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        hist.addView(h);
        hist.addView(botao("Limpar", () -> { Registro.limpar(this); atualizar(); }, false));
        avancado.addView(hist);
        logView = texto("", 11, corTexto);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        avancado.addView(logView);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(corFundo);
        // Android 15+ desenha atrás das barras do sistema: afasta o conteúdo delas.
        sv.setFitsSystemWindows(true);
        sv.addView(root);
        setContentView(sv);
        getWindow().setStatusBarColor(corFundo);
        getWindow().setNavigationBarColor(corFundo);
    }

    private void atualizar() {
        boolean ligado = prefs.getBoolean("enabled", false);
        String fase = prefs.getString("fase", "idle");
        long ate = prefs.getLong("fase_ate", 0);
        boolean vigente = ate > System.currentTimeMillis();
        String hora = vigente ? CicloService.hora(ate) : "";

        if (!ligado) {
            statusTitulo.setText("Desligado");
            statusTitulo.setTextColor(corSuave);
            statusDetalhe.setText("Ligue o ciclo para economizar os dados móveis com a tela apagada.");
        } else if ("off".equals(fase) && vigente) {
            statusTitulo.setText("Sem internet");
            statusTitulo.setTextColor(corDestaque);
            statusDetalhe.setText("Dados desligados até " + hora + ".");
        } else if ("on".equals(fase) && vigente) {
            statusTitulo.setText("Com internet");
            statusTitulo.setTextColor(corTexto);
            statusDetalhe.setText("Atualizando até " + hora + ", depois desliga de novo.");
        } else if ("espera".equals(fase) && vigente) {
            statusTitulo.setText("Tela apagada");
            statusTitulo.setTextColor(corTexto);
            statusDetalhe.setText("Começa a economizar às " + hora + ".");
        } else if ("pausa".equals(fase) && vigente) {
            statusTitulo.setText("Dados desligados por você");
            statusTitulo.setTextColor(corTexto);
            statusDetalhe.setText("O app não religa. Tenta de novo às " + hora + ".");
        } else {
            statusTitulo.setText("Ativo");
            statusTitulo.setTextColor(corDestaque);
            statusDetalhe.setText("Aguardando a tela apagar.");
        }
        suprimir = true;
        cicloSwitch.setChecked(ligado);
        suprimir = false;

        avisos.removeAllViews();
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean bateria = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        if (!notif) {
            aviso("Notificações bloqueadas: sem elas a rotina não recebe o sinal.", "Permitir", () ->
                    abrir(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        }
        if (!bateria) {
            aviso("A bateria do app está restrita: o ciclo pode atrasar ou parar.", "Corrigir", this::pedirBateria);
        }

        String log = Registro.texto(this);
        logView.setText(log.isEmpty() ? "(vazio)" : log);
    }

    // ---------------- Ações ----------------

    private void ligarCiclo(boolean on) {
        if (on && !salvar()) {
            suprimir = true;
            cicloSwitch.setChecked(false);
            suprimir = false;
            return;
        }
        // Zera o passo: pode ter sobrado de uma versão anterior.
        if (on) prefs.edit().putInt("step", 0).putString("fase", "idle").apply();
        prefs.edit().putBoolean("enabled", on).apply();
        if (on) {
            Registro.add(this, "Ciclo ligado (\"" + CicloService.palavraOff(prefs) + "\"/\"" + CicloService.palavraOn(prefs)
                    + "\", espera " + prefs.getInt("wait_min", 3) + ", sem internet " + prefs.getInt("off_min", 15)
                    + ", com internet " + prefs.getInt("on_min", 2) + " min, tela "
                    + (prefs.getBoolean("tela_interrompe", true) ? "interrompe" : "não interrompe") + ").");
            startForegroundService(new Intent(this, CicloService.class));
        } else {
            startService(new Intent(this, CicloService.class).setAction(CicloService.ACTION_STOP));
        }
        atualizar();
    }

    private void testar(String action) {
        if (!prefs.getBoolean("enabled", false)) {
            toast("Ligue o ciclo primeiro.");
            return;
        }
        startForegroundService(new Intent(this, CicloService.class).setAction(action));
        toast("Sinal enviado. Veja se o ícone de dados muda.");
    }

    private boolean salvar() {
        Integer w = ler(waitField, 1, 120), off = ler(offField, 2, 1440), on = ler(onField, 2, 1440);
        if (w == null || off == null || on == null) {
            toast("Tempos inválidos: espera 1–120 min; sem e com internet 2–1440 min.");
            return false;
        }
        String pOff = palavraOffField.getText().toString().trim();
        String pOn = palavraOnField.getText().toString().trim();
        if (pOff.isEmpty() || pOn.isEmpty()) {
            toast("As duas palavras são obrigatórias.");
            return false;
        }
        String a = pOff.toLowerCase(Locale.ROOT), b = pOn.toLowerCase(Locale.ROOT);
        if (a.contains(b) || b.contains(a)) {
            toast("Uma palavra está dentro da outra (\"Desligar\" contém \"ligar\"). Use, por exemplo, \"Ativar\".");
            return false;
        }
        if (prefs.getBoolean("enabled", false)
                && (!pOff.equals(CicloService.palavraOff(prefs)) || !pOn.equals(CicloService.palavraOn(prefs)))) {
            toast("Desligue o ciclo antes de trocar as palavras.");
            return false;
        }
        prefs.edit().putInt("wait_min", w).putInt("off_min", off).putInt("on_min", on)
                .putString("palavra_off", pOff).putString("palavra_on", pOn)
                .putBoolean("tela_interrompe", telaBox.isChecked()).apply();
        toast("Salvo.");
        return true;
    }

    private void pedirBateria() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            abrir(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    private void abrir(Intent i) {
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            toast("Não consegui abrir essa configuração.");
        }
    }

    // ---------------- Peças da tela ----------------

    private LinearLayout cartao(LinearLayout pai) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(corCartao);
        bg.setCornerRadius(dp(24));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        pai.addView(c, lp);
        return c;
    }

    private void rotulo(LinearLayout pai, String s) {
        TextView t = texto(s, 14, corSuave);
        t.setPadding(dp(8), dp(12), 0, dp(6));
        pai.addView(t);
    }

    /** Cartão com título que abre e fecha ao tocar. */
    private LinearLayout secao(LinearLayout pai, String titulo, boolean aberta) {
        LinearLayout c = cartao(pai);
        TextView t = texto((aberta ? "▾  " : "▸  ") + titulo, 17, corTexto);
        t.setPadding(0, dp(4), 0, dp(4));
        c.addView(t);
        LinearLayout corpo = new LinearLayout(this);
        corpo.setOrientation(LinearLayout.VERTICAL);
        corpo.setPadding(0, dp(8), 0, 0);
        corpo.setVisibility(aberta ? View.VISIBLE : View.GONE);
        c.addView(corpo);
        t.setOnClickListener(v -> {
            boolean abrir = corpo.getVisibility() != View.VISIBLE;
            corpo.setVisibility(abrir ? View.VISIBLE : View.GONE);
            t.setText((abrir ? "▾  " : "▸  ") + titulo);
        });
        return corpo;
    }

    private void aviso(String msg, String acao, Runnable r) {
        LinearLayout c = cartao(avisos);
        c.addView(texto(msg, 14, corAlerta));
        c.addView(botao(acao, r, false));
    }

    private EditText campoNumero(LinearLayout pai, String rotulo, int valor) {
        pai.addView(texto(rotulo, 13, corSuave));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(valor));
        e.setTextColor(corTexto);
        pai.addView(e);
        return e;
    }

    private EditText campoTexto(LinearLayout pai, String rotulo, String valor) {
        pai.addView(texto(rotulo, 13, corSuave));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setSingleLine(true);
        e.setText(valor);
        e.setTextColor(corTexto);
        pai.addView(e);
        return e;
    }

    private TextView texto(String s, int sp, int cor) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(cor);
        return t;
    }

    private Button botao(String s, Runnable r, boolean principal) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        if (principal) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(corDestaque);
            bg.setCornerRadius(dp(20));
            b.setBackground(bg);
            b.setTextColor(0xFFFFFFFF);
        }
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private Integer ler(EditText f, int min, int max) {
        try {
            int v = Integer.parseInt(f.getText().toString().trim());
            return v >= min && v <= max ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
