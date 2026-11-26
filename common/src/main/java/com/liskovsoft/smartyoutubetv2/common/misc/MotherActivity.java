package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyCharacterMap.UnavailableException;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;
import com.r0adkll.slidr.Slidr;
import com.r0adkll.slidr.model.SlidrConfig;
import com.r0adkll.slidr.model.SlidrListener;
import com.r0adkll.slidr.model.SlidrPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MotherActivity extends FragmentActivity {
    private static final String TAG = MotherActivity.class.getSimpleName();
    private static final float DEFAULT_DENSITY = 2.0f; // xhdpi
    private static final float DEFAULT_WIDTH = 1920f; // xhdpi
    private static DisplayMetrics sCachedDisplayMetrics;
    private static final long SYSTEM_BARS_CHECK_INTERVAL_MS = 2_000;
    private SingleTapClickHelper mSingleTapClickHelper;
    protected static boolean sIsInPipMode;
    private ScreensaverManager mScreensaverManager;
    // Make static in case Don't keep activities enabled in Developer settings
    private static List<OnPermissions> mOnPermissions;
    private static List<OnResult> mOnResults;
    private long mLastKeyDownTime;
    private boolean mEnableThrottleKeyDown;
    private boolean mIsOculusQuestFixEnabled;
    private boolean mIsFullscreenModeEnabled;
    private boolean mIsBackPressed;

    public interface OnPermissions {
        void onPermissions(int requestCode, String[] permissions, int[] grantResults);
    }

    public interface OnResult {
        void onResult(int requestCode, int resultCode, Intent data);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Fixing: Only fullscreen opaque activities can request orientation (api 26)
        // NOTE: You should remove 'screenOrientation' from the manifest.
        // NOTE: Possible side effect: initDpi() won't work: "When you setRequestedOrientation() the view may be restarted"
        //if (VERSION.SDK_INT != 26) {
        //    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //}
        // Mobile build: follow the device rotation (portrait and landscape)
        if (Utils.isMobileBuild(this)) {
            Utils.applyMobileOrientation(this);
        }
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Starting %s...", this.getClass().getSimpleName());

        mIsOculusQuestFixEnabled = PlayerTweaksData.instance(this).isOculusQuestFixEnabled();
        // Native (phone) UI keeps the system bars, like other phone apps
        mIsFullscreenModeEnabled = !isNativeUi() && GeneralData.instance(this).isFullscreenModeEnabled();

        initDpi();
        initTheme();

        // Search Fullscreen routine inside onPause() method
        if (!mIsFullscreenModeEnabled && !isNativeUi()) {
            // There's no way to do this programmatically!
            setTheme(R.style.FitSystemWindows);

            // totally disabling the translucency or any color placed on the status bar and navigation bar
            //if (Build.VERSION.SDK_INT >= 19) {
            //    Window w = getWindow();
            //    w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            //}
        }

        if (mIsOculusQuestFixEnabled && VERSION.SDK_INT != 26 && !Utils.isMobileBuild(this)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        mScreensaverManager = new ScreensaverManager(this); // moved below the theme to fix side effects

        if (Utils.isMobileBuild(this) && !isNativeUi()) {
            mSingleTapClickHelper = new SingleTapClickHelper(this);
        }

        //Helpers.addFullscreenListener(this);

        initEdgeSlide();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        if (mSingleTapClickHelper != null) {
            mSingleTapClickHelper.beforeDispatch(event);
        }

        try {
            boolean result = super.dispatchTouchEvent(event);

            if (mSingleTapClickHelper != null) {
                mSingleTapClickHelper.afterDispatch(event);
            }

            return result;
        } catch (NullPointerException | SecurityException | IllegalStateException | ArrayIndexOutOfBoundsException e) {
            // Attempt to invoke interface method 'boolean android.app.trust.ITrustManager.isDeviceLocked(int)' on a null object reference
            // Permission Denial: starting Intent
            // IllegalStateException: exitFreeformMode: You can only go fullscreen from freeform.
            e.printStackTrace();
            return false;
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event == null) { // handled
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean isKeepScreenOff = mScreensaverManager.isScreenOff() && Helpers.equalsAny(event.getKeyCode(),
                    new int[]{KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN});
            if (!isKeepScreenOff) {
                mScreensaverManager.enable();
            }
        }

        try {
            return super.dispatchKeyEvent(event);
        } catch (NullPointerException | IllegalArgumentException | IllegalStateException | SecurityException | UnavailableException e) {
            // NullPointerException: 'android.view.Window androidx.core.app.ComponentActivity.getWindow()' on a null object reference
            // IllegalArgumentException: View is not a direct child of HorizontalGridView
            // Fatal Exception: java.lang.IllegalStateException
            // android.permission.RECORD_AUDIO required for search (Android 5 mostly)
            // Fatal Exception: java.lang.SecurityException
            // Not allowed to bind to service Intent { act=android.speech.RecognitionService cmp=com.xgimi.duertts/com.baidu.duer.services.tvser
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) { // shortcut for closing PIP
            PlaybackPresenter.instance(this).forceFinish();
            return true;
        }

        boolean result = super.onKeyDown(keyCode, event);

        // Fix buggy G20s menu key (focus lost on key press)
        return KeyHelpers.isMenuKey(keyCode) || throttleKeyDown(keyCode) || result;
    }

    public void finishReally() {
        try {
            //if (VERSION.SDK_INT >= 21 && getViewManager().getTopView() != null) { // remain root activity in recents
            //    super.finishAndRemoveTask();
            //} else {
            //    super.finish();
            //}

            // finishAndRemoveTask() must be used carefully for intermediate activities:
            // calling it right after starting a parent activity races with that
            // startActivity() call. On slower devices, task removal can complete
            // first, killing the process instead of navigating back. Use finish()
            // when returning to an existing parent — the task cleans up automatically
            // for singleInstance activities. Reserve finishAndRemoveTask() for actual
            // app exit.
            // The issue: https://github.com/yuliskov/SmartTube/issues/6010
            super.finish();
        } catch (Exception e) {
            // TextView not attached to window manager (IllegalArgumentException)
        }
    }

    /**
     * Mobile build: the screen is made for the phone (not the TV UI).<br/>
     * It uses the system density, keeps the system bars and its own theme.
     */
    public boolean isNativeUi() {
        return false;
    }

    @Override
    protected void attachBaseContext(Context context) {
        if (context != null && isNativeUi()) {
            // Own configuration (resources) with the system density. The TV screens change the density of the shared one.
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.densityDpi = Resources.getSystem().getDisplayMetrics().densityDpi;
            Locale locale = LocaleUpdater.getSavedLocale(context);
            if (locale != null) {
                config.setLocale(locale);
            }
            super.attachBaseContext(context.createConfigurationContext(config));
            return;
        }

        Context contextWrapper = null;

        if (context != null) {
            // NOTE: Use only cached metrics. Because metrics creation involves using WindowManager, which isn't available at this stage.
            contextWrapper = LocaleContextWrapper.wrap(context, LocaleUpdater.getSavedLocale(context), sCachedDisplayMetrics);
        }

        super.attachBaseContext(contextWrapper);
    }

    @Override
    protected void onResume() {
        mIsBackPressed = false;
        try {
            super.onResume();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        // 4K fix with AFR
        applyCustomConfig();

        applyFullscreenModeIfNeeded();

        // Restore this activity's screensaver policy after returning to the foreground.
        mScreensaverManager.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopSystemBarsRestore();

        // Stop managing the screensaver so a paused activity cannot keep the display awake.
        mScreensaverManager.suspend();
    }

    @Override
    protected void onDestroy() {
        if (mScreensaverManager != null) {
            mScreensaverManager.cleanup();
        }

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        applyCustomConfig();
    }

    public ScreensaverManager getScreensaverManager() {
        return mScreensaverManager;
    }

    protected void initTheme() {
        int rootThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (rootThemeResId > 0) {
            setTheme(rootThemeResId);
        }
    }

    private void initDpi() {
        if (isNativeUi()) {
            return;
        }

        getResources().getDisplayMetrics().setTo(getDisplayMetrics(this));
    }

    private DisplayMetrics getDisplayMetrics(Context context) {
        if (Utils.isMobileBuild(context)) {
            // Mobile build: the screen could be rotated. Width and height follow the current orientation.
            // The density depends on the longest side only, so the UI elements have the same size in both orientations.
            DisplayMetrics displayMetrics = new DisplayMetrics();
            Utils.getWindowDisplayMetrics(this, displayMetrics);
            float uiScale = MainUIData.instance(context).getUIScale();
            int longSidePixels = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
            float density = DEFAULT_DENSITY / (DEFAULT_WIDTH / longSidePixels) * uiScale;
            displayMetrics.density = density;
            displayMetrics.scaledDensity = density;
            sCachedDisplayMetrics = displayMetrics;
            return displayMetrics;
        }

        // BUG: adapt to resolution change (e.g. on AFR)
        // Don't disable caching or you will experience weird sizes on cards in video suggestions (e.g. after exit from PIP)!
        if (sCachedDisplayMetrics == null) {
            // NOTE: Don't replace with getResources().getDisplayMetrics(). Shows wrong metrics here!
            DisplayMetrics displayMetrics = new DisplayMetrics();
            Utils.getWindowDisplayMetrics(this, displayMetrics);
            float uiScale = MainUIData.instance(context).getUIScale();
            // Take into the account screen orientation (e.g. when running on phone)
            int widthPixels = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
            float widthRatio = DEFAULT_WIDTH / widthPixels;
            float density = DEFAULT_DENSITY / widthRatio * uiScale;
            displayMetrics.density = density;
            displayMetrics.scaledDensity = density;
            sCachedDisplayMetrics = displayMetrics;
        }

        return sCachedDisplayMetrics;
    }

    private void applyCustomConfig() {
        // NOTE: dpi should come after locale update to prevent resources overriding.

        // Fix sudden language change.
        // Could happen when screen goes off or after PIP mode.
        LocaleUpdater.applySavedLocale(this);

        // Fix sudden dpi change.
        // Could happen when screen goes off or after PIP mode.
        initDpi();
    }

    private void applyFullscreenModeIfNeeded() {
        if (mIsFullscreenModeEnabled) {
            // Most of the fullscreen tweaks could be performed in styles but not all.
            // E.g. Hide bottom navigation bar (couldn't be done in styles).
            Helpers.makeActivityFullscreen2(this);
            startSystemBarsRestore();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Mobile build: the system bars could be shown by the keyboard or a system dialog
        if (hasFocus && mIsFullscreenModeEnabled && Utils.isMobileBuild(this) && getWindow() != null
                && !SystemBarsHelper.isKeyboardVisible(getWindow().getDecorView())) {
            Helpers.makeActivityFullscreen2(this);
        }
    }

    /**
     * Mobile build: the system bars could stay visible over the content (e.g. after the keyboard is closed).
     * Hide them again while the activity is in the foreground.
     */
    private void startSystemBarsRestore() {
        if (!Utils.isMobileBuild(this) || getWindow() == null) {
            return;
        }

        View decorView = getWindow().getDecorView();
        decorView.removeCallbacks(mSystemBarsRestore);
        decorView.postDelayed(mSystemBarsRestore, SYSTEM_BARS_CHECK_INTERVAL_MS);
    }

    private void stopSystemBarsRestore() {
        if (getWindow() != null) {
            getWindow().getDecorView().removeCallbacks(mSystemBarsRestore);
        }
    }

    private final Runnable mSystemBarsRestore = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || getWindow() == null || !mIsFullscreenModeEnabled) {
                return;
            }

            View decorView = getWindow().getDecorView();

            if (hasWindowFocus() && SystemBarsHelper.isSystemBarsVisible(decorView) && !SystemBarsHelper.isKeyboardVisible(decorView)) {
                Helpers.makeActivityFullscreen2(MotherActivity.this);
            }

            decorView.postDelayed(this, SYSTEM_BARS_CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (mOnPermissions != null) {
            for (OnPermissions callback : mOnPermissions) {
                callback.onPermissions(requestCode, permissions, grantResults);
            }
            mOnPermissions.clear();
            mOnPermissions = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mOnResults != null) {
            for (OnResult callback : mOnResults) {
                callback.onResult(requestCode, resultCode, data);
            }
            mOnResults.clear();
            mOnResults = null;
        }
    }

    @Override
    public void onBackPressed() {
        mIsBackPressed = true;
        super.onBackPressed();
        // Oculus Quest fix: back button not closing the activity
        if (mIsOculusQuestFixEnabled) {
            finish();
        }
    }

    public void addOnPermissions(OnPermissions onPermissions) {
        if (mOnPermissions == null) {
            mOnPermissions = new ArrayList<>();
        }

        mOnPermissions.remove(onPermissions);
        mOnPermissions.add(onPermissions);
    }

    public void addOnResult(OnResult onResult) {
        if (mOnResults == null) {
            mOnResults = new ArrayList<>();
        }

        mOnResults.remove(onResult);
        mOnResults.add(onResult);
    }

    /**
     * Use this method only upon exiting from the app.<br/>
     * Big troubles with AFR resolution switch!
     */
    public static void invalidate() {
        sCachedDisplayMetrics = null;
        sIsInPipMode = false;
    }

    public static DisplayMetrics getCachedDisplayMetrics() {
        return sCachedDisplayMetrics;
    }

    /**
     * Comments focus fix<br/>
     * https://stackoverflow.com/questions/34277425/recyclerview-items-lose-focus
     */
    private boolean throttleKeyDown(int keyCode) {
        if (mEnableThrottleKeyDown && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            long current = System.currentTimeMillis();
            if (current - mLastKeyDownTime < 100) {
                return true;
            }

            mLastKeyDownTime = current;
        }

        return false;
    }

    /**
     * Comments focus fix<br/>
     * https://stackoverflow.com/questions/34277425/recyclerview-items-lose-focus
     */
    public void enableThrottleKeyDown(boolean enable) {
        mEnableThrottleKeyDown = enable;
    }

    //@Override
    //public void setTheme(int resid) {
    //    super.setTheme(resid);
    //
    //    // No way to do this programmatically!
    //    if (!GeneralData.instance(this).isFullscreenModeEnabled()) {
    //        super.setTheme(R.style.FitSystemWindows);
    //    }
    //}

    protected ViewManager getViewManager() {
        return ViewManager.instance(this);
    }

    protected GeneralData getGeneralData() {
        return GeneralData.instance(this);
    }

    protected PlayerTweaksData getPlayerTweaksData() {
        return PlayerTweaksData.instance(this);
    }

    protected PlayerData getPlayerData() {
        return PlayerData.instance(this);
    }

    protected MainUIData getMainUIData() {
        return MainUIData.instance(this);
    }

    protected MediaServiceData getMediaServiceData() {
        return MediaServiceData.instance();
    }

    protected final boolean isBackPressed() {
        return mIsBackPressed;
    }

    protected final void resetBackState() {
        mIsBackPressed = false;
    }

    private void initEdgeSlide() {
        if (VERSION.SDK_INT < 21 || !Helpers.isTouchSupported(this) || Utils.isSystemGestureArrowEnabled(this)) {
            return;
        }

        SlidrConfig config = new SlidrConfig.Builder()
                .position(SlidrPosition.LEFT) // Swipe from the left
                .edge(true)              // Only trigger from the screen edge
                .edgeSize(0.18f)              // Grab 18% of the screen (good for cars)
                .scrimStartAlpha(0f)          // Don't dim the background screen
                .scrimEndAlpha(0f)            // Background clear when finished
                .distanceThreshold(0.1f)      // Set drag distance to minimum
                .partial(true)           // Don't do full slide animation
                .listener(new SlidrListener() {
                    @Override
                    public void onSlideStateChanged(int state) {}

                    @Override
                    public void onSlideChange(float percent) {}

                    @Override
                    public void onSlideOpened() {}

                    @Override
                    public boolean onSlideClosed() {
                        // This replaces the default finish() with your back logic
                        onBackPressed();
                        return true; // Tells the library we handled the close
                    }
                })
                .build();

        // Attach to this activity
        Slidr.attach(this, config);
    }
}
