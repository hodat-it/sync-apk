package com.a54.sync;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiClient {
    // Thay đường link Google Apps Script của bạn vào đây:
    private static final String GAS_URL = "YOUR_GAS_WEB_APP_URL";
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public interface SyncCallback {
        void onSyncResult(boolean f1, boolean f2, boolean f3, String pendingNotif);
    }

    public static void postEvent(String eventType, String key, String value) {
        executor.execute(() -> {
            try {
                URL url = new URL(GAS_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("action", "log_event");
                json.put("event_type", eventType);
                json.put(key, value);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    public static void syncWithServer(Context context, SyncCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(GAS_URL + "?role=phone&t=" + System.currentTimeMillis());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JSONObject data = new JSONObject(response.toString());
                JSONObject cfg = data.getJSONObject("config");
                boolean f1 = cfg.optBoolean("f1_unlock", false);
                boolean f2 = cfg.optBoolean("f2_interval", false);
                boolean f3 = cfg.optBoolean("f3_notif", false);
                String pendingNotif = data.optString("pending_notif", "");

                callback.onSyncResult(f1, f2, f3, pendingNotif);
            } catch (Exception ignored) {}
        });
    }
}
