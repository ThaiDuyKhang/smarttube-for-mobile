package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;

import java.util.List;

/**
 * Mobile build: the red progress line of the player, like the YouTube app.<br/>
 * Thin line at the bottom of the player, sponsor segments, a thumb while the controls are shown, drag to seek.
 */
public class MobileTimeBar extends View {
    private static final int COLOR_TRACK = 0x4DFFFFFF;
    private static final int COLOR_BUFFERED = 0x80FFFFFF;
    private static final int COLOR_PLAYED = 0xFFFF0033;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mDensity;
    private long mDurationMs;
    private long mPositionMs;
    private long mBufferedMs;
    private long mScrubPositionMs = -1;
    private boolean mIsThumbShown;
    private List<SeekBarSegment> mSegments;
    private Listener mListener;

    public interface Listener {
        void onScrubStart();
        void onScrubMove(long positionMs);
        void onScrubStop(long positionMs);
    }

    public MobileTimeBar(Context context) {
        this(context, null);
    }

    public MobileTimeBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setProgress(long positionMs, long bufferedMs, long durationMs) {
        mPositionMs = positionMs;
        mBufferedMs = bufferedMs;
        mDurationMs = durationMs;
        invalidate();
    }

    public void setSegments(List<SeekBarSegment> segments) {
        mSegments = segments;
        invalidate();
    }

    public void setThumbShown(boolean shown) {
        if (mIsThumbShown != shown) {
            mIsThumbShown = shown;
            invalidate();
        }
    }

    public boolean isScrubbing() {
        return mScrubPositionMs >= 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float lineHeight = (isScrubbing() ? 5 : 3) * mDensity;
        float bottom = getHeight() - (mIsThumbShown ? getHeight() / 2f - lineHeight / 2 : 0);
        float top = bottom - lineHeight;

        mPaint.setColor(COLOR_TRACK);
        canvas.drawRect(0, top, width, bottom, mPaint);

        if (mDurationMs <= 0) {
            return;
        }

        mPaint.setColor(COLOR_BUFFERED);
        canvas.drawRect(0, top, width * fraction(mBufferedMs), bottom, mPaint);

        long position = isScrubbing() ? mScrubPositionMs : mPositionMs;
        float playedX = width * fraction(position);
        mPaint.setColor(COLOR_PLAYED);
        canvas.drawRect(0, top, playedX, bottom, mPaint);

        if (mSegments != null) {
            for (SeekBarSegment segment : mSegments) {
                mPaint.setColor(segment.color);
                canvas.drawRect(width * segment.startProgress, top, width * segment.endProgress, bottom, mPaint);
            }
        }

        if (mIsThumbShown || isScrubbing()) {
            mPaint.setColor(COLOR_PLAYED);
            canvas.drawCircle(playedX, (top + bottom) / 2, (isScrubbing() ? 9 : 6) * mDensity, mPaint);
        }
    }

    private float fraction(long ms) {
        return mDurationMs > 0 ? Math.max(0, Math.min(1, (float) ms / mDurationMs)) : 0;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDurationMs <= 0 || !isEnabled()) {
            return false;
        }

        long position = (long) (mDurationMs * Math.max(0, Math.min(1, event.getX() / getWidth())));

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                mScrubPositionMs = position;
                if (mListener != null) {
                    mListener.onScrubStart();
                    mListener.onScrubMove(position);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                mScrubPositionMs = position;
                if (mListener != null) {
                    mListener.onScrubMove(position);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mScrubPositionMs = -1;
                mPositionMs = position;
                if (mListener != null) {
                    mListener.onScrubStop(position);
                }
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }
}
