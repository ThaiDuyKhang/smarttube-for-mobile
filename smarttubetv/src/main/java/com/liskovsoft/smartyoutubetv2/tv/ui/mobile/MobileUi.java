package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Mobile build: small helpers shared by the phone screens
 */
public final class MobileUi {
    // Channel avatars seen on the watch page (the channel page doesn't get them)
    private static final java.util.Map<String, String> sChannelIcons = new java.util.HashMap<>();

    private MobileUi() {
    }

    public static void putChannelIcon(String channelId, String iconUrl) {
        if (channelId != null && iconUrl != null) {
            sChannelIcons.put(channelId, iconUrl);
        }
    }

    public static String getChannelIcon(String channelId) {
        return channelId != null ? sChannelIcons.get(channelId) : null;
    }

    @SuppressWarnings("deprecation")
    public static boolean isOnline(Activity activity) {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) activity.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return true;
        }
        try {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (SecurityException e) {
            return true;
        }
    }

    /**
     * Dark status and navigation bar icons on the light theme
     */
    @android.annotation.TargetApi(21)
    @SuppressWarnings("deprecation")
    public static void applySystemBarColors(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT < 21) {
            return; // the mobile build starts at Android 5
        }

        boolean light = com.liskovsoft.smartyoutubetv2.common.utils.MobileThemeHelper.isLight(activity);

        // The base theme (Theme.Black) doesn't paint the bars: let the app do it
        activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        // The watch page: black bars around the player, white icons
        if (activity instanceof MobilePlaybackActivity) {
            activity.getWindow().setStatusBarColor(0xFF000000);
            activity.getWindow().setNavigationBarColor(0xFF000000);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT < 23) {
            if (light) {
                // White icons only before Android 6: keep the bars dark
                activity.getWindow().setStatusBarColor(0xFF000000);
                activity.getWindow().setNavigationBarColor(0xFF000000);
            }
            return;
        }

        // Bars in the page color (some screens of the base classes paint them black)
        int barColor = androidx.core.content.ContextCompat.getColor(activity, R.color.mobile_bg);
        activity.getWindow().setStatusBarColor(barColor);
        if (android.os.Build.VERSION.SDK_INT >= 26 || !light) {
            activity.getWindow().setNavigationBarColor(barColor);
        }

        android.view.View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags = light ? flags | android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : flags & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            flags = light ? flags | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : flags & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else if (light) {
            activity.getWindow().setNavigationBarColor(0xFF000000); // no dark navigation icons before Android 8
        }
        decor.setSystemUiVisibility(flags);
    }

    public static boolean isSignedIn() {
        return MediaServiceManager.instance().getSelectedAccount() != null;
    }

    /**
     * Runs the action when signed in. Otherwise explains that the action needs an account and offers to sign in.
     */
    public static void requireSignIn(Activity activity, Runnable action) {
        if (isSignedIn()) {
            action.run();
            return;
        }

        if (activity.isFinishing()) {
            return;
        }

        new AlertDialog.Builder(activity, com.liskovsoft.smartyoutubetv2.common.utils.MobileThemeHelper.isLight(activity) ?
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert : android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.mobile_sign_in_required_title)
                .setMessage(R.string.mobile_sign_in_required)
                .setPositiveButton(R.string.mobile_sign_in, (dialog, which) -> YTSignInPresenter.instance(activity).start())
                .setNegativeButton(R.string.mobile_cancel, null)
                .show();
    }

    /**
     * Readable message instead of the raw exception (e.g. "UnknownHostException: Unable to resolve host...")
     */
    public static String toReadableError(Activity activity, String message) {
        if (!isOnline(activity)) {
            return activity.getString(R.string.mobile_error_network);
        }

        if (TextUtils.isEmpty(message)) {
            return activity.getString(R.string.mobile_error_generic);
        }

        String lower = message.toLowerCase();

        if (lower.contains("unknownhost") || lower.contains("unable to resolve host") || lower.contains("connectexception")
                || lower.contains("sockettimeout") || lower.contains("timeout") || lower.contains("network is unreachable")
                || lower.contains("failed to connect") || lower.contains("sslhandshake") || lower.contains("connection reset")) {
            return activity.getString(R.string.mobile_error_network);
        }

        if (lower.contains("exception") || lower.contains(".java:")) {
            return activity.getString(R.string.mobile_error_generic);
        }

        return message; // already a message for people (e.g. "sign in to see your subscriptions")
    }
}
