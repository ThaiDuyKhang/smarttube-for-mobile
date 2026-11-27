package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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
    private boolean mIsLandscapeSheet;

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

            applyPlayerOrientation();
        }
    }

    /**
     * Opened over a landscape video: stay in landscape, don't turn to portrait. The player screen tells it
     * (its own configuration doesn't follow the rotation, see MotherActivity, so ask the player).
     */
    private void applyPlayerOrientation() {
        if (this instanceof AppDialogActivityLandscape) {
            return; // landscape from the manifest
        }

        // Android 8.0 doesn't allow it on a translucent activity (crash): it rotates with the device there
        if (VERSION.SDK_INT != 26) {
            setRequestedOrientation(isPlayerLandscape(this) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    /**
     * The player is the top screen and is shown in landscape (its own configuration doesn't follow the rotation,
     * see MotherActivity, so ask the player)
     */
    public static boolean isPlayerLandscape(android.content.Context context) {
        PlaybackView player = PlaybackPresenter.instance(context).getView();
        return player instanceof MobilePlaybackActivity && !((MobilePlaybackActivity) player).isFinishing()
                && ViewManager.instance(context).getTopView() == PlaybackView.class
                && !(VERSION.SDK_INT >= 24 && ((MobilePlaybackActivity) player).isInPictureInPictureMode())
                && ((MobilePlaybackActivity) player).isShownInLandscape();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (Utils.isMobileBuild(this)) {
            applyPlayerOrientation(); // singleInstance: the next menu may reuse this screen
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

        // Portrait: a sheet at the bottom. Landscape (full screen video): a floating panel in the middle, like the YouTube app.
        boolean isLandscape = width > height;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pane.getLayoutParams();
        int sheetWidth = isLandscape ? Math.max((int) (360 * density), (int) (width * 0.46f)) : Math.min(width, (int) (600 * density));
        int sheetHeight = (int) (height * (isLandscape ? 0.9f : 0.62f));
        int bottomMargin = isLandscape ? (int) (8 * density) : 0;

        if (lp.width != sheetWidth || lp.height != sheetHeight || lp.bottomMargin != bottomMargin
                || lp.gravity != (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)) {
            lp.width = sheetWidth;
            lp.height = sheetHeight;
            lp.bottomMargin = bottomMargin;
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            pane.setLayoutParams(lp);
        }

        // Room for the handle; the bottom of the sheet stays above the navigation bar (the window draws behind it)
        // (the part of the window under the navigation bar: below the visible frame)
        int navBar = 0;
        if (!isLandscape) {
            android.graphics.Rect visible = new android.graphics.Rect();
            content.getWindowVisibleDisplayFrame(visible);
            int[] location = new int[2];
            content.getLocationOnScreen(location);
            navBar = Math.max(0, location[1] + height - visible.bottom);
        }
        if (pane.getPaddingTop() != (int) (16 * density) || pane.getPaddingBottom() != navBar) {
            pane.setPadding(0, (int) (16 * density), 0, navBar);
        }

        if (!(pane.getBackground() instanceof GradientDrawable) || isLandscape != mIsLandscapeSheet) {
            mIsLandscapeSheet = isLandscape;
            GradientDrawable background = new GradientDrawable();
            float radius = 16 * density;
            float bottom = isLandscape ? radius : 0;
            background.setCornerRadii(new float[] {radius, radius, radius, radius, bottom, bottom, bottom, bottom});
            background.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.mobile_surface));
            pane.setBackground(background);
            if (VERSION.SDK_INT >= 21) {
                pane.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                pane.setClipToOutline(true);
            }
        }

        // Handle bar at the top of the sheet
        ViewGroup container = (ViewGroup) pane.getParent();
        if (container != null) {
            View handle = container.findViewWithTag("mobile_sheet_handle");
            if (handle == null) {
                handle = new View(this);
                handle.setTag("mobile_sheet_handle");
                GradientDrawable shape = new GradientDrawable();
                shape.setCornerRadius(2 * density);
                shape.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.mobile_text_secondary));
                handle.setBackground(shape);
                container.addView(handle);
            }
            FrameLayout.LayoutParams handleLp = new FrameLayout.LayoutParams((int) (36 * density), (int) (4 * density));
            handleLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            handleLp.bottomMargin = bottomMargin + sheetHeight - (int) (12 * density);
            handle.setLayoutParams(handleLp);
            handle.bringToFront();
        }

        fixTitles(pane, density);

        // Tap outside the sheet: close
        if (container != null && !container.hasOnClickListeners()) {
            container.setOnClickListener(v -> finish());
            pane.setClickable(true); // don't close on taps inside
        }
    }

    /**
     * The TV title of the menus is 64dp high with 27dp above it: on phones the same compact header for every menu
     * (the handle, 8dp, the title, 12dp), whatever the level of the menu.
     */
    private void fixTitles(View view, float density) {
        if (view.getId() == R.id.decor_title && view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            int top = (int) (8 * density);
            int bottom = (int) (12 * density);
            int side = (int) (20 * density);
            if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT || lp.topMargin != top || lp.bottomMargin != bottom || lp.getMarginStart() != side) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.topMargin = top;
                lp.bottomMargin = bottom;
                lp.setMarginStart(side);
                lp.setMarginEnd(side);
                view.setLayoutParams(lp);
                if (view instanceof android.widget.TextView) {
                    ((android.widget.TextView) view).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
                }
            }
            return;
        }

        if (view instanceof androidx.leanback.widget.VerticalGridView && view.getTag(R.id.mobile_touch_list) == null) {
            // TV lists keep the selected row at a key line in the middle and clamp the scroll around it: swiping back
            // down could get stuck. On phones: the key line at the top edge, like a plain touch list.
            view.setTag(R.id.mobile_touch_list, true);
            androidx.leanback.widget.VerticalGridView grid = (androidx.leanback.widget.VerticalGridView) view;
            grid.setWindowAlignment(androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
            // The list stops at its last row (no empty space after it) and at its first row
            grid.setWindowAlignmentPreferKeyLineOverHighEdge(false);
            grid.setWindowAlignmentPreferKeyLineOverLowEdge(false);
            grid.setWindowAlignmentOffset(0);
            grid.setWindowAlignmentOffsetPercent(0);
            grid.setItemAlignmentOffset(0);
            grid.setItemAlignmentOffsetPercent(0);
            grid.setFocusScrollStrategy(androidx.leanback.widget.BaseGridView.FOCUS_SCROLL_ITEM);
            grid.setPadding(grid.getPaddingLeft(), 0, grid.getPaddingRight(), (int) (8 * density));
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                fixTitles(group.getChildAt(i), density);
            }
        }
    }

    @Override
    public boolean isNativeUi() {
        // Phones: phone density and the light/dark theme of the app (see MotherActivity.attachBaseContext)
        return IS_MOBILE_BUILD;
    }

    private static final boolean IS_MOBILE_BUILD = "stmobile".equals(com.liskovsoft.smartyoutubetv2.tv.BuildConfig.FLAVOR);

    @Override
    protected void initTheme() {
        if (IS_MOBILE_BUILD) {
            setTheme(R.style.MobileDialogTheme);
            return;
        }

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

        if (IS_MOBILE_BUILD) {
            overridePendingTransition(0, R.anim.mobile_sheet_out); // slides down
        }
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
