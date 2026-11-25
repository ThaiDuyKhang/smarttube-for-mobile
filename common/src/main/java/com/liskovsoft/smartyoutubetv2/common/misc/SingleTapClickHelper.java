package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.core.view.ViewCompat;
import com.liskovsoft.smartyoutubetv2.common.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Phones and tablets: one tap should activate an item.<br/>
 * The TV UI consists of the views that are focusable in touch mode.
 * By Android design, the first tap on such a view only moves the focus (selects an item) and the second tap clicks it.<br/>
 * This helper performs the click right after the tap moved the focus, so one tap selects and activates the item
 * (e.g. opens a video, opens a section, presses a button or a settings item).<br/>
 * Long press (context menu) and scrolling aren't affected.<br/>
 * Opt-out for a view: {@code view.setTag(R.id.single_tap_click_disabled, true)}
 */
public class SingleTapClickHelper {
    private static final float MIN_VISIBLE_ALPHA = 0.1f;
    private final Activity mActivity;
    private final int mTouchSlop;
    private final int mLongPressTimeoutMs;
    private final List<View> mVisibleViewsAtDown = new ArrayList<>();
    private final Rect mTempRect = new Rect();
    private boolean mIsTap;
    private float mDownX;
    private float mDownY;
    private View mFocusBeforeUp;

    public SingleTapClickHelper(Activity activity) {
        mActivity = activity;
        mTouchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        mLongPressTimeoutMs = ViewConfiguration.getLongPressTimeout();
    }

    /**
     * Call before the event is dispatched to the views
     */
    public void beforeDispatch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsTap = true;
                mDownX = event.getX();
                mDownY = event.getY();
                mFocusBeforeUp = null;
                collectVisibleViews(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (isMoved(event)) {
                    mIsTap = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isMoved(event) || event.getEventTime() - event.getDownTime() >= mLongPressTimeoutMs) {
                    mIsTap = false;
                }
                mFocusBeforeUp = mActivity.getCurrentFocus();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                mIsTap = false;
                break;
        }
    }

    /**
     * Call after the event is dispatched to the views
     */
    public void afterDispatch(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_UP && mIsTap) {
            View focused = mActivity.getCurrentFocus();

            // The tap moved the focus to the item. So the system didn't click it. Do this manually.
            if (focused != null && focused != mFocusBeforeUp && isClickTarget(focused)) {
                focused.post(() -> {
                    if (ViewCompat.isAttachedToWindow(focused) && focused.isFocused()) {
                        focused.performClick();
                    }
                });
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mIsTap = false;
            mFocusBeforeUp = null;
            mVisibleViewsAtDown.clear(); // don't leak the views
        }
    }

    private boolean isClickTarget(View view) {
        return !Boolean.TRUE.equals(view.getTag(R.id.single_tap_click_disabled)) &&
                view.isFocusableInTouchMode() && view.isClickable() && view.isEnabled() && view.hasOnClickListeners() &&
                containsView(mVisibleViewsAtDown, view); // the item was visible under the finger when the tap started
    }

    private boolean isMoved(MotionEvent event) {
        return Math.abs(event.getX() - mDownX) > mTouchSlop || Math.abs(event.getY() - mDownY) > mTouchSlop;
    }

    private void collectVisibleViews(MotionEvent event) {
        mVisibleViewsAtDown.clear();

        View root = mActivity.getWindow() != null ? mActivity.getWindow().peekDecorView() : null;

        if (root != null) {
            collectVisibleViews(root, (int) event.getX(), (int) event.getY(), 1f);
        }
    }

    /**
     * Collect the views under the touch point that the user can see (e.g. skip the player controls that are fading in)
     */
    private void collectVisibleViews(View view, int x, int y, float parentAlpha) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }

        float alpha = parentAlpha * view.getAlpha();

        if (alpha < MIN_VISIBLE_ALPHA) {
            return;
        }

        if (!view.getGlobalVisibleRect(mTempRect) || !mTempRect.contains(x, y)) {
            return;
        }

        mVisibleViewsAtDown.add(view);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectVisibleViews(group.getChildAt(i), x, y, alpha);
            }
        }
    }

    private static boolean containsView(List<View> views, View view) {
        for (View item : views) {
            if (item == view) {
                return true;
            }
        }

        return false;
    }
}
