package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * Player row: the control buttons (first child) and the info block (second child, e.g. time or quality).<br/>
 * They are placed side by side, like on TV.<br/>
 * Mobile build in portrait (narrow screen): the info block is moved below the buttons (instead of drawing over them).<br/>
 * Landscape keeps the original (TV) look.
 */
public class ControlsInfoLayout extends RelativeLayout {
    private final boolean mIsStackEnabled;
    private boolean mIsStacked;

    public ControlsInfoLayout(Context context) {
        this(context, null);
    }

    public ControlsInfoLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ControlsInfoLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mIsStackEnabled = Utils.isMobileBuild(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!mIsStackEnabled || getChildCount() < 2) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Depends on the orientation only, so the result is stable between the layout passes
        setStacked(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void setStacked(boolean stacked) {
        if (mIsStacked == stacked) {
            return;
        }

        mIsStacked = stacked;

        View controls = getChildAt(0);
        LayoutParams lp = (LayoutParams) getChildAt(1).getLayoutParams();

        if (stacked) {
            lp.removeRule(RelativeLayout.CENTER_VERTICAL);
            lp.addRule(RelativeLayout.BELOW, controls.getId());
        } else {
            lp.removeRule(RelativeLayout.BELOW);
            lp.addRule(RelativeLayout.CENTER_VERTICAL);
        }
    }
}
