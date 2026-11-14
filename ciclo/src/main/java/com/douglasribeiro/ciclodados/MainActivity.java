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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 1;

    private SharedPreferences prefs;
    private Ui ui;

    private TextView statusTitulo, statusDetalhe, logView;
    private Button pausaBotao;
    private LinearLayout avisos, lista;
    private Switch geral;
    private boolean suprimir;

    private final SharedPreferences.OnSharedPreferenceChangeListener mudou = (p, k) -> {
        if (k == null || k.contains("fase") || k.equals("enabled") || k.equals("log")
                || k.equals("pausa_ate") || k.contains("eco_") || k.equals("automacoes")) {
            runOnUiThread(this::atualizar);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Registro.prefs(this);
        ui = new Ui(this);
        Automacao.carregar(prefs); // migra a configuração antiga, se houver
        montar();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        if (!prefs.getBoolean("tutorial_visto", false)) {
            prefs.edit().putBoolean("tutorial_visto", true).apply();
            startActivity(new Intent(this, TutorialActivity.class));
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

    private void montar() {
        LinearLayout root = ui.raiz();
        TextView titulo = ui.texto("Pulso", 30, ui.texto);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulo.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        root.addView(titulo);
        TextView sub = ui.texto("Liga e desliga rotinas da Samsung em ciclos e horários", 14, ui.suave);
        sub.setPadding(ui.dp(4), 0, 0, ui.dp(16));
        root.addView(sub);

        LinearLayout status = ui.cartao(root);
        statusTitulo = ui.texto("", 22, ui.texto);
        statusTitulo.setTypeface(Typeface.DEFAULT_BOLD);
        status.addView(statusTitulo);
        statusDetalhe = ui.texto("", 14, ui.suave);
        statusDetalhe.setPadding(0, ui.dp(4), 0, ui.dp(8));
        status.addView(statusDetalhe);
        geral = new Switch(this);
        geral.setText("Pulso ligado");
        geral.setTextColor(ui.texto);
        geral.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        geral.setPadding(0, ui.dp(8), 0, ui.dp(4));
        geral.setOnCheckedChangeListener((b, on) -> {
            if (!suprimir) ligar(on);
        });
        status.addView(geral);
        pausaBotao = ui.botao("", this::alternarPausa, false);
        status.addView(pausaBotao);

        avisos = new LinearLayout(this);
        avisos.setOrientation(LinearLayout.VERTICAL);
        root.addView(avisos);

        ui.rotulo(root, "Automações");
        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        root.addView(lista);
        root.addView(ui.botao("+ Nova automação", () -> abrirAutomacao(-1), true));

        LinearLayout tut = ui.cartao(root);
        tut.addView(ui.texto("📖  Como funciona (tutorial passo a passo)", 17, ui.destaque));
        tut.setOnClickListener(v -> startActivity(new Intent(this, TutorialActivity.class)));

        LinearLayout hist = ui.secao(root, "Histórico");
        hist.addView(ui.botao("Limpar", () -> { Registro.limpar(this); atualizar(); }, false));
        logView = ui.texto("", 11, ui.texto);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        hist.addView(logView);

        ui.mostrar(root);
    }

    private void atualizar() {
        long agora = System.currentTimeMillis();
        boolean ligado = prefs.getBoolean("enabled", false);
        long pausa = prefs.getLong("pausa_ate", 0);
        boolean pausado = pausa > agora;
        List<Automacao> todas = Automacao.carregar(prefs);
        int ativas = 0;
        for (Automacao a : todas) if (a.ativo) ativas++;

        statusTitulo.setTextColor(ui.texto);
        if (!ligado) {
            statusTitulo.setText("Desligado");
            statusTitulo.setTextColor(ui.suave);
            statusDetalhe.setText("Ligue para as automações funcionarem.");
        } else if (pausado) {
            statusTitulo.setText("Pausado");
            statusDetalhe.setText("Tudo normal até " + CicloService.hora(pausa) + ".");
        } else {
            statusTitulo.setText("Ativo");
            statusTitulo.setTextColor(ui.destaque);
            statusDetalhe.setText(ativas == 1 ? "1 automação ativa." : ativas + " automações ativas.");
        }
        suprimir = true;
        geral.setChecked(ligado);
        suprimir = false;
        pausaBotao.setVisibility(ligado ? View.VISIBLE : View.GONE);
        pausaBotao.setText(pausado ? "Retomar agora" : "Pausar tudo por 1 hora");

        lista.removeAllViews();
        if (todas.isEmpty()) {
            LinearLayout c = ui.cartao(lista);
            c.addView(ui.texto("Nenhuma automação. Toque em \"+ Nova automação\".", 14, ui.suave));
        }
        for (Automacao a : todas) cartaoAutomacao(a, ligado, agora);

        avisos.removeAllViews();
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean bateria = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        if (!notif) {
            aviso("Notificações bloqueadas: sem elas as rotinas não recebem o sinal.", "Permitir", () ->
                    abrir(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        }
        if (!bateria) {
            aviso("A bateria do app está restrita: os ciclos podem atrasar ou parar.", "Corrigir", this::pedirBateria);
        }

        String log = Registro.texto(this);
        logView.setText(log.isEmpty() ? "(vazio)" : log);
    }

    private void cartaoAutomacao(Automacao a, boolean ligado, long agora) {
        LinearLayout c = ui.cartao(lista);
        c.setOnClickListener(v -> abrirAutomacao(a.id));
        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        TextView nome = ui.texto(a.nome, 18, a.ativo ? ui.texto : ui.suave);
        nome.setTypeface(Typeface.DEFAULT_BOLD);
        nome.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        topo.addView(nome);
        Switch s = new Switch(this);
        s.setChecked(a.ativo);
        s.setOnCheckedChangeListener((b, on) -> {
            List<Automacao> todas = Automacao.carregar(prefs);
            Automacao x = Automacao.porId(todas, a.id);
            if (x == null) return;
            x.ativo = on;
            Automacao.salvar(prefs, todas);
            avisarServico();
        });
        topo.addView(s);
        c.addView(topo);

        String k = a.k();
        String fase = prefs.getString(k + "fase", "idle");
        long ate = prefs.getLong(k + "fase_ate", 0);
        String motivo = prefs.getString(k + "fase_motivo", "");
        boolean vigente = ate > agora;
        String hora = vigente ? CicloService.hora(ate) : "";
        String st;
        int cor = ui.suave;
        if (!a.ativo) st = "Desativada";
        else if (!ligado) st = "Pulso desligado";
        else if (("off".equals(fase) && vigente) || "economia".equals(fase)) {
            st = "Em economia" + (vigente ? " até " + hora : motivo.isEmpty() ? "" : " · " + motivo);
            cor = ui.destaque;
        } else if ("on".equals(fase) && vigente) st = "Normal até " + hora;
        else if ("espera".equals(fase) && vigente) st = "Economia começa às " + hora;
        else if ("pausa_usuario".equals(fase) && vigente) st = "Dados desligados por você · tenta às " + hora;
        else st = motivo.isEmpty() ? "Aguardando a tela apagar" : motivo;
        c.addView(ui.texto(st, 14, cor));
        c.addView(ui.texto("\"" + a.off + "\" / \"" + a.on + "\" · " + a.offMin + " min / " + a.onMin + " min"
                + (a.periodos.isEmpty() ? "" : " · " + a.periodos.size() + " horário(s)"), 12, ui.suave));
        if (a.dados) {
            TextView eco = ui.texto("Hoje em economia: " + Estatistica.duracao(Estatistica.noDia(prefs, k, agora, agora))
                    + "  (toque e segure para ver 7 dias)", 12, ui.suave);
            c.setOnLongClickListener(v -> {
                eco.setText("Em economia:\n" + String.join("\n", Estatistica.semana(prefs, k, agora)));
                return true;
            });
            c.addView(eco);
        }
    }

    private void abrirAutomacao(int id) {
        startActivity(new Intent(this, AutomacaoActivity.class).putExtra(AutomacaoActivity.EXTRA_ID, id));
    }

    private void ligar(boolean on) {
        if (on) {
            String erro = Automacao.conflito(Automacao.carregar(prefs));
            if (erro != null) {
                ui.toast(erro);
                suprimir = true;
                geral.setChecked(false);
                suprimir = false;
                return;
            }
            SharedPreferences.Editor e = prefs.edit();
            for (Automacao a : Automacao.carregar(prefs)) e.putInt(a.k() + "step", 0);
            e.remove("pausa_ate").putBoolean("enabled", true).apply();
            Registro.add(this, "Pulso ligado.");
            startForegroundService(new Intent(this, CicloService.class));
        } else {
            prefs.edit().putBoolean("enabled", false).apply();
            startService(new Intent(this, CicloService.class).setAction(CicloService.ACTION_STOP));
        }
        atualizar();
    }

    private void alternarPausa() {
        boolean pausado = prefs.getLong("pausa_ate", 0) > System.currentTimeMillis();
        startForegroundService(new Intent(this, CicloService.class)
                .setAction(pausado ? CicloService.ACTION_RETOMAR : CicloService.ACTION_PAUSAR));
    }

    private void avisarServico() {
        if (prefs.getBoolean("enabled", false)) {
            startForegroundService(new Intent(this, CicloService.class).setAction(CicloService.ACTION_REAVALIAR));
        }
    }

    private void aviso(String msg, String acao, Runnable r) {
        LinearLayout c = ui.cartao(avisos);
        c.addView(ui.texto(msg, 14, ui.alerta));
        c.addView(ui.botao(acao, r, false));
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
            ui.toast("Não consegui abrir essa configuração.");
        }
    }
}
