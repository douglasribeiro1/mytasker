package com.douglasribeiro.ciclodados;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Criar ou editar uma automação: nome, palavras (copiáveis), ciclo, condições e horários. */
public class AutomacaoActivity extends Activity {

    static final String EXTRA_ID = "id";

    private SharedPreferences prefs;
    private Ui ui;
    private Automacao a;
    private boolean nova;
    private List<Periodo> periodos;

    private EditText nome, palavraOff, palavraOn, espera, off, on;
    private CheckBox ativo, dados, wifi, carregando;
    private RadioGroup sair;
    private RadioButton sairTela, sairDesbloqueio, sairNunca;
    private LinearLayout lista;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Registro.prefs(this);
        ui = new Ui(this);
        int id = getIntent().getIntExtra(EXTRA_ID, -1);
        List<Automacao> todas = Automacao.carregar(prefs);
        a = Automacao.porId(todas, id);
        nova = a == null;
        if (nova) {
            a = new Automacao();
            a.id = Automacao.novoId(todas);
            a.nome = "";
            a.off = "";
            a.on = "";
            a.dados = false;
            a.wifi = false;
            a.carregando = false;
        }
        periodos = new ArrayList<>(a.periodos);
        montar();
    }

    private void montar() {
        LinearLayout root = ui.raiz();
        ui.titulo(root, nova ? "Nova automação" : a.nome);

        LinearLayout c = ui.cartao(root);
        nome = ui.campo(c, "Nome (só para você; não aparece na notificação)", a.nome);
        nome.setHint("Ex.: Wi-Fi");
        ativo = ui.caixa(c, "Automação ativa", a.ativo);

        ui.rotulo(root, "Palavras da notificação");
        LinearLayout p = ui.cartao(root);
        p.addView(ui.texto("Cada palavra vira o título da notificação desta automação. Copie e cole cada uma "
                + "na condição \"Notificação recebida\" da rotina correspondente.", 13, ui.suave));
        palavraOff = ui.campoCopiavel(p, "Economia (a rotina desliga)", a.off);
        palavraOff.setHint("Ex.: Desligar Wi-Fi");
        palavraOn = ui.campoCopiavel(p, "Normal (a rotina religa)", a.on);
        palavraOn.setHint("Ex.: Religar Wi-Fi");
        p.addView(ui.texto("Pode juntar palavras para disparar várias rotinas de uma vez "
                + "(ex.: economia \"Dados OFF Wifi OFF\", normal \"Dados ON Wifi ON\"). "
                + "Só não pode o texto de economia conter uma palavra de normal, ou o contrário "
                + "(ex.: \"Desligar Wi-Fi\" contém \"ligar Wi-Fi\"). Automações desativadas não contam.", 12, ui.suave));

        ui.rotulo(root, "Ciclo padrão (fora dos horários)");
        LinearLayout t = ui.cartao(root);
        espera = ui.numero(t, "Espera depois de apagar a tela (min)", a.espera);
        off = ui.numero(t, "Economia (min)", a.offMin);
        on = ui.numero(t, "Normal (min)", a.onMin);

        ui.rotulo(root, "Condições");
        LinearLayout cond = ui.cartao(root);
        cond.addView(ui.texto("Sair da economia quando usar o celular:", 13, ui.suave));
        sair = new RadioGroup(this);
        sairTela = radio(sair, "Ao acender a tela");
        sairDesbloqueio = radio(sair, "Só ao desbloquear (olhar as horas não interrompe)");
        sairNunca = radio(sair, "Nunca (o ciclo segue mesmo em uso)");
        sair.check(Automacao.SAIR_DESBLOQUEIO.equals(a.sair) ? sairDesbloqueio.getId()
                : Automacao.SAIR_NUNCA.equals(a.sair) ? sairNunca.getId() : sairTela.getId());
        cond.addView(sair);
        wifi = ui.caixa(cond, "Sem economia quando conectado ao Wi-Fi", a.wifi);
        carregando = ui.caixa(cond, "Sem economia quando carregando", a.carregando);
        dados = ui.caixa(cond, "Esta automação controla os dados móveis", a.dados);
        cond.addView(ui.texto("Com a última marcada, o app respeita quando você mesmo desliga os dados, "
                + "confere se a rotina religou e mostra o tempo em economia. "
                + "Se a rotina desliga o Wi-Fi, não marque \"Sem economia quando conectado ao Wi-Fi\".", 12, ui.suave));

        ui.rotulo(root, "Horários");
        LinearLayout h = ui.cartao(root);
        h.addView(ui.texto("O primeiro da lista que estiver valendo é o que manda.", 12, ui.suave));
        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        h.addView(lista);
        h.addView(ui.botao("+ Adicionar horário", () -> editarPeriodo(-1), false));
        desenharPeriodos();

        root.addView(ui.botao("Salvar", this::salvar, true));

        if (!nova) {
            ui.rotulo(root, "Testar (salve antes; o Pulso precisa estar ligado)");
            LinearLayout teste = new LinearLayout(this);
            teste.addView(ui.botao("Enviar economia", () -> testar(CicloService.ACTION_TESTE_OFF), false));
            teste.addView(ui.botao("Enviar normal", () -> testar(CicloService.ACTION_TESTE_ON), false));
            root.addView(teste);
            Button excluir = ui.botao("Excluir automação", this::excluir, false);
            excluir.setTextColor(ui.alerta);
            root.addView(excluir);
        }
        ui.mostrar(root);
    }

    private void salvar() {
        Integer w = Ui.ler(espera, 1, 120), vOff = Ui.ler(off, 1, 1440), vOn = Ui.ler(on, 1, 1440);
        if (w == null || vOff == null || vOn == null) {
            ui.toast("Tempos inválidos: espera 1–120 min; economia e normal 1–1440 min.");
            return;
        }
        String n = nome.getText().toString().trim();
        if (n.isEmpty()) {
            ui.toast("Dê um nome à automação.");
            return;
        }
        List<Automacao> todas = Automacao.carregar(prefs);
        Automacao antiga = Automacao.porId(todas, a.id);
        String offAntes = antiga != null ? antiga.off : "", onAntes = antiga != null ? antiga.on : "";
        a.nome = n;
        a.off = palavraOff.getText().toString().trim();
        a.on = palavraOn.getText().toString().trim();
        a.ativo = ativo.isChecked();
        a.dados = dados.isChecked();
        a.espera = w;
        a.offMin = vOff;
        a.onMin = vOn;
        int s = sair.getCheckedRadioButtonId();
        a.sair = s == sairDesbloqueio.getId() ? Automacao.SAIR_DESBLOQUEIO
                : s == sairNunca.getId() ? Automacao.SAIR_NUNCA : Automacao.SAIR_TELA;
        a.wifi = wifi.isChecked();
        a.carregando = carregando.isChecked();
        a.periodos = periodos;
        if (antiga != null) todas.set(todas.indexOf(antiga), a);
        else todas.add(a);
        String erro = Automacao.conflito(todas);
        if (erro != null) {
            ui.toast(erro);
            return;
        }
        boolean trocouPalavras = antiga != null && (!offAntes.equals(a.off) || !onAntes.equals(a.on));
        if (trocouPalavras && "off".equals(prefs.getString(a.k() + "sinal", "on"))) {
            ui.toast("Esta automação está em economia agora. Pause o Pulso antes de trocar as palavras.");
            return;
        }
        Automacao.salvar(prefs, todas);
        Registro.add(this, "Automação \"" + a.nome + "\" salva.");
        avisarServico();
        ui.toast(trocouPalavras || nova ? "Salvo. Agora crie/atualize as rotinas com essas palavras." : "Salvo.");
        finish();
    }

    private RadioButton radio(RadioGroup g, String texto) {
        RadioButton r = new RadioButton(this);
        r.setText(texto);
        r.setTextColor(ui.texto);
        r.setId(View.generateViewId());
        g.addView(r);
        return r;
    }

    private void excluir() {
        new AlertDialog.Builder(this)
                .setTitle("Excluir \"" + a.nome + "\"?")
                .setMessage("Se estiver em economia, o app manda a palavra de normal antes de remover. "
                        + "As rotinas na Samsung você apaga lá.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (d, w) -> {
                    List<Automacao> todas = Automacao.carregar(prefs);
                    Automacao x = Automacao.porId(todas, a.id);
                    if (x != null) todas.remove(x);
                    Automacao.salvar(prefs, todas);
                    Registro.add(this, "Automação \"" + a.nome + "\" excluída.");
                    avisarServico();
                    finish();
                })
                .show();
    }

    private void testar(String action) {
        if (!prefs.getBoolean("enabled", false)) {
            ui.toast("Ligue o Pulso primeiro.");
            return;
        }
        startForegroundService(new Intent(this, CicloService.class).setAction(action)
                .putExtra(CicloService.EXTRA_ID, a.id));
        ui.toast("Sinal enviado. Veja se a rotina reagiu.");
    }

    private void avisarServico() {
        if (prefs.getBoolean("enabled", false)) {
            startForegroundService(new Intent(this, CicloService.class).setAction(CicloService.ACTION_REAVALIAR));
        }
    }

    // ---------------- Horários ----------------

    private void desenharPeriodos() {
        lista.removeAllViews();
        if (periodos.isEmpty()) {
            TextView t = ui.texto("Nenhum horário. Fora deles vale o ciclo padrão.", 14, ui.suave);
            t.setPadding(0, ui.dp(8), 0, ui.dp(8));
            lista.addView(t);
            return;
        }
        for (int i = 0; i < periodos.size(); i++) {
            Periodo p = periodos.get(i);
            final int idx = i;
            LinearLayout linha = new LinearLayout(this);
            linha.setGravity(Gravity.CENTER_VERTICAL);
            linha.setPadding(0, ui.dp(10), 0, ui.dp(10));
            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView n = ui.texto((p.ativo ? "" : "(desativado) ") + p.nome, 16, p.ativo ? ui.texto : ui.suave);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            txt.addView(n);
            txt.addView(ui.texto(p.textoDias() + " · " + p.textoHorario() + "\n" + p.textoModo(), 13, ui.suave));
            linha.addView(txt);
            linha.setOnClickListener(v -> editarPeriodo(idx));
            if (i > 0) {
                linha.addView(ui.botao("↑", () -> {
                    periodos.add(idx - 1, periodos.remove(idx));
                    desenharPeriodos();
                }, false));
            }
            lista.addView(linha);
        }
    }

    private void editarPeriodo(int idx) {
        Periodo p = idx >= 0 ? periodos.get(idx) : new Periodo();
        final int[] ini = {p.ini}, fim = {p.fim};

        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);

        EditText nomeP = ui.campo(f, "Nome", idx >= 0 ? p.nome : "Madrugada");

        f.addView(ui.texto("Dias", 13, ui.suave));
        LinearLayout dias = new LinearLayout(this);
        CheckBox[] cbs = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            cbs[i] = new CheckBox(this);
            cbs[i].setText(Periodo.LETRAS[i]);
            cbs[i].setChecked((p.dias & (1 << i)) != 0);
            dias.addView(cbs[i]);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(dias);
        f.addView(hs);

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
        f.addView(ui.texto("Início igual ao fim = o dia todo. Pode atravessar a meia-noite.", 12, ui.suave));

        f.addView(ui.texto("\nO que fazer nesse horário", 13, ui.suave));
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
        EditText offP = ui.numero(tempos, "Economia (min)", p.off);
        EditText onP = ui.numero(tempos, "Normal (min)", p.on);
        f.addView(tempos);
        modo.setOnCheckedChangeListener((g, id) -> tempos.setVisibility(id == rCiclo.getId() ? View.VISIBLE : View.GONE));
        modo.check(Periodo.CICLO.equals(p.modo) ? rCiclo.getId()
                : Periodo.NORMAL.equals(p.modo) ? rNormal.getId() : rEco.getId());
        tempos.setVisibility(Periodo.CICLO.equals(p.modo) ? View.VISIBLE : View.GONE);

        CheckBox ativoP = new CheckBox(this);
        ativoP.setText("Horário ativo");
        ativoP.setChecked(p.ativo);
        f.addView(ativoP);

        ScrollView sv = new ScrollView(this);
        sv.addView(f);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(idx >= 0 ? "Editar horário" : "Novo horário")
                .setView(sv)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("OK", null);
        if (idx >= 0) {
            b.setNeutralButton("Excluir", (d, w) -> {
                periodos.remove(idx);
                desenharPeriodos();
            });
        }
        AlertDialog d = b.create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int mask = 0;
            for (int i = 0; i < 7; i++) if (cbs[i].isChecked()) mask |= 1 << i;
            if (mask == 0) {
                ui.toast("Escolha pelo menos um dia.");
                return;
            }
            String m = modo.getCheckedRadioButtonId() == rCiclo.getId() ? Periodo.CICLO
                    : modo.getCheckedRadioButtonId() == rNormal.getId() ? Periodo.NORMAL : Periodo.ECONOMIA;
            Integer vOff = Ui.ler(offP, 1, 1440), vOn = Ui.ler(onP, 1, 1440);
            if (Periodo.CICLO.equals(m) && (vOff == null || vOn == null)) {
                ui.toast("Tempos inválidos: 1 a 1440 min.");
                return;
            }
            String nn = nomeP.getText().toString().trim();
            p.nome = nn.isEmpty() ? "Horário" : nn;
            p.dias = mask;
            p.ini = ini[0];
            p.fim = fim[0];
            p.modo = m;
            if (vOff != null) p.off = vOff;
            if (vOn != null) p.on = vOn;
            p.ativo = ativoP.isChecked();
            if (idx < 0) periodos.add(p);
            desenharPeriodos();
            ui.toast("Lembre de tocar em Salvar.");
            d.dismiss();
        }));
        d.show();
    }
}
