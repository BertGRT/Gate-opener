package com.portail.gate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class Notif {

    private static int id = 1;

    // Ecrit dans le journal ET affiche une notification
    static void show(Context ctx, String title, String text) {
        Journal.add(ctx, text);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel c = new NotificationChannel("portail", "Portail", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(c);

        Notification n = new Notification.Builder(ctx, "portail")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build();

        nm.notify(id++, n);
    }
}
