package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Mobile build: mixes and playlists look like a stack of cards (two layers above the thumbnail) with a
 * "Mix" / "N videos" label, like the YouTube app. Used by every card type (feeds, rows, search, channel, watch page).
 */
public final class MobileCollections {
    private static final int STACK_DP = 8; // room above the thumbnail for the two layers

    private MobileCollections() {
    }

    /**
     * A mix or a playlist (not a single video, not a channel)
     */
    public static boolean isCollection(Video video) {
        if (video == null || video.isLive || video.isUpcoming) {
            return false;
        }

        // A playlist that opens as a page (search results): no video id, a channel id, the playlist type
        // or a count label ("31 videos", not a length "3:45")
        if (video.isPlaylistAsChannel() || (video.videoId == null && video.hasChannel() && isCountLabel(video.badge))) {
            return true;
        }

        if (video.isChannel()) {
            return false;
        }

        // A mix made from a video (radio playlist "RD..." with a "Mix" label, not a length)
        String playlistId = video.getPlaylistId();
        if (playlistId != null && playlistId.startsWith("RD") && !TextUtils.isEmpty(video.badge) && !video.badge.matches(".*\\d+:\\d\\d.*")) {
            return true;
        }

        // (isMix() alone also matches premieres and streams to come: a mix has a playlist or no video id)
        if ((video.isMix() && (video.hasPlaylist() || video.videoId == null)) || video.itemType == MediaItem.TYPE_PLAYLIST
                || (video.videoId == null && video.hasPlaylist())) {
            return true;
        }

        // Search results and some shelves: a playlist with its first video, labelled "31 videos" (not a length "3:45")
        return video.hasPlaylist() && isCountLabel(video.badge);
    }

    private static boolean isCountLabel(String badge) {
        return !TextUtils.isEmpty(badge) && badge.matches(".*\\d.*") && !badge.matches(".*\\d+:\\d\\d.*");
    }

    /**
     * A mix made by YouTube ("Mix - ...") rather than a playlist made by someone
     */
    public static boolean isMix(Video video) {
        if (video == null) {
            return false;
        }

        String playlistId = video.getPlaylistId();
        if (playlistId != null && playlistId.startsWith("RD")) {
            return true;
        }

        String title = video.getTitle() != null ? video.getTitle().toString() : "";
        String badge = video.badge != null ? video.badge : "";
        return title.startsWith("Mix") || title.startsWith("Danh sách kết hợp") || badge.contains("Mix") || badge.contains("kết hợp");
    }

    /**
     * Applies (or removes, on recycled views) the stack and the collection label. The badge sits in the thumbnail frame.
     */
    public static void decorate(TextView badge, Video video) {
        if (!(badge.getParent() instanceof FrameLayout)) {
            return;
        }

        FrameLayout frame = (FrameLayout) badge.getParent();
        View thumb = frame.findViewById(R.id.mobile_thumb);
        Context context = frame.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        boolean collection = isCollection(video);
        boolean fullWidthCard = frame.getId() == R.id.mobile_thumb_frame;

        if (collection) {
            frame.setPadding(0, Math.round(STACK_DP * density), 0, 0);
            frame.setBackground(new StackDrawable(context));
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                // Rounded thumbnail under the layers (the frame itself isn't clipped, the layers are above it)
                frame.setClipToOutline(false);
                if (thumb != null) {
                    final float radius = 12 * density;
                    thumb.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                        }
                    });
                    thumb.setClipToOutline(true);
                }
            }
            if (fullWidthCard) {
                setSideMargins(frame, Math.round(12 * density)); // an inset card, like the YouTube app
            }

            // Label: the mix icon and "Mix", or the playlist icon and the count ("25 videos")
            boolean mix = isMix(video);
            CharSequence text = mix ? context.getString(R.string.mobile_mix) :
                    !TextUtils.isEmpty(video.badge) ? video.badge : context.getString(R.string.mobile_playlist);
            badge.setVisibility(View.VISIBLE);
            badge.setText(text);
            badge.setBackgroundResource(R.drawable.mobile_collection_badge_bg);
            badge.setCompoundDrawablesWithIntrinsicBounds(mix ? R.drawable.mobile_ic_v_mix : R.drawable.mobile_ic_v_playlist, 0, 0, 0);
            badge.setCompoundDrawablePadding(Math.round(4 * density));
            badge.setPadding(Math.round(8 * density), Math.round(3 * density), Math.round(8 * density), Math.round(3 * density));
        } else if (frame.getBackground() instanceof StackDrawable) {
            // Recycled from a collection: back to a plain card
            frame.setPadding(0, 0, 0, 0);
            frame.setBackground(null);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                frame.setClipToOutline(Boolean.TRUE.equals(frame.getTag(R.id.mobile_rounded)));
                if (thumb != null) {
                    thumb.setClipToOutline(false);
                }
            }
            if (fullWidthCard) {
                setSideMargins(frame, 0);
            }
            badge.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            badge.setPadding(Math.round(4 * density), Math.round(1 * density), Math.round(4 * density), Math.round(1 * density));
        }
    }

    private static void setSideMargins(View view, int margin) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (lp.leftMargin != margin || lp.rightMargin != margin) {
                lp.leftMargin = margin;
                lp.rightMargin = margin;
                view.setLayoutParams(lp);
            }
        }
    }

    /**
     * Two layers peeking above the thumbnail: the farther one narrower and darker
     */
    private static final class StackDrawable extends Drawable {
        private final Paint mFar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mNear = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mDensity;
        private final RectF mRect = new RectF();

        StackDrawable(Context context) {
            mDensity = context.getResources().getDisplayMetrics().density;
            int base = ContextCompat.getColor(context, R.color.mobile_text_secondary);
            mFar.setColor(base);
            mFar.setAlpha(70);
            mNear.setColor(base);
            mNear.setAlpha(130);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float radius = 10 * mDensity;
            float step = 4 * mDensity;
            // farther layer: top row, inset the most
            mRect.set(b.left + b.width() * 0.08f, b.top, b.right - b.width() * 0.08f, b.top + step * 3);
            canvas.drawRoundRect(mRect, radius, radius, mFar);
            // nearer layer
            mRect.set(b.left + b.width() * 0.04f, b.top + step, b.right - b.width() * 0.04f, b.top + step * 4);
            canvas.drawRoundRect(mRect, radius, radius, mNear);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
