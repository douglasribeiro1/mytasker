package com.douglasribeiro.ciclodados;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Tutorial passo a passo. Abre sozinho na primeira vez e fica no cartão "Como funciona". */
public class TutorialActivity extends Activity {

    private int corFundo, corCartao, corTexto, corSuave, corDestaque;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean escuro = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        corFundo = escuro ? 0xFF000000 : 0xFFF2F2F7;
        corCartao = escuro ? 0xFF1C1C1E : 0xFFFFFFFF;
        corTexto = escuro ? 0xFFF2F2F2 : 0xFF111111;
        corSuave = escuro ? 0xFFA0A0A5 : 0xFF6B6B70;
        corDestaque = escuro ? 0xFF8C9EFF : 0xFF3949AB;

        SharedPreferences prefs = Registro.prefs(this);
        List<Automacao> todas = Automacao.carregar(prefs);
        Automacao ex = todas.isEmpty() ? new Automacao() : todas.get(0);
        String off = ex.off, on = ex.on;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));

        TextView titulo = texto("Como funciona", 28, corTexto);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulo.setPadding(dp(4), dp(8), 0, dp(16));
        root.addView(titulo);

        passo(root, "A ideia",
                "Apps comuns não podem desligar os dados móveis, o Wi-Fi e outras coisas. As rotinas da Samsung podem, "
                + "mas não sabem fazer ciclos (\"15 min desliga, 2 min liga\").\n\n"
                + "O Pulso faz a parte do tempo: cada automação tem uma notificação própria cujo título muda entre "
                + "duas palavras. Duas rotinas suas leem essas palavras e fazem o trabalho:\n\n"
                + "• palavra de economia (ex.: \"" + off + "\") → a rotina desliga\n"
                + "• palavra de normal (ex.: \"" + on + "\") → a rotina religa\n\n"
                + "Você pode ter quantas automações quiser (dados, Wi-Fi, tempo de tela...), cada uma com "
                + "suas palavras, tempos e horários.", null, null);

        passo(root, "1. Permissões",
                "• Notificações do Pulso: permitidas, sem silenciar nem bloquear — é por elas que as rotinas "
                + "recebem o sinal.\n"
                + "• Bateria do Pulso: \"Sem restrições\". Sem isso o Android pode atrasar ou parar os ciclos.",
                "Abrir configurações do Pulso", () -> abrir(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        passo(root, "2. Criar a automação no Pulso",
                "Toque em \"+ Nova automação\" (ou abra uma existente) e preencha:\n"
                + "• Nome — só para você.\n"
                + "• Palavra de economia e palavra de normal — cada uma tem um botão Copiar.\n"
                + "• Tempos, condições e horários.\n\n"
                + "Regra das palavras: nenhuma pode estar dentro de outra, nem entre automações diferentes. "
                + "\"Desligar Wi-Fi\" contém \"ligar Wi-Fi\" — prefira pares como "
                + "\"Desligar Wi-Fi\" / \"Religar Wi-Fi\" ou \"Wi-Fi OFF\" / \"Wi-Fi ON\". "
                + "O app avisa se houver conflito.", null, null);

        passo(root, "3. Criar as duas rotinas na Samsung",
                "Em Configurações → Modos e Rotinas → Rotinas → +:\n\n"
                + "Rotina de economia\n"
                + "SE: Notificação recebida → app Pulso → cole a palavra de economia.\n"
                + "ENTÃO: o que deve desligar (ex.: Dados móveis → Desligado).\n\n"
                + "Rotina de normal\n"
                + "SE: Notificação recebida → app Pulso → cole a palavra de normal.\n"
                + "ENTÃO: o que deve voltar (ex.: Dados móveis → Ligado).\n\n"
                + "Repita para cada automação.",
                "Abrir Modos e Rotinas", this::abrirRotinas);

        passo(root, "4. Testar",
                "Ligue o Pulso. Abra a automação e toque em \"Enviar economia\": a rotina deve agir. "
                + "\"Enviar normal\" desfaz. Se nada acontecer, confira na rotina o app e a palavra (use o Copiar).",
                null, null);

        passo(root, "5. Ciclo padrão",
                "Para cada automação ativa:\n"
                + "• A tela apaga → espera → economia → normal → economia...\n"
                + "• Usar o celular volta ao normal na hora. Você escolhe: ao acender a tela, só ao desbloquear "
                + "(olhar as horas não interrompe) ou nunca.\n"
                + "• No Wi-Fi ou carregando, fica no normal (opcional). Carregando cobre o carro/Android Auto.\n"
                + "• \"Esta automação controla os dados móveis\": respeita quando você mesmo desliga os dados, "
                + "confere 1 min depois de voltar ao normal (reenvia e avisa se falhar) e mostra o tempo em economia.",
                null, null);

        passo(root, "6. Horários",
                "Cada automação pode ter quantos horários quiser: dias, início e fim (pode passar da meia-noite) "
                + "e o que fazer:\n"
                + "• Economia o tempo todo — mesmo com a tela acesa (ex.: madrugada).\n"
                + "• Ciclo com tempos próprios — ex.: 30 min economia / 2 min normal.\n"
                + "• Sem ciclo — tudo normal.\n\n"
                + "Se dois horários se sobrepõem, vale o de cima (↑ reordena). "
                + "Pausa, carregando e Wi-Fi valem acima dos horários.", null, null);

        passo(root, "7. Atalhos",
                "• \"Pausar tudo por 1 hora\": na tela do app e na notificação \"Pulso\". Volta sozinho.\n"
                + "• Painel rápido: puxe o painel → lápis (editar) → arraste o botão \"Pulso\". "
                + "Toque para pausar ou retomar.\n"
                + "• O Pulso volta sozinho depois de reiniciar o celular.", null, null);

        passo(root, "Se algo não funcionar",
                "• Nada acontece: confira se as notificações do Pulso aparecem e se as rotinas estão ativas.\n"
                + "• Duas rotinas disparando juntas: uma palavra está dentro de outra — troque.\n"
                + "• Duas automações mexendo na mesma coisa ao mesmo tempo se atrapalham — evite.\n"
                + "• Se a rotina desliga o Wi-Fi, não marque \"Sem economia quando conectado ao Wi-Fi\".\n"
                + "• O Histórico (tela principal) mostra tudo o que o Pulso fez, com horário.\n"
                + "• Mensagens podem atrasar durante a economia; ao acender a tela, chegam na hora. "
                + "Ligações normais continuam funcionando.", null, null);

        Button ok = botao("Entendi");
        ok.setOnClickListener(v -> finish());
        root.addView(ok);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(corFundo);
        sv.setFitsSystemWindows(true);
        sv.addView(root);
        setContentView(sv);
        getWindow().setStatusBarColor(corFundo);
        getWindow().setNavigationBarColor(corFundo);
    }

    private void passo(LinearLayout pai, String titulo, String corpo, String acao, Runnable r) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(corCartao);
        bg.setCornerRadius(dp(24));
        c.setBackground(bg);
        TextView t = texto(titulo, 18, corDestaque);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(8));
        c.addView(t);
        TextView b = texto(corpo, 15, corTexto);
        b.setLineSpacing(0, 1.15f);
        c.addView(b);
        if (acao != null) {
            Button bt = new Button(this);
            bt.setText(acao);
            bt.setAllCaps(false);
            bt.setOnClickListener(v -> r.run());
            c.addView(bt);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        pai.addView(c, lp);
    }

    private void abrirRotinas() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.samsung.android.app.routines");
        if (i == null) {
            toast("Abra Configurações → Modos e Rotinas.");
            abrir(new Intent(Settings.ACTION_SETTINGS));
            return;
        }
        abrir(i);
    }

    private void abrir(Intent i) {
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            toast("Não consegui abrir. Vá em Configurações.");
        }
    }

    private Button botao(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(corDestaque);
        bg.setCornerRadius(dp(20));
        b.setBackground(bg);
        b.setTextColor(0xFFFFFFFF);
        return b;
    }

    private TextView texto(String s, int sp, int cor) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(cor);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
