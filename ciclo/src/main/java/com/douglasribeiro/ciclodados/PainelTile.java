package com.douglasribeiro.ciclodados;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Botão no painel rápido: com o Pulso ligado, pausa por 1 hora ou retoma. Desligado, abre o app. */
public class PainelTile extends TileService {

    @Override
    public void onStartListening() {
        atualizar();
    }

    @Override
    public void onClick() {
        SharedPreferences p = Registro.prefs(this);
        if (!p.getBoolean("enabled", false)) {
            Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(i);
            }
            return;
        }
        boolean pausado = p.getLong("pausa_ate", 0) > System.currentTimeMillis();
        Intent s = new Intent(this, CicloService.class)
                .setAction(pausado ? CicloService.ACTION_RETOMAR : CicloService.ACTION_PAUSAR);
        try {
            startForegroundService(s);
        } catch (RuntimeException e) {
            try {
                startService(s);
            } catch (RuntimeException ignored) {
            }
        }
        // Atualiza já com o estado esperado.
        if (pausado) p.edit().remove("pausa_ate").apply();
        else p.edit().putLong("pausa_ate", System.currentTimeMillis() + 60 * 60_000L).apply();
        atualizar();
    }

    private void atualizar() {
        Tile t = getQsTile();
        if (t == null) return;
        SharedPreferences p = Registro.prefs(this);
        boolean ligado = p.getBoolean("enabled", false);
        long pausa = p.getLong("pausa_ate", 0);
        boolean pausado = pausa > System.currentTimeMillis();
        t.setLabel("Pulso");
        t.setState(ligado && !pausado ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) {
            t.setSubtitle(!ligado ? "Desligado" : pausado ? "Pausado até " + CicloService.hora(pausa) : "Ativo");
        }
        t.updateTile();
    }
}
