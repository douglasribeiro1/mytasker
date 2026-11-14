package com.douglasribeiro.ciclodados;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
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
    private TextView permView;
    private TextView logView;
    private EditText waitField, offField, onField, palavraOffField, palavraOnField;
    private CheckBox telaBox;
    private Switch cicloSwitch;
    private boolean suprimir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Registro.prefs(this);
        montarTela();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizar();
    }

    private void montarTela() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView titulo = texto("Ciclo Dados", 20);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(titulo);

        root.addView(texto(
                "O app mantém uma notificação fixa que muda de texto para avisar a rotina da Samsung:\n"
                + "• palavra de desligar → tempo sem internet\n"
                + "• palavra de ligar → tempo com internet\n\n"
                + "Em Modos e Rotinas, crie duas rotinas com \"Notificação recebida\" (app Ciclo Dados):\n"
                + "1) com a palavra de desligar → Dados móveis: desligado\n"
                + "2) com a palavra de ligar → Dados móveis: ligado\n\n"
                + "Uma palavra não pode estar dentro da outra (\"Desligar\" contém \"ligar\").", 14));

        permView = texto("", 14);
        permView.setPadding(0, dp(12), 0, dp(4));
        root.addView(permView);
        root.addView(botao("Bateria: deixar sem restrições", this::pedirBateria));

        root.addView(texto("\nPalavras da notificação:", 16));
        palavraOffField = campoTexto(root, "Palavra para DESLIGAR os dados", CicloService.palavraOff(prefs));
        palavraOnField = campoTexto(root, "Palavra para LIGAR os dados", CicloService.palavraOn(prefs));

        root.addView(texto("\nTempos (minutos):", 16));
        waitField = campo(root, "Espera depois de apagar a tela", prefs.getInt("wait_min", 3));
        offField = campo(root, "Tempo sem internet (mínimo 2)", prefs.getInt("off_min", 15));
        onField = campo(root, "Tempo com internet (mínimo 2)", prefs.getInt("on_min", 2));

        telaBox = new CheckBox(this);
        telaBox.setText("Acender a tela interrompe o ciclo (e religa os dados na hora)");
        telaBox.setChecked(prefs.getBoolean("tela_interrompe", true));
        root.addView(telaBox);

        root.addView(botao("Salvar configurações", this::salvar));

        cicloSwitch = new Switch(this);
        cicloSwitch.setText("Ciclo ligado");
        cicloSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        cicloSwitch.setPadding(0, dp(16), 0, dp(16));
        cicloSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!suprimir) ligarCiclo(on);
        });
        root.addView(cicloSwitch);

        root.addView(texto("Testar as rotinas agora, com a tela acesa (o ciclo precisa estar ligado):", 14));
        LinearLayout teste = new LinearLayout(this);
        teste.addView(botao("Enviar desligar", () -> testar(CicloService.ACTION_TESTE_OFF)));
        teste.addView(botao("Enviar ligar", () -> testar(CicloService.ACTION_TESTE_ON)));
        root.addView(teste);

        LinearLayout linha = new LinearLayout(this);
        linha.addView(botao("Atualizar histórico", this::atualizar));
        linha.addView(botao("Limpar", () -> { Registro.limpar(this); atualizar(); }));
        root.addView(linha);

        root.addView(texto("Histórico (mais recente em cima):", 14));
        logView = texto("", 12);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        root.addView(logView);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private void testar(String action) {
        if (!prefs.getBoolean("enabled", false)) {
            toast("Ligue o ciclo primeiro.");
            return;
        }
        startForegroundService(new Intent(this, CicloService.class).setAction(action));
        toast("Sinal enviado. Veja se o ícone de dados muda.");
    }

    private void ligarCiclo(boolean on) {
        if (on && !salvar()) {
            suprimir = true;
            cicloSwitch.setChecked(false);
            suprimir = false;
            return;
        }
        // Zera o passo: pode ter sobrado de uma versão anterior.
        if (on) prefs.edit().putInt("step", 0).apply();
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

    private boolean salvar() {
        Integer w = ler(waitField, 1, 120), off = ler(offField, 2, 1440), on = ler(onField, 2, 1440);
        if (w == null || off == null || on == null) {
            toast("Tempos inválidos: espera 1–120, sem e com internet 2–1440.");
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
            toast("Uma palavra está dentro da outra. Ex.: \"Desligar\" contém \"ligar\". Use \"Ativar\".");
            return false;
        }
        boolean ativo = prefs.getBoolean("enabled", false);
        if (ativo && (!pOff.equals(CicloService.palavraOff(prefs)) || !pOn.equals(CicloService.palavraOn(prefs)))) {
            toast("Desligue o ciclo antes de trocar as palavras.");
            return false;
        }
        prefs.edit().putInt("wait_min", w).putInt("off_min", off).putInt("on_min", on)
                .putString("palavra_off", pOff).putString("palavra_on", pOn)
                .putBoolean("tela_interrompe", telaBox.isChecked()).apply();
        toast("Configurações salvas.");
        return true;
    }

    private void atualizar() {
        boolean bateria = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        permView.setText("Notificações: " + (notif ? "OK" : "FALTA (sem elas não há sinal)")
                + "\nBateria sem restrições: " + (bateria ? "OK" : "FALTA"));
        suprimir = true;
        cicloSwitch.setChecked(prefs.getBoolean("enabled", false));
        suprimir = false;
        String log = Registro.texto(this);
        logView.setText(log.isEmpty() ? "(vazio)" : log);
    }

    private void pedirBateria() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException e2) {
                toast("Não consegui abrir essa configuração.");
            }
        }
    }

    private Integer ler(EditText f, int min, int max) {
        try {
            int v = Integer.parseInt(f.getText().toString().trim());
            return v >= min && v <= max ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private EditText campo(LinearLayout root, String rotulo, int valor) {
        root.addView(texto(rotulo, 14));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(valor));
        root.addView(e);
        return e;
    }

    private EditText campoTexto(LinearLayout root, String rotulo, String valor) {
        root.addView(texto(rotulo, 14));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setSingleLine(true);
        e.setText(valor);
        root.addView(e);
        return e;
    }

    private TextView texto(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private Button botao(String s, Runnable r) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
