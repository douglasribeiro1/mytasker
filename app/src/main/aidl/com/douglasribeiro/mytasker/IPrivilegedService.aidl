package com.douglasribeiro.mytasker;

interface IPrivilegedService {
    void destroy() = 16777114;          // código reservado pelo Shizuku para encerrar o serviço
    void exit() = 1;
    boolean setMobileData(boolean enabled) = 2;
    boolean setWifi(boolean enabled) = 3;
    // subId: assinatura de dados padrão (SubscriptionManager.getDefaultDataSubscriptionId()), ou -1.
    boolean isMobileDataEnabled(int subId) = 4;
}
