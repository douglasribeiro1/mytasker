package com.douglasribeiro.ciclodados;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
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

    private void marcar(String e) {
        estado = e;
        Registro.add(this, "   janela: " + e);
    }
}
