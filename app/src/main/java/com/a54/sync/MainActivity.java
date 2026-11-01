package com.a54.sync;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private Switch sw1, sw2, sw3;
    private boolean isUpdatingFromServer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Xin quyền thông báo trên Android 13+ (Samsung A54)
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 70, 50, 50);
        layout.setBackgroundColor(Color.parseColor("#0F172A"));

        TextView title = new TextView(this);
        title.setText("A54 Background Sync");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Mặc định tất cả chức năng đều TẮT khi mới tải về. Bạn có thể bật/tắt tại đây hoặc trên Web Máy tính.");
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setPadding(0, 0, 0, 40);
        layout.addView(sub);

        SharedPreferences prefs = getSharedPreferences("A54Prefs", MODE_PRIVATE);

        sw1 = createSwitch("1. Báo App mở tiếp theo sau khi mở khóa", prefs.getBoolean("f1_unlock", false), "f1_unlock");
        sw2 = createSwitch("2. Chạy ngầm 90 phút báo App 1 lần", prefs.getBoolean("f2_interval", false), "f2_interval");
        sw3 = createSwitch("3. Nhận Notification & Báo Click / Xóa", prefs.getBoolean("f3_notif", false), "f3_notif");

        layout.addView(sw1);
        layout.addView(sw2);
        layout.addView(sw3);

        Button btnAcc = new Button(this);
        btnAcc.setText("Cấp quyền Chạy ngầm (Accessibility)");
        btnAcc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(btnAcc);

        setContentView(layout);
        syncSwitchesWithCloud();
    }

    private Switch createSwitch(String text, boolean defaultVal, String key) {
        Switch sw = new Switch(this);
        sw.setText(text);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(15);
        sw.setPadding(0, 35, 0, 35);
        sw.setChecked(defaultVal); // Mặc định là false (TẮT)
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isUpdatingFromServer) {
                getSharedPreferences("A54Prefs", MODE_PRIVATE).edit().putBoolean(key, isChecked).apply();
                ApiClient.postEvent("update_config", key, isChecked);
            }
        });
        return sw;
    }

    private void syncSwitchesWithCloud() {
        ApiClient.fetchPhoneState(response -> {
            try {
                JSONObject cfg = response.getJSONObject("config");
                runOnUiThread(() -> {
                    isUpdatingFromServer = true;
                    sw1.setChecked(cfg.optBoolean("f1_unlock", false));
                    sw2.setChecked(cfg.optBoolean("f2_interval", false));
                    sw3.setChecked(cfg.optBoolean("f3_notif", false));
                    isUpdatingFromServer = false;
                });
            } catch (Exception ignored) {}
        });
    }
}
