package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.res.Resources;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile build: the videos of a screen (shelves of {@link VideoGroup}) and the list that shows them.<br/>
 * Used by the search, watch and channel screens.
 */
public class MobileFeed {
    private static final int LOAD_MORE_THRESHOLD = 4;
    private final List<Block> mBlocks = new ArrayList<>();
    private final RecyclerView mList;
    private final MobileFeedAdapter mAdapter;
    private final GridLayoutManager mLayoutManager;
    private final LoadMoreListener mLoadMoreListener;
    private Video mLastLoadMoreVideo;

    public interface LoadMoreListener {
        boolean isLoading();
        void onLoadMore(Video lastVideo);
    }

    /**
     * Videos of one group (shelf)
     */
    public static class Block {
        public final int id;
        public final String title;
        public final boolean isShorts;
        public final List<Video> videos = new ArrayList<>();

        Block(VideoGroup group) {
            id = group.getId();
            title = group.getTitle();
            isShorts = group.isShorts();
        }
    }

    public MobileFeed(RecyclerView list, MobileFeedAdapter adapter, LoadMoreListener loadMoreListener) {
        mList = list;
        mAdapter = adapter;
        mLoadMoreListener = loadMoreListener;

        // Phones in portrait: one card per row. Landscape and tablets: a grid, like the YouTube app.
        mLayoutManager = new GridLayoutManager(list.getContext(), getSpanCount());
        mLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return mAdapter.getItemViewType(position) == MobileFeedAdapter.TYPE_VIDEO ? 1 : mLayoutManager.getSpanCount();
            }
        });
        list.setLayoutManager(mLayoutManager);
        list.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) {
                updateSpanCount();
            }
        });
        // Gutters between grid cards (none on a single column)
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                if (mLayoutManager.getSpanCount() > 1 && position != RecyclerView.NO_POSITION
                        && mAdapter.getItemViewType(position) == MobileFeedAdapter.TYPE_VIDEO) {
                    outRect.left = dp(8);
                    outRect.right = dp(8);
                }
            }
        });
        list.setAdapter(adapter);
        list.setHasFixedSize(true);
        list.setItemViewCacheSize(8); // scrolling back up doesn't rebind the last cards
        if (list.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            // Updated cards don't blink
            ((androidx.recyclerview.widget.SimpleItemAnimator) list.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkLoadMore();
            }
        });
    }

    public List<Block> getBlocks() {
        return mBlocks;
    }

    public boolean isEmpty() {
        for (Block block : mBlocks) {
            if (!block.videos.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public void clear() {
        mBlocks.clear();
        mLastLoadMoreVideo = null;
    }

    /**
     * Applies the group from a presenter (append, replace, remove, sync...)
     */
    public void apply(VideoGroup group) {
        if (group == null) {
            return;
        }

        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            if (group.getPosition() == -1) {
                clear();
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

    public void removeBlock(int id) {
        Block block = findBlock(id);

        if (block != null) {
            mBlocks.remove(block);
        }
    }

    public Block findBlock(int id) {
        for (Block block : mBlocks) {
            if (block.id == id) {
                return block;
            }
        }

        return null;
    }

    /**
     * Adds the feed items of the blocks: a header (when needed), then a shorts row or the video cards
     */
    public void addItems(List<MobileFeedAdapter.Item> items, boolean showTitles) {
        for (Block block : mBlocks) {
            if (block.videos.isEmpty()) {
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
    }

    public void setItems(List<MobileFeedAdapter.Item> items) {
        mAdapter.setItems(items);
        mList.post(this::checkLoadMore); // the page could be shorter than the screen
    }

    public void checkLoadMore() {
        int count = mAdapter.getItemCount();

        if (count == 0 || mLoadMoreListener == null) {
            return;
        }

        if (mLayoutManager.findLastVisibleItemPosition() < count - LOAD_MORE_THRESHOLD) {
            return;
        }

        Video last = null;
        for (int i = count - 1; i >= 0 && last == null; i--) {
            last = mAdapter.getItems().get(i).getLastVideo();
        }

        if (last != null && last != mLastLoadMoreVideo && !mLoadMoreListener.isLoading()) {
            mLastLoadMoreVideo = last;
            mLoadMoreListener.onLoadMore(last);
        }
    }

    private int getSpanCount() {
        // The list width follows rotation and split screen (the activity configuration doesn't, see MotherActivity)
        int width = mList != null && mList.getWidth() > 0 ? mList.getWidth() : Resources.getSystem().getDisplayMetrics().widthPixels;
        return Math.max(1, Math.min(4, width / dp(340)));
    }

    private void updateSpanCount() {
        int spanCount = getSpanCount();

        if (mLayoutManager.getSpanCount() != spanCount) {
            mLayoutManager.setSpanCount(spanCount);
            mList.post(mAdapter::notifyDataSetChanged); // card sizes depend on the width
        }
    }

    private static int dp(int value) {
        return Math.round(value * Resources.getSystem().getDisplayMetrics().density);
    }
}
