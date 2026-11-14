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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 1;

    private SharedPreferences prefs;
    private TextView permView;
    private TextView logView;
    private EditText waitField, openField, closedField, telaField;
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

        TextView titulo = texto("Ciclo Dados — versão de teste", 20);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(titulo);

        root.addView(texto(
                "Com a tela apagada, o app acende a tela por poucos segundos (brilho mínimo) só para a rotina "
                + "ver o app aberto, apaga a tela e continua aberto pelo tempo sem internet.\n\n"
                + "1) Dê as permissões abaixo (Acessibilidade é opcional: serve para apagar a tela na hora).\n"
                + "2) Na Samsung (Modos e Rotinas), crie a rotina: SE \"App aberto: Ciclo Dados\" "
                + "ENTÃO \"Dados móveis: desligado\".\n"
                + "3) Ligue o ciclo, apague a tela e deixe o celular parado uns 15–20 min.\n"
                + "4) Volte aqui e leia o histórico.\n\n"
                + "Obs.: enquanto esta tela está aberta, a rotina também desliga os dados. "
                + "Isso é normal e serve para conferir que a rotina está certa: abra o app com a tela acesa "
                + "e veja se o ícone de dados some.", 14));

        permView = texto("", 14);
        permView.setPadding(0, dp(12), 0, dp(4));
        root.addView(permView);

        root.addView(botao("Permitir \"Aparecer sobre outros apps\"", () ->
                abrir(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))));
        root.addView(botao("Bateria: deixar sem restrições", this::pedirBateria));
        root.addView(botao("Acessibilidade: permitir apagar a tela (opcional)", () ->
                abrir(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(texto("Se a Acessibilidade aparecer bloqueada: Configurações → Aplicativos → Ciclo Dados → "
                + "⋮ (canto de cima) → \"Permitir configurações restritas\" e tente de novo.", 12));

        root.addView(texto("\nTempos (minutos):", 16));
        waitField = campo(root, "Espera depois de apagar a tela", prefs.getInt("wait_min", 1));
        openField = campo(root, "Tempo aberto (sem internet, mínimo 2)", prefs.getInt("open_min", 5));
        closedField = campo(root, "Tempo fechado (com internet, mínimo 2)", prefs.getInt("closed_min", 2));
        telaField = campo(root, "Segundos de tela acesa no gatilho (1–30)", prefs.getInt("tela_seg", 5));
        root.addView(botao("Salvar tempos", this::salvar));

        cicloSwitch = new Switch(this);
        cicloSwitch.setText("Ciclo ligado");
        cicloSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        cicloSwitch.setPadding(0, dp(16), 0, dp(16));
        cicloSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!suprimir) ligarCiclo(on);
        });
        root.addView(cicloSwitch);

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

    private void ligarCiclo(boolean on) {
        if (on && !salvar()) {
            suprimir = true;
            cicloSwitch.setChecked(false);
            suprimir = false;
            return;
        }
        prefs.edit().putBoolean("enabled", on).apply();
        if (on) {
            Registro.add(this, "Ciclo ligado (espera " + prefs.getInt("wait_min", 1) + ", aberto "
                    + prefs.getInt("open_min", 5) + ", fechado " + prefs.getInt("closed_min", 2) + " min, tela "
                    + prefs.getInt("tela_seg", 5) + " s).");
            startForegroundService(new Intent(this, CicloService.class));
            if (!Settings.canDrawOverlays(this)) toast("Falta permitir \"Aparecer sobre outros apps\".");
        } else {
            startService(new Intent(this, CicloService.class).setAction(CicloService.ACTION_STOP));
        }
        atualizar();
    }

    private boolean salvar() {
        Integer w = ler(waitField, 1, 120), o = ler(openField, 2, 1440), c = ler(closedField, 2, 1440),
                t = ler(telaField, 1, 30);
        if (w == null || o == null || c == null || t == null) {
            toast("Tempos inválidos: espera 1–120, aberto e fechado 2–1440, tela 1–30 s.");
            return false;
        }
        prefs.edit().putInt("wait_min", w).putInt("open_min", o).putInt("closed_min", c).putInt("tela_seg", t).apply();
        toast("Tempos salvos.");
        return true;
    }

    private void atualizar() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean bateria = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        permView.setText("Aparecer sobre outros apps: " + (overlay ? "OK" : "FALTA")
                + "\nBateria sem restrições: " + (bateria ? "OK" : "FALTA")
                + "\nNotificações: " + (notif ? "OK" : "FALTA")
                + "\nAcessibilidade (opcional): " + (TelaService.ativo() ? "OK" : "desligada"));
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
