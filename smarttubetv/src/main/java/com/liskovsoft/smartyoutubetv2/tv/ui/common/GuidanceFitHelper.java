package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.leanback.widget.GuidanceStylist;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * Mobile build (narrow portrait screen): the sign-in screens show a code that must be fully visible.<br/>
 * The code (title) stays on one line: its text is made smaller only if it doesn't fit (never bigger than the original).<br/>
 * The description (contains the sign-in url) isn't truncated.<br/>
 * The icon (QR code) takes at most a quarter of the guidance width (the original size on the wide screen).
 */
public final class GuidanceFitHelper {
    private static final float MIN_TITLE_SIZE_SP = 14;
    private static final float MAX_ICON_WIDTH_RATIO = 0.25f;

    private GuidanceFitHelper() {
    }

    public static void install(GuidanceStylist stylist) {
        if (stylist == null || stylist.getTitleView() == null || !Utils.isMobileBuild(stylist.getTitleView().getContext())) {
            return;
        }

        TextView description = stylist.getDescriptionView();

        if (description != null) {
            description.setMaxLines(Integer.MAX_VALUE);
            description.setEllipsize(null);
        }

        ImageView icon = stylist.getIconView();

        if (icon != null && icon.getParent() instanceof View && icon.getLayoutParams() != null && icon.getLayoutParams().width > 0) {
            View container = (View) icon.getParent();
            int originalIconWidth = icon.getLayoutParams().width;
            container.addOnLayoutChangeListener((View v, int left, int top, int right, int bottom,
                                                 int oldLeft, int oldTop, int oldRight, int oldBottom) -> fitIcon(icon, container, originalIconWidth));
        }

        TextView title = stylist.getTitleView();
        float originalSizePx = title.getTextSize();
        float minSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_TITLE_SIZE_SP, title.getResources().getDisplayMetrics());

        title.setMaxLines(1);
        title.setEllipsize(null);
        // Recalculate on each layout: the text (code) and the width (rotation) could be changed
        title.addOnLayoutChangeListener((View v, int left, int top, int right, int bottom,
                                         int oldLeft, int oldTop, int oldRight, int oldBottom) -> fitTitle(title, originalSizePx, minSizePx));
        fitTitle(title, originalSizePx, minSizePx);
    }

    private static void fitIcon(ImageView icon, View container, int originalIconWidth) {
        int containerWidth = container.getWidth() - container.getPaddingLeft() - container.getPaddingRight();

        if (containerWidth <= 0) {
            return;
        }

        // Depends on the container width only, so the result is stable between the layout passes
        int targetWidth = Math.min(originalIconWidth, (int) (containerWidth * MAX_ICON_WIDTH_RATIO));
        ViewGroup.LayoutParams lp = icon.getLayoutParams();

        if (lp.width != targetWidth) {
            lp.width = targetWidth;
            icon.post(() -> icon.setLayoutParams(lp));
        }
    }

    private static void fitTitle(TextView title, float originalSizePx, float minSizePx) {
        if (!(title.getParent() instanceof View)) {
            return;
        }

        // NOTE: the title is wrap_content. Use the free space from the title start to the parent end.
        View parent = (View) title.getParent();
        int availableWidth = parent.getWidth() - parent.getPaddingRight() - title.getLeft()
                - title.getCompoundPaddingLeft() - title.getCompoundPaddingRight();
        CharSequence text = title.getText();

        if (availableWidth <= 0 || TextUtils.isEmpty(text)) {
            return;
        }

        // Measure with the original size
        float currentSizePx = title.getPaint().getTextSize();
        title.getPaint().setTextSize(originalSizePx);
        float textWidth = title.getPaint().measureText(text, 0, text.length());
        title.getPaint().setTextSize(currentSizePx);

        float targetSizePx = textWidth > availableWidth ? Math.max(minSizePx, originalSizePx * availableWidth / textWidth) : originalSizePx;

        // Avoid the endless layout: change only if the difference is noticeable
        if (Math.abs(targetSizePx - title.getTextSize()) >= 1) {
            float finalSizePx = targetSizePx;
            title.post(() -> title.setTextSize(TypedValue.COMPLEX_UNIT_PX, finalSizePx));
        }
    }
}
