package com.a54.sync;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONObject;

public class MonitorAccessibilityService extends AccessibilityService {
    private boolean waitingForFirstAppAfterUnlock = false;
    private String currentForegroundApp = "Màn hình chính";
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final long INTERVAL_90_MINUTES = 90 * 60 * 1000L; // 90 phút

    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                waitingForFirstAppAfterUnlock = true;
            }
        }
    };

    // Bộ đếm chạy ngầm 90 phút 1 lần
    private final Runnable runnable90Min = new Runnable() {
        @Override
        public void run() {
            SharedPreferences prefs = getSharedPreferences("A54Prefs", MODE_PRIVATE);
            if (prefs.getBoolean("f2_interval", false)) {
                ApiClient.postEvent("interval_90m", "app_name", currentForegroundApp);
            }
            handler.postDelayed(this, INTERVAL_90_MINUTES);
        }
    };

    // Kiểm tra lệnh bắn Notification và đồng bộ trạng thái công tắc mỗi 10 giây
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            ApiClient.fetchPhoneState(response -> {
                try {
                    JSONObject cfg = response.getJSONObject("config");
                    SharedPreferences prefs = getSharedPreferences("A54Prefs", MODE_PRIVATE);
                    prefs.edit()
                        .putBoolean("f1_unlock", cfg.getBoolean("f1_unlock"))
                        .putBoolean("f2_interval", cfg.getBoolean("f2_interval"))
                        .putBoolean("f3_notif", cfg.getBoolean("f3_notif"))
                        .apply();

                    String notifMsg = response.optString("notif", "");
                    if (!notifMsg.isEmpty() && cfg.getBoolean("f3_notif")) {
                        showInteractiveNotification(notifMsg);
                    }
                } catch (Exception ignored) {}
            });
            handler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        handler.postDelayed(runnable90Min, INTERVAL_90_MINUTES);
        handler.post(syncRunnable);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            String pkg = event.getPackageName().toString();
            // Bỏ qua các giao diện hệ thống và bàn phím để lấy đúng ứng dụng người dùng mở
            if (pkg.contains("systemui") || pkg.contains("launcher") || pkg.contains("inputmethod") || pkg.equals("com.a54.sync")) {
                return;
            }
            currentForegroundApp = getReadableAppName(pkg);

            if (waitingForFirstAppAfterUnlock) {
                waitingForFirstAppAfterUnlock = false;
                SharedPreferences prefs = getSharedPreferences("A54Prefs", MODE_PRIVATE);
                if (prefs.getBoolean("f1_unlock", false)) {
                    ApiClient.postEvent("unlock_app", "app_name", currentForegroundApp);
                }
            }
        }
    }

    private String getReadableAppName(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString() + " (" + pkg + ")";
        } catch (Exception e) {
            return pkg;
        }
    }

    private void showInteractiveNotification(String message) {
        String channelId = "a54_sync_channel";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "A54 PC Notifications", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        int notifId = (int) (System.currentTimeMillis() % 10000);

        Intent clickIntent = new Intent(this, NotifActionReceiver.class);
        clickIntent.setAction("ACTION_CLICK_" + notifId);
        clickIntent.putExtra("action_type", "CLICKED");
        clickIntent.putExtra("notif_body", message);
        clickIntent.putExtra("notif_id", notifId);
        PendingIntent clickPending = PendingIntent.getBroadcast(
            this, notifId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent deleteIntent = new Intent(this, NotifActionReceiver.class);
        deleteIntent.setAction("ACTION_DELETE_" + notifId);
        deleteIntent.putExtra("action_type", "DISMISSED");
        deleteIntent.putExtra("notif_body", message);
        deleteIntent.putExtra("notif_id", notifId);
        PendingIntent deletePending = PendingIntent.getBroadcast(
            this, notifId + 1, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, channelId)
            : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
               .setContentTitle("Thông báo từ Máy tính")
               .setContentText(message)
               .setContentIntent(clickPending)  // Bắt sự kiện CLICK
               .setDeleteIntent(deletePending)  // Bắt sự kiện GẠT XÓA
               .setAutoCancel(true);

        nm.notify(notifId, builder.build());
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(unlockReceiver);
        handler.removeCallbacks(runnable90Min);
        handler.removeCallbacks(syncRunnable);
    }
}
