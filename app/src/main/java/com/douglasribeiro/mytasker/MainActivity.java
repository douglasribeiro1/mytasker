package com.douglasribeiro.mytasker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    static final String PREFS = "mytasker";
    private static final int REQ_SHIZUKU = 100;
    private static final int REQ_NOTIF = 101;

    private SharedPreferences prefs;
    private TextView statusView;
    private EditText offlineField;
    private EditText onlineField;
    private EditText graceField;
    private CheckBox mobileBox;
    private CheckBox wifiBox;
    private Switch cycleSwitch;
    private boolean suppressSwitch;

    private final Shizuku.OnBinderReceivedListener binderReceived = this::refreshStatus;
    private final Shizuku.OnBinderDeadListener binderDead = this::refreshStatus;
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        refreshStatus();
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            toast("Shizuku autorizado. Ative o ciclo de novo.");
        } else {
            toast("Permissão negada no Shizuku.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permissionResult);
        super.onDestroy();
    }

    private void buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        root.addView(text("MyTasker", 22, true));
        statusView = text("", 15, false);
        root.addView(statusView);

        root.addView(text("O que o ciclo pode desligar (só se estiver ligado; nunca liga o que você desligou)", 14, false));
        mobileBox = new CheckBox(this);
        mobileBox.setText("Dados móveis");
        mobileBox.setChecked(prefs.getBoolean("cycle_mobile", true));
        root.addView(mobileBox);
        wifiBox = new CheckBox(this);
        wifiBox.setText("Wi-Fi");
        wifiBox.setChecked(prefs.getBoolean("cycle_wifi", false));
        root.addView(wifiBox);

        root.addView(text("Minutos de segurança após apagar a tela", 14, false));
        graceField = numberField(prefs.getInt("grace_min", 3));
        root.addView(graceField);

        root.addView(text("Minutos desligado", 14, false));
        offlineField = numberField(prefs.getInt("offline_min", 15));
        root.addView(offlineField);

        root.addView(text("Minutos ligado (para sincronizar)", 14, false));
        onlineField = numberField(prefs.getInt("online_min", 2));
        root.addView(onlineField);

        Button save = new Button(this);
        save.setText("Salvar configurações");
        save.setOnClickListener(v -> saveIntervals());
        root.addView(save);

        cycleSwitch = new Switch(this);
        cycleSwitch.setText("Ciclo ativo");
        cycleSwitch.setTextSize(16);
        cycleSwitch.setChecked(prefs.getBoolean("active", false));
        cycleSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!suppressSwitch) onCycleToggled(checked);
        });
        root.addView(cycleSwitch);

        Button permission = new Button(this);
        permission.setText("Pedir autorização ao Shizuku");
        permission.setOnClickListener(v -> requestShizukuPermission());
        root.addView(permission);

        root.addView(text("Deixe o MyTasker com a bateria em \"Sem restrições\" nas configurações do app. "
                + "O Shizuku precisa ser iniciado de novo após cada reinício do celular.", 13, false));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void saveIntervals() {
        int offline = parse(offlineField.getText().toString());
        int online = parse(onlineField.getText().toString());
        int grace = parse(graceField.getText().toString());
        if (offline < 1 || online < 1 || grace < 1 || offline > 1440 || online > 1440 || grace > 120) {
            toast("Use números inteiros: segurança de 1 a 120, ciclos de 1 a 1440 minutos.");
            return;
        }
        if (!mobileBox.isChecked() && !wifiBox.isChecked()) {
            toast("Marque dados móveis, Wi-Fi ou os dois.");
            return;
        }
        prefs.edit()
                .putInt("offline_min", offline)
                .putInt("online_min", online)
                .putInt("grace_min", grace)
                .putBoolean("cycle_mobile", mobileBox.isChecked())
                .putBoolean("cycle_wifi", wifiBox.isChecked())
                .apply();
        if (prefs.getBoolean("active", false)) {
            startCycleService();
        }
        toast("Configurações salvas.");
    }

    private void onCycleToggled(boolean on) {
        if (on) {
            if (!shizukuAuthorized()) {
                setSwitchQuietly(false);
                return;
            }
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            }
            prefs.edit().putBoolean("active", true).apply();
            startCycleService();
        } else {
            prefs.edit().putBoolean("active", false).apply();
            stopService(new Intent(this, CicloService.class));
        }
    }

    private void startCycleService() {
        startForegroundService(new Intent(this, CicloService.class));
    }

    private boolean shizukuAuthorized() {
        if (!Shizuku.pingBinder()) {
            toast("O Shizuku não está rodando. Abra o app Shizuku e inicie-o.");
            return false;
        }
        if (Shizuku.isPreV11()) {
            toast("Versão do Shizuku muito antiga.");
            return false;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            toast("Autorize o MyTasker no Shizuku primeiro.");
            return false;
        }
        return true;
    }

    private void requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            toast("O Shizuku não está rodando.");
            return;
        }
        if (Shizuku.isPreV11()) {
            toast("Versão do Shizuku muito antiga.");
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            toast("Já autorizado.");
            return;
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            toast("Negado definitivamente. Autorize pelo app Shizuku.");
            return;
        }
        Shizuku.requestPermission(REQ_SHIZUKU);
    }

    private void refreshStatus() {
        if (statusView == null) return;
        String text;
        if (!Shizuku.pingBinder()) {
            text = "Shizuku: não está rodando";
        } else if (Shizuku.isPreV11()) {
            text = "Shizuku: versão antiga";
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            text = "Shizuku: rodando · autorizado";
        } else {
            text = "Shizuku: rodando · sem autorização";
        }
        statusView.setText(text);
    }

    private void setSwitchQuietly(boolean checked) {
        suppressSwitch = true;
        cycleSwitch.setChecked(checked);
        suppressSwitch = false;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private EditText numberField(int value) {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(String.valueOf(value));
        return field;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
