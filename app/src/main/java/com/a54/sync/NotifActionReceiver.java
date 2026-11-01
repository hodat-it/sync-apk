package com.a54.sync;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotifActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String type = intent.getStringExtra("action_type");
        String body = intent.getStringExtra("notif_body");
        int notifId = intent.getIntExtra("notif_id", 1001);

        if ("CLICKED".equals(type)) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(notifId);
            ApiClient.postEvent("notif_feedback", "detail", "Đã CLICK vào thông báo (\"" + body + "\")");
        } else if ("DISMISSED".equals(type)) {
            ApiClient.postEvent("notif_feedback", "detail", "Đã XÓA / GẠT BỎ thông báo (\"" + body + "\")");
        }
    }
}
