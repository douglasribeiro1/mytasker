package com.douglasribeiro.ciclodados;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Peças de tela em comum: paleta azul/roxa (escura ou clara, conforme o celular), cartões, campos, botões. */
final class Ui {

    final Activity a;
    final boolean escuro;
    final int fundo, cartao, borda, texto, suave, destaque, destaque2, alerta, ok;

    Ui(Activity a) {
        this.a = a;
        escuro = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        fundo = escuro ? 0xFF0B1020 : 0xFFF3F5FB;
        cartao = escuro ? 0xFF161D33 : 0xFFFFFFFF;
        borda = escuro ? 0xFF243050 : 0xFFE2E7F3;
        texto = escuro ? 0xFFF1F4FF : 0xFF101632;
        suave = escuro ? 0xFF9AA4C3 : 0xFF5D6788;
        destaque = escuro ? 0xFF5B8CFF : 0xFF2F6BFF;
        destaque2 = escuro ? 0xFF9B6BFF : 0xFF7C3AED;
        alerta = escuro ? 0xFFFF8A65 : 0xFFD9480F;
        ok = escuro ? 0xFF34D399 : 0xFF16A34A;
    }

    LinearLayout raiz() {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(32));
        root.setBackgroundColor(fundo);
        return root;
    }

    void mostrar(LinearLayout root) {
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(fundo);
        sv.setFitsSystemWindows(true); // Android 15+ desenha atrás das barras do sistema
        sv.addView(root);
        a.setContentView(sv);
        barras();
    }

    /** Tela com conteúdo rolável e uma barra fixa embaixo. */
    void mostrar(View conteudo, View barra) {
        LinearLayout tudo = new LinearLayout(a);
        tudo.setOrientation(LinearLayout.VERTICAL);
        tudo.setBackgroundColor(fundo);
        tudo.setFitsSystemWindows(true);
        conteudo.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        tudo.addView(conteudo);
        tudo.addView(barra);
        a.setContentView(tudo);
        barras();
    }

    @SuppressWarnings("deprecation")
    private void barras() {
        a.getWindow().setStatusBarColor(fundo);
        a.getWindow().setNavigationBarColor(escuro ? fundo : cartao);
        if (!escuro) {
            a.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    TextView titulo(LinearLayout pai, String s) {
        TextView t = texto(s, 26, texto);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(8), 0, dp(12));
        pai.addView(t);
        return t;
    }

    GradientDrawable fundoCartao() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cartao);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), borda);
        return bg;
    }

    GradientDrawable gradiente(int raio) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{escuro ? 0xFF1E3A8A : destaque, escuro ? 0xFF4C1D95 : destaque2});
        g.setCornerRadius(dp(raio));
        return g;
    }

    LinearLayout cartao(LinearLayout pai) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(fundoCartao());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        pai.addView(c, lp);
        return c;
    }

    /** Cartão de destaque com gradiente azul → roxo (textos brancos). */
    LinearLayout cartaoDestaque(LinearLayout pai) {
        LinearLayout c = cartao(pai);
        c.setBackground(gradiente(26));
        c.setPadding(dp(20), dp(20), dp(20), dp(18));
        return c;
    }

    /** Círculo com gradiente e uma letra ou símbolo no meio. */
    TextView icone(String s, int tamanhoDp) {
        TextView t = texto(s, tamanhoDp / 2 - 2, 0xFFFFFFFF);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = gradiente(0);
        g.setShape(GradientDrawable.OVAL);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(tamanhoDp), dp(tamanhoDp));
        lp.rightMargin = dp(14);
        t.setLayoutParams(lp);
        return t;
    }

    void rotulo(LinearLayout pai, String s) {
        TextView t = texto(s, 14, suave);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(6), dp(14), 0, dp(8));
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
        tingir(c);
        pai.addView(c);
        return c;
    }

    void tingir(CompoundButton b) {
        b.setButtonTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{destaque, suave}));
    }

    /** Interruptor com as cores do app. sobreGradiente = dentro do cartão azul/roxo. */
    void tingir(Switch s, boolean sobreGradiente) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{0xFFFFFFFF, escuro ? 0xFFB8C0DA : 0xFFFFFFFF}));
        s.setTrackTintList(new ColorStateList(st, sobreGradiente
                ? new int[]{0xAAFFFFFF, 0x55FFFFFF}
                : new int[]{destaque, escuro ? 0xFF3A4566 : 0xFFC9D0E3}));
    }

    EditText numero(LinearLayout pai, String rotulo, int valor) {
        pai.addView(texto(rotulo, 13, suave));
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(valor));
        e.setTextColor(texto);
        e.setBackgroundTintList(ColorStateList.valueOf(destaque));
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
        e.setHintTextColor(suave);
        e.setBackgroundTintList(ColorStateList.valueOf(destaque));
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
        e.setHintTextColor(suave);
        e.setBackgroundTintList(ColorStateList.valueOf(destaque));
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

    /** principal = pílula com gradiente; senão, pílula com contorno. */
    Button botao(String s, Runnable r, boolean principal) {
        Button b = new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(44));
        b.setPadding(dp(18), 0, dp(18), 0);
        if (principal) {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{destaque, destaque2});
            bg.setCornerRadius(dp(24));
            b.setBackground(bg);
            b.setTextColor(0xFFFFFFFF);
            b.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x00000000);
            bg.setStroke(dp(1), destaque);
            bg.setCornerRadius(dp(24));
            b.setBackground(bg);
            b.setTextColor(destaque);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), dp(8), dp(6));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    /** Botão que ocupa a largura toda. */
    Button botaoLargo(String s, Runnable r, boolean principal) {
        Button b = botao(s, r, principal);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(8));
        b.setLayoutParams(lp);
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
