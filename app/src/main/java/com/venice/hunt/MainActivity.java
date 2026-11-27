package com.venice.hunt;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatActivity;

import java.net.*;
import java.io.*;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private EditText input;
    private LinearLayout list;
    private SharedPreferences prefs;

    private final ArrayList<String> savedTargets = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        input = findViewById(R.id.input);
        list = findViewById(R.id.list);
        prefs = getSharedPreferences("hunt_standalone", MODE_PRIVATE);

        // Termux-related buttons are no longer needed.
        findViewById(R.id.btnSetup).setVisibility(View.GONE);
        findViewById(R.id.btnOpen).setVisibility(View.GONE);

        loadSaved();

        findViewById(R.id.btnRecon).setOnClickListener(v -> {
            String target = input.getText().toString().trim();
            if (target.isEmpty()) {
                toast("একটি domain লিখুন");
                return;
            }
            recon(target);
        });

        findViewById(R.id.btnWeb).setOnClickListener(v -> {
            String target = input.getText().toString().trim();
            if (target.isEmpty()) {
                toast("একটি website URL লিখুন");
                return;
            }
            webCheck(target);
        });

        findViewById(R.id.btnPassword).setOnClickListener(v -> {
            passwordCheck(input.getText().toString());
        });

        findViewById(R.id.btnAuto).setOnClickListener(v -> {
            String target = input.getText().toString().trim();
            if (target.isEmpty()) {
                toast("একটি domain বা URL লিখুন");
                return;
            }
            fullAuto(target);
        });

        findViewById(R.id.btnResults).setOnClickListener(v -> showResults());

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String target = input.getText().toString().trim();
            if (target.isEmpty()) {
                toast("আগে domain বা URL লিখুন");
                return;
            }
            if (!savedTargets.contains(target)) {
                savedTargets.add(target);
                saveTargets();
            }
            showSaved();
            toast("Saved");
        });

        findViewById(R.id.btnRunNow).setOnClickListener(v -> {
            String target = input.getText().toString().trim();
            if (target.isEmpty()) {
                toast("একটি domain লিখুন");
                return;
            }
            recon(target);
        });

        showSaved();
    }

    private void recon(String target) {
        runTask("RECON", () -> {
            String host = getHost(target);
            StringBuilder out = new StringBuilder();

            out.append("HUNT RECON\n");
            out.append("Target: ").append(host).append("\n\n");

            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                out.append("DNS RESULTS\n");

                for (InetAddress address : addresses) {
                    out.append("IP: ").append(address.getHostAddress())
                       .append("\n");
                }
            } catch (Exception e) {
                out.append("DNS Error: ").append(e.getMessage()).append("\n");
            }

            out.append("\nDNS check completed.");
            return out.toString();
        });
    }

    private void webCheck(String target) {
        runTask("WEB SECURITY", () -> checkWebsite(target));
    }

    private String checkWebsite(String target) {
        StringBuilder out = new StringBuilder();

        try {
            String address = normalizeUrl(target);
            URL url = new URL(address);

            if (!url.getProtocol().equals("http") &&
                !url.getProtocol().equals("https")) {
                return "Only HTTP and HTTPS URLs are supported.";
            }

            HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "HUNT-Android/1.0");

            int code = connection.getResponseCode();

            out.append("HUNT WEB SECURITY\n");
            out.append("URL: ").append(address).append("\n");
            out.append("HTTP Status: ").append(code).append("\n\n");

            out.append("SECURITY HEADERS\n");

            String[] headers = {
                "Strict-Transport-Security",
                "Content-Security-Policy",
                "X-Content-Type-Options",
                "X-Frame-Options",
                "Referrer-Policy",
                "Permissions-Policy"
            };

            for (String header : headers) {
                String value = connection.getHeaderField(header);
                out.append(header).append(": ")
                   .append(value == null ? "Not detected" : value)
                   .append("\n");
            }

            if (connection instanceof HttpsURLConnection) {
                HttpsURLConnection secure =
                    (HttpsURLConnection) connection;

                out.append("\nHTTPS CERTIFICATE\n");

                Certificate[] certs = secure.getServerCertificates();

                if (certs.length > 0 &&
                    certs[0] instanceof java.security.cert.X509Certificate) {

                    java.security.cert.X509Certificate cert =
                        (java.security.cert.X509Certificate) certs[0];

                    out.append("Subject: ")
                       .append(cert.getSubjectX500Principal()).append("\n");

                    out.append("Issuer: ")
                       .append(cert.getIssuerX500Principal()).append("\n");

                    out.append("Expires: ")
                       .append(cert.getNotAfter()).append("\n");
                }
            } else {
                out.append("\nWARNING: Connection is not HTTPS.\n");
            }

            connection.disconnect();

        } catch (Exception e) {
            out.append("Web check error: ").append(e.getMessage());
        }

        return out.toString();
    }

    private void passwordCheck(String password) {
        if (password.isEmpty()) {
            toast("Password লিখুন");
            return;
        }

        int score = 0;

        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;

        String level;

        if (score <= 2) level = "WEAK";
        else if (score <= 4) level = "MEDIUM";
        else level = "STRONG";

        String result =
            "HUNT PASSWORD CHECK\n\n" +
            "Length: " + password.length() + "\n" +
            "Score: " + score + "/6\n" +
            "Strength: " + level + "\n\n" +
            "Use a long, unique password. Avoid personal information.";

        saveResult(result);
        display(result);
    }

    private void fullAuto(String target) {
        runTask("FULL AUTO", () -> {
            String host;
            try {
                host = getHost(target);
            } catch (Exception e) {
                return "Invalid target: " + e.getMessage();
            }

            StringBuilder out = new StringBuilder();

            out.append("HUNT FULL AUTO\n");
            out.append("Target: ").append(host).append("\n\n");

            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                out.append("DNS RESULTS\n");

                for (InetAddress address : addresses) {
                    out.append(address.getHostAddress()).append("\n");
                }
            } catch (Exception e) {
                out.append("DNS: ").append(e.getMessage()).append("\n");
            }

            out.append("\nWEB SECURITY RESULTS\n");
            out.append(checkWebsite(target));

            return out.toString();
        });
    }

    private interface Job {
        String run();
    }

    private void runTask(String title, Job job) {
        display(title + "\n\nপরীক্ষা চলছে...");

        new Thread(() -> {
            String result = job.run();
            saveResult(result);

            runOnUiThread(() -> display(result));
        }).start();
    }

    private String normalizeUrl(String target) {
        if (!target.startsWith("http://") &&
            !target.startsWith("https://")) {
            return "https://" + target;
        }
        return target;
    }

    private String getHost(String target) throws Exception {
        URL url = new URL(normalizeUrl(target));
        String host = url.getHost();

        if (host.isEmpty()) {
            throw new Exception("Invalid domain");
        }
        return host;
    }

    private void saveResult(String result) {
        try {
            File file = new File(getFilesDir(), "hunt-results.txt");

            FileOutputStream stream =
                new FileOutputStream(file, true);

            stream.write(
                ("\n\n==========\n" + new Date() +
                 "\n" + result).getBytes("UTF-8")
            );

            stream.close();
        } catch (Exception ignored) {
        }
    }

    private void showResults() {
        File file = new File(getFilesDir(), "hunt-results.txt");

        if (!file.exists()) {
            display("এখনো কোনো Results নেই।");
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(file), "UTF-8"
                )
            );

            StringBuilder result = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }

            reader.close();
            display(result.toString());

        } catch (Exception e) {
            display("Results error: " + e.getMessage());
        }
    }

    private void loadSaved() {
        Set<String> values =
            prefs.getStringSet("targets", new HashSet<>());

        savedTargets.clear();
        savedTargets.addAll(values);
    }

    private void saveTargets() {
        prefs.edit()
            .putStringSet("targets",
                new HashSet<>(savedTargets))
            .apply();
    }

    private void showSaved() {
        if (savedTargets.isEmpty()) {
            display("HUNT READY\n\nEnter a domain or password above.");
            return;
        }

        StringBuilder out = new StringBuilder();
        out.append("SAVED TARGETS\n\n");

        for (String target : savedTargets) {
            out.append("• ").append(target).append("\n");
        }

        display(out.toString());
    }

    private void display(String text) {
        runOnUiThread(() -> {
            list.removeAllViews();

            TextView view = new TextView(this);
            view.setText(text);
            view.setTextColor(0xFFFFFFFF);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            view.setTypeface(Typeface.MONOSPACE);
            int padding = dp(12);
            view.setPadding(padding, padding, padding, padding);

            list.addView(view);
        });
    }

    private int dp(int value) {
        return Math.round(
            value * getResources().getDisplayMetrics().density
        );
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
