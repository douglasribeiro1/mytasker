package com.douglasribeiro.mytasker;

import java.io.InputStream;

/**
 * Roda no processo do Shizuku com identidade de shell (ADB), por isso pode executar "svc data".
 */
public class PrivilegedService extends IPrivilegedService.Stub {

    public PrivilegedService() {
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public boolean setMobileData(boolean enabled) {
        try {
            Process process = new ProcessBuilder(
                    "/system/bin/svc", "data", enabled ? "enable" : "disable")
                    .redirectErrorStream(true)
                    .start();
            drain(process.getInputStream());
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void drain(InputStream in) throws Exception {
        byte[] buffer = new byte[256];
        while (in.read(buffer) != -1) {
            // descarta a saída do comando
        }
    }
}
