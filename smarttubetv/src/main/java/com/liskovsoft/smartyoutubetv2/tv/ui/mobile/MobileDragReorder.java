package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.annotation.SuppressLint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile build: long press and drag to move the items of a {@link LinearLayout} (menu rows, chips).<br/>
 * A long press without moving runs the usual long press action instead.
 */
public final class MobileDragReorder {
    public interface Listener {
        void onOrderChanged(List<String> keys);
    }

    private MobileDragReorder() {
    }

    /**
     * @param key stored as the view tag, given back by the listener in the new order
     * @param firstMovable index of the first child that moves (e.g. 1 when the first child is a fixed button)
     * @param onLongPress the long press action when the item wasn't moved, can be null
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void attach(View child, String key, LinearLayout container, int firstMovable, Listener listener, Runnable onLongPress) {
        child.setTag(key);
        Drag drag = new Drag(child, container, firstMovable, listener, onLongPress);
        child.setOnTouchListener(drag);
        child.setOnLongClickListener(v -> {
            drag.start();
            return true;
        });
    }

    private static final class Drag implements View.OnTouchListener {
        private final View mChild;
        private final LinearLayout mContainer;
        private final int mFirstMovable;
        private final Listener mListener;
        private final Runnable mOnLongPress;
        private final boolean mIsHorizontal;
        private float mLastRaw;
        private float mStartRaw;
        private int mStartScroll;
        private float mConsumed;
        private boolean mIsDragging;
        private boolean mIsMoved;

        Drag(View child, LinearLayout container, int firstMovable, Listener listener, Runnable onLongPress) {
            mChild = child;
            mContainer = container;
            mFirstMovable = firstMovable;
            mListener = listener;
            mOnLongPress = onLongPress;
            mIsHorizontal = container.getOrientation() == LinearLayout.HORIZONTAL;
        }

        void start() {
            mIsDragging = true;
            mIsMoved = false;
            mConsumed = 0;
            mStartRaw = mLastRaw;
            mStartScroll = getScroll();
            mChild.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            mChild.animate().scaleX(1.06f).scaleY(1.06f).alpha(0.9f).setDuration(120).start();
            androidx.core.view.ViewCompat.setTranslationZ(mChild, mChild.getResources().getDisplayMetrics().density * 8);
            ViewParent parent = mContainer.getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
            }
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            float raw = mIsHorizontal ? event.getRawX() : event.getRawY();

            if (!mIsDragging) {
                mLastRaw = raw;
                return false; // clicks and the long press go on as usual
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    autoScroll(raw);
                    move(raw - mStartRaw + (getScroll() - mStartScroll));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stop();
                    return true;
            }

            return true;
        }

        private void move(float delta) {
            float translation = delta - mConsumed;
            int index = mContainer.indexOfChild(mChild);

            // Passed the middle of the next or previous item: they swap places
            if (translation > 0 && index + 1 < mContainer.getChildCount()) {
                View next = mContainer.getChildAt(index + 1);
                float extent = extent(next);
                if (translation > extent / 2) {
                    mContainer.removeView(next);
                    mContainer.addView(next, index); // the dragged view stays attached (it keeps the touch)
                    mConsumed += extent;
                    translation -= extent;
                    mIsMoved = true;
                }
            } else if (translation < 0 && index - 1 >= mFirstMovable) {
                View previous = mContainer.getChildAt(index - 1);
                float extent = extent(previous);
                if (-translation > extent / 2) {
                    mContainer.removeView(previous);
                    mContainer.addView(previous, index);
                    mConsumed -= extent;
                    translation += extent;
                    mIsMoved = true;
                }
            }

            if (mIsHorizontal) {
                mChild.setTranslationX(translation);
            } else {
                mChild.setTranslationY(translation);
            }
        }

        private void stop() {
            mIsDragging = false;
            mChild.animate().translationX(0).translationY(0).scaleX(1).scaleY(1).alpha(1).setDuration(150).start();
            androidx.core.view.ViewCompat.setTranslationZ(mChild, 0);
            ViewParent parent = mContainer.getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(false);
            }

            if (mIsMoved) {
                List<String> keys = new ArrayList<>();
                for (int i = mFirstMovable; i < mContainer.getChildCount(); i++) {
                    Object tag = mContainer.getChildAt(i).getTag();
                    if (tag instanceof String) {
                        keys.add((String) tag);
                    }
                }
                mListener.onOrderChanged(keys);
            } else if (mOnLongPress != null) {
                mOnLongPress.run();
            }
        }

        private float extent(View view) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            return mIsHorizontal ? view.getWidth() + lp.leftMargin + lp.rightMargin : view.getHeight() + lp.topMargin + lp.bottomMargin;
        }

        private int getScroll() {
            ViewParent parent = mContainer.getParent();
            if (parent instanceof HorizontalScrollView) {
                return ((HorizontalScrollView) parent).getScrollX();
            }
            if (parent instanceof ScrollView) {
                return ((ScrollView) parent).getScrollY();
            }
            return 0;
        }

        /**
         * Near the edges of the scrolling parent: scrolls to reach the items out of sight
         */
        private void autoScroll(float raw) {
            ViewParent parent = mContainer.getParent();
            if (!(parent instanceof View)) {
                return;
            }

            View scroller = (View) parent;
            int[] location = new int[2];
            scroller.getLocationOnScreen(location);
            float start = mIsHorizontal ? location[0] : location[1];
            float size = mIsHorizontal ? scroller.getWidth() : scroller.getHeight();
            float edge = 48 * scroller.getResources().getDisplayMetrics().density;
            int step = (int) (12 * scroller.getResources().getDisplayMetrics().density);

            int dir = raw < start + edge ? -1 : raw > start + size - edge ? 1 : 0;
            if (dir != 0) {
                if (mIsHorizontal) {
                    scroller.scrollBy(dir * step, 0);
                } else {
                    scroller.scrollBy(0, dir * step);
                }
            }
        }
    }
}
