package com.a54.sync;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private Switch swF1, swF2, swF3;
    private TextView tvStatus;
    private Button btnOpenAccessibility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);

        tvStatus = new TextView(this);
        tvStatus.setText("Trạng thái: Đang kết nối...");
        tvStatus.setTextSize(16);
        tvStatus.setPadding(0, 0, 0, 30);
        layout.addView(tvStatus);

        btnOpenAccessibility = new Button(this);
        btnOpenAccessibility.setText("Cấp quyền Chạy ngầm (Accessibility)");
        btnOpenAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        layout.addView(btnOpenAccessibility);

        swF1 = new Switch(this);
        swF1.setText("1. Báo cáo sử dụng app khi mở/tắt màn hình");
        swF1.setPadding(0, 30, 0, 20);
        layout.addView(swF1);

        swF2 = new Switch(this);
        swF2.setText("2. Báo cáo định kỳ 90 phút");
        swF2.setPadding(0, 20, 0, 20);
        layout.addView(swF2);

        swF3 = new Switch(this);
        swF3.setText("3. Bắn Notification sang điện thoại");
        swF3.setPadding(0, 20, 0, 30);
        layout.addView(swF3);

        setContentView(layout);

        SharedPreferences prefs = getSharedPreferences("A54Prefs", MODE_PRIVATE);
        swF1.setChecked(prefs.getBoolean("f1_unlock", false));
        swF2.setChecked(prefs.getBoolean("f2_interval", false));
        swF3.setChecked(prefs.getBoolean("f3_notif", false));

        setupSwitchListener(swF1, "f1_unlock");
        setupSwitchListener(swF2, "f2_interval");
        setupSwitchListener(swF3, "f3_notif");

        loadServerState();
    }

    private void setupSwitchListener(Switch sw, String key) {
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            getSharedPreferences("A54Prefs", MODE_PRIVATE).edit().putBoolean(key, isChecked).apply();
            ApiClient.postEvent("update_config", key, String.valueOf(isChecked));
            Toast.makeText(this, "Đã cập nhật trạng thái", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadServerState() {
        ApiClient.fetchPhoneState(response -> runOnUiThread(() -> {
            if (response != null && !response.isEmpty()) {
                try {
                    // Chuyển chuỗi String response thành JSONObject hợp lệ trước khi lấy config
                    JSONObject data = new JSONObject(response);
                    if (data.has("config")) {
                        JSONObject cfg = data.getJSONObject("config");
                        boolean f1 = cfg.optBoolean("f1_unlock", false);
                        boolean f2 = cfg.optBoolean("f2_interval", false);
                        boolean f3 = cfg.optBoolean("f3_notif", false);

                        swF1.setChecked(f1);
                        swF2.setChecked(f2);
                        swF3.setChecked(f3);

                        getSharedPreferences("A54Prefs", MODE_PRIVATE).edit()
                                .putBoolean("f1_unlock", f1)
                                .putBoolean("f2_interval", f2)
                                .putBoolean("f3_notif", f3)
                                .apply();

                        tvStatus.setText("Trạng thái: Đã kết nối với Web");
                    }
                } catch (Exception e) {
                    tvStatus.setText("Trạng thái: Dữ liệu lỗi");
                }
            } else {
                tvStatus.setText("Trạng thái: Không thể kết nối Web");
            }
        }));
    }
}
