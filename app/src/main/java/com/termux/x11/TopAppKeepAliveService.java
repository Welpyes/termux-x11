package com.termux.x11;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class TopAppKeepAliveService extends Service {
    private static final String CHANNEL_ID = "termux_x11_topapp_runner";
    private static final int NOTIFICATION_ID = 7893;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startAsForeground();
        android.util.Log.i("TopAppKeepAlive", "service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        android.util.Log.i("TopAppKeepAlive", "service start command");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        android.util.Log.i("TopAppKeepAlive", "service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Termux:X11 top-app runner",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps the experimental Termux:X11 top-app runner alive.");
        nm.createNotificationChannel(channel);
    }

    private void startAsForeground() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                pendingFlags
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Termux:X11 top-app session")
                .setContentText("Experimental runner keep-alive is active")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }
}
