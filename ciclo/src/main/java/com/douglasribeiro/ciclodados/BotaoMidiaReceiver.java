package com.douglasribeiro.ciclodados;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recebe os botões de mídia (play/pause do fone) e ignora. Serve só para o app contar como app de mídia. */
public class BotaoMidiaReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Nada a fazer.
    }
}
