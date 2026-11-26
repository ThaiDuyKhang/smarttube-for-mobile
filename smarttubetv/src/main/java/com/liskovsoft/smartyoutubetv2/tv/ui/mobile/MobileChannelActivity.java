package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile build: channel page like the YouTube app.<br/>
 * Avatar, name, subscribers, subscribe button, tabs (Home + one per shelf: videos, shorts, playlists, live...),
 * search in the channel and sorting.
 */
public class MobileChannelActivity extends MobileActivity implements ChannelView, MobileFeedAdapter.Callback {
    private static final int TAB_HOME = -1;
    private static final int GROUP_SEARCH = 112; // see ChannelPresenter.onSearchSubmit
    private static final int GROUP_SORT = 144; // see ChannelPresenter.onSearchSettingsClicked
    private ChannelPresenter mPresenter;
    private MobileFeedAdapter mAdapter;
    private MobileFeed mFeed;
    private View mHeader;
    private LinearLayout mTabs;
    private View mProgress;
    private TextView mBarTitle;
    private EditText mSearchField;
    private int mSelectedTab = TAB_HOME;
    private Boolean mIsSubscribed;
    private boolean mIsCreated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_activity_channel);

        mProgress = findViewById(R.id.mobile_channel_progress);
        mBarTitle = findViewById(R.id.mobile_channel_bar_title);
        mSearchField = findViewById(R.id.mobile_channel_search_field);
        RecyclerView list = findViewById(R.id.mobile_channel_list);

        mHeader = getLayoutInflater().inflate(R.layout.mobile_channel_header, (ViewGroup) findViewById(android.R.id.content), false);
        mTabs = mHeader.findViewById(R.id.mobile_channel_tabs);
        mHeader.findViewById(R.id.mobile_channel_subscribe).setOnClickListener(v -> MobileUi.requireSignIn(this, this::toggleSubscribe));

        mAdapter = new MobileFeedAdapter(this);
        mAdapter.setCustomView(mHeader);
        mFeed = new MobileFeed(list, mAdapter, new MobileFeed.LoadMoreListener() {
            @Override
            public boolean isLoading() {
                return mPresenter.hasPendingActions();
            }

            @Override
            public void onLoadMore(Video lastVideo) {
                mPresenter.onScrollEnd(lastVideo);
            }
        });

        findViewById(R.id.mobile_channel_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.mobile_channel_search).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.mobile_channel_sort).setOnClickListener(v -> mPresenter.onSearchSettingsClicked());
        findViewById(R.id.mobile_channel_more).setOnClickListener(v -> {
            if (mPresenter.getChannel() != null) {
                VideoMenuPresenter.instance(this).showMenu(mPresenter.getChannel());
            }
        });
        mSearchField.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if ((actionId == EditorInfo.IME_ACTION_SEARCH || isEnter) && !TextUtils.isEmpty(mSearchField.getText().toString().trim())) {
                hideKeyboard();
                mPresenter.onSearchSubmit(mSearchField.getText().toString().trim());
                return true;
            }
            return false;
        });

        mPresenter = ChannelPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
        mIsCreated = true;

        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mIsCreated) {
            mPresenter.onViewResumed();
        }

        mIsCreated = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void finishReally() {
        super.finishReally();
        mPresenter.onFinish();
    }

    @Override
    public void onBackPressed() {
        if (mSearchField.getVisibility() == View.VISIBLE) {
            toggleSearch();
            return;
        }

        if (mSelectedTab != TAB_HOME) {
            selectTab(TAB_HOME);
            return;
        }

        super.onBackPressed();
    }

    // ------------------------------- Header -------------------------------

    private void bindHeader() {
        Video channel = mPresenter.getChannel();

        String name = null;
        if (channel != null) {
            name = channel.isChannel() ? channel.getTitle() : channel.getAuthor();
        }
        if (TextUtils.isEmpty(name)) {
            String channelId = mPresenter.getChannelId();
            name = channelId != null && channelId.startsWith("@") ? channelId : null;
        }

        TextView nameView = mHeader.findViewById(R.id.mobile_channel_name);
        nameView.setText(name);
        mBarTitle.setText(name);

        TextView letter = mHeader.findViewById(R.id.mobile_channel_letter);
        letter.setText(!TextUtils.isEmpty(name) ? name.substring(0, 1).toUpperCase() : null);

        ImageView avatar = mHeader.findViewById(R.id.mobile_channel_avatar);
        String avatarUrl = MobileUi.getChannelIcon(channel != null && channel.channelId != null ? channel.channelId : mPresenter.getChannelId());
        if (avatarUrl == null && channel != null && channel.isChannel()) {
            avatarUrl = channel.getCardImageUrl();
        }
        if (!TextUtils.isEmpty(avatarUrl)) {
            Glide.with(this).load(avatarUrl).apply(RequestOptions.circleCropTransform()).into(avatar);
        } else {
            avatar.setImageDrawable(null);
        }

        List<String> info = new ArrayList<>();
        String channelId = mPresenter.getChannelId();
        if (channelId != null && channelId.startsWith("@")) {
            info.add(channelId);
        }
        if (channel != null && !TextUtils.isEmpty(channel.subscriberCount)) {
            info.add(channel.subscriberCount);
        }
        TextView infoView = mHeader.findViewById(R.id.mobile_channel_info);
        infoView.setText(TextUtils.join(" • ", info));
        infoView.setVisibility(info.isEmpty() ? View.GONE : View.VISIBLE);

        if (mIsSubscribed == null && channel != null) {
            mIsSubscribed = channel.isSubscribed;
        }
        boolean subscribed = mIsSubscribed != null && mIsSubscribed;
        TextView subscribe = mHeader.findViewById(R.id.mobile_channel_subscribe);
        subscribe.setText(subscribed ? R.string.mobile_subscribed : R.string.mobile_subscribe);
        subscribe.setBackgroundResource(subscribed ? R.drawable.mobile_pill_bg : R.drawable.mobile_round_button_light);
        subscribe.setTextColor(ContextCompat.getColor(this, subscribed ? R.color.mobile_text : R.color.mobile_text_on_light));
        subscribe.setVisibility(mPresenter.getChannelId() != null ? View.VISIBLE : View.GONE);
    }

    private void toggleSubscribe() {
        String channelId = mPresenter.getChannel() != null && mPresenter.getChannel().channelId != null ?
                mPresenter.getChannel().channelId : mPresenter.getChannelId();

        if (channelId == null) {
            return;
        }

        boolean subscribed = mIsSubscribed != null && mIsSubscribed;
        RxHelper.execute(subscribed ?
                YouTubeServiceManager.instance().getMediaItemService().unsubscribeObserve(channelId) :
                YouTubeServiceManager.instance().getMediaItemService().subscribeObserve(channelId));
        mIsSubscribed = !subscribed;

        if (mPresenter.getChannel() != null) {
            mPresenter.getChannel().isSubscribed = mIsSubscribed;
        }

        MessageHelpers.showMessage(this, mIsSubscribed ? R.string.mobile_subscribed : R.string.mobile_subscribe);
        bindHeader();
    }

    private void rebuildTabs() {
        mTabs.removeAllViews();
        addTab(TAB_HOME, getString(R.string.mobile_channel_home));

        for (MobileFeed.Block block : mFeed.getBlocks()) {
            if (block.videos.isEmpty()) {
                continue;
            }

            String title = block.id == GROUP_SEARCH ? getString(R.string.mobile_search_results) :
                    block.id == GROUP_SORT ? getString(R.string.mobile_sort) : block.title;

            if (!TextUtils.isEmpty(title)) {
                addTab(block.id, title);
            }
        }
    }

    private void addTab(int id, String title) {
        boolean selected = id == mSelectedTab;
        TextView tab = new TextView(this);
        tab.setText(title);
        tab.setTextSize(15);
        tab.setSingleLine(true);
        tab.setGravity(android.view.Gravity.CENTER);
        tab.setPadding(dp(12), 0, dp(12), 0);
        tab.setTextColor(ContextCompat.getColor(this, selected ? R.color.mobile_text : R.color.mobile_text_secondary));
        tab.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        if (selected) {
            tab.setBackgroundResource(R.drawable.mobile_tab_indicator);
        }
        tab.setOnClickListener(v -> selectTab(id));
        mTabs.addView(tab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void selectTab(int id) {
        mSelectedTab = id;
        rebuild();
    }

    // ------------------------------- Content -------------------------------

    private void rebuild() {
        bindHeader();

        if (mSelectedTab != TAB_HOME && mFeed.findBlock(mSelectedTab) == null) {
            mSelectedTab = TAB_HOME;
        }

        rebuildTabs();

        List<MobileFeedAdapter.Item> items = new ArrayList<>();
        items.add(MobileFeedAdapter.Item.view());

        if (mSelectedTab == TAB_HOME) {
            mFeed.addItems(items, true);
        } else {
            MobileFeed.Block block = mFeed.findBlock(mSelectedTab);
            if (block.isShorts) {
                // A grid of shorts: two per row
                for (int i = 0; i < block.videos.size(); i += 2) {
                    List<Video> pair = new ArrayList<>(block.videos.subList(i, Math.min(i + 2, block.videos.size())));
                    items.add(MobileFeedAdapter.Item.shortsRow(pair, pair.get(0)));
                }
            } else {
                for (Video video : block.videos) {
                    items.add(MobileFeedAdapter.Item.video(video));
                }
            }
        }

        mFeed.setItems(items);
    }

    private void toggleSearch() {
        boolean show = mSearchField.getVisibility() != View.VISIBLE;
        mSearchField.setVisibility(show ? View.VISIBLE : View.GONE);
        mBarTitle.setVisibility(show ? View.GONE : View.VISIBLE);
        findViewById(R.id.mobile_channel_sort).setVisibility(show ? View.GONE : View.VISIBLE);
        findViewById(R.id.mobile_channel_more).setVisibility(show ? View.GONE : View.VISIBLE);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) {
            mSearchField.requestFocus();
            if (imm != null) {
                imm.showSoftInput(mSearchField, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchField.getWindowToken(), 0);
        }
    }

    // ------------------------------- ChannelView -------------------------------

    @Override
    public void update(VideoGroup videoGroup) {
        if (videoGroup == null) {
            return;
        }

        mFeed.apply(videoGroup);

        // Search results and sorting replace the content: show them right away
        if (videoGroup.getId() == GROUP_SEARCH || videoGroup.getId() == GROUP_SORT) {
            mSelectedTab = videoGroup.getId();
        }

        rebuild();
    }

    @Override
    public void setPosition(int index) {
        // The selected tab is shown from the top
    }

    @Override
    public void showProgressBar(boolean show) {
        mProgress.setVisibility(show && mFeed.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void clear() {
        mFeed.clear();
        mSelectedTab = TAB_HOME;
        mIsSubscribed = null;
        rebuild();
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
        if (item.onClick != null) {
            item.onClick.run();
        }
    }

    @Override
    public void onSwitchAccountClicked() {
    }

    @Override
    public void onSettingsClicked() {
    }

    @Override
    public Account getAccount() {
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
