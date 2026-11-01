package com.a54.sync;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiClient {
    // THAY LINK GOOGLE APPS SCRIPT CỦA BẠN VÀO ĐÂY:
    public static final String GAS_URL = "https://script.google.com/macros/s/AKfycbyEOucHLuhEvxaKhNu9NXlWHF3UmOocWAKt8kCAXO5LQRG8jM8cbxzyQGQ2Xg86s9V_7Q/exec";

    public interface Callback {
        void onResult(JSONObject response);
    }

    public static void postEvent(String action, String keyOrField, Object value) {
        new Thread(() -> {
            try {
                URL url = new URL(GAS_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("action", action);
                if ("update_config".equals(action)) {
                    json.put("key", keyOrField);
                    json.put("value", value);
                } else if ("notif_feedback".equals(action)) {
                    json.put("detail", value);
                } else {
                    json.put("app_name", value);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                conn.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }

    public static void fetchPhoneState(Callback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(GAS_URL + "?role=phone&t=" + System.currentTimeMillis());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(true);

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    content.append(line);
                }
                in.close();
                if (callback != null) {
                    callback.onResult(new JSONObject(content.toString()));
                }
            } catch (Exception ignored) {}
        }).start();
    }
}
