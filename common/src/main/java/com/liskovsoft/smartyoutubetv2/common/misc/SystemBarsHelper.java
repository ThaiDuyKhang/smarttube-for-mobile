package com.liskovsoft.smartyoutubetv2.common.misc;

import android.graphics.Rect;
import android.os.Build.VERSION;
import android.view.View;
import android.view.WindowInsets;

/**
 * Mobile build: helps to keep the fullscreen (immersive) mode on phones and tablets
 */
public final class SystemBarsHelper {
    /**
     * Part of the window height that the keyboard should cover to be considered visible
     */
    private static final float KEYBOARD_MIN_HEIGHT_RATIO = 0.2f;

    private SystemBarsHelper() {
    }

    /**
     * Status bar or navigation bar is visible over the content
     */
    @SuppressWarnings("deprecation")
    public static boolean isSystemBarsVisible(View decorView) {
        if (decorView == null) {
            return false;
        }

        if (VERSION.SDK_INT >= 30) {
            WindowInsets insets = decorView.getRootWindowInsets();
            return insets != null && (insets.isVisible(WindowInsets.Type.statusBars()) || insets.isVisible(WindowInsets.Type.navigationBars()));
        }

        int hiddenBars = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        return (decorView.getSystemUiVisibility() & hiddenBars) != hiddenBars;
    }

    /**
     * Soft keyboard is shown. Don't hide the system bars while the user is typing.
     */
    public static boolean isKeyboardVisible(View decorView) {
        if (decorView == null) {
            return false;
        }

        if (VERSION.SDK_INT >= 30) {
            WindowInsets insets = decorView.getRootWindowInsets();
            return insets != null && insets.isVisible(WindowInsets.Type.ime());
        }

        View rootView = decorView.getRootView();
        int windowHeight = rootView.getHeight();

        if (windowHeight <= 0) {
            return false;
        }

        Rect visibleFrame = new Rect();
        rootView.getWindowVisibleDisplayFrame(visibleFrame);

        return windowHeight - visibleFrame.height() > windowHeight * KEYBOARD_MIN_HEIGHT_RATIO;
    }
}
