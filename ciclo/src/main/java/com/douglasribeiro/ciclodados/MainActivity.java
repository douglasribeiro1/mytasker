package com.douglasribeiro.ciclodados;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 1;

    private SharedPreferences prefs;
    private int corFundo, corCartao, corTexto, corSuave, corDestaque, corAlerta;

    private TextView statusTitulo, statusDetalhe, resumoHoje, resumoSemana, logView;
    private Button pausaBotao;
    private LinearLayout avisos, listaPeriodos;
    private EditText waitField, offField, onField, palavraOffField, palavraOnField;
    private CheckBox telaBox, wifiBox, carregandoBox;
    private Switch cicloSwitch;
    private boolean suprimir;

    private final SharedPreferences.OnSharedPreferenceChangeListener mudou = (p, k) -> {
        if (k == null || k.startsWith("fase") || k.equals("enabled") || k.equals("log")
                || k.equals("pausa_ate") || k.startsWith("eco_")) {
            runOnUiThread(this::atualizar);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Registro.prefs(this);
        boolean escuro = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        corFundo = escuro ? 0xFF000000 : 0xFFF2F2F7;
        corCartao = escuro ? 0xFF1C1C1E : 0xFFFFFFFF;
        corTexto = escuro ? 0xFFF2F2F2 : 0xFF111111;
        corSuave = escuro ? 0xFFA0A0A5 : 0xFF6B6B70;
        corDestaque = escuro ? 0xFF8C9EFF : 0xFF3949AB;
        corAlerta = escuro ? 0xFFFF8A65 : 0xFFC0392B;
        montarTela();
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

    // ---------------- Tela ----------------

    private void montarTela() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        root.setBackgroundColor(corFundo);

        TextView titulo = texto("Pulso", 30, corTexto);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulo.setPadding(dp(4), dp(8), 0, 0);
        root.addView(titulo);
        TextView sub = texto("Avisa as rotinas da Samsung na hora certa", 14, corSuave);
        sub.setPadding(dp(4), 0, 0, dp(16));
        root.addView(sub);

        // Status
        LinearLayout status = cartao(root);
        statusTitulo = texto("", 22, corTexto);
        statusTitulo.setTypeface(Typeface.DEFAULT_BOLD);
        status.addView(statusTitulo);
        statusDetalhe = texto("", 14, corSuave);
        statusDetalhe.setPadding(0, dp(4), 0, dp(8));
        status.addView(statusDetalhe);
        cicloSwitch = new Switch(this);
        cicloSwitch.setText("Pulso ligado");
        cicloSwitch.setTextColor(corTexto);
        cicloSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        cicloSwitch.setPadding(0, dp(8), 0, dp(4));
        cicloSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!suprimir) ligar(on);
        });
        status.addView(cicloSwitch);
        pausaBotao = botao("", this::alternarPausa, false);
        status.addView(pausaBotao);

        avisos = new LinearLayout(this);
        avisos.setOrientation(LinearLayout.VERTICAL);
        root.addView(avisos);

        // Resumo
        LinearLayout resumo = cartao(root);
        resumo.addView(texto("Hoje em economia", 13, corSuave));
        resumoHoje = texto("", 22, corTexto);
        resumoHoje.setTypeface(Typeface.DEFAULT_BOLD);
        resumo.addView(resumoHoje);
        resumoSemana = texto("", 12, corSuave);
        resumoSemana.setVisibility(View.GONE);
        resumo.addView(resumoSemana);
        resumo.setOnClickListener(v ->
                resumoSemana.setVisibility(resumoSemana.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        // Ciclo padrão
        rotulo(root, "Ciclo padrão (fora dos horários)");
        LinearLayout tempos = cartao(root);
        waitField = campoNumero(tempos, "Espera depois de apagar a tela (min)", prefs.getInt("wait_min", 3));
        offField = campoNumero(tempos, "Economia (min)", prefs.getInt("off_min", 15));
        onField = campoNumero(tempos, "Normal (min)", prefs.getInt("on_min", 2));
        telaBox = caixa(tempos, "Ao acender a tela, sair da economia na hora", prefs.getBoolean("tela_interrompe", true));
        wifiBox = caixa(tempos, "Sem economia quando conectado ao Wi-Fi", prefs.getBoolean("pular_wifi", true));
        carregandoBox = caixa(tempos, "Sem economia quando carregando", prefs.getBoolean("pular_carregando", true));
        TextView dicaWifi = texto("Se a sua rotina também desliga o Wi-Fi, desmarque a opção do Wi-Fi.", 12, corSuave);
        tempos.addView(dicaWifi);
        tempos.addView(botao("Salvar", this::salvar, true));

        // Horários
        rotulo(root, "Horários");
        LinearLayout horarios = cartao(root);
        horarios.addView(texto("O primeiro da lista que estiver valendo é o que manda.", 12, corSuave));
        listaPeriodos = new LinearLayout(this);
        listaPeriodos.setOrientation(LinearLayout.VERTICAL);
        horarios.addView(listaPeriodos);
        horarios.addView(botao("+ Adicionar horário", () -> editarPeriodo(-1), false));
        desenharPeriodos();

        // Tutorial
        LinearLayout tut = cartao(root);
        TextView tt = texto("📖  Como funciona (tutorial passo a passo)", 17, corDestaque);
        tut.addView(tt);
        tut.setOnClickListener(v -> startActivity(new Intent(this, TutorialActivity.class)));

        // Avançado
        LinearLayout avancado = secao(root, "Avançado");
        avancado.addView(texto("Palavras da notificação (uma não pode estar dentro da outra):", 13, corSuave));
        palavraOffField = campoTexto(avancado, "Para economia (desligar)", CicloService.palavraOff(prefs));
        palavraOnField = campoTexto(avancado, "Para normal (ligar)", CicloService.palavraOn(prefs));
        avancado.addView(botao("Salvar palavras", this::salvar, false));
        avancado.addView(texto("\nTestar as rotinas agora (com o Pulso ligado):", 13, corSuave));
        LinearLayout teste = new LinearLayout(this);
        teste.addView(botao("Enviar desligar", () -> testar(CicloService.ACTION_TESTE_OFF), false));
        teste.addView(botao("Enviar ligar", () -> testar(CicloService.ACTION_TESTE_ON), false));
        avancado.addView(teste);
        LinearLayout hist = new LinearLayout(this);
        hist.setGravity(Gravity.CENTER_VERTICAL);
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
        sv.setFitsSystemWindows(true); // Android 15+ desenha atrás das barras do sistema
        sv.addView(root);
        setContentView(sv);
        getWindow().setStatusBarColor(corFundo);
        getWindow().setNavigationBarColor(corFundo);
    }

    private void atualizar() {
        boolean ligado = prefs.getBoolean("enabled", false);
        long agora = System.currentTimeMillis();
        String fase = prefs.getString("fase", "idle");
        long ate = prefs.getLong("fase_ate", 0);
        boolean vigente = ate > agora;
        String hora = vigente ? CicloService.hora(ate) : "";
        String motivo = prefs.getString("fase_motivo", "");
        long pausa = prefs.getLong("pausa_ate", 0);
        boolean pausado = pausa > agora;

        statusTitulo.setTextColor(corTexto);
        if (!ligado) {
            statusTitulo.setText("Desligado");
            statusTitulo.setTextColor(corSuave);
            statusDetalhe.setText("Ligue para economizar com a tela apagada.");
        } else if (pausado) {
            statusTitulo.setText("Pausado");
            statusDetalhe.setText("Volta sozinho às " + CicloService.hora(pausa) + ".");
        } else if ("off".equals(fase) && vigente) {
            statusTitulo.setText("Em economia");
            statusTitulo.setTextColor(corDestaque);
            statusDetalhe.setText("Até " + hora + ".");
        } else if ("economia".equals(fase)) {
            statusTitulo.setText("Em economia");
            statusTitulo.setTextColor(corDestaque);
            statusDetalhe.setText(motivo.isEmpty() ? "Horário fixo." : motivo + ".");
        } else if ("on".equals(fase) && vigente) {
            statusTitulo.setText("Normal");
            statusDetalhe.setText("Atualizando até " + hora + ", depois volta à economia.");
        } else if ("espera".equals(fase) && vigente) {
            statusTitulo.setText("Tela apagada");
            statusDetalhe.setText("Economia começa às " + hora + ".");
        } else if ("pausa_usuario".equals(fase) && vigente) {
            statusTitulo.setText("Dados desligados por você");
            statusDetalhe.setText("O app não mexe. Tenta de novo às " + hora + ".");
        } else {
            statusTitulo.setText("Ativo");
            statusTitulo.setTextColor(corDestaque);
            statusDetalhe.setText(motivo.isEmpty() ? "Aguardando a tela apagar." : motivo + ".");
        }
        suprimir = true;
        cicloSwitch.setChecked(ligado);
        suprimir = false;
        pausaBotao.setVisibility(ligado ? View.VISIBLE : View.GONE);
        pausaBotao.setText(pausado ? "Retomar agora" : "Pausar 1 hora");

        resumoHoje.setText(Estatistica.duracao(Estatistica.noDia(prefs, agora, agora)));
        resumoSemana.setText("\n" + String.join("\n", Estatistica.semana(prefs, agora)));

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
            aviso("A bateria do app está restrita: o ciclo pode atrasar ou parar.", "Corrigir", this::pedirBateria);
        }

        String log = Registro.texto(this);
        logView.setText(log.isEmpty() ? "(vazio)" : log);
    }

    // ---------------- Horários ----------------

    private void desenharPeriodos() {
        listaPeriodos.removeAllViews();
        List<Periodo> l = Periodo.carregar(prefs);
        if (l.isEmpty()) {
            TextView t = texto("Nenhum horário. Fora deles vale o ciclo padrão.", 14, corSuave);
            t.setPadding(0, dp(8), 0, dp(8));
            listaPeriodos.addView(t);
            return;
        }
        for (int i = 0; i < l.size(); i++) {
            Periodo p = l.get(i);
            final int idx = i;
            LinearLayout linha = new LinearLayout(this);
            linha.setGravity(Gravity.CENTER_VERTICAL);
            linha.setPadding(0, dp(10), 0, dp(10));
            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView n = texto((p.ativo ? "" : "(desativado) ") + p.nome, 16, p.ativo ? corTexto : corSuave);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            txt.addView(n);
            txt.addView(texto(p.textoDias() + " · " + p.textoHorario() + "\n" + p.textoModo(), 13, corSuave));
            linha.addView(txt);
            linha.setOnClickListener(v -> editarPeriodo(idx));
            if (i > 0) {
                Button sobe = botao("↑", () -> {
                    List<Periodo> lst = Periodo.carregar(prefs);
                    lst.add(idx - 1, lst.remove(idx));
                    Periodo.salvar(prefs, lst);
                    desenharPeriodos();
                    avisarServico();
                }, false);
                linha.addView(sobe);
            }
            listaPeriodos.addView(linha);
        }
    }

    private void editarPeriodo(int idx) {
        List<Periodo> lista = Periodo.carregar(prefs);
        Periodo p = idx >= 0 ? lista.get(idx) : new Periodo();
        final int[] ini = {p.ini}, fim = {p.fim};

        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), 0);

        EditText nome = campoTexto(f, "Nome", idx >= 0 ? p.nome : "Madrugada");

        f.addView(texto("Dias", 13, corSuave));
        LinearLayout dias = new LinearLayout(this);
        CheckBox[] cbs = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            cbs[i] = new CheckBox(this);
            cbs[i].setText(Periodo.LETRAS[i]);
            cbs[i].setChecked((p.dias & (1 << i)) != 0);
            dias.addView(cbs[i]);
        }
        rolarHorizontal(f, dias);

        LinearLayout horas = new LinearLayout(this);
        Button bIni = new Button(this), bFim = new Button(this);
        bIni.setAllCaps(false);
        bFim.setAllCaps(false);
        bIni.setText("Início " + Periodo.hhmm(ini[0]));
        bFim.setText("Fim " + Periodo.hhmm(fim[0]));
        bIni.setOnClickListener(v -> new TimePickerDialog(this, (tp, hh, mm) -> {
            ini[0] = hh * 60 + mm;
            bIni.setText("Início " + Periodo.hhmm(ini[0]));
        }, ini[0] / 60, ini[0] % 60, true).show());
        bFim.setOnClickListener(v -> new TimePickerDialog(this, (tp, hh, mm) -> {
            fim[0] = hh * 60 + mm;
            bFim.setText("Fim " + Periodo.hhmm(fim[0]));
        }, fim[0] / 60, fim[0] % 60, true).show());
        horas.addView(bIni);
        horas.addView(bFim);
        f.addView(horas);
        f.addView(texto("Início igual ao fim = o dia todo. Pode atravessar a meia-noite.", 12, corSuave));

        f.addView(texto("\nO que fazer nesse horário", 13, corSuave));
        RadioGroup modo = new RadioGroup(this);
        RadioButton rEco = new RadioButton(this), rCiclo = new RadioButton(this), rNormal = new RadioButton(this);
        rEco.setText("Economia o tempo todo (mesmo com a tela acesa)");
        rEco.setId(View.generateViewId());
        rCiclo.setText("Ciclo com tempos próprios");
        rCiclo.setId(View.generateViewId());
        rNormal.setText("Sem ciclo (tudo normal)");
        rNormal.setId(View.generateViewId());
        modo.addView(rEco);
        modo.addView(rCiclo);
        modo.addView(rNormal);
        f.addView(modo);

        LinearLayout tempos = new LinearLayout(this);
        tempos.setOrientation(LinearLayout.VERTICAL);
        EditText off = campoNumero(tempos, "Economia (min)", p.off);
        EditText on = campoNumero(tempos, "Normal (min)", p.on);
        f.addView(tempos);
        modo.setOnCheckedChangeListener((g, id) -> tempos.setVisibility(id == rCiclo.getId() ? View.VISIBLE : View.GONE));
        modo.check(Periodo.CICLO.equals(p.modo) ? rCiclo.getId()
                : Periodo.NORMAL.equals(p.modo) ? rNormal.getId() : rEco.getId());
        tempos.setVisibility(Periodo.CICLO.equals(p.modo) ? View.VISIBLE : View.GONE);

        CheckBox ativo = new CheckBox(this);
        ativo.setText("Horário ativo");
        ativo.setChecked(p.ativo);
        f.addView(ativo);

        ScrollView sv = new ScrollView(this);
        sv.addView(f);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(idx >= 0 ? "Editar horário" : "Novo horário")
                .setView(sv)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null);
        if (idx >= 0) {
            b.setNeutralButton("Excluir", (d, w) -> {
                List<Periodo> l = Periodo.carregar(prefs);
                l.remove(idx);
                Periodo.salvar(prefs, l);
                desenharPeriodos();
                avisarServico();
            });
        }
        AlertDialog d = b.create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int mask = 0;
            for (int i = 0; i < 7; i++) if (cbs[i].isChecked()) mask |= 1 << i;
            if (mask == 0) {
                toast("Escolha pelo menos um dia.");
                return;
            }
            String m = modo.getCheckedRadioButtonId() == rCiclo.getId() ? Periodo.CICLO
                    : modo.getCheckedRadioButtonId() == rNormal.getId() ? Periodo.NORMAL : Periodo.ECONOMIA;
            Integer vOff = ler(off, 2, 1440), vOn = ler(on, 2, 1440);
            if (Periodo.CICLO.equals(m) && (vOff == null || vOn == null)) {
                toast("Tempos inválidos: 2 a 1440 min.");
                return;
            }
            p.nome = nome.getText().toString().trim().isEmpty() ? "Horário" : nome.getText().toString().trim();
            p.dias = mask;
            p.ini = ini[0];
            p.fim = fim[0];
            p.modo = m;
            if (vOff != null) p.off = vOff;
            if (vOn != null) p.on = vOn;
            p.ativo = ativo.isChecked();
            List<Periodo> l = Periodo.carregar(prefs);
            if (idx >= 0) l.set(idx, p);
            else l.add(p);
            Periodo.salvar(prefs, l);
            desenharPeriodos();
            avisarServico();
            d.dismiss();
        }));
        d.show();
    }

    private void rolarHorizontal(LinearLayout pai, View v) {
        android.widget.HorizontalScrollView h = new android.widget.HorizontalScrollView(this);
        h.addView(v);
        pai.addView(h);
    }

    // ---------------- Ações ----------------

    private void ligar(boolean on) {
        if (on && !salvar()) {
            suprimir = true;
            cicloSwitch.setChecked(false);
            suprimir = false;
            return;
        }
        if (on) prefs.edit().putInt("step", 0).putString("fase", "idle").remove("pausa_ate").apply();
        prefs.edit().putBoolean("enabled", on).apply();
        if (on) {
            Registro.add(this, "Ligado (\"" + CicloService.palavraOff(prefs) + "\"/\"" + CicloService.palavraOn(prefs)
                    + "\", espera " + prefs.getInt("wait_min", 3) + ", economia " + prefs.getInt("off_min", 15)
                    + ", normal " + prefs.getInt("on_min", 2) + " min).");
            startForegroundService(new Intent(this, CicloService.class));
        } else {
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

    private void testar(String action) {
        if (!prefs.getBoolean("enabled", false)) {
            toast("Ligue o Pulso primeiro.");
            return;
        }
        startForegroundService(new Intent(this, CicloService.class).setAction(action));
        toast("Sinal enviado. Veja se a rotina reagiu.");
    }

    private boolean salvar() {
        Integer w = ler(waitField, 1, 120), off = ler(offField, 2, 1440), on = ler(onField, 2, 1440);
        if (w == null || off == null || on == null) {
            toast("Tempos inválidos: espera 1–120 min; economia e normal 2–1440 min.");
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
            toast("Desligue o Pulso antes de trocar as palavras.");
            return false;
        }
        prefs.edit().putInt("wait_min", w).putInt("off_min", off).putInt("on_min", on)
                .putString("palavra_off", pOff).putString("palavra_on", pOn)
                .putBoolean("tela_interrompe", telaBox.isChecked())
                .putBoolean("pular_wifi", wifiBox.isChecked())
                .putBoolean("pular_carregando", carregandoBox.isChecked()).apply();
        toast("Salvo.");
        avisarServico();
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

    private LinearLayout secao(LinearLayout pai, String titulo) {
        LinearLayout c = cartao(pai);
        TextView t = texto("▸  " + titulo, 17, corTexto);
        t.setPadding(0, dp(4), 0, dp(4));
        c.addView(t);
        LinearLayout corpo = new LinearLayout(this);
        corpo.setOrientation(LinearLayout.VERTICAL);
        corpo.setPadding(0, dp(8), 0, 0);
        corpo.setVisibility(View.GONE);
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

    private CheckBox caixa(LinearLayout pai, String s, boolean v) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(corTexto);
        c.setChecked(v);
        c.setPadding(0, dp(6), 0, dp(6));
        pai.addView(c);
        return c;
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
