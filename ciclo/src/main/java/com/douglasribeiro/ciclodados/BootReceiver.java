package com.douglasribeiro.ciclodados;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Depois de reiniciar o celular ou atualizar o app (os alarmes e a notificação se perdem), retoma sozinho.
 * Não manda sinal na hora: logo depois de ligar, o Modos e Rotinas da Samsung ainda não carregou e
 * perderia o sinal (foi o que deixou os dados desligados depois do reinício da madrugada).
 * Espera um pouco e então o serviço reenvia o sinal certo e confere.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a);
        if (!boot && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        SharedPreferences p = Registro.prefs(context);
        if (!p.getBoolean("enabled", false)) return;
        long espera = boot ? CicloService.ESPERA_BOOT_MS : CicloService.ESPERA_ATUALIZACAO_MS;
        long ate = System.currentTimeMillis() + espera;
        SharedPreferences.Editor e = p.edit();
        for (Automacao x : Automacao.carregar(p)) e.putInt(x.k() + "step", 0);
        e.putLong("inicio_ate", ate).apply();
        Registro.add(context, (boot ? "Celular reiniciado" : "App atualizado")
                + ": sinais às " + CicloService.hora(ate) + ", depois que as rotinas carregarem.");
        try {
            context.startForegroundService(new Intent(context, CicloService.class));
        } catch (RuntimeException ex) {
            Registro.add(context, "ERRO ao retomar: " + ex.getClass().getSimpleName() + ". Abra o app.");
        }
    }
}
