package com.douglasribeiro.ciclodados;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * A "janela" que fica aberta durante o período sem internet. Enquanto ela está aberta,
 * a rotina da Samsung ("app aberto: Ciclo Dados") desliga os dados móveis.
 * Ela não acende a tela. Fecha sozinha quando o serviço manda ou quando você acende a tela.
 */
public class JanelaActivity extends Activity {

    private static WeakReference<JanelaActivity> atual = new WeakReference<>(null);
    static volatile String estado = "fechada";

    static void fechar() {
        JanelaActivity a = atual.get();
        if (a != null && !a.isFinishing()) a.finishAndRemoveTask();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        atual = new WeakReference<>(this);
        acenderBrevemente();
        TextView t = new TextView(this);
        t.setText("Ciclo Dados\n\nPeríodo sem internet.\nEsta tela fecha sozinha.");
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(Color.BLACK);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        setContentView(t);
        marcar("criada");
    }

    @Override protected void onResume() { super.onResume(); marcar("em primeiro plano (onResume)"); }
    @Override protected void onPause() { super.onPause(); marcar("pausada (onPause)"); }
    @Override protected void onStop() { super.onStop(); marcar("parada (onStop)"); }

    @Override
    protected void onDestroy() {
        if (atual.get() == this) atual = new WeakReference<>(null);
        marcar("fechada");
        super.onDestroy();
    }

    /**
     * Gatilho: acende a tela no mínimo de brilho, por poucos segundos, só para a Samsung ver o app aberto.
     * Depois apaga a tela pela Acessibilidade (igual ao botão lateral). Sem Acessibilidade, a tela apaga
     * no tempo de bloqueio do próprio celular. O app continua aberto atrás da tela apagada.
     */
    private void acenderBrevemente() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.01f;
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int seg = Registro.prefs(this).getInt("tela_seg", 5);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (!CicloService.gatilhoAtivo) return; // você acendeu/desbloqueou: não apaga a tela na sua mão
            if (TelaService.apagarTela()) {
                Registro.add(this, "Gatilho: " + seg + " s de tela, apagando pela Acessibilidade.");
            } else {
                Registro.add(this, "Gatilho: " + seg + " s de tela. Sem Acessibilidade: apaga no tempo de bloqueio do celular.");
            }
        }, seg * 1000L);
    }

    private void marcar(String e) {
        estado = e;
        Registro.add(this, "   janela: " + e);
    }
}
