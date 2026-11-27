package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.MobileThemeHelper;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobile build: "Save to..." as a sheet (like the YouTube app): the playlists of the account with their picture,
 * a bookmark to add or remove the video, and a button to make a new playlist.
 */
public final class MobileSaveSheet {
    private MobileSaveSheet() {
    }

    public static void show(Activity activity, Video video, boolean landscape) {
        if (video == null || video.videoId == null) {
            return;
        }

        MobileUi.requireSignIn(activity, () -> showInt(activity, video, landscape));
    }

    private static void showInt(Activity activity, Video video, boolean landscape) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        float density = activity.getResources().getDisplayMetrics().density;
        int text = ContextCompat.getColor(activity, R.color.mobile_text);
        int secondary = ContextCompat.getColor(activity, R.color.mobile_text_secondary);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(density, 10), 0, dp(density, 12));

        View handle = new View(activity);
        GradientDrawable handleShape = new GradientDrawable();
        handleShape.setCornerRadius(dp(density, 2));
        handleShape.setColor(secondary);
        handle.setBackground(handleShape);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(density, 36), dp(density, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(handle, handleLp);

        TextView title = new TextView(activity);
        title.setText(R.string.mobile_save_to);
        title.setTextColor(text);
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(density, 20), dp(density, 16), dp(density, 20), dp(density, 8));
        root.addView(title);

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ProgressBar progress = new ProgressBar(activity);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(density, 36), dp(density, 36));
        progressLp.gravity = Gravity.CENTER_HORIZONTAL;
        progressLp.topMargin = dp(density, 24);
        progressLp.bottomMargin = dp(density, 24);
        rows.addView(progress, progressLp);

        // "+ New playlist"
        TextView create = new TextView(activity);
        create.setText(R.string.mobile_new_playlist);
        create.setTextColor(text);
        create.setTextSize(15);
        create.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        create.setGravity(Gravity.CENTER);
        create.setCompoundDrawablesWithIntrinsicBounds(R.drawable.mobile_ic_plus, 0, 0, 0);
        create.setCompoundDrawablePadding(dp(density, 8));
        create.setPadding(dp(density, 20), 0, dp(density, 24), 0);
        create.setBackgroundResource(R.drawable.mobile_speed_pill);
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(density, 44));
        createLp.setMargins(dp(density, 16), dp(density, 8), dp(density, 16), 0);
        root.addView(create, createLp);
        create.setOnClickListener(v -> {
            dialog.dismiss();
            askNewPlaylist(activity, video);
        });

        GradientDrawable background = new GradientDrawable();
        float radius = dp(density, 16);
        float bottom = landscape ? radius : 0;
        background.setCornerRadii(new float[] {radius, radius, radius, radius, bottom, bottom, bottom, bottom});
        background.setColor(ContextCompat.getColor(activity, R.color.mobile_surface));
        root.setBackground(background);
        dialog.setContentView(root);

        Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            View decor = activity.getWindow().getDecorView();
            int width = decor.getWidth() > 0 ? decor.getWidth() : metrics.widthPixels;
            int height = decor.getHeight() > 0 ? decor.getHeight() : metrics.heightPixels;
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            window.setWindowAnimations(R.style.MobileDialogWindowAnimation);
            window.setLayout(landscape ? Math.max(dp(density, 360), (int) (width * 0.46f)) : ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (height * (landscape ? 0.9f : 0.6f)));
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = landscape ? dp(density, 8) : 0;
            params.dimAmount = 0.5f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();

        // The playlists with the state of this video, and their pictures (from the playlists section)
        MediaItemService itemService = YouTubeServiceManager.instance().getMediaItemService();
        ContentService contentService = YouTubeServiceManager.instance().getContentService();
        Map<String, String> pictures = new HashMap<>();
        RxHelper.execute(contentService.getPlaylistsObserve(),
                group -> {
                    List<MediaItem> items = group != null ? group.getMediaItems() : null;
                    if (items != null) {
                        for (MediaItem item : items) {
                            if (item.getPlaylistId() != null) {
                                pictures.put(item.getPlaylistId(), item.getCardImageUrl());
                            }
                        }
                    }
                },
                error -> { },
                () -> RxHelper.execute(itemService.getPlaylistsInfoObserve(video.videoId),
                        infos -> activity.runOnUiThread(() -> fillRows(activity, rows, infos, pictures, video, density)),
                        error -> activity.runOnUiThread(() -> {
                            rows.removeAllViews();
                            Toast.makeText(activity, MobileUi.toReadableError(activity, error.getMessage()), Toast.LENGTH_SHORT).show();
                        })));
    }

    private static void fillRows(Activity activity, LinearLayout rows, List<PlaylistInfo> infos, Map<String, String> pictures, Video video, float density) {
        rows.removeAllViews();

        if (infos == null) {
            return;
        }

        int text = ContextCompat.getColor(activity, R.color.mobile_text);
        int secondary = ContextCompat.getColor(activity, R.color.mobile_text_secondary);

        for (PlaylistInfo info : infos) {
            boolean[] saved = {info.isSelected()};

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(density, 16), dp(density, 8), dp(density, 12), dp(density, 8));
            row.setBackgroundResource(R.drawable.mobile_ripple);

            ImageView picture = new ImageView(activity);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable placeholder = new GradientDrawable();
            placeholder.setCornerRadius(dp(density, 8));
            placeholder.setColor(ContextCompat.getColor(activity, R.color.mobile_thumb_placeholder));
            picture.setBackground(placeholder);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                picture.setClipToOutline(true);
            }
            MobileFeedAdapter.loadImage(picture, pictures.get(info.getPlaylistId()));
            row.addView(picture, new LinearLayout.LayoutParams(dp(density, 80), dp(density, 45)));

            LinearLayout texts = new LinearLayout(activity);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(density, 14), 0, dp(density, 8), 0);
            TextView name = new TextView(activity);
            name.setText(info.getTitle());
            name.setTextColor(text);
            name.setTextSize(16);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            if (info.getSize() > 0) {
                TextView count = new TextView(activity);
                count.setText(activity.getString(R.string.mobile_video_count, info.getSize()));
                count.setTextColor(secondary);
                count.setTextSize(13);
                texts.addView(count);
            }
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            ImageView mark = new ImageView(activity);
            mark.setImageResource(saved[0] ? R.drawable.mobile_ic_save_filled : R.drawable.mobile_ic_save);
            row.addView(mark, new LinearLayout.LayoutParams(dp(density, 28), dp(density, 28)));

            row.setOnClickListener(v -> {
                saved[0] = !saved[0];
                mark.setImageResource(saved[0] ? R.drawable.mobile_ic_save_filled : R.drawable.mobile_ic_save);
                MediaItemService service = YouTubeServiceManager.instance().getMediaItemService();
                RxHelper.execute(saved[0] ?
                                (video.mediaItem != null ? service.addToPlaylistObserve(info.getPlaylistId(), video.mediaItem) :
                                        service.addToPlaylistObserve(info.getPlaylistId(), video.videoId)) :
                                service.removeFromPlaylistObserve(info.getPlaylistId(), video.videoId),
                        error -> activity.runOnUiThread(() -> Toast.makeText(activity,
                                MobileUi.toReadableError(activity, error.getMessage()), Toast.LENGTH_SHORT).show()));
                Toast.makeText(activity, activity.getString(saved[0] ? R.string.mobile_saved_to : R.string.mobile_removed_from,
                        info.getTitle()), Toast.LENGTH_SHORT).show();
            });

            rows.addView(row);
        }
    }

    private static void askNewPlaylist(Activity activity, Video video) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(R.string.mobile_playlist_name);
        int padding = dp(activity.getResources().getDisplayMetrics().density, 20);
        LinearLayout box = new LinearLayout(activity);
        box.setPadding(padding, padding / 2, padding, 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(activity, MobileThemeHelper.isLight(activity) ?
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert : android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.mobile_new_playlist)
                .setView(box)
                .setPositiveButton(R.string.mobile_create, (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    MediaItemService service = YouTubeServiceManager.instance().getMediaItemService();
                    RxHelper.execute(video.mediaItem != null ? service.createPlaylistObserve(name, video.mediaItem) :
                                    service.createPlaylistObserve(name, video.videoId),
                            error -> activity.runOnUiThread(() -> Toast.makeText(activity,
                                    MobileUi.toReadableError(activity, error.getMessage()), Toast.LENGTH_SHORT).show()));
                    Toast.makeText(activity, activity.getString(R.string.mobile_saved_to, name), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.mobile_cancel, null)
                .show();
    }

    private static int dp(float density, int value) {
        return Math.round(value * density);
    }
}
