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
        String off = CicloService.palavraOff(prefs), on = CicloService.palavraOn(prefs);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));

        TextView titulo = texto("Como funciona", 28, corTexto);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulo.setPadding(dp(4), dp(8), 0, dp(16));
        root.addView(titulo);

        passo(root, "A ideia",
                "Apps comuns não podem desligar os dados móveis. As rotinas da Samsung podem.\n\n"
                + "O Pulso não desliga nada sozinho: ele mantém uma notificação fixa e troca o texto dela na hora certa. "
                + "Duas rotinas suas leem esse texto e fazem o trabalho:\n\n"
                + "• \"" + off + "\" → economia (a rotina desliga o que você escolheu)\n"
                + "• \"" + on + "\" → normal (a rotina religa)\n\n"
                + "Você escolhe o que as rotinas fazem: dados móveis, Wi-Fi ou outra coisa.", null, null);

        passo(root, "1. Permissões",
                "• Notificações do Pulso: permitidas (é por elas que as rotinas recebem o sinal). "
                + "Não silencie nem bloqueie a notificação do Pulso.\n"
                + "• Bateria do Pulso: \"Sem restrições\". Sem isso o Android pode atrasar ou parar o ciclo.",
                "Abrir bateria do Pulso", () -> abrir(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        passo(root, "2. Rotina de economia",
                "Em Configurações → Modos e Rotinas → Rotinas → +:\n\n"
                + "SE: Notificação recebida → escolha o app Pulso → informe a palavra \"" + off + "\".\n"
                + "ENTÃO: Dados móveis → Desligado. (Se quiser, acrescente Wi-Fi → Desligado.)\n\n"
                + "Dê um nome (ex.: \"Pulso economia\") e salve.",
                "Abrir Modos e Rotinas", this::abrirRotinas);

        passo(root, "3. Rotina de volta ao normal",
                "Crie outra rotina igual, mas:\n\n"
                + "SE: Notificação recebida → app Pulso → palavra \"" + on + "\".\n"
                + "ENTÃO: Dados móveis → Ligado. (E Wi-Fi → Ligado, se você colocou na outra.)\n\n"
                + "As palavras não podem estar uma dentro da outra: \"Desligar\" contém \"ligar\", "
                + "por isso o padrão para voltar é \"" + on + "\". Dá para trocar em Avançado.", null, null);

        passo(root, "4. Testar",
                "Ligue o Pulso. Em Avançado, toque em \"Enviar desligar\": o ícone de dados deve sumir. "
                + "Toque em \"Enviar ligar\": ele volta. Se não mudar, revise as rotinas (app e palavra).", null, null);

        passo(root, "5. Ciclo padrão",
                "Com o Pulso ligado:\n"
                + "• A tela apaga → espera (padrão 3 min) → economia (15 min) → normal (2 min) → economia...\n"
                + "• Acender a tela volta ao normal na hora (dá para desmarcar).\n"
                + "• No Wi-Fi ou carregando, fica no normal (dá para desmarcar).\n"
                + "• Se você mesmo desligou os dados, o Pulso não religa.\n"
                + "• 1 min depois de voltar ao normal, o Pulso confere se os dados voltaram. Se não, reenvia "
                + "o sinal e, se ainda falhar, avisa.", null, null);

        passo(root, "6. Horários",
                "Adicione quantos quiser. Cada um tem dias, início e fim (pode passar da meia-noite) e o que fazer:\n"
                + "• Economia o tempo todo — mesmo com a tela acesa (ex.: madrugada).\n"
                + "• Ciclo com tempos próprios — ex.: 30 min economia / 2 min normal.\n"
                + "• Sem ciclo — tudo normal (ex.: horário de trabalho).\n\n"
                + "Se dois horários se sobrepõem, vale o de cima. Use ↑ para reordenar. "
                + "Pausa, carregando e Wi-Fi valem acima dos horários.", null, null);

        passo(root, "7. Atalhos",
                "• \"Pausar 1 hora\": na tela do app e na própria notificação. Volta sozinho.\n"
                + "• Painel rápido: puxe o painel duas vezes → lápis (editar) → arraste o botão \"Pulso\". "
                + "Toque nele para pausar ou retomar.\n"
                + "• O Pulso volta sozinho depois de reiniciar o celular.", null, null);

        passo(root, "Se algo não funcionar",
                "• Nada acontece: confira se a notificação do Pulso aparece e se as rotinas estão ativas.\n"
                + "• Os dados não voltam: confira a rotina do \"" + on + "\".\n"
                + "• Se a sua rotina desliga o Wi-Fi, desmarque \"Sem economia quando conectado ao Wi-Fi\".\n"
                + "• Em Avançado → Histórico aparece tudo o que o Pulso fez, com horário.\n"
                + "• Mensagens podem chegar com atraso durante a economia; ao acender a tela, chegam na hora. "
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
