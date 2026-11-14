package com.douglasribeiro.mytasker;

interface IPrivilegedService {
    void destroy() = 16777114;          // código reservado pelo Shizuku para encerrar o serviço
    void exit() = 1;
    boolean setMobileData(boolean enabled) = 2;
}
