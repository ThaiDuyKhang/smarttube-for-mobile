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

        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
