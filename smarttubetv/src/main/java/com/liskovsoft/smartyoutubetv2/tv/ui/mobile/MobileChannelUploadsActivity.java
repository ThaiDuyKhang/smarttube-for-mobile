package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile build: the uploads of one channel or a playlist (e.g. opened from the subscriptions channel list)
 */
public class MobileChannelUploadsActivity extends MobileActivity implements ChannelUploadsView, MobileFeedAdapter.Callback {
    private ChannelUploadsPresenter mPresenter;
    private MobileFeed mFeed;
    private View mProgress;
    private TextView mTitle;
    private boolean mIsCreated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_activity_channel);

        mProgress = findViewById(R.id.mobile_channel_progress);
        mTitle = findViewById(R.id.mobile_channel_bar_title);
        findViewById(R.id.mobile_channel_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.mobile_channel_search).setVisibility(View.GONE);
        findViewById(R.id.mobile_channel_sort).setVisibility(View.GONE);
        findViewById(R.id.mobile_channel_more).setVisibility(View.GONE);

        RecyclerView list = findViewById(R.id.mobile_channel_list);
        mFeed = new MobileFeed(list, new MobileFeedAdapter(this), new MobileFeed.LoadMoreListener() {
            @Override
            public boolean isLoading() {
                return mPresenter.hasPendingActions();
            }

            @Override
            public void onLoadMore(Video lastVideo) {
                mPresenter.onScrollEnd(lastVideo);
            }
        });

        mPresenter = ChannelUploadsPresenter.instance(this);
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

    private void rebuild() {
        Video channel = mPresenter.getChannel();
        String title = channel != null ? (!TextUtils.isEmpty(channel.getAuthor()) && channel.isChannel() ? channel.getAuthor() : channel.getTitle()) : null;
        mTitle.setText(title);

        List<MobileFeedAdapter.Item> items = new ArrayList<>();
        mFeed.addItems(items, false);
        mFeed.setItems(items);
    }

    @Override
    public void update(VideoGroup videoGroup) {
        mFeed.apply(videoGroup);
        rebuild();
    }

    @Override
    public void showProgressBar(boolean show) {
        mProgress.setVisibility(show && mFeed.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void clear() {
        mFeed.clear();
        rebuild();
    }

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
}
