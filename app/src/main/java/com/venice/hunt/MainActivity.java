package com.venice.hunt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;

public class MainActivity extends AppCompatActivity {

    private static final String PERM = "com.termux.permission.RUN_COMMAND";
    private static final String KEY = "cmds";
    private static final String TERMUX = "com.termux";

    private EditText input;
    private LinearLayout list;
    private SharedPreferences prefs;
    private final JSONArray arr = new JSONArray();
    private ActivityResultLauncher<String> permLauncher;
    private String pendingCmd;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("hunt", MODE_PRIVATE);
        input = findViewById(R.id.input);
        list = findViewById(R.id.list);

        permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), ok -> {
                    if (ok && pendingCmd != null) {
                        String c = pendingCmd;
                        pendingCmd = null;
                        send(c);
                    } else if (!ok) {
                        toast("পারমিশন ছাড়া কমান্ড চলবে না");
                    }
                });

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String c = input.getText().toString().trim();
            if (c.isEmpty()) return;
            addCommand(c);
            input.setText("");
        });

        findViewById(R.id.btnRunNow).setOnClickListener(v -> {
            String c = input.getText().toString().trim();
            if (c.isEmpty()) return;
            run(c);
        });

        findViewById(R.id.btnSetup).setOnClickListener(v -> run(
                "termux-setup-storage; pkg update -y; pkg install -y python"));

        findViewById(R.id.btnOpen).setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(TERMUX);
            if (i != null) startActivity(i);
            else toast("Termux ইনস্টল করা নেই");
        });

        if (prefs.getBoolean("first", true)) {
            prefs.edit().putBoolean("first", false).apply();
            addCommand("cd ~ && python hunt.py");
            addCommand("cd ~ && python hunt.py --help");
            addCommand("ls -la ~");
        } else {
            load();
            render();
        }

        if (!hasPerm()) permLauncher.launch(PERM);
    }

    private boolean hasPerm() {
        return ContextCompat.checkSelfPermission(this, PERM)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void run(String cmd) {
        if (!hasPerm()) {
            pendingCmd = cmd;
            permLauncher.launch(PERM);
            return;
        }
        send(cmd);
    }

    private void send(String cmd) {
        Intent i = new Intent();
        i.setClassName(TERMUX, "com.termux.app.RunCommandService");
        i.setAction("com.termux.RUN_COMMAND");
        i.putExtra("com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/bash");
        i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", cmd});
        i.putExtra("com.termux.RUN_COMMAND_WORKDIR",
                "/data/data/com.termux/files/home");
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            startService(i);
        } catch (Exception e) {
            try {
                Intent t = getPackageManager().getLaunchIntentForPackage(TERMUX);
                if (t != null) startActivity(t);
            } catch (Exception ignored) { }
            toast("Termux খুলে আবার চেষ্টা করুন");
        }
    }

    private void addCommand(String c) {
        arr.put(c);
        save();
        render();
    }

    private void removeCommand(int idx) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i == idx) continue;
            out.put(arr.optString(i));
        }
        while (arr.length() > 0) arr.remove(0);
        for (int i = 0; i < out.length(); i++) arr.put(out.optString(i));
        save();
        render();
    }

    private void save() {
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    private void load() {
        try {
            JSONArray j = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < j.length(); i++) arr.put(j.optString(i));
        } catch (Exception ignored) { }
    }

    private void render() {
        list.removeAllViews();
        if (arr.length() == 0) {
            TextView t = new TextView(this);
            t.setText("এখনো কিছু সেভ করা নেই।");
            t.setAlpha(0.6f);
            list.addView(t);
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            final int idx = i;
            final String cmd = arr.optString(i);
            TextView t = new TextView(this);
            t.setText(cmd);
            t.setTypeface(Typeface.MONOSPACE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            int p = dp(12);
            t.setPadding(p, p, p, p);
            t.setBackgroundResource(android.R.drawable.list_selector_background);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> run(cmd));
            t.setOnLongClickListener(v -> {
                removeCommand(idx);
                toast("মুছে ফেলা হয়েছে");
                return true;
            });
            list.addView(t);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
