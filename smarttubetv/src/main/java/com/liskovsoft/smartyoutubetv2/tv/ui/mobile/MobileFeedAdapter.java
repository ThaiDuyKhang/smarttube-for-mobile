package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile build: the vertical feed of the main screen.
 */
public class MobileFeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_SHORTS_ROW = 2;
    public static final int TYPE_SHORT_PAGE = 3;
    public static final int TYPE_SETTING = 4;
    public static final int TYPE_YOU_HEADER = 5;
    public static final int TYPE_SUGGESTION = 6;
    public static final int TYPE_VIEW = 7;

    public interface Callback {
        void onVideoClicked(Video video);
        void onVideoLongClicked(Video video);
        void onSettingClicked(SettingsItem item);
        void onSwitchAccountClicked();
        void onSettingsClicked();
        Account getAccount();
        default void onSuggestionClicked(String text) {}
        default void onSuggestionFillClicked(String text) {}
        default void onSuggestionLongClicked(String text) {}
    }

    public static class Item {
        final int type;
        final Video video;
        final List<Video> videos;
        final String title;
        final SettingsItem setting;
        final long id;

        private Item(int type, Video video, List<Video> videos, String title, SettingsItem setting, long id) {
            this.type = type;
            this.video = video;
            this.videos = videos;
            this.title = title;
            this.setting = setting;
            this.id = id;
        }

        public static Item header(String title, Object owner) {
            return new Item(TYPE_HEADER, null, null, title, null, makeId(TYPE_HEADER, owner));
        }

        public static Item video(Video video) {
            return new Item(TYPE_VIDEO, video, null, null, null, makeId(TYPE_VIDEO, video));
        }

        public static Item shortsRow(List<Video> videos, Object owner) {
            return new Item(TYPE_SHORTS_ROW, null, videos, null, null, makeId(TYPE_SHORTS_ROW, owner));
        }

        public static Item shortPage(Video video) {
            return new Item(TYPE_SHORT_PAGE, video, null, null, null, makeId(TYPE_SHORT_PAGE, video));
        }

        public static Item setting(SettingsItem setting) {
            return new Item(TYPE_SETTING, null, null, null, setting, makeId(TYPE_SETTING, setting));
        }

        public static Item suggestion(String text, boolean isHistory) {
            return new Item(TYPE_SUGGESTION, null, null, text, isHistory ? HISTORY_MARK : null, makeId(TYPE_SUGGESTION, text));
        }

        /**
         * The custom view of the adapter (see {@link #setCustomView(View)}), e.g. the video details on the watch page
         */
        public static Item view() {
            return new Item(TYPE_VIEW, null, null, null, null, makeId(TYPE_VIEW, TYPE_VIEW));
        }

        public static Item youHeader() {
            return new Item(TYPE_YOU_HEADER, null, null, null, null, makeId(TYPE_YOU_HEADER, TYPE_YOU_HEADER));
        }

        private static long makeId(int type, Object owner) {
            int hash = owner instanceof String ? owner.hashCode() : System.identityHashCode(owner);
            return ((long) type << 32) | (hash & 0xffffffffL);
        }

        /**
         * Last video of the item (used to load the next page)
         */
        public Video getLastVideo() {
            if (video != null) {
                return video;
            }

            return videos != null && !videos.isEmpty() ? videos.get(videos.size() - 1) : null;
        }
    }

    private static final SettingsItem HISTORY_MARK = new SettingsItem(null, null);
    private final Callback mCallback;
    private View mCustomView;
    private final List<Item> mItems = new ArrayList<>();

    public MobileFeedAdapter(Callback callback) {
        mCallback = callback;
        setHasStableIds(true);
    }

    public void setItems(List<Item> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setCustomView(View view) {
        mCustomView = view;
    }

    public List<Item> getItems() {
        return mItems;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public long getItemId(int position) {
        return mItems.get(position).id;
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case TYPE_HEADER:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_header, parent, false));
            case TYPE_SHORTS_ROW:
                return new ShortsRowHolder(inflater.inflate(R.layout.mobile_item_shorts_row, parent, false));
            case TYPE_SHORT_PAGE:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_short_page, parent, false));
            case TYPE_SETTING:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_setting, parent, false));
            case TYPE_YOU_HEADER:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_you_header, parent, false));
            case TYPE_SUGGESTION:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_suggestion, parent, false));
            case TYPE_VIEW:
                if (mCustomView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) mCustomView.getParent()).removeView(mCustomView);
                }
                mCustomView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new SimpleHolder(mCustomView);
            case TYPE_VIDEO:
            default:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_video, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = mItems.get(position);

        switch (item.type) {
            case TYPE_HEADER:
                ((TextView) holder.itemView.findViewById(R.id.mobile_header_title)).setText(item.title);
                break;
            case TYPE_VIDEO:
                bindVideo(holder.itemView, item.video);
                break;
            case TYPE_SHORTS_ROW:
                ((ShortsRowHolder) holder).bind(item.videos);
                break;
            case TYPE_SHORT_PAGE:
                bindShortPage(holder.itemView, item.video);
                break;
            case TYPE_SETTING:
                bindSetting(holder.itemView, item.setting);
                break;
            case TYPE_YOU_HEADER:
                bindYouHeader(holder.itemView);
                break;
            case TYPE_SUGGESTION:
                bindSuggestion(holder.itemView, item.title, item.setting == HISTORY_MARK);
                break;
        }
    }

    private void bindSuggestion(View view, String text, boolean isHistory) {
        ((ImageView) view.findViewById(R.id.mobile_suggestion_icon)).setImageResource(isHistory ? R.drawable.mobile_ic_history : R.drawable.mobile_ic_search);
        ((TextView) view.findViewById(R.id.mobile_suggestion_text)).setText(text);
        view.setOnClickListener(v -> mCallback.onSuggestionClicked(text));
        view.setOnLongClickListener(v -> {
            mCallback.onSuggestionLongClicked(text);
            return true;
        });
        view.findViewById(R.id.mobile_suggestion_fill).setOnClickListener(v -> mCallback.onSuggestionFillClicked(text));
    }

    private void bindVideo(View view, Video video) {
        AspectRatioImageView thumb = view.findViewById(R.id.mobile_thumb);
        loadImage(thumb, video.getCardImageUrl());

        TextView badge = view.findViewById(R.id.mobile_badge);
        bindBadge(badge, video);

        ProgressBar watched = view.findViewById(R.id.mobile_watched);
        if (video.percentWatched > 0) {
            watched.setVisibility(View.VISIBLE);
            watched.setProgress(Math.round(video.percentWatched));
        } else {
            watched.setVisibility(View.GONE);
        }

        ((TextView) view.findViewById(R.id.mobile_video_title)).setText(video.getTitle());
        setTextOrHide(view.findViewById(R.id.mobile_video_meta), video.getSecondTitle());

        view.setOnClickListener(v -> mCallback.onVideoClicked(video));
        view.setOnLongClickListener(v -> {
            mCallback.onVideoLongClicked(video);
            return true;
        });
        ImageButton more = view.findViewById(R.id.mobile_video_more);
        more.setOnClickListener(v -> mCallback.onVideoLongClicked(video));
    }

    private void bindShortPage(View view, Video video) {
        ImageView thumb = view.findViewById(R.id.mobile_page_thumb);
        loadImage(thumb, video.getCardImageUrl());
        setTextOrHide(view.findViewById(R.id.mobile_page_author), video.getAuthor());
        ((TextView) view.findViewById(R.id.mobile_page_title)).setText(video.getTitle());
        view.setOnClickListener(v -> mCallback.onVideoClicked(video));
        view.findViewById(R.id.mobile_page_more).setOnClickListener(v -> mCallback.onVideoLongClicked(video));
    }

    /**
     * The TV settings icons replaced with the Lucide ones
     */
    private static int toLucideIcon(int resId) {
        if (resId == R.drawable.settings_account) return R.drawable.mobile_ic_set_account;
        if (resId == R.drawable.settings_cast) return R.drawable.mobile_ic_set_cast;
        if (resId == R.drawable.settings_language) return R.drawable.mobile_ic_set_language;
        if (resId == R.drawable.settings_app) return R.drawable.mobile_ic_set_app;
        if (resId == R.drawable.settings_main_ui) return R.drawable.mobile_ic_set_main_ui;
        if (resId == R.drawable.settings_player) return R.drawable.mobile_ic_set_player;
        if (resId == R.drawable.settings_afr) return R.drawable.mobile_ic_set_afr;
        if (resId == R.drawable.settings_subtitles) return R.drawable.mobile_ic_set_subtitles;
        if (resId == R.drawable.settings_search) return R.drawable.mobile_ic_set_search;
        if (resId == R.drawable.settings_block) return R.drawable.mobile_ic_set_block;
        if (resId == R.drawable.settings_dearrow) return R.drawable.mobile_ic_set_dearrow;
        if (resId == R.drawable.settings_backup) return R.drawable.mobile_ic_set_backup;
        if (resId == R.drawable.settings_about) return R.drawable.mobile_ic_set_about;
        if (resId == R.drawable.settings_update) return R.drawable.mobile_ic_set_update;
        return resId;
    }

    private void bindSetting(View view, SettingsItem setting) {
        ImageView icon = view.findViewById(R.id.mobile_row_icon);
        if (setting.imageResId > 0) {
            icon.setVisibility(View.VISIBLE);
            icon.setImageResource(toLucideIcon(setting.imageResId));
        } else {
            icon.setVisibility(View.INVISIBLE);
        }
        ((TextView) view.findViewById(R.id.mobile_row_title)).setText(setting.title);
        view.setOnClickListener(v -> {
            if (setting.onClick != null) {
                mCallback.onSettingClicked(setting);
            }
        });
    }

    private void bindYouHeader(View view) {
        Context context = view.getContext();
        Account account = mCallback.getAccount();
        ImageView avatar = view.findViewById(R.id.mobile_account_avatar);
        TextView name = view.findViewById(R.id.mobile_account_name);
        TextView email = view.findViewById(R.id.mobile_account_email);
        Button switchButton = view.findViewById(R.id.mobile_account_switch);

        if (account != null && !account.isEmpty()) {
            name.setText(account.getName());
            setTextOrHide(email, account.getEmail());
            switchButton.setText(R.string.mobile_switch_account);
            if (!TextUtils.isEmpty(account.getAvatarImageUrl())) {
                Glide.with(context).load(account.getAvatarImageUrl())
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.mobile_ic_account)
                        .into(avatar);
            } else {
                avatar.setImageResource(R.drawable.mobile_ic_account);
            }
        } else {
            name.setText(R.string.mobile_not_signed_in);
            email.setVisibility(View.GONE);
            switchButton.setText(R.string.mobile_sign_in);
            avatar.setImageResource(R.drawable.mobile_ic_account);
        }

        switchButton.setOnClickListener(v -> mCallback.onSwitchAccountClicked());
        view.findViewById(R.id.mobile_account_settings).setOnClickListener(v -> mCallback.onSettingsClicked());
    }

    static void bindBadge(TextView badge, Video video) {
        if (video.isLive) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(R.string.mobile_live_badge);
            badge.setBackgroundResource(R.drawable.mobile_live_badge_bg);
        } else if (!TextUtils.isEmpty(video.badge)) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(video.badge);
            badge.setBackgroundResource(R.drawable.mobile_badge_bg);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    static void loadImage(ImageView view, String url) {
        if (TextUtils.isEmpty(url)) {
            Glide.with(view).clear(view);
            view.setImageDrawable(null);
            return;
        }

        Glide.with(view).load(url).into(view);
    }

    static void setTextOrHide(TextView view, CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(text);
        }
    }

    private static class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    /**
     * Horizontal shelf of shorts (two cards on the screen)
     */
    private class ShortsRowHolder extends RecyclerView.ViewHolder {
        private final ShortsAdapter mAdapter = new ShortsAdapter();

        ShortsRowHolder(@NonNull View itemView) {
            super(itemView);
            RecyclerView row = (RecyclerView) itemView;
            row.setLayoutManager(new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            row.setAdapter(mAdapter);
        }

        void bind(List<Video> videos) {
            mAdapter.setVideos(videos);
        }
    }

    private class ShortsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Video> mVideos = new ArrayList<>();

        void setVideos(List<Video> videos) {
            mVideos.clear();
            mVideos.addAll(videos);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mVideos.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mobile_item_short, parent, false);
            // Two cards fit the screen: (screen - side paddings - space between) / 2
            Context context = parent.getContext();
            float density = context.getResources().getDisplayMetrics().density;
            int screenWidth = android.content.res.Resources.getSystem().getDisplayMetrics().widthPixels; // follows rotation
            // Two cards per screen on phones, capped on landscape and tablets
            int cardWidth = (int) Math.min((screenWidth - 12 * density - 12 * density - 8 * density) / 2, 200 * density);
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = cardWidth;
            view.setLayoutParams(lp);
            ((AspectRatioImageView) view.findViewById(R.id.mobile_short_thumb)).setRatio(AspectRatioImageView.RATIO_SHORTS);
            return new SimpleHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Video video = mVideos.get(position);
            View view = holder.itemView;
            loadImage(view.findViewById(R.id.mobile_short_thumb), video.getCardImageUrl());
            ((TextView) view.findViewById(R.id.mobile_short_title)).setText(video.getTitle());
            setTextOrHide(view.findViewById(R.id.mobile_short_meta), video.getSecondTitle());
            view.setOnClickListener(v -> mCallback.onVideoClicked(video));
            view.setOnLongClickListener(v -> {
                mCallback.onVideoLongClicked(video);
                return true;
            });
        }
    }
}
