package com.douglasribeiro.ciclodados;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Confere tudo o que o Pulso precisa para funcionar e mostra o estado do celular agora. */
public class DiagnosticoActivity extends Activity {

    private static final String ROTINAS = "com.samsung.android.app.routines";

    private SharedPreferences prefs;
    private Ui ui;
    private LinearLayout checagens, estado, testes;
    private TextView versao;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean testando;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Registro.prefs(this);
        ui = new Ui(this);
        LinearLayout root = ui.raiz();
        ui.titulo(root, "Diagnóstico");

        ui.rotulo(root, "O que o Pulso precisa");
        checagens = ui.cartao(root);

        ui.rotulo(root, "Celular agora");
        estado = ui.cartao(root);

        ui.rotulo(root, "Testar as rotinas de dados");
        testes = ui.cartao(root);

        ui.rotulo(root, "Versão");
        LinearLayout v = ui.cartao(root);
        versao = ui.texto("", 15, ui.texto);
        v.addView(versao);
        v.addView(ui.botao("Procurar atualização", this::procurar, false));
        v.addView(ui.texto("Link para compartilhar o Pulso:\n" + Atualizacao.PAGINA, 13, ui.suave));
        v.addView(ui.botao("Copiar link", () -> ui.copiar(Atualizacao.PAGINA), false));

        root.addView(ui.botaoLargo("Atualizar diagnóstico", this::preencher, true));
        ui.mostrar(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        preencher();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void preencher() {
        checagens.removeAllViews();
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean ligado = prefs.getBoolean("enabled", false);
        List<Automacao> todas = Automacao.carregar(prefs);
        int ativas = 0;
        for (Automacao a : todas) if (a.ativo) ativas++;

        item(ligado, "Pulso ligado", ligado ? null : "Ligue na tela inicial.", null, null);

        boolean resumo = false;
        if (ligado) {
            for (StatusBarNotification s : nm.getActiveNotifications()) if (s.getId() == 1) resumo = true;
        }
        item(!ligado || resumo, "Notificação do Pulso na barra",
                !ligado ? "Aparece quando o Pulso está ligado." : resumo ? null
                        : "Não está aparecendo. Desligue e ligue o Pulso.", null, null);

        boolean notif = nm.areNotificationsEnabled();
        NotificationChannel ch = nm.getNotificationChannel("sinal");
        boolean canal = ch == null || ch.getImportance() != NotificationManager.IMPORTANCE_NONE;
        item(notif && canal, "Notificações permitidas",
                notif && canal ? null : "Sem elas as rotinas não recebem o sinal.",
                "Abrir", () -> abrir(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));

        boolean bateria = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        item(bateria, "Bateria \"Sem restrições\"",
                bateria ? null : "O Android pode atrasar ou parar os ciclos.", "Corrigir", this::pedirBateria);

        if (Build.VERSION.SDK_INT >= 28) {
            boolean restrito = getSystemService(ActivityManager.class).isBackgroundRestricted();
            item(!restrito, "Segundo plano liberado",
                    restrito ? "O app está restrito em segundo plano." : null, "Abrir", this::detalhesApp);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            boolean alarmes = getSystemService(AlarmManager.class).canScheduleExactAlarms();
            item(alarmes, "Alarmes no horário exato", alarmes ? null : "Os tempos podem atrasar.", null, null);
        }

        boolean rotinas = getPackageManager().getLaunchIntentForPackage(ROTINAS) != null;
        item(rotinas, "Modos e Rotinas da Samsung",
                rotinas ? null : "Não encontrado. O Pulso precisa dele (só celulares Samsung).",
                rotinas ? "Abrir" : null, rotinas ? () -> abrir(getPackageManager().getLaunchIntentForPackage(ROTINAS)) : null);

        item(ativas > 0, "Automações ativas: " + ativas, ativas > 0 ? null : "Crie ou ative uma automação.", null, null);
        String conflito = Automacao.conflito(todas);
        item(conflito == null, "Palavras sem conflito", conflito, null, null);

        TextView dica = ui.texto("Na Samsung, confira também: Configurações → Bateria → Limites de uso em segundo plano. "
                + "O Pulso não pode estar em \"Apps em suspensão\" nem em \"Apps em suspensão profunda\".", 12, ui.suave);
        dica.setPadding(0, ui.dp(10), 0, 0);
        checagens.addView(dica);

        estado.removeAllViews();
        linha(estado, "Tela", Aparelho.telaLigada(this)
                ? (Aparelho.bloqueado(this) ? "acesa, bloqueada" : "acesa, desbloqueada") : "apagada");
        linha(estado, "Carregando", Aparelho.carregando(this) ? "sim" : "não");
        linha(estado, "Rede em uso", Aparelho.rede(this));
        linha(estado, "Dados móveis", Aparelho.dadosLigados(this) ? "ligados" : "desligados");
        linha(estado, "Roteador Wi-Fi", Aparelho.roteadorLigado(this) ? "ligado" : "desligado");
        linha(estado, "Em chamada", Aparelho.emChamada(this) ? "sim" : "não");
        long pausa = prefs.getLong("pausa_ate", 0);
        if (pausa > System.currentTimeMillis()) linha(estado, "Pausa", "até " + CicloService.hora(pausa));
        TextView obs = ui.texto("Ligue o roteador ou faça uma chamada e toque em \"Atualizar diagnóstico\" "
                + "para ver se o Pulso percebe.", 12, ui.suave);
        obs.setPadding(0, ui.dp(8), 0, 0);
        estado.addView(obs);

        testes.removeAllViews();
        testes.addView(ui.texto("Manda a palavra de economia, confere se os dados desligaram, depois manda a "
                + "de normal e confere se religaram. A internet cai por uns 20 segundos.", 13, ui.suave));
        boolean algum = false;
        for (Automacao a : todas) {
            if (!a.ativo || !a.dados) continue;
            algum = true;
            testes.addView(ui.botao("Testar \"" + a.nome + "\"", () -> testar(a), false));
        }
        if (!algum) {
            testes.addView(ui.texto("\nNenhuma automação ativa com \"controla os dados móveis\". "
                    + "Para as outras, use os botões de teste dentro da automação.", 13, ui.suave));
        }

        String nova = Atualizacao.disponivel(this);
        versao.setText("Instalada: " + Atualizacao.nomeAtual(this)
                + (nova != null ? "\nDisponível: " + nova : ""));
    }

    private void item(boolean bom, String titulo, String detalhe, String acao, Runnable r) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, ui.dp(6), 0, ui.dp(6));
        TextView ic = ui.texto(bom ? "✓" : "!", 16, 0xFFFFFFFF);
        ic.setTypeface(Typeface.DEFAULT_BOLD);
        ic.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(bom ? ui.ok : ui.alerta);
        ic.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28));
        lp.rightMargin = ui.dp(12);
        l.addView(ic, lp);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        txt.addView(ui.texto(titulo, 15, ui.texto));
        if (detalhe != null) txt.addView(ui.texto(detalhe, 12, bom ? ui.suave : ui.alerta));
        l.addView(txt);
        if (!bom && acao != null && r != null) l.addView(ui.botao(acao, r, false));
        checagens.addView(l);
    }

    private void linha(LinearLayout pai, String nome, String valor) {
        LinearLayout l = new LinearLayout(this);
        l.setPadding(0, ui.dp(4), 0, ui.dp(4));
        TextView n = ui.texto(nome, 15, ui.suave);
        n.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        l.addView(n);
        TextView v = ui.texto(valor, 15, ui.texto);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(v);
        pai.addView(l);
    }

    // ---------------- Teste da rotina ----------------

    private void testar(Automacao a) {
        if (!prefs.getBoolean("enabled", false)) {
            ui.toast("Ligue o Pulso primeiro.");
            return;
        }
        if (testando) {
            ui.toast("Já tem um teste em andamento.");
            return;
        }
        if (!Aparelho.dadosLigados(this)) {
            ui.toast("Os dados móveis já estão desligados. Ligue-os antes do teste.");
            return;
        }
        testando = true;
        TextView r = ui.texto("\"" + a.nome + "\": enviando \"" + a.off + "\"…", 14, ui.texto);
        r.setPadding(0, ui.dp(8), 0, 0);
        testes.addView(r);
        enviar(CicloService.ACTION_TESTE_OFF, a);
        h.postDelayed(() -> {
            boolean desligou = !Aparelho.dadosLigados(this);
            r.append(desligou ? "\n✓ dados desligaram." : "\n✗ dados NÃO desligaram: confira a rotina de \"" + a.off + "\".");
            r.append("\nEnviando \"" + a.on + "\"…");
            enviar(CicloService.ACTION_TESTE_ON, a);
            h.postDelayed(() -> {
                boolean religou = Aparelho.dadosLigados(this);
                r.append(religou ? "\n✓ dados religaram." : "\n✗ dados NÃO religaram: confira a rotina de \"" + a.on + "\".");
                r.append(desligou && religou ? "\nTudo certo!" : "");
                r.setTextColor(desligou && religou ? ui.ok : ui.alerta);
                testando = false;
            }, 20_000);
        }, 20_000);
    }

    private void enviar(String action, Automacao a) {
        startForegroundService(new Intent(this, CicloService.class).setAction(action)
                .putExtra(CicloService.EXTRA_ID, a.id));
    }

    // ---------------- Outros ----------------

    private void procurar() {
        ui.toast("Procurando…");
        Atualizacao.verificar(this, true, erro -> {
            if (erro != null) {
                ui.toast(erro);
                return;
            }
            String nova = Atualizacao.disponivel(this);
            if (nova == null) ui.toast("Você já está na versão mais recente.");
            else abrir(new Intent(Intent.ACTION_VIEW, Uri.parse(Atualizacao.link(this))));
            preencher();
        });
    }

    private void pedirBateria() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            detalhesApp();
        }
    }

    private void detalhesApp() {
        abrir(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void abrir(Intent i) {
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            ui.toast("Não consegui abrir.");
        }
    }
}
