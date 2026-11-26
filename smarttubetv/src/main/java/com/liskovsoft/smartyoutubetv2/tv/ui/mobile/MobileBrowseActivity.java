package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mobile build: the main screen. Bottom tabs (Home, Shorts, Subscriptions, You), category chips, vertical feed and the
 * explore menu. All the data comes from {@link BrowsePresenter} (same as the TV screen).
 */
public class MobileBrowseActivity extends MobileActivity implements BrowseView, MobileFeedAdapter.Callback {
    private static final int TAB_HOME = 0;
    private static final int TAB_SHORTS = 1;
    private static final int TAB_SUBSCRIPTIONS = 2;
    private static final int TAB_YOU = 3;
    private static final int LOAD_MORE_THRESHOLD = 4;
    /**
     * Sections that have their own place (a tab or a top bar button) instead of a chip
     */
    private static final List<Integer> NON_CHIP_SECTIONS = Arrays.asList(
            MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_SETTINGS,
            MediaGroup.TYPE_NOTIFICATIONS, MediaGroup.TYPE_CHANNEL_UPLOADS, MediaGroup.TYPE_MY_VIDEOS,
            MediaGroup.TYPE_PLAYBACK_QUEUE, MediaGroup.TYPE_BLOCKED_CHANNELS, MediaGroup.TYPE_USER_PLAYLISTS);

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<BrowseSection> mSections = new ArrayList<>();
    private final List<Block> mBlocks = new ArrayList<>();
    private final List<SettingsItem> mSettings = new ArrayList<>();
    private BrowsePresenter mPresenter;
    private BrowseSection mCurrentSection;
    private int mLastHomeSectionId = MediaGroup.TYPE_HOME;
    private int mCurrentTab = TAB_HOME;
    private boolean mIsFirstSectionSelected;
    private final Object mHistoryHeaderOwner = new Object();
    private final java.util.Map<Integer, SettingsItem> mLibraryItems = new java.util.HashMap<>();
    private boolean mIsCreated;
    private Video mLastLoadMoreVideo;

    private RecyclerView mFeed;
    private GridLayoutManager mLayoutManager;
    private final PagerSnapHelper mSnapHelper = new PagerSnapHelper();
    private boolean mIsSnapAttached;
    private MobileFeedAdapter mAdapter;
    private View mTopBar;
    private View mChipsScroll;
    private LinearLayout mChips;
    private View mExploreButton;
    private View mSettingsButton;
    private View mNotificationsButton;
    private ProgressBar mProgress;
    private View mErrorView;
    private TextView mErrorMessage;
    private Button mErrorAction;
    private LinearLayout mBottomNav;
    private View mDrawer;
    private View mDrawerScrim;
    private LinearLayout mDrawerItems;
    private final List<View> mTabViews = new ArrayList<>();

    /**
     * Videos of one group (shelf) of the current section
     */
    private static class Block {
        final int id;
        final String title;
        final boolean isShorts;
        final List<Video> videos = new ArrayList<>();

        Block(VideoGroup group) {
            id = group.getId();
            title = group.getTitle();
            isShorts = group.isShorts();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_activity_browse);

        initViews();
        initBottomNav();

        mPresenter = BrowsePresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
        mIsCreated = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mIsCreated) {
            mPresenter.onViewResumed();
        }

        mIsCreated = false;

        if (mCurrentTab == TAB_YOU) {
            rebuildFeed(); // the account could be changed
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        mPresenter.onViewDestroyed();
    }

    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            closeDrawer();
            return;
        }

        // A library page goes back to the You page
        if (mCurrentTab == TAB_YOU && !isYouRoot()) {
            openSectionById(MediaGroup.TYPE_HISTORY);
            return;
        }

        // Back to the first page of the Home tab before exit
        if (mCurrentSection != null && mCurrentSection.getId() != MediaGroup.TYPE_HOME && findSection(MediaGroup.TYPE_HOME) != null) {
            openSection(findSection(MediaGroup.TYPE_HOME));
            return;
        }

        super.onBackPressed();
    }

    private void initViews() {
        mTopBar = findViewById(R.id.mobile_top_bar);
        mChipsScroll = findViewById(R.id.mobile_chips_scroll);
        mChips = findViewById(R.id.mobile_chips);
        mExploreButton = findViewById(R.id.mobile_explore);
        mSettingsButton = findViewById(R.id.mobile_settings);
        mNotificationsButton = findViewById(R.id.mobile_notifications);
        mProgress = findViewById(R.id.mobile_progress);
        mErrorView = findViewById(R.id.mobile_error);
        mErrorMessage = findViewById(R.id.mobile_error_message);
        mErrorAction = findViewById(R.id.mobile_error_action);
        mBottomNav = findViewById(R.id.mobile_bottom_nav);
        mDrawer = findViewById(R.id.mobile_drawer);
        mDrawerScrim = findViewById(R.id.mobile_drawer_scrim);
        mDrawerItems = findViewById(R.id.mobile_drawer_items);

        mFeed = findViewById(R.id.mobile_feed);
        // Phones in portrait: one card per row. Landscape and tablets: a grid, like the YouTube app.
        mLayoutManager = new GridLayoutManager(this, getSpanCount());
        mLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return mAdapter.getItemViewType(position) == MobileFeedAdapter.TYPE_VIDEO ? 1 : mLayoutManager.getSpanCount();
            }
        });
        mFeed.setLayoutManager(mLayoutManager);
        mFeed.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) {
                updateSpanCount();
            }
        });
        // Gutters between grid cards (none on a single column)
        mFeed.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                if (mLayoutManager.getSpanCount() > 1 && position != RecyclerView.NO_POSITION
                        && mAdapter.getItemViewType(position) == MobileFeedAdapter.TYPE_VIDEO) {
                    outRect.left = dp(8);
                    outRect.right = dp(8);
                }
            }
        });
        mAdapter = new MobileFeedAdapter(this);
        mFeed.setAdapter(mAdapter);
        mFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkLoadMore();
            }
        });

        mExploreButton.setOnClickListener(v -> openDrawer());
        mDrawerScrim.setOnClickListener(v -> closeDrawer());
        findViewById(R.id.mobile_search).setOnClickListener(v -> SearchPresenter.instance(this).startSearch(null));
        mNotificationsButton.setOnClickListener(v -> openSectionById(MediaGroup.TYPE_NOTIFICATIONS));
        mSettingsButton.setOnClickListener(v -> onSettingsClicked());

        ((TextView) findViewById(R.id.mobile_drawer_footer)).setText(
                String.format("%s %s", getString(R.string.app_name), AppInfoHelpers.getAppVersionName(this)));
    }

    // ------------------------------- Bottom navigation -------------------------------

    private void initBottomNav() {
        addTab(TAB_HOME, R.drawable.mobile_ic_home, R.drawable.mobile_ic_home_filled, getString(R.string.header_home));
        addTab(TAB_SHORTS, R.drawable.mobile_ic_shorts, R.drawable.mobile_ic_shorts_filled, getString(R.string.header_shorts));
        addTab(TAB_SUBSCRIPTIONS, R.drawable.mobile_ic_subscriptions, R.drawable.mobile_ic_subscriptions_filled,
                getString(R.string.header_subscriptions));
        addTab(TAB_YOU, R.drawable.mobile_ic_account, R.drawable.mobile_ic_account, getString(R.string.mobile_tab_you));
        updateTabs();
    }

    private void addTab(int tab, int iconResId, int selectedIconResId, String title) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(getSelectableBackground());
        view.setContentDescription(title);

        ImageView icon = new ImageView(this);
        icon.setTag(new int[] {iconResId, selectedIconResId});
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconSize = dp(24);
        view.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(10);
        label.setTextColor(ContextCompat.getColor(this, R.color.mobile_text));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(2);
        view.addView(label, labelParams);

        view.setOnClickListener(v -> onTabClicked(tab));
        mBottomNav.addView(view, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        mTabViews.add(view);
    }

    private void updateTabs() {
        for (int i = 0; i < mTabViews.size(); i++) {
            ViewGroup tab = (ViewGroup) mTabViews.get(i);
            ImageView icon = (ImageView) tab.getChildAt(0);
            int[] icons = (int[]) icon.getTag();
            boolean selected = i == mCurrentTab;
            icon.setImageResource(selected ? icons[1] : icons[0]);
            tab.setSelected(selected);
        }

        boolean isShorts = mCurrentTab == TAB_SHORTS;
        boolean isYou = mCurrentTab == TAB_YOU;
        // Shorts: full screen pages. You: account header instead of the chips.
        mTopBar.setVisibility(isShorts ? View.GONE : View.VISIBLE);
        mChipsScroll.setVisibility(mCurrentTab == TAB_HOME ? View.VISIBLE : View.GONE);
        mSettingsButton.setVisibility(isYou ? View.VISIBLE : View.GONE);
        mNotificationsButton.setVisibility(findSection(MediaGroup.TYPE_NOTIFICATIONS) != null ? View.VISIBLE : View.GONE);
        // Shorts pages fill the feed exactly, so no bottom padding there
        mFeed.setPadding(0, 0, 0, isShorts ? 0 : dp(8));
        setSnapEnabled(isShorts);
    }

    private void onTabClicked(int tab) {
        if (tab == mCurrentTab && tab != TAB_HOME) {
            mFeed.scrollToPosition(0);
            return;
        }

        switch (tab) {
            case TAB_HOME:
                if (mCurrentTab == TAB_HOME && mCurrentSection != null && mCurrentSection.getId() == mLastHomeSectionId) {
                    mFeed.scrollToPosition(0);
                    return;
                }
                BrowseSection home = findSection(mLastHomeSectionId);
                openSection(home != null ? home : getFirstChipSection());
                break;
            case TAB_SHORTS:
                openSectionById(MediaGroup.TYPE_SHORTS);
                break;
            case TAB_SUBSCRIPTIONS:
                openSectionById(MediaGroup.TYPE_SUBSCRIPTIONS);
                break;
            case TAB_YOU:
                openSectionById(MediaGroup.TYPE_HISTORY);
                break;
        }
    }

    private void setSnapEnabled(boolean enabled) {
        if (enabled == mIsSnapAttached) {
            return;
        }

        mSnapHelper.attachToRecyclerView(enabled ? mFeed : null);
        mIsSnapAttached = enabled;
    }

    // ------------------------------- Sections -------------------------------

    private void openSectionById(int sectionId) {
        BrowseSection section = findSection(sectionId);

        if (section != null) {
            openSection(section);
        } else if (sectionId == MediaGroup.TYPE_HISTORY) {
            // The section could be disabled. Show the account anyway.
            mCurrentSection = null;
            mCurrentTab = TAB_YOU;
            resetContent();
            updateTabs();
            updateChips();
            rebuildFeed();
        }
    }

    private void openSection(BrowseSection section) {
        if (section == null) {
            return;
        }

        mCurrentSection = section;
        mCurrentTab = getTab(section);

        if (mCurrentTab == TAB_HOME) {
            mLastHomeSectionId = section.getId();
        }

        resetContent();
        updateTabs();
        updateChips();
        rebuildFeed();

        mPresenter.onSectionFocused(section.getId());
    }

    private void resetContent() {
        mBlocks.clear();
        mSettings.clear();
        mLastLoadMoreVideo = null;
        hideError();
        mFeed.scrollToPosition(0);
    }

    private static int getTab(BrowseSection section) {
        if (section == null) {
            return TAB_YOU;
        }

        switch (section.getId()) {
            case MediaGroup.TYPE_SHORTS:
                return TAB_SHORTS;
            case MediaGroup.TYPE_SUBSCRIPTIONS:
                return TAB_SUBSCRIPTIONS;
            default:
                // Chips stay on Home. History, playlists, channels, etc. are pages of the You tab.
                return isChipSection(section) ? TAB_HOME : TAB_YOU;
        }
    }

    private BrowseSection findSection(int sectionId) {
        for (BrowseSection section : mSections) {
            if (section.getId() == sectionId) {
                return section;
            }
        }

        return null;
    }

    private int indexOf(int sectionId) {
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == sectionId) {
                return i;
            }
        }

        return -1;
    }

    private static boolean isChipSection(BrowseSection section) {
        return !NON_CHIP_SECTIONS.contains(section.getId());
    }

    private BrowseSection getFirstChipSection() {
        for (BrowseSection section : mSections) {
            if (isChipSection(section)) {
                return section;
            }
        }

        return mSections.isEmpty() ? null : mSections.get(0);
    }

    // ------------------------------- Chips -------------------------------

    private void updateChips() {
        // Keep the explore button (first child)
        mChips.removeViews(1, mChips.getChildCount() - 1);

        for (BrowseSection section : mSections) {
            if (!isChipSection(section)) {
                continue;
            }

            boolean selected = mCurrentTab == TAB_HOME && mCurrentSection != null && mCurrentSection.getId() == section.getId();
            TextView chip = new TextView(this);
            chip.setText(section.getId() == MediaGroup.TYPE_HOME ? getString(R.string.mobile_all) : section.getTitle());
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setBackgroundResource(selected ? R.drawable.mobile_chip_selected_bg : R.drawable.mobile_chip_bg);
            chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.mobile_text_on_light : R.color.mobile_text));
            chip.setOnClickListener(v -> openSection(section));
            chip.setSelected(selected);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
            lp.setMarginStart(dp(8));
            mChips.addView(chip, lp);
        }
    }

    // ------------------------------- Explore menu (drawer) -------------------------------

    private boolean isDrawerOpen() {
        return mDrawer.getVisibility() == View.VISIBLE;
    }

    private void openDrawer() {
        mDrawerItems.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (BrowseSection section : mSections) {
            View row = inflater.inflate(R.layout.mobile_item_setting, mDrawerItems, false);
            ImageView icon = row.findViewById(R.id.mobile_row_icon);
            if (section.getResId() > 0) {
                icon.setImageResource(section.getResId());
            } else if (!TextUtils.isEmpty(section.getIconUrl())) {
                Glide.with(this).load(section.getIconUrl()).into(icon);
            }
            ((TextView) row.findViewById(R.id.mobile_row_title)).setText(section.getTitle());
            row.setSelected(mCurrentSection != null && mCurrentSection.getId() == section.getId());
            row.setOnClickListener(v -> {
                closeDrawer();
                openSection(section);
            });
            row.setOnLongClickListener(v -> {
                closeDrawer();
                mPresenter.onSectionLongPressed(section.getId());
                return true;
            });
            mDrawerItems.addView(row);
        }

        mDrawerScrim.setAlpha(0);
        mDrawerScrim.setVisibility(View.VISIBLE);
        mDrawerScrim.animate().alpha(1).setDuration(200).start();
        mDrawer.setVisibility(View.VISIBLE);
        mDrawer.setTranslationX(-dp(304));
        mDrawer.animate().translationX(0).setDuration(200).start();
    }

    private void closeDrawer() {
        mDrawerScrim.animate().alpha(0).setDuration(200).withEndAction(() -> mDrawerScrim.setVisibility(View.GONE)).start();
        mDrawer.animate().translationX(-dp(304)).setDuration(200).withEndAction(() -> mDrawer.setVisibility(View.GONE)).start();
    }

    // ------------------------------- Feed -------------------------------

    private void rebuildFeed() {
        List<MobileFeedAdapter.Item> items = new ArrayList<>();

        if (mCurrentTab == TAB_YOU) {
            if (isYouRoot()) {
                // Account, then the library pages, then the watch history
                items.add(MobileFeedAdapter.Item.youHeader());

                for (BrowseSection section : mSections) {
                    if (getTab(section) == TAB_YOU && section.getId() != MediaGroup.TYPE_HISTORY) {
                        items.add(MobileFeedAdapter.Item.setting(getLibraryItem(section)));
                    }
                }

                if (hasVideos()) {
                    items.add(MobileFeedAdapter.Item.header(getString(R.string.mobile_watched_videos), mHistoryHeaderOwner));
                }
            } else {
                items.add(MobileFeedAdapter.Item.header(mCurrentSection.getTitle(), mCurrentSection));
            }
        }

        for (SettingsItem setting : mSettings) {
            items.add(MobileFeedAdapter.Item.setting(setting));
        }

        boolean showTitles = mCurrentSection != null && mCurrentSection.getType() == BrowseSection.TYPE_ROW;

        for (Block block : mBlocks) {
            if (block.videos.isEmpty()) {
                continue;
            }

            if (mCurrentTab == TAB_SHORTS) {
                for (Video video : block.videos) {
                    items.add(MobileFeedAdapter.Item.shortPage(video));
                }
                continue;
            }

            if ((showTitles || block.isShorts) && !TextUtils.isEmpty(block.title)) {
                items.add(MobileFeedAdapter.Item.header(block.title, block));
            }

            if (block.isShorts) {
                items.add(MobileFeedAdapter.Item.shortsRow(block.videos, block));
            } else {
                for (Video video : block.videos) {
                    items.add(MobileFeedAdapter.Item.video(video));
                }
            }
        }

        mAdapter.setItems(items);
        mHandler.post(this::checkLoadMore); // the page could be shorter than the screen
    }

    private int getSpanCount() {
        // The feed width follows rotation and split screen (the activity configuration doesn't, see MotherActivity)
        int width = mFeed != null && mFeed.getWidth() > 0 ? mFeed.getWidth() : Resources.getSystem().getDisplayMetrics().widthPixels;
        return Math.max(1, Math.min(4, width / dp(340)));
    }

    private void updateSpanCount() {
        int spanCount = getSpanCount();

        if (mLayoutManager != null && mLayoutManager.getSpanCount() != spanCount) {
            mLayoutManager.setSpanCount(spanCount);
            mFeed.post(() -> mAdapter.notifyDataSetChanged()); // card sizes depend on the width
        }
    }

    private boolean isYouRoot() {
        return mCurrentTab == TAB_YOU && (mCurrentSection == null || mCurrentSection.getId() == MediaGroup.TYPE_HISTORY);
    }

    private boolean hasVideos() {
        for (Block block : mBlocks) {
            if (!block.videos.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private SettingsItem getLibraryItem(BrowseSection section) {
        SettingsItem item = mLibraryItems.get(section.getId());

        if (item == null || !TextUtils.equals(item.title, section.getTitle())) {
            item = new SettingsItem(section.getTitle(), () -> openSection(section), section.getResId());
            mLibraryItems.put(section.getId(), item);
        }

        return item;
    }

    private void checkLoadMore() {
        int count = mAdapter.getItemCount();

        if (count == 0 || mCurrentSection == null || !mSettings.isEmpty()) {
            return;
        }

        if (mLayoutManager.findLastVisibleItemPosition() < count - LOAD_MORE_THRESHOLD) {
            return;
        }

        Video last = null;
        for (int i = count - 1; i >= 0 && last == null; i--) {
            last = mAdapter.getItems().get(i).getLastVideo();
        }

        if (last != null && last != mLastLoadMoreVideo && !mPresenter.hasPendingActions()) {
            mLastLoadMoreVideo = last;
            mPresenter.onScrollEnd(last);
        }
    }

    private Block findBlock(int id) {
        for (Block block : mBlocks) {
            if (block.id == id) {
                return block;
            }
        }

        return null;
    }

    private void applyGroup(VideoGroup group) {
        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            if (group.getPosition() == -1) {
                mBlocks.clear();
                mLastLoadMoreVideo = null;
            } else {
                Block block = findBlock(group.getId());
                if (block != null) {
                    mBlocks.remove(block);
                }
            }
        } else if (action == VideoGroup.ACTION_REMOVE) {
            for (Block block : mBlocks) {
                block.videos.removeAll(group.getVideos());
            }
            return;
        } else if (action == VideoGroup.ACTION_REMOVE_AUTHOR) {
            if (!group.isEmpty()) {
                String author = group.getVideos().get(0).getAuthor();
                for (Block block : mBlocks) {
                    List<Video> toRemove = new ArrayList<>();
                    for (Video video : block.videos) {
                        if (TextUtils.equals(author, video.getAuthor())) {
                            toRemove.add(video);
                        }
                    }
                    block.videos.removeAll(toRemove);
                }
            }
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            for (Video video : group.getVideos()) {
                for (Block block : mBlocks) {
                    for (Video origin : block.videos) {
                        if (origin.equals(video)) {
                            origin.sync(video);
                        }
                    }
                }
            }
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        Block block = findBlock(group.getId());

        if (block == null) {
            block = new Block(group);
            int position = group.getPosition();
            if (position >= 0 && position <= mBlocks.size()) {
                mBlocks.add(position, block);
            } else {
                mBlocks.add(block);
            }
        }

        List<Video> videos = new ArrayList<>(group.getVideos());

        if (action == VideoGroup.ACTION_PREPEND) {
            block.videos.addAll(0, videos);
        } else {
            block.videos.addAll(videos);
        }
    }

    private boolean belongsToCurrentSection(VideoGroup group) {
        BrowseSection section = group.getSection();
        // A late answer from the previous section
        return section == null || mCurrentSection == null || section.getId() == mCurrentSection.getId();
    }

    // ------------------------------- BrowseView -------------------------------

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) {
            return;
        }

        int existing = indexOf(section.getId());

        if (existing != -1 && (index == -1 || existing == index)) {
            return;
        }

        if (existing != -1) {
            mSections.remove(existing);
        }

        if (index < 0 || index > mSections.size()) {
            mSections.add(section);
        } else {
            mSections.add(index, section);
        }

        updateChips();
        updateTabs();
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) {
            return;
        }

        int index = indexOf(section.getId());

        if (index != -1) {
            mSections.remove(index);
            updateChips();
            updateTabs();
        }
    }

    @Override
    public void removeAllSections() {
        mSections.clear();
        updateChips();
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index < 0 || mSections.isEmpty()) {
            return;
        }

        BrowseSection section = mSections.get(Math.min(index, mSections.size() - 1));

        // The app always starts on Home, whatever section was open last time
        if (!mIsFirstSectionSelected) {
            mIsFirstSectionSelected = true;
            BrowseSection home = findSection(MediaGroup.TYPE_HOME);
            if (home != null) {
                section = home;
            }
        }

        openSection(section);
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null || !belongsToCurrentSection(group)) {
            return;
        }

        hideError();
        applyGroup(group);
        rebuildFeed();
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null) {
            return;
        }

        hideError();
        mBlocks.clear();
        mSettings.clear();
        mSettings.addAll(group.getItems());
        rebuildFeed();
    }

    @Override
    public void clearSection(BrowseSection section) {
        mBlocks.clear();
        mSettings.clear();
        rebuildFeed();
    }

    @Override
    public void selectSectionItem(int index) {
        // NOP: the feed keeps its position
    }

    @Override
    public void selectSectionItem(Video item) {
        if (item == null) {
            return;
        }

        List<MobileFeedAdapter.Item> items = mAdapter.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (item.equals(items.get(i).video)) {
                mLayoutManager.scrollToPositionWithOffset(i, 0);
                return;
            }
        }
    }

    @Override
    public void showError(ErrorFragmentData data) {
        mBlocks.clear();
        mSettings.clear();
        rebuildFeed();

        // The You page already has its own sign in button
        if (data == null || isYouRoot()) {
            return;
        }

        mErrorView.setVisibility(View.VISIBLE);
        String message = MobileUi.toReadableError(this, data.getMessage());
        mErrorMessage.setText(message);
        String actionText = data.getActionText();

        if (!MobileUi.isOnline(this)) {
            actionText = null; // no network: "retry" instead of "sign in"
        }

        if (TextUtils.isEmpty(actionText)) {
            // Network and other errors: offer to load the section again
            BrowseSection section = mCurrentSection;
            boolean canRetry = section != null && !TextUtils.equals(message, data.getMessage());
            mErrorAction.setVisibility(canRetry ? View.VISIBLE : View.GONE);
            mErrorAction.setText(R.string.mobile_retry);
            mErrorAction.setOnClickListener(v -> {
                hideError();
                mPresenter.onSectionFocused(section.getId());
            });
        } else {
            mErrorAction.setVisibility(View.VISIBLE);
            mErrorAction.setText(actionText);
            mErrorAction.setOnClickListener(v -> data.onAction());
        }
    }

    private void hideError() {
        mErrorView.setVisibility(View.GONE);
    }

    @Override
    public void showProgressBar(boolean show) {
        mHandler.post(() -> mProgress.setVisibility(show ? View.VISIBLE : View.GONE));
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgress.getVisibility() == View.VISIBLE;
    }

    @Override
    public void focusOnContent() {
        // NOP: touch screen
    }

    @Override
    public boolean isEmpty() {
        return mBlocks.isEmpty() && mSettings.isEmpty();
    }

    @Override
    public void updateBadge() {
        // NOP: no badge in the top bar
    }

    // ------------------------------- Feed callbacks -------------------------------

    @Override
    public void onVideoClicked(Video video) {
        mPresenter.onVideoItemSelected(video);
        mPresenter.onVideoItemClicked(video);
    }

    @Override
    public void onVideoLongClicked(Video video) {
        mPresenter.onVideoItemSelected(video);
        mPresenter.onVideoItemLongClicked(video);
    }

    @Override
    public void onSettingClicked(SettingsItem item) {
        item.onClick.run();
    }

    @Override
    public void onSwitchAccountClicked() {
        if (getAccount() == null) {
            // Not signed in: straight to the sign in screen (the account list would be empty)
            YTSignInPresenter.instance(this).start();
        } else {
            AccountSelectionPresenter.instance(this).show(true);
        }
    }

    @Override
    public void onSettingsClicked() {
        openSectionById(MediaGroup.TYPE_SETTINGS);
    }

    @Override
    public Account getAccount() {
        return MediaServiceManager.instance().getSelectedAccount();
    }

    // ------------------------------- Utils -------------------------------

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getSelectableBackground() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }
}
