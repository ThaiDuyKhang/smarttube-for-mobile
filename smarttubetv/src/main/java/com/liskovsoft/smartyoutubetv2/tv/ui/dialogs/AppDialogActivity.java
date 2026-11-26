package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GlobalKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.misc.PlayerKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.MobilePlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

public class AppDialogActivity extends MotherActivity {
    private static final String TAG = AppDialogActivity.class.getSimpleName();
    private AppDialogFragment mFragment;
    private GlobalKeyTranslator mGlobalKeyTranslator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_app_settings);
        // Can't use getSupportFragmentManager because AppDialogFragment isn't subclass of androidx fragment
        mFragment = (AppDialogFragment) getFragmentManager().findFragmentById(R.id.app_settings_fragment);

        mGlobalKeyTranslator = new PlayerKeyTranslator(this);
        mGlobalKeyTranslator.apply();

        if (Utils.isMobileBuild(this)) {
            initBottomSheet();
        }
    }

    /**
     * Mobile build: the dialog as a bottom sheet like the YouTube app (instead of the TV side panel).<br/>
     * Dimmed background, tap outside to close.
     */
    private void initBottomSheet() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0.5f);

        View root = getWindow().getDecorView();
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyBottomSheet());
        root.post(this::applyBottomSheet);
    }

    private void applyBottomSheet() {
        View pane = findViewById(R.id.settings_preference_fragment_container);

        if (pane == null || !(pane.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        View content = findViewById(android.R.id.content);
        int width = content.getWidth();
        int height = content.getHeight();

        if (width == 0 || height == 0) {
            return;
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pane.getLayoutParams();
        int sheetWidth = Math.min(width, (int) (600 * density));
        int sheetHeight = (int) (height * (width > height ? 0.85f : 0.6f));

        if (lp.width != sheetWidth || lp.height != sheetHeight || lp.gravity != (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)) {
            lp.width = sheetWidth;
            lp.height = sheetHeight;
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            pane.setLayoutParams(lp);
        }

        if (!(pane.getBackground() instanceof GradientDrawable)) {
            GradientDrawable background = new GradientDrawable();
            float radius = 12 * density;
            background.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
            background.setColor(0xFF212121);
            pane.setBackground(background);
            if (VERSION.SDK_INT >= 21) {
                pane.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                pane.setClipToOutline(true);
            }
        }

        // Tap outside the sheet: close
        View container = (View) pane.getParent();
        if (container != null && !container.hasOnClickListeners()) {
            container.setOnClickListener(v -> finish());
            pane.setClickable(true); // don't close on taps inside
        }
    }

    @Override
    protected void initTheme() {
        int settingsThemeResId = MainUIData.instance(this).getColorScheme().settingsThemeResId;
        if (settingsThemeResId > 0) {
            setTheme(settingsThemeResId);
        }
    }

    private void setupActivity() {
        // Fix crash in AppSettingsActivity: "Only fullscreen opaque activities can request orientation"
        // Error happen only on Android O (api 26) when you set "portrait" orientation in manifest
        // So, to fix the problem, set orientation here instead of manifest
        // More info: https://stackoverflow.com/questions/48072438/java-lang-illegalstateexception-only-fullscreen-opaque-activities-can-request-o
        if (Utils.isMobileBuild(this)) {
            // Already applied inside MotherActivity (follows the device rotation)
            return;
        }

        if (VERSION.SDK_INT != 26) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        //if (KeyHelpers.isConfirmKey(event.getKeyCode()) && !event.isLongPress()) {
        //    return true;
        //}

        //return mGlobalKeyTranslator.translate(event) || super.dispatchKeyEvent(event);
        KeyEvent newEvent = mGlobalKeyTranslator.translate(event);
        return handleNavigation(newEvent) || super.dispatchKeyEvent(newEvent);
    }
    
    private boolean handleNavigation(KeyEvent event) {
        if (event == null) {
            return false;
        }

        // Toggle dialog
        if (!mFragment.isOverlay() && (KeyHelpers.isLeftRightKey(event.getKeyCode()) || KeyHelpers.isMenuKey(event.getKeyCode()))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                finish();
            }
            return true;
        }

        // Notification dialog type. Imitate notification behavior.
        if (mFragment.isOverlay() && (KeyHelpers.isNavigationKey(event.getKeyCode()) || KeyHelpers.isMenuKey(event.getKeyCode()))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                finish();
            }
            PlaybackView view = PlaybackPresenter.instance(this).getView();
            if (view instanceof Fragment) {
                Activity activity = ((Fragment) view).getActivity();
                if (activity != null) {
                    activity.dispatchKeyEvent(event);
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public void finish() {
        // NOTE: Fragment's onDestroy/onDestroyView are not reliable way to catch dialog finish
        Log.d(TAG, "Dialog finish");
        if (mFragment != null) { // fragment isn't created yet (expandable = true)
            // Fix touchscreen/mouse DPAD emulation on API 28+
            // The bug on API 28+ when using touchscreen/mouse to traverse nested dialog categories.
            // In this case the back gesture closes dialog instead of return to the previous category.
            // Cause: touchscreen/mouse input flips the view hierarchy into touch mode, which back-handling
            // treats differently than real D-pad input.
            if (isBackPressed() && mFragment.canGoBack()) {
                mFragment.goBack();
                // No additional event like onResume is fired, so we should reset the state explicitly
                resetBackState();
                return;
            }

            mFragment.onFinish();
        }

        // Destroy dialog when BACK is pressed. NoHistory isn't reliable if combined with singleInstance
        finishReally();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        // Respect PIP mode
        if (ViewManager.instance(this).getTopView() == PlaybackView.class && PlaybackPresenter.instance(this).getContext() instanceof PlaybackActivity) {
            ((PlaybackActivity) PlaybackPresenter.instance(this).getContext()).onUserLeaveHint();
        } else if (ViewManager.instance(this).getTopView() == PlaybackView.class && PlaybackPresenter.instance(this).getContext() instanceof MobilePlaybackActivity) {
            ((MobilePlaybackActivity) PlaybackPresenter.instance(this).getContext()).onUserLeaveHint();
        }

        finish();
    }
}
