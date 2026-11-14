package com.douglasribeiro.mytasker;

import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Roda no processo do Shizuku com identidade de shell (ADB).
 * Segurança: só executa comandos fixos, sem montar texto vindo de fora (o único número recebido é validado).
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
        return run("/system/bin/svc", "data", enabled ? "enable" : "disable") != null;
    }

    @Override
    public boolean setWifi(boolean enabled) {
        if (Build.VERSION.SDK_INT >= 30
                && run("/system/bin/cmd", "wifi", "set-wifi-enabled", enabled ? "enabled" : "disabled") != null) {
            return true;
        }
        return run("/system/bin/svc", "wifi", enabled ? "enable" : "disable") != null;
    }

    @Override
    public boolean isMobileDataEnabled(int subId) {
        if (subId >= 0 && subId < 100_000) {
            String perSim = run("/system/bin/settings", "get", "global", "mobile_data" + subId);
            if (perSim != null && (perSim.equals("0") || perSim.equals("1"))) {
                return perSim.equals("1");
            }
        }
        return "1".equals(run("/system/bin/settings", "get", "global", "mobile_data"));
    }

    /** Executa o comando e devolve a saída (sem espaços nas pontas), ou null se falhar. */
    private static String run(String... cmd) {
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = read(process.getInputStream());
            return process.waitFor() == 0 ? out.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (bytes.size() < 4096) bytes.write(buffer, 0, n);
        }
        return bytes.toString(StandardCharsets.UTF_8.name());
    }
}
