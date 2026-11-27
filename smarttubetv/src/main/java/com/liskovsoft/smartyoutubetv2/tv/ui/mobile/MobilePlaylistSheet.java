package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * Mobile build: the queue of the mix or playlist being played, as a sheet over the watch page (like the YouTube app):
 * the title, who made it, repeat, close, then the videos with the one playing highlighted.
 */
public final class MobilePlaylistSheet {
    public interface Listener {
        void onVideoPicked(Video video);
        void onRepeatClicked();
        boolean isRepeatOn();
    }

    private MobilePlaylistSheet() {
    }

    public static Dialog show(Activity activity, CharSequence title, CharSequence subtitle, List<Video> videos, Video current,
                              boolean landscape, Listener listener) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        float density = activity.getResources().getDisplayMetrics().density;
        int text = ContextCompat.getColor(activity, R.color.mobile_text);
        int secondary = ContextCompat.getColor(activity, R.color.mobile_text_secondary);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(density, 10), 0, 0);

        // Handle
        View handle = new View(activity);
        GradientDrawable handleShape = new GradientDrawable();
        handleShape.setCornerRadius(dp(density, 2));
        handleShape.setColor(secondary);
        handle.setBackground(handleShape);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(density, 36), dp(density, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(density, 6);
        root.addView(handle, handleLp);

        // Title + close
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(density, 16), 0, dp(density, 4), 0);
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(text);
        titleView.setTextSize(20);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(titleView);
        if (subtitle != null) {
            TextView subtitleView = new TextView(activity);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(secondary);
            subtitleView.setTextSize(13);
            subtitleView.setMaxLines(1);
            subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            subtitleView.setPadding(0, dp(density, 2), 0, 0);
            titles.addView(subtitleView);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton close = iconButton(activity, R.drawable.mobile_ic_close, R.string.mobile_cancel);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(density, 48), dp(density, 48)));
        root.addView(header);

        // Repeat
        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(density, 4), dp(density, 4), dp(density, 4), dp(density, 4));
        ImageButton repeat = iconButton(activity, R.drawable.mobile_ic_repeat, R.string.mobile_repeat);
        Runnable updateRepeat = () -> {
            boolean on = listener.isRepeatOn();
            repeat.setAlpha(on ? 1f : 0.55f);
            repeat.setBackgroundResource(on ? R.drawable.mobile_round_button_bg : R.drawable.mobile_ripple_borderless);
        };
        repeat.setOnClickListener(v -> {
            listener.onRepeatClicked();
            repeat.post(updateRepeat);
        });
        updateRepeat.run();
        controls.addView(repeat, new LinearLayout.LayoutParams(dp(density, 48), dp(density, 48)));
        root.addView(controls);

        View divider = new View(activity);
        divider.setBackgroundColor(ContextCompat.getColor(activity, R.color.mobile_divider));
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(density, 1) / 2)));

        // Videos
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(new Adapter(videos, current, video -> {
            dialog.dismiss();
            listener.onVideoPicked(video);
        }));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        int index = videos.indexOf(current);
        if (index > 0) {
            ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(index, dp(density, 8));
        }

        // Sheet background: rounded top (bottom too in landscape, a floating panel)
        GradientDrawable background = new GradientDrawable();
        float radius = dp(density, 16);
        float bottom = landscape ? radius : 0;
        background.setCornerRadii(new float[] {radius, radius, radius, radius, bottom, bottom, bottom, bottom});
        background.setColor(ContextCompat.getColor(activity, R.color.mobile_surface));
        root.setBackground(background);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            root.setClipToOutline(true);
        }
        dialog.setContentView(root);

        Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            View content = activity.getWindow().getDecorView();
            int width = content.getWidth() > 0 ? content.getWidth() : metrics.widthPixels;
            int height = content.getHeight() > 0 ? content.getHeight() : metrics.heightPixels;
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            window.setWindowAnimations(R.style.MobileDialogWindowAnimation);
            window.setLayout(landscape ? Math.max(dp(density, 360), (int) (width * 0.5f)) : ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (height * (landscape ? 0.9f : 0.7f)));
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = landscape ? dp(density, 8) : 0;
            params.dimAmount = 0.5f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        dialog.show();
        return dialog;
    }

    private static ImageButton iconButton(Activity activity, int iconResId, int descriptionResId) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(iconResId);
        button.setContentDescription(activity.getString(descriptionResId));
        button.setBackgroundResource(R.drawable.mobile_ripple_borderless);
        return button;
    }

    private static int dp(float density, int value) {
        return Math.round(value * density);
    }

    private interface Picker {
        void pick(Video video);
    }

    private static final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Video> mVideos;
        private final Video mCurrent;
        private final Picker mPicker;

        Adapter(List<Video> videos, Video current, Picker picker) {
            mVideos = videos;
            mCurrent = current;
            mPicker = picker;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mobile_item_compact, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View view = holder.itemView;
            Video video = mVideos.get(position);
            boolean playing = video.equals(mCurrent);

            MobileFeedAdapter.roundThumb((View) view.findViewById(R.id.mobile_thumb).getParent());
            MobileFeedAdapter.loadImage(view.findViewById(R.id.mobile_thumb), video.getCardImageUrl());
            TextView badge = view.findViewById(R.id.mobile_badge);
            MobileFeedAdapter.bindBadge(badge, video);
            if (playing) {
                // The one playing: an "audio lines" mark instead of the length
                badge.setVisibility(View.VISIBLE);
                badge.setText("");
                badge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.mobile_ic_v_now_playing, 0, 0, 0);
            } else if (!MobileCollections.isCollection(video)) {
                badge.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
            view.findViewById(R.id.mobile_watched).setVisibility(View.GONE);
            ((TextView) view.findViewById(R.id.mobile_video_title)).setText(video.getTitle());
            MobileFeedAdapter.setTextOrHide(view.findViewById(R.id.mobile_video_meta), video.getAuthor());
            view.findViewById(R.id.mobile_video_more).setVisibility(View.GONE);
            view.setBackgroundColor(playing ? ContextCompat.getColor(view.getContext(), R.color.mobile_chip) : 0);
            view.setOnClickListener(v -> mPicker.pick(video));
        }

        @Override
        public int getItemCount() {
            return mVideos.size();
        }
    }
}
