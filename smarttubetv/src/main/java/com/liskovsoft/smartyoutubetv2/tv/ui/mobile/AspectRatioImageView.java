package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;

import android.widget.ImageView;

/**
 * Mobile build: image that keeps the aspect ratio (height = width * ratio). Default: 16:9 video thumbnail.
 */
@SuppressLint("AppCompatCustomView") // AppCompat isn't used by this module
public class AspectRatioImageView extends ImageView {
    public static final float RATIO_VIDEO = 9f / 16f;
    public static final float RATIO_SHORTS = 16f / 9f;
    private float mRatio = RATIO_VIDEO;

    public AspectRatioImageView(Context context) {
        super(context);
    }

    public AspectRatioImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectRatioImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setRatio(float ratio) {
        if (mRatio != ratio) {
            mRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width * mRatio);
        setMeasuredDimension(width, height);
    }
}
