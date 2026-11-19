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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 1;

    private SharedPreferences prefs;
    private Ui ui;

    private ScrollView rolagem;
    private LinearLayout paginaInicio, paginaHistorico;
    private TextView statusTitulo, statusDetalhe, logView;
    private Button pausaBotao;
    private LinearLayout avisos, lista, atualizacao, semana;
    private TextView abaInicio, abaHistorico;
    private Switch geral;
    private boolean suprimir;

    private final SharedPreferences.OnSharedPreferenceChangeListener mudou = (p, k) -> {
        if (k == null || k.contains("fase") || k.equals("enabled") || k.equals("log")
                || k.equals("pausa_ate") || k.contains("eco_") || k.equals("automacoes") || k.startsWith("upd_")) {
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
        Atualizacao.verificar(this, false, null);
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

    @Override
    public void onBackPressed() {
        if (paginaHistorico.getVisibility() == View.VISIBLE) mostrarPagina(false);
        else super.onBackPressed();
    }

    // ---------------- Montagem ----------------

    private void montar() {
        LinearLayout root = ui.raiz();
        root.addView(cabecalho());

        paginaInicio = new LinearLayout(this);
        paginaInicio.setOrientation(LinearLayout.VERTICAL);
        root.addView(paginaInicio);
        montarInicio(paginaInicio);

        paginaHistorico = new LinearLayout(this);
        paginaHistorico.setOrientation(LinearLayout.VERTICAL);
        paginaHistorico.setVisibility(View.GONE);
        root.addView(paginaHistorico);
        montarHistorico(paginaHistorico);

        rolagem = new ScrollView(this);
        rolagem.setBackgroundColor(ui.fundo);
        rolagem.addView(root);
        ui.mostrar(rolagem, barraInferior());
    }

    private View cabecalho() {
        LinearLayout linha = new LinearLayout(this);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        linha.setPadding(ui.dp(4), ui.dp(4), 0, ui.dp(16));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_stat);
        logo.setColorFilter(ui.destaque);
        LinearLayout.LayoutParams lpLogo = new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46));
        lpLogo.rightMargin = ui.dp(10);
        linha.addView(logo, lpLogo);
        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        textos.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView titulo = ui.texto("Pulso", 30, ui.texto);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        textos.addView(titulo);
        textos.addView(ui.texto("Sinais que disparam suas rotinas da Samsung", 13, ui.suave));
        linha.addView(textos);
        TextView diag = ui.texto("⚙", 26, ui.suave);
        diag.setPadding(ui.dp(12), ui.dp(4), ui.dp(4), ui.dp(4));
        diag.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticoActivity.class)));
        linha.addView(diag);
        return linha;
    }

    private void montarInicio(LinearLayout pg) {
        LinearLayout status = ui.cartaoDestaque(pg);
        statusTitulo = ui.texto("", 26, 0xFFFFFFFF);
        statusTitulo.setTypeface(Typeface.DEFAULT_BOLD);
        status.addView(statusTitulo);
        statusDetalhe = ui.texto("", 14, 0xDDFFFFFF);
        statusDetalhe.setPadding(0, ui.dp(4), 0, ui.dp(8));
        status.addView(statusDetalhe);
        geral = new Switch(this);
        geral.setText("Pulso ligado");
        geral.setTextColor(0xFFFFFFFF);
        geral.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        geral.setPadding(0, ui.dp(8), 0, ui.dp(4));
        ui.tingir(geral, true);
        geral.setOnCheckedChangeListener((b, on) -> {
            if (!suprimir) ligar(on);
        });
        status.addView(geral);
        pausaBotao = ui.botao("", this::alternarPausa, false);
        pausaBotao.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable contorno = new android.graphics.drawable.GradientDrawable();
        contorno.setColor(0x22FFFFFF);
        contorno.setStroke(ui.dp(1), 0x88FFFFFF);
        contorno.setCornerRadius(ui.dp(24));
        pausaBotao.setBackground(contorno);
        status.addView(pausaBotao);

        atualizacao = new LinearLayout(this);
        atualizacao.setOrientation(LinearLayout.VERTICAL);
        pg.addView(atualizacao);

        avisos = new LinearLayout(this);
        avisos.setOrientation(LinearLayout.VERTICAL);
        pg.addView(avisos);

        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        topo.setPadding(ui.dp(4), ui.dp(12), 0, ui.dp(6));
        TextView t = ui.texto("Minhas automações", 20, ui.texto);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        topo.addView(t);
        topo.addView(ui.botao("+ Nova", () -> abrirAutomacao(-1), true));
        pg.addView(topo);

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        pg.addView(lista);
    }

    private void montarHistorico(LinearLayout pg) {
        LinearLayout s = ui.cartao(pg);
        TextView ts = ui.texto("Tempo em economia (dados móveis)", 17, ui.texto);
        ts.setTypeface(Typeface.DEFAULT_BOLD);
        s.addView(ts);
        semana = new LinearLayout(this);
        semana.setOrientation(LinearLayout.VERTICAL);
        s.addView(semana);

        LinearLayout r = ui.cartao(pg);
        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        TextView tr = ui.texto("Registro", 17, ui.texto);
        tr.setTypeface(Typeface.DEFAULT_BOLD);
        tr.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        topo.addView(tr);
        topo.addView(ui.botao("Copiar", () -> {
            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Registro do Pulso", Registro.texto(this)));
            ui.toast("Registro copiado.");
        }, false));
        topo.addView(ui.botao("Limpar", () -> { Registro.limpar(this); atualizar(); }, false));
        r.addView(topo);
        logView = ui.texto("", 11, ui.texto);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        r.addView(logView);
    }

    private View barraInferior() {
        LinearLayout barra = new LinearLayout(this);
        barra.setBackgroundColor(ui.escuro ? 0xFF0F1528 : ui.cartao);
        barra.setPadding(0, ui.dp(6), 0, ui.dp(6));
        View linha = new View(this);
        abaInicio = aba(barra, "⌂", "Início", () -> mostrarPagina(false));
        abaHistorico = aba(barra, "▤", "Histórico", () -> mostrarPagina(true));
        aba(barra, "✓", "Diagnóstico", () -> startActivity(new Intent(this, DiagnosticoActivity.class)));
        aba(barra, "?", "Tutorial", () -> startActivity(new Intent(this, TutorialActivity.class)));
        LinearLayout tudo = new LinearLayout(this);
        tudo.setOrientation(LinearLayout.VERTICAL);
        linha.setBackgroundColor(ui.borda);
        tudo.addView(linha, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)));
        tudo.addView(barra);
        marcarAbas(false);
        return tudo;
    }

    private TextView aba(LinearLayout barra, String simbolo, String nome, Runnable r) {
        TextView t = ui.texto(simbolo + "\n" + nome, 12, ui.suave);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, ui.dp(4), 0, ui.dp(4));
        t.setOnClickListener(v -> r.run());
        barra.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return t;
    }

    private void marcarAbas(boolean historico) {
        abaInicio.setTextColor(historico ? ui.suave : ui.destaque);
        abaHistorico.setTextColor(historico ? ui.destaque : ui.suave);
        abaInicio.setTypeface(historico ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
        abaHistorico.setTypeface(historico ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void mostrarPagina(boolean historico) {
        paginaInicio.setVisibility(historico ? View.GONE : View.VISIBLE);
        paginaHistorico.setVisibility(historico ? View.VISIBLE : View.GONE);
        marcarAbas(historico);
        rolagem.scrollTo(0, 0);
    }

    // ---------------- Atualização da tela ----------------

    private void atualizar() {
        long agora = System.currentTimeMillis();
        boolean ligado = prefs.getBoolean("enabled", false);
        long pausa = prefs.getLong("pausa_ate", 0);
        boolean pausado = pausa > agora;
        List<Automacao> todas = Automacao.carregar(prefs);
        int ativas = 0;
        for (Automacao a : todas) if (a.ativo) ativas++;

        if (!ligado) {
            statusTitulo.setText("Desligado");
            statusDetalhe.setText("Ligue para as automações funcionarem.");
        } else if (pausado) {
            statusTitulo.setText("Pausado");
            statusDetalhe.setText("Tudo normal até " + CicloService.hora(pausa) + ".");
        } else {
            statusTitulo.setText("Ativo");
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
            c.addView(ui.texto("Nenhuma automação. Toque em \"+ Nova\".", 14, ui.suave));
        }
        for (Automacao a : todas) cartaoAutomacao(a, ligado, agora);

        atualizacao.removeAllViews();
        String nova = Atualizacao.disponivel(this);
        if (nova != null) {
            LinearLayout c = ui.cartao(atualizacao);
            TextView t = ui.texto("Nova versão disponível: " + nova, 16, ui.destaque);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(t);
            c.addView(ui.texto("Baixe e instale por cima. As configurações continuam.", 13, ui.suave));
            c.addView(ui.botao("Baixar", () -> abrir(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(Atualizacao.link(this)))), true));
        }

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

        semana.removeAllViews();
        boolean algum = false;
        for (Automacao a : todas) {
            if (!a.dados) continue;
            algum = true;
            TextView n = ui.texto(a.nome, 15, ui.destaque);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            n.setPadding(0, ui.dp(10), 0, ui.dp(2));
            semana.addView(n);
            semana.addView(ui.texto(String.join("\n", Estatistica.semana(prefs, a.k(), agora)), 14, ui.texto));
        }
        if (!algum) {
            semana.addView(ui.texto("Aparece aqui para as automações com \"controla os dados móveis\" marcado.",
                    13, ui.suave));
        }
        String log = Registro.texto(this);
        logView.setText(log.isEmpty() ? "(vazio)" : log);
    }

    private void cartaoAutomacao(Automacao a, boolean ligado, long agora) {
        LinearLayout c = ui.cartao(lista);
        c.setOnClickListener(v -> abrirAutomacao(a.id));
        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        String letra = a.nome.isEmpty() ? "?" : a.nome.substring(0, 1).toUpperCase();
        TextView ic = ui.icone(letra, 46);
        if (!a.ativo) ic.setAlpha(0.4f);
        topo.addView(ic);

        LinearLayout meio = new LinearLayout(this);
        meio.setOrientation(LinearLayout.VERTICAL);
        meio.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView nome = ui.texto(a.nome, 18, a.ativo ? ui.texto : ui.suave);
        nome.setTypeface(Typeface.DEFAULT_BOLD);
        meio.addView(nome);

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
            cor = ui.destaque2;
        } else if ("on".equals(fase) && vigente) {
            st = "Normal até " + hora;
            cor = ui.ok;
        } else if ("espera".equals(fase) && vigente) st = "Economia começa às " + hora;
        else st = motivo.isEmpty() ? "Aguardando a tela apagar" : motivo;
        meio.addView(ui.texto(st, 14, cor));
        topo.addView(meio);

        Switch s = new Switch(this);
        s.setChecked(a.ativo);
        ui.tingir(s, false);
        s.setOnCheckedChangeListener((b, on) -> {
            List<Automacao> todas = Automacao.carregar(prefs);
            Automacao x = Automacao.porId(todas, a.id);
            if (x == null) return;
            x.ativo = on;
            if (on) {
                String erro = Automacao.conflito(todas);
                if (erro != null) {
                    ui.toast(erro);
                    b.setChecked(false);
                    return;
                }
            }
            Automacao.salvar(prefs, todas);
            avisarServico();
        });
        topo.addView(s);
        c.addView(topo);

        TextView det = ui.texto("\"" + a.off + "\" / \"" + a.on + "\" · " + a.offMin + " min / " + a.onMin + " min"
                + (a.periodos.isEmpty() ? "" : " · " + a.periodos.size() + " horário(s)"), 12, ui.suave);
        det.setPadding(ui.dp(60), ui.dp(6), 0, 0);
        c.addView(det);
        if (a.dados) {
            TextView eco = ui.texto("Hoje em economia: "
                    + Estatistica.duracao(Estatistica.noDia(prefs, k, agora, agora)), 12, ui.suave);
            eco.setPadding(ui.dp(60), ui.dp(2), 0, 0);
            c.addView(eco);
        }
    }

    // ---------------- Ações ----------------

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
