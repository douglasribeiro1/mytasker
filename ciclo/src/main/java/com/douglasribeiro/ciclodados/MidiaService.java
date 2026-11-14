package com.douglasribeiro.ciclodados;

import android.media.browse.MediaBrowser;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import java.util.ArrayList;
import java.util.List;

/**
 * Serviço de mídia vazio. Existe só para o Android (e a rotina da Samsung) reconhecer o
 * Ciclo Dados como app de mídia e mostrá-lo na lista "A mídia está sendo reproduzida".
 * Não expõe nenhum conteúdo.
 */
public class MidiaService extends MediaBrowserService {

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot("vazio", null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        result.sendResult(new ArrayList<>());
    }
}
