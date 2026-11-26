package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Mobile build: keeps the video sound playing with the screen off or the app in the background.<br/>
 * Foreground "media playback" service: lock screen / notification controls, CPU and Wi-Fi kept awake while playing.
 */
public class MobilePlaybackService extends Service {
    private static final String TAG = MobilePlaybackService.class.getSimpleName();
    private static final String CHANNEL_ID = "mobile_playback";
    private static final int NOTIFICATION_ID = 7301;
    static final String ACTION_PLAY_PAUSE = "app.smarttube.mobile.PLAY_PAUSE";
    static final String ACTION_NEXT = "app.smarttube.mobile.NEXT";
    static final String ACTION_PREVIOUS = "app.smarttube.mobile.PREVIOUS";
    static final String ACTION_STOP = "app.smarttube.mobile.STOP";
    private static Controls sControls;
    private static String sTitle;
    private static String sAuthor;
    private static boolean sIsPlaying;
    private static MediaSessionCompat.Token sToken;
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    /**
     * Implemented by the player
     */
    public interface Controls {
        void onPlayPause();
        void onNext();
        void onPrevious();
        void onStop();
    }

    /**
     * Shows or updates the playback notification and keeps the app alive
     */
    public static void update(Context context, Controls controls, MediaSessionCompat.Token token, String title, String author, boolean isPlaying) {
        sControls = controls;
        sToken = token;
        sTitle = title;
        sAuthor = author;
        sIsPlaying = isPlaying;

        try {
            Intent intent = new Intent(context, MobilePlaybackService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            // Not allowed to start from the background (the notification is shown on the next start)
            Log.e(TAG, "Can't start the playback service: %s", e.getMessage());
        }
    }

    public static void stop(Context context) {
        sControls = null;
        sToken = null;
        context.stopService(new Intent(context, MobilePlaybackService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Controls controls = sControls;

        if (action != null && controls != null) {
            switch (action) {
                case ACTION_PLAY_PAUSE:
                    controls.onPlayPause();
                    break;
                case ACTION_NEXT:
                    controls.onNext();
                    break;
                case ACTION_PREVIOUS:
                    controls.onPrevious();
                    break;
                case ACTION_STOP:
                    controls.onStop();
                    stopSelf();
                    return START_NOT_STICKY;
            }
        }

        Notification notification = createNotification();

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "startForeground failed: %s", e.getMessage());
        }

        updateLocks(sIsPlaying);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        updateLocks(false);
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // The app is swiped away from the recents: stop the sound
        Controls controls = sControls;
        if (controls != null) {
            controls.onStop();
        }
        stopSelf();
    }

    private Notification createNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26 && manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.mobile_background_play), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(this, MobilePlaybackActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.mobile_ic_headphones)
                .setContentTitle(sTitle)
                .setContentText(sAuthor)
                .setContentIntent(PendingIntent.getActivity(this, 0, open, pendingFlags()))
                .setDeleteIntent(actionIntent(ACTION_STOP, 4))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(sIsPlaying)
                .addAction(R.drawable.mobile_ic_skip_prev, getString(R.string.mobile_previous), actionIntent(ACTION_PREVIOUS, 1))
                .addAction(sIsPlaying ? R.drawable.mobile_ic_pause : R.drawable.mobile_ic_play,
                        getString(sIsPlaying ? R.string.mobile_pause : R.string.mobile_play), actionIntent(ACTION_PLAY_PAUSE, 2))
                .addAction(R.drawable.mobile_ic_skip_next, getString(R.string.mobile_next), actionIntent(ACTION_NEXT, 3))
                .addAction(R.drawable.mobile_ic_close, getString(R.string.mobile_close_player), actionIntent(ACTION_STOP, 5));

        androidx.media.app.NotificationCompat.MediaStyle style = new androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2);
        if (sToken != null) {
            style.setMediaSession(sToken);
        }
        builder.setStyle(style);

        return builder.build();
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MobilePlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent, pendingFlags());
    }

    private static int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    @SuppressLint("WakelockTimeout") // released when the playback stops
    @SuppressWarnings("deprecation")
    private void updateLocks(boolean hold) {
        if (hold) {
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartTube:playback");
                    mWakeLock.setReferenceCounted(false);
                }
            }
            if (mWifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SmartTube:playback");
                    mWifiLock.setReferenceCounted(false);
                }
            }
            if (mWakeLock != null && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
            }
            if (mWifiLock != null && !mWifiLock.isHeld()) {
                mWifiLock.acquire();
            }
        } else {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            if (mWifiLock != null && mWifiLock.isHeld()) {
                mWifiLock.release();
            }
        }
    }
}
