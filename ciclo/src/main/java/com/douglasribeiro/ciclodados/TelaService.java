package com.douglasribeiro.ciclodados;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

/**
 * Serve só para apagar a tela depois do gatilho (mesmo efeito do botão lateral).
 * Não lê nada da tela: não recebe eventos nem conteúdo de janelas.
 */
public class TelaService extends AccessibilityService {

    private static volatile TelaService instancia;

    static boolean ativo() {
        return instancia != null;
    }

    static boolean apagarTela() {
        TelaService s = instancia;
        if (s == null || Build.VERSION.SDK_INT < 28) return false;
        return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    protected void onServiceConnected() {
        instancia = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instancia = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instancia = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
