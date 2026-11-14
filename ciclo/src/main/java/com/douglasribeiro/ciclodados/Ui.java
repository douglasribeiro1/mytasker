package com.douglasribeiro.ciclodados;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Peças de tela em comum (cartões, campos, botões) com as cores do tema claro/escuro. */
final class Ui {

    final Activity a;
    final int fundo, cartao, texto, suave, destaque, alerta;

    Ui(Activity a) {
        this.a = a;
        boolean escuro = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        fundo = escuro ? 0xFF000000 : 0xFFF2F2F7;
        cartao = escuro ? 0xFF1C1C1E : 0xFFFFFFFF;
        texto = escuro ? 0xFFF2F2F2 : 0xFF111111;
        suave = escuro ? 0xFFA0A0A5 : 0xFF6B6B70;
        destaque = escuro ? 0xFF8C9EFF : 0xFF3949AB;
        alerta = escuro ? 0xFFFF8A65 : 0xFFC0392B;
    }

    LinearLayout raiz() {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        root.setBackgroundColor(fundo);
        return root;
    }

    void mostrar(LinearLayout root) {
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(fundo);
        sv.setFitsSystemWindows(true); // Android 15+ desenha atrás das barras do sistema
        sv.addView(root);
        a.setContentView(sv);
        a.getWindow().setStatusBarColor(fundo);
        a.getWindow().setNavigationBarColor(fundo);
    }

    TextView titulo(LinearLayout pai, String s) {
        TextView t = texto(s, 28, texto);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(8), 0, dp(12));
        pai.addView(t);
        return t;
    }

    LinearLayout cartao(LinearLayout pai) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cartao);
        bg.setCornerRadius(dp(24));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        pai.addView(c, lp);
        return c;
    }

    void rotulo(LinearLayout pai, String s) {
        TextView t = texto(s, 14, suave);
        t.setPadding(dp(8), dp(12), 0, dp(6));
        pai.addView(t);
    }

    LinearLayout secao(LinearLayout pai, String titulo) {
        LinearLayout c = cartao(pai);
        TextView t = texto("▸  " + titulo, 17, texto);
        t.setPadding(0, dp(4), 0, dp(4));
        c.addView(t);
        LinearLayout corpo = new LinearLayout(a);
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

    CheckBox caixa(LinearLayout pai, String s, boolean v) {
        CheckBox c = new CheckBox(a);
        c.setText(s);
        c.setTextColor(texto);
        c.setChecked(v);
        c.setPadding(0, dp(6), 0, dp(6));
        pai.addView(c);
        return c;
    }

    EditText numero(LinearLayout pai, String rotulo, int valor) {
        pai.addView(texto(rotulo, 13, suave));
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(valor));
        e.setTextColor(texto);
        pai.addView(e);
        return e;
    }

    EditText campo(LinearLayout pai, String rotulo, String valor) {
        pai.addView(texto(rotulo, 13, suave));
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setSingleLine(true);
        e.setText(valor);
        e.setTextColor(texto);
        pai.addView(e);
        return e;
    }

    /** Campo de texto com botão "Copiar" ao lado (para colar exatamente na rotina da Samsung). */
    EditText campoCopiavel(LinearLayout pai, String rotulo, String valor) {
        pai.addView(texto(rotulo, 13, suave));
        LinearLayout linha = new LinearLayout(a);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setSingleLine(true);
        e.setText(valor);
        e.setTextColor(texto);
        e.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        linha.addView(e);
        linha.addView(botao("Copiar", () -> copiar(e.getText().toString().trim()), false));
        pai.addView(linha);
        return e;
    }

    void copiar(String s) {
        ClipboardManager cm = a.getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("Pulso", s));
        toast("Copiado: " + s);
    }

    TextView texto(String s, int sp, int cor) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(cor);
        return t;
    }

    Button botao(String s, Runnable r, boolean principal) {
        Button b = new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        if (principal) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(destaque);
            bg.setCornerRadius(dp(20));
            b.setBackground(bg);
            b.setTextColor(0xFFFFFFFF);
        }
        b.setOnClickListener(v -> r.run());
        return b;
    }

    static Integer ler(EditText f, int min, int max) {
        try {
            int v = Integer.parseInt(f.getText().toString().trim());
            return v >= min && v <= max ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    void toast(String s) {
        Toast.makeText(a, s, Toast.LENGTH_SHORT).show();
    }

    int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics());
    }
}
