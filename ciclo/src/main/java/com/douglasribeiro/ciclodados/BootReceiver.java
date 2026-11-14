package com.douglasribeiro.ciclodados;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Depois de reiniciar o celular ou atualizar o app (os alarmes se perdem), retoma sozinho. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        SharedPreferences p = Registro.prefs(context);
        if (!p.getBoolean("enabled", false)) return;
        // Os alarmes se perderam: cada automação recomeça do zero (o sinal atual é mantido).
        SharedPreferences.Editor e = p.edit();
        for (Automacao x : Automacao.carregar(p)) e.putInt(x.k() + "step", 0);
        e.apply();
        Registro.add(context, Intent.ACTION_BOOT_COMPLETED.equals(a)
                ? "Celular reiniciado: retomando." : "App atualizado: retomando.");
        try {
            context.startForegroundService(new Intent(context, CicloService.class));
        } catch (RuntimeException ex) {
            Registro.add(context, "ERRO ao retomar: " + ex.getClass().getSimpleName() + ". Abra o app.");
        }
    }
}
