package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

/**
 * Mobile build: light or dark phone screens (like the YouTube app).<br/>
 * Applied to the configuration of the phone screens: the colors come from values (light) or values-night (dark).
 */
public final class MobileThemeHelper {
    public static final int THEME_DARK = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_SYSTEM = 2;
    private static final String PREFS = "mobile_ui";
    private static final String KEY_THEME = "theme";

    private MobileThemeHelper() {
    }

    public static int getTheme(Context context) {
        return prefs(context).getInt(KEY_THEME, THEME_DARK);
    }

    public static void setTheme(Context context, int theme) {
        prefs(context).edit().putInt(KEY_THEME, theme).commit();
    }

    /**
     * Sets the night mode of the config from the user choice
     */
    public static void apply(Context context, Configuration config) {
        boolean night;

        switch (getTheme(context)) {
            case THEME_LIGHT:
                night = false;
                break;
            case THEME_SYSTEM:
                night = (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                break;
            default:
                night = true;
                break;
        }

        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
    }

    public static boolean isLight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext() != null ?
                context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE) :
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
