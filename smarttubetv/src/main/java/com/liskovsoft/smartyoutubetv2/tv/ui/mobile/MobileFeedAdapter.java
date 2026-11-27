package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    public static final int TYPE_HEADER_LINK = 8;
    public static final int TYPE_VIDEO_ROW = 9;
    public static final int TYPE_COMPACT = 10;
    public static final int TYPE_CHANNEL = 11;
    public static final int TYPE_PILL = 12;
    public static final int TYPE_CHIPS = 13;
    public static final int TYPE_SECTION = 14;

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
        default void onChannelBellClicked(Video channel) {}
        default void onViewChannelClicked() {}
        /** Fills a row of chips (TYPE_CHIPS) */
        default void bindChips(LinearLayout chips) {}
    }

    public static class Item {
        final int type;
        final Video video;
        final List<Video> videos;
        final String title;
        final SettingsItem setting;
        final long id;
        Runnable action;
        Object tag;
        // Snapshot to tell if the row must be redrawn
        final float watched;
        final int count;

        private Item(int type, Video video, List<Video> videos, String title, SettingsItem setting, long id) {
            this.type = type;
            this.video = video;
            this.videos = videos;
            this.title = title;
            this.setting = setting;
            this.id = id;
            this.watched = video != null ? video.percentWatched : 0;
            this.count = videos != null ? videos.size() : 0;
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

        /** Title with a ">" that opens the full page */
        public static Item headerLink(String title, Object owner, Runnable action) {
            Item item = new Item(TYPE_HEADER_LINK, null, null, title, null, makeId(TYPE_HEADER_LINK, owner));
            item.action = action;
            return item;
        }

        /** Horizontal row of small video cards */
        public static Item videoRow(List<Video> videos, Object owner) {
            return new Item(TYPE_VIDEO_ROW, null, videos, null, null, makeId(TYPE_VIDEO_ROW, owner));
        }

        /** Thumbnail on the left (playlists) */
        public static Item compact(Video video) {
            return new Item(TYPE_COMPACT, video, null, null, null, makeId(TYPE_COMPACT, video));
        }

        /** Subscribed channel with the notifications button */
        public static Item channel(Video channel) {
            return new Item(TYPE_CHANNEL, channel, null, null, null, makeId(TYPE_CHANNEL, channel));
        }

        /** Dropdown pill ("Most relevant v") */
        public static Item pill(String title, Runnable action) {
            Item item = new Item(TYPE_PILL, null, null, title, null, makeId(TYPE_PILL, TYPE_PILL));
            item.action = action;
            return item;
        }

        /** Group title with a divider above (none on the first group) */
        public static Item section(String title, Object groupKey, boolean isFirst) {
            Item item = new Item(TYPE_SECTION, null, null, title, null, makeId(TYPE_SECTION, groupKey));
            item.tag = groupKey;
            item.action = isFirst ? null : () -> { };
            return item;
        }

        public Object getTag() {
            return tag;
        }

        /** A row of chips filled by the callback */
        public static Item chips(Object owner, String state) {
            // The state (e.g. the chosen chip) is in the title: a new state redraws the row
            return new Item(TYPE_CHIPS, null, null, state, null, makeId(TYPE_CHIPS, owner));
        }

        public static Item youHeader() {
            return new Item(TYPE_YOU_HEADER, null, null, null, null, makeId(TYPE_YOU_HEADER, TYPE_YOU_HEADER));
        }

        private static long makeId(int type, Object owner) {
            int hash = owner instanceof String ? owner.hashCode() : System.identityHashCode(owner);
            return ((long) type << 32) | (hash & 0xffffffffL);
        }

        public SettingsItem getSetting() {
            return type == TYPE_SETTING ? setting : null;
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
    private final RecyclerView.RecycledViewPool mShortsPool = new RecyclerView.RecycledViewPool();
    private View mCustomView;
    private final List<Item> mItems = new ArrayList<>();

    public MobileFeedAdapter(Callback callback) {
        mCallback = callback;
        setHasStableIds(true);
    }

    /**
     * Only the changed rows are updated (a new page doesn't redraw the whole list or reload the images)
     */
    public void setItems(List<Item> items) {
        if (mItems.isEmpty() || items.isEmpty() || mItems.size() + items.size() > 4000) {
            mItems.clear();
            mItems.addAll(items);
            notifyDataSetChanged();
            return;
        }

        List<Item> oldItems = new ArrayList<>(mItems);
        androidx.recyclerview.widget.DiffUtil.DiffResult diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
                new androidx.recyclerview.widget.DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return oldItems.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return items.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldPosition, int newPosition) {
                        return oldItems.get(oldPosition).id == items.get(newPosition).id;
                    }

                    @Override
                    public boolean areContentsTheSame(int oldPosition, int newPosition) {
                        Item oldItem = oldItems.get(oldPosition);
                        Item newItem = items.get(newPosition);
                        // Same video: redraw only when the watched progress changed. Shorts row: when it got more videos.
                        return oldItem.type != TYPE_YOU_HEADER && oldItem.type != TYPE_VIEW
                                && oldItem.watched == newItem.watched && oldItem.count == newItem.count
                                && TextUtils.equals(oldItem.title, newItem.title);
                    }
                }, false);

        mItems.clear();
        mItems.addAll(items);
        diff.dispatchUpdatesTo(this);
    }

    /**
     * A row dragged to a new place
     */
    public void moveItem(int from, int to) {
        if (from < 0 || to < 0 || from >= mItems.size() || to >= mItems.size()) {
            return;
        }
        mItems.add(to, mItems.remove(from));
        notifyItemMoved(from, to);
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
            case TYPE_HEADER_LINK:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_header_link, parent, false));
            case TYPE_VIDEO_ROW:
                return new VideoRowHolder(inflater.inflate(R.layout.mobile_item_video_row, parent, false));
            case TYPE_COMPACT:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_compact, parent, false));
            case TYPE_CHANNEL:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_channel, parent, false));
            case TYPE_PILL:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_pill, parent, false));
            case TYPE_CHIPS:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_chips, parent, false));
            case TYPE_SECTION:
                return new SimpleHolder(inflater.inflate(R.layout.mobile_item_section, parent, false));
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
            case TYPE_HEADER_LINK:
                ((TextView) holder.itemView.findViewById(R.id.mobile_header_title)).setText(item.title);
                holder.itemView.setOnClickListener(v -> item.action.run());
                break;
            case TYPE_VIDEO_ROW:
                ((VideoRowHolder) holder).bind(item.videos);
                break;
            case TYPE_COMPACT:
                bindVideo(holder.itemView, item.video);
                break;
            case TYPE_CHANNEL:
                bindChannel(holder.itemView, item.video);
                break;
            case TYPE_PILL:
                TextView pill = holder.itemView.findViewById(R.id.mobile_pill);
                pill.setText(item.title);
                pill.setOnClickListener(v -> item.action.run());
                break;
            case TYPE_CHIPS:
                mCallback.bindChips(holder.itemView.findViewById(R.id.mobile_row_chips));
                break;
            case TYPE_SECTION:
                ((TextView) holder.itemView.findViewById(R.id.mobile_header_title)).setText(item.title);
                holder.itemView.findViewById(R.id.mobile_section_divider).setVisibility(item.action != null ? View.VISIBLE : View.GONE);
                break;
        }
    }

    private void bindChannel(View view, Video channel) {
        ImageView avatar = view.findViewById(R.id.mobile_channel_item_avatar);
        String url = channel.getCardImageUrl();
        if (TextUtils.isEmpty(url)) {
            Glide.with(view).clear(avatar);
            avatar.setImageDrawable(null);
        } else {
            Glide.with(view).load(url).apply(RequestOptions.circleCropTransform()).into(avatar);
        }
        ((TextView) view.findViewById(R.id.mobile_channel_item_name)).setText(channel.getTitle());
        view.findViewById(R.id.mobile_channel_item_dot).setVisibility(channel.hasNewContent ? View.VISIBLE : View.GONE);
        view.setOnClickListener(v -> mCallback.onVideoClicked(channel));
        view.setOnLongClickListener(v -> {
            mCallback.onVideoLongClicked(channel);
            return true;
        });
        view.findViewById(R.id.mobile_channel_item_bell).setOnClickListener(v -> mCallback.onChannelBellClicked(channel));
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

    /**
     * Rounded thumbnails like the YouTube app (cards that don't fill the screen width: small cards, lists, grid, shorts)
     */
    @android.annotation.TargetApi(21)
    static void roundThumb(View thumbFrame) {
        if (android.os.Build.VERSION.SDK_INT < 21 || Boolean.TRUE.equals(thumbFrame.getTag(R.id.mobile_rounded))) {
            return;
        }

        thumbFrame.setTag(R.id.mobile_rounded, true);
        float radius = 12 * thumbFrame.getResources().getDisplayMetrics().density;
        thumbFrame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                int screenWidth = view.getResources().getDisplayMetrics().widthPixels;
                boolean isFullWidth = view.getWidth() > screenWidth * 0.9f;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), isFullWidth ? 0 : radius);
            }
        });
        thumbFrame.setClipToOutline(true);
    }

    private void bindVideo(View view, Video video) {
        AspectRatioImageView thumb = view.findViewById(R.id.mobile_thumb);
        roundThumb((View) thumb.getParent());
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
        if (resId == R.drawable.settings_account) return R.drawable.mobile_ic_m_account_manage;
        if (resId == R.drawable.settings_version) return R.drawable.mobile_ic_m_tag;
        if (resId == R.drawable.settings_source) return R.drawable.mobile_ic_m_github;
        if (resId == R.drawable.settings_donate) return R.drawable.mobile_ic_m_coffee;
        if (resId == R.drawable.settings_cast) return R.drawable.mobile_ic_set_cast;
        if (resId == R.drawable.settings_language) return R.drawable.mobile_ic_m_language;
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
        if (resId == R.drawable.settings_theme) return R.drawable.mobile_ic_set_theme;
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
        Button first = view.findViewById(R.id.mobile_account_switch);
        Button second = view.findViewById(R.id.mobile_account_settings);

        if (account != null && !account.isEmpty()) {
            name.setText(account.getName());
            setTextOrHide(email, account.getEmail());
            if (!TextUtils.isEmpty(account.getAvatarImageUrl())) {
                Glide.with(context).load(account.getAvatarImageUrl())
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.mobile_ic_account)
                        .into(avatar);
            } else {
                avatar.setImageResource(R.drawable.mobile_ic_account);
            }
            // Signed in: your channel, switch account
            first.setText(R.string.mobile_view_channel);
            first.setOnClickListener(v -> mCallback.onViewChannelClicked());
            second.setText(R.string.mobile_switch_account);
            second.setOnClickListener(v -> mCallback.onSwitchAccountClicked());
        } else {
            name.setText(R.string.mobile_not_signed_in);
            email.setVisibility(View.GONE);
            avatar.setImageResource(R.drawable.mobile_ic_account);
            first.setText(R.string.mobile_sign_in);
            first.setOnClickListener(v -> mCallback.onSwitchAccountClicked());
            second.setText(R.string.mobile_settings);
            second.setOnClickListener(v -> mCallback.onSettingsClicked());
        }
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

        Glide.with(view)
                .load(url)
                .apply(THUMB_OPTIONS)
                .into(view);
    }

    // Thumbnails: half the memory (no transparency needed), quick fade, kept in the disk cache
    private static final RequestOptions THUMB_OPTIONS = new RequestOptions()
            .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC);

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
            LinearLayoutManager layoutManager = new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false);
            layoutManager.setInitialPrefetchItemCount(3); // the first cards are ready when the row scrolls in
            row.setLayoutManager(layoutManager);
            row.setRecycledViewPool(mShortsPool); // the rows share their cards
            row.setHasFixedSize(true);
            row.setAdapter(mAdapter);
        }

        void bind(List<Video> videos) {
            mAdapter.setVideos(videos);
        }
    }

    /**
     * Horizontal row of small cards: own adapter, the rows share their cards
     */
    private class VideoRowHolder extends RecyclerView.ViewHolder {
        private final SmallCardAdapter mAdapter = new SmallCardAdapter();

        VideoRowHolder(@NonNull View itemView) {
            super(itemView);
            RecyclerView row = (RecyclerView) itemView;
            LinearLayoutManager layoutManager = new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false);
            layoutManager.setInitialPrefetchItemCount(3);
            row.setLayoutManager(layoutManager);
            row.setHasFixedSize(true);
            row.setAdapter(mAdapter);
        }

        void bind(List<Video> videos) {
            mAdapter.setVideos(videos);
        }
    }

    private class SmallCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
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
            return new SimpleHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.mobile_item_small_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            bindVideo(holder.itemView, mVideos.get(position));
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
            roundThumb((View) view.findViewById(R.id.mobile_short_thumb).getParent());
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
