package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngine;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.BackboneQueueNavigator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mobile build: watch page like the YouTube app.<br/>
 * Player on top with touch controls, then the details (title, channel, like, share, save...) and the related videos.<br/>
 * Full screen in landscape, Shorts as a full screen vertical player (swipe for the next one).<br/>
 * Same player engine and presenters as the TV player ({@link ExoPlayerController}, {@link PlaybackPresenter}).
 */
public class MobilePlaybackActivity extends MobileActivity implements PlaybackView, MobileFeedAdapter.Callback {
    private static final String TAG = MobilePlaybackActivity.class.getSimpleName();
    private static final String SELECTED_VIDEO_ID = "SelectedVideoId";
    private static final int CONTROLS_HIDE_MS = 3_000;
    private static final int SEEK_STEP_MS = 10_000;
    private static final int PROGRESS_UPDATE_MS = 250;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SparseIntArray mButtonStates = new SparseIntArray();
    private PlaybackPresenter mPresenter;
    private ExoPlayerController mExoPlayerController;
    private ExoPlayerInitializer mPlayerInitializer;
    private SimpleExoPlayer mPlayer;
    private MediaSessionCompat mMediaSession;
    private MediaSessionConnector mMediaSessionConnector;
    private SubtitleManager mSubtitleManager;
    private boolean mIsEngineBlocked;
    private String mSelectedVideoId;

    private View mRoot;
    private View mPlayerContainer;
    private PlayerView mPlayerView;
    private ImageView mThumb;
    private View mBuffering;
    private View mControls;
    private TextView mControlsTitle;
    private ImageButton mPlayPause;
    private TextView mTime;
    private ImageButton mFullscreenButton;
    private View mFullscreenActionsScroll;
    private LinearLayout mFullscreenActions;
    private MobileTimeBar mTimeBar;
    private TextView mSeekHint;
    private View mShortsOverlay;
    private RecyclerView mList;
    private MobileFeedAdapter mAdapter;
    private MobileFeed mFeed;
    private View mDetails;

    private boolean mIsFullscreen;
    private boolean mIsShorts;
    private boolean mIsControlsShown;
    private int mResizeMode = PlayerEngine.RESIZE_MODE_DEFAULT;
    private int mLastWidth;
    private int mLastHeight;
    private boolean mIsDescriptionShown;
    private boolean mIsAutoplayPending;
    private boolean mIsPausedByUser;

    private final Runnable mHideControls = () -> setControlsShown(false);
    private final Runnable mUpdateProgress = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            mHandler.postDelayed(this, PROGRESS_UPDATE_MS);
        }
    };
    private final Runnable mHideSeekHint = () -> mSeekHint.setVisibility(View.GONE);

    // ------------------------------- Lifecycle -------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_activity_playback);

        mSelectedVideoId = savedInstanceState != null ? savedInstanceState.getString(SELECTED_VIDEO_ID, null) : null;

        initViews();

        mPlayerInitializer = new ExoPlayerInitializer(this);
        mPresenter = PlaybackPresenter.instance(this);
        mPresenter.setView(this);
        mExoPlayerController = new ExoPlayerController(this, mPresenter);

        // Fix open previous video
        if (mPresenter.getVideo() != null) {
            mSelectedVideoId = null;
        }

        applyBackgroundPlayDefault();
        mPresenter.onViewInitialized();

        if (mSelectedVideoId != null) {
            mPresenter.openVideo(mSelectedVideoId);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Store position in case activity is crashed
        outState.putString(SELECTED_VIDEO_ID, getVideo() != null ? getVideo().videoId : null);
    }

    // NOTE: depending of SDK version Start/Stop may be called with delay (SDK_INT > 23) or not called at all (PIP/Dialogs)!

    @Override
    protected void onStart() {
        super.onStart();

        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Util.SDK_INT <= 23 || mPlayer == null) {
            initializePlayer();
        }

        // NOTE: don't move this into another place! Multiple components rely on it.
        mPresenter.onViewResumed();

        blockEngine(false); // reset bg mode
        mHandler.post(mUpdateProgress);
        applyLayout();
    }

    @Override
    protected void onPause() {
        boolean hasDialogBug = AppDialogPresenter.instance(this).isDialogShown() && Build.VERSION.SDK_INT <= 23;
        boolean isScreenOff = getPlayerData().getBackgroundMode() != PlayerData.BACKGROUND_MODE_DEFAULT && Utils.isHardScreenOff(this);

        if (hasDialogBug || isScreenOff) {
            blockEngine(true);
        }

        super.onPause();

        // NOTE: don't move this into another place! Multiple components rely on it.
        mPresenter.onViewPaused();

        if (Util.SDK_INT <= 23) {
            maybeReleasePlayer();
        }

        mHandler.removeCallbacks(mUpdateProgress);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (Util.SDK_INT > 23) {
            maybeReleasePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mHandler.removeCallbacksAndMessages(null);

        // Fix situations when engine didn't properly destroyed. E.g. after closing dialogs.
        releasePlayer();

        if (mPresenter.getView() == this) {
            mPresenter.onViewDestroyed();
        }
    }

    // ------------------------------- Back, PIP, background playback (like the TV player) -------------------------------

    @Override
    public void onBackPressed() {
        // Full screen on a phone: back to the portrait page first
        if (mIsFullscreen && !mIsShorts) {
            exitFullscreen();
            return;
        }

        super.onBackPressed();
    }

    /**
     * BACK pressed, minimize button pressed
     */
    @Override
    public void finish() {
        Log.d(TAG, "Finishing activity...");

        if (!skipPip()) {
            enterPipMode(); // NOTE: without this call app will hangs when pressing on PIP button
        }

        if (doNotDestroy() && !skipPip()) {
            blockEngine(true);
            getViewManager().blockTop(this);
            getViewManager().startParentView(this);
        } else {
            if (getPlayerTweaksData().isKeepFinishedActivityEnabled()) {
                getViewManager().startParentView(this);

                maybeReleasePlayer();
            } else {
                super.finish();
            }
        }
    }

    @Override
    public void finishReally() {
        super.finishReally();
        MobilePlaybackService.stop(this);

        if (Util.SDK_INT > 23) {
            maybeReleasePlayer();
        }

        mPresenter.onFinish();
    }

    /**
     * HOME or BACK pressed
     */
    @Override
    public void onUserLeaveHint() {
        if (isBackPressed() || isFinishing() || getViewManager().isNewViewPending() || getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK) {
            return;
        }

        switch (getPlayerData().getBackgroundMode()) {
            case PlayerData.BACKGROUND_MODE_PIP:
                enterPipMode();
                if (doNotDestroy()) {
                    blockEngine(true);
                    getViewManager().blockTop(this);
                }
                break;
            case PlayerData.BACKGROUND_MODE_SOUND:
            case PlayerData.BACKGROUND_MODE_PLAY_BEHIND:
                if (doNotDestroy()) {
                    blockEngine(true);
                    getViewManager().blockTop(this);
                }
                break;
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        // PIP window: the video only
        showControls(false);
        applyLayout();
    }

    @TargetApi(24)
    private void enterPipMode() {
        if (Helpers.isPictureInPictureSupported(this) && wannaEnterToPip()) {
            Log.d(TAG, "Entering PIP mode...");

            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
                } else {
                    enterPictureInPictureMode();
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public boolean isInPipMode() {
        return Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
    }

    private boolean skipPip() {
        return isBackPressed() && getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME;
    }

    @TargetApi(24)
    private boolean wannaEnterToPip() {
        boolean isPip = getPlayerData().getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP || isEngineBlocked();
        return isPip && !isInPictureInPictureMode();
    }

    private boolean doNotDestroy() {
        sIsInPipMode = isInPipMode();
        boolean isBackground = getPlayerData().getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_SOUND || isEngineBlocked();
        return sIsInPipMode || isBackground;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Hardware keys, remote controls, keyboards
        switch (keyCode) {
            case KeyEvent.KEYCODE_SPACE:
                togglePlayback();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                seekBy(-SEEK_STEP_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seekBy(SEEK_STEP_MS);
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------- Views -------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        mRoot = findViewById(R.id.mobile_watch_root);
        mPlayerContainer = findViewById(R.id.mobile_player);
        mPlayerView = findViewById(R.id.mobile_video);
        mThumb = findViewById(R.id.mobile_player_thumb);
        mBuffering = findViewById(R.id.mobile_player_buffering);
        mControls = findViewById(R.id.mobile_controls);
        mControlsTitle = findViewById(R.id.mobile_controls_title);
        mPlayPause = findViewById(R.id.mobile_play_pause);
        mTime = findViewById(R.id.mobile_time);
        mFullscreenButton = findViewById(R.id.mobile_fullscreen);
        mFullscreenActionsScroll = findViewById(R.id.mobile_fullscreen_actions_scroll);
        mFullscreenActions = findViewById(R.id.mobile_fullscreen_actions);
        mTimeBar = findViewById(R.id.mobile_time_bar);
        mSeekHint = findViewById(R.id.mobile_seek_hint);
        mShortsOverlay = findViewById(R.id.mobile_shorts_overlay);
        mList = findViewById(R.id.mobile_watch_list);

        mPlayerView.setKeepContentOnPlayerReset(false);

        // Player gestures: tap - controls, double tap - seek, swipe - next short / exit full screen
        GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mIsShorts) {
                    boolean isLoading = mPlayer != null && mPlayer.getPlaybackState() != Player.STATE_READY && mPlayer.getPlaybackState() != Player.STATE_ENDED;
                    if (isLoading) {
                        setPlayWhenReady(true); // still loading: keep it playing
                    } else {
                        togglePlayback();
                        showSeekHint(getString(getPlayWhenReady() ? R.string.mobile_play : R.string.mobile_pause), true);
                    }
                } else {
                    showControls(!mIsControlsShown);
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (mIsShorts) {
                    return false;
                }
                boolean forward = e.getX() > mPlayerContainer.getWidth() / 2f;
                seekBy(forward ? SEEK_STEP_MS : -SEEK_STEP_MS);
                showSeekHint(getString(forward ? R.string.mobile_seek_forward : R.string.mobile_seek_back), !forward);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) < Math.abs(dx) * 1.5f || Math.abs(dy) < dp(48)) {
                    return false;
                }

                if (mIsShorts) {
                    if (dy < 0) {
                        mPresenter.onNextClicked();
                    } else {
                        mPresenter.onPreviousClicked();
                    }
                    return true;
                }

                if (dy > 0) {
                    // Swipe down: leave full screen, or minimize the page
                    if (mIsFullscreen) {
                        exitFullscreen();
                    } else {
                        onBackPressed();
                    }
                } else if (!mIsFullscreen) {
                    enterFullscreen();
                }
                return true;
            }
        });
        mPlayerContainer.setOnTouchListener((v, event) -> gestures.onTouchEvent(event));

        findViewById(R.id.mobile_minimize).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.mobile_subtitles).setOnClickListener(v -> clickButton(R.id.lb_control_closed_captioning));
        findViewById(R.id.mobile_subtitles).setOnLongClickListener(v -> longClickButton(R.id.lb_control_closed_captioning));
        findViewById(R.id.mobile_player_settings).setOnClickListener(v -> clickButton(R.id.lb_control_high_quality));
        findViewById(R.id.mobile_previous).setOnClickListener(v -> {
            mPresenter.onPreviousClicked();
            scheduleHideControls();
        });
        findViewById(R.id.mobile_next).setOnClickListener(v -> {
            mPresenter.onNextClicked();
            scheduleHideControls();
        });
        mPlayPause.setOnClickListener(v -> {
            togglePlayback();
            scheduleHideControls();
        });
        mFullscreenButton.setOnClickListener(v -> {
            if (mIsFullscreen) {
                exitFullscreen();
            } else {
                enterFullscreen();
            }
        });

        mTimeBar.setListener(new MobileTimeBar.Listener() {
            @Override
            public void onScrubStart() {
                mHandler.removeCallbacks(mHideControls);
                if (!mIsShorts) {
                    showControls(true);
                }
            }

            @Override
            public void onScrubMove(long positionMs) {
                mTime.setText(String.format("%s / %s", formatTime(positionMs), formatTime(getDurationMs())));
            }

            @Override
            public void onScrubStop(long positionMs) {
                setPositionMs(positionMs);
                mPresenter.onSeekPositionChanged(positionMs);
                scheduleHideControls();
            }
        });

        // Shorts overlay
        findViewById(R.id.mobile_shorts_like).setOnClickListener(v -> clickButton(R.id.action_thumbs_up));
        findViewById(R.id.mobile_shorts_dislike).setOnClickListener(v -> clickButton(R.id.action_thumbs_down));
        findViewById(R.id.mobile_shorts_comments).setOnClickListener(v -> clickButton(R.id.action_chat));
        findViewById(R.id.mobile_shorts_share).setOnClickListener(v -> clickButton(R.id.action_share));
        findViewById(R.id.mobile_shorts_more).setOnClickListener(v -> showVideoMenu());
        findViewById(R.id.mobile_shorts_channel).setOnClickListener(v -> clickButton(R.id.action_channel));

        // Details and related videos
        mDetails = getLayoutInflater().inflate(R.layout.mobile_watch_details, (ViewGroup) mRoot, false); // the list has no layout manager yet
        mDetails.findViewById(R.id.mobile_watch_info).setOnClickListener(v -> {
            mIsDescriptionShown = !mIsDescriptionShown;
            bindDetails();
        });
        mDetails.findViewById(R.id.mobile_watch_channel).setOnClickListener(v -> clickButton(R.id.action_channel));
        mDetails.findViewById(R.id.mobile_watch_subscribe).setOnClickListener(v -> clickButton(R.id.action_subscribe));
        mDetails.findViewById(R.id.mobile_watch_subscribe).setOnLongClickListener(v -> longClickButton(R.id.action_subscribe));
        mDetails.findViewById(R.id.mobile_watch_like).setOnClickListener(v -> clickButton(R.id.action_thumbs_up));
        mDetails.findViewById(R.id.mobile_watch_dislike).setOnClickListener(v -> clickButton(R.id.action_thumbs_down));
        mDetails.findViewById(R.id.mobile_watch_share).setOnClickListener(v -> clickButton(R.id.action_share));
        mDetails.findViewById(R.id.mobile_watch_save).setOnClickListener(v -> clickButton(R.id.action_playlist_add));
        mDetails.findViewById(R.id.mobile_watch_speed).setOnClickListener(v -> clickButton(R.id.action_video_speed));
        mDetails.findViewById(R.id.mobile_watch_quality).setOnClickListener(v -> clickButton(R.id.lb_control_high_quality));
        mDetails.findViewById(R.id.mobile_watch_repeat).setOnClickListener(v -> clickButton(R.id.action_repeat));
        mDetails.findViewById(R.id.mobile_watch_repeat).setOnLongClickListener(v -> longClickButton(R.id.action_repeat));
        mDetails.findViewById(R.id.mobile_watch_pip).setOnClickListener(v -> clickButton(R.id.action_pip));
        mDetails.findViewById(R.id.mobile_watch_pip).setVisibility(Helpers.isPictureInPictureSupported(this) ? View.VISIBLE : View.GONE);
        mDetails.findViewById(R.id.mobile_watch_background).setOnClickListener(v -> toggleBackgroundPlay());
        mDetails.findViewById(R.id.mobile_watch_comments).setOnClickListener(v -> clickButton(R.id.action_chat));

        mAdapter = new MobileFeedAdapter(this);
        mAdapter.setCustomView(mDetails);
        mFeed = new MobileFeed(mList, mAdapter, new MobileFeed.LoadMoreListener() {
            @Override
            public boolean isLoading() {
                return false; // MobileFeed asks once per last video
            }

            @Override
            public void onLoadMore(Video lastVideo) {
                mPresenter.onScrollEnd(lastVideo);
            }
        });

        initFullscreenActions();

        // Rotation, split screen, PIP: choose the layout from the real size
        mRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != mLastWidth || b - t != mLastHeight) {
                mLastWidth = r - l;
                mLastHeight = b - t;
                v.post(this::applyLayout);
            }
        });

        rebuildList();
    }

    private void initFullscreenActions() {
        addFullscreenAction(R.string.mobile_like, R.id.action_thumbs_up);
        addFullscreenAction(R.string.mobile_comments, R.id.action_chat);
        addFullscreenAction(R.string.mobile_save, R.id.action_playlist_add);
        addFullscreenAction(R.string.mobile_speed, R.id.action_video_speed);
        addFullscreenAction(R.string.mobile_quality, R.id.lb_control_high_quality);
        addFullscreenAction(R.string.mobile_share, R.id.action_share);
    }

    private void addFullscreenAction(int titleResId, int buttonId) {
        TextView action = new TextView(this);
        action.setText(titleResId);
        action.setTextColor(0xFFFFFFFF);
        action.setTextSize(13);
        action.setGravity(android.view.Gravity.CENTER);
        action.setPadding(dp(14), 0, dp(14), 0);
        action.setBackgroundResource(R.drawable.mobile_pill_translucent_bg);
        action.setOnClickListener(v -> clickButton(buttonId));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMarginEnd(dp(8));
        mFullscreenActions.addView(action, lp);
    }

    // ------------------------------- Layout: portrait page, full screen, shorts -------------------------------

    private void enterFullscreen() {
        mIsFullscreen = true;
        if (isPhone()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        applyLayout();
    }

    private void exitFullscreen() {
        mIsFullscreen = false;
        if (isPhone()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        applyLayout();
    }

    private boolean isPhone() {
        android.util.DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        return Math.min(metrics.widthPixels, metrics.heightPixels) / metrics.density < 600;
    }

    private boolean isLandscape() {
        int width = mRoot.getWidth() > 0 ? mRoot.getWidth() : Resources.getSystem().getDisplayMetrics().widthPixels;
        int height = mRoot.getHeight() > 0 ? mRoot.getHeight() : Resources.getSystem().getDisplayMetrics().heightPixels;
        return width > height;
    }

    private void applyLayout() {
        if (mRoot == null) {
            return;
        }

        boolean isPip = isInPipMode();

        // Phone in landscape: always full screen. Tablet: only on request.
        if (isLandscape() && isPhone()) {
            mIsFullscreen = true;
        } else if (!isLandscape() && isPhone() && getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            mIsFullscreen = false;
        }

        boolean playerOnly = mIsFullscreen || mIsShorts || isPip;

        ViewGroup.LayoutParams lp = mPlayerContainer.getLayoutParams();
        int width = mRoot.getWidth() > 0 ? mRoot.getWidth() : Resources.getSystem().getDisplayMetrics().widthPixels;
        int height = playerOnly ? ViewGroup.LayoutParams.MATCH_PARENT : Math.min(width * 9 / 16, (int) (mRoot.getHeight() * 0.6f));
        if (height == 0) {
            height = width * 9 / 16;
        }
        if (lp.height != height) {
            lp.height = height;
            mPlayerContainer.setLayoutParams(lp);
        }

        mList.setVisibility(playerOnly ? View.GONE : View.VISIBLE);
        mShortsOverlay.setVisibility(mIsShorts && !isPip ? View.VISIBLE : View.GONE);
        mControlsTitle.setVisibility(mIsFullscreen ? View.VISIBLE : View.INVISIBLE);
        mFullscreenActionsScroll.setVisibility(mIsFullscreen && !mIsShorts ? View.VISIBLE : View.GONE);
        mFullscreenButton.setImageResource(mIsFullscreen ? R.drawable.mobile_ic_fullscreen_exit : R.drawable.mobile_ic_fullscreen);
        mFullscreenButton.setContentDescription(getString(mIsFullscreen ? R.string.mobile_exit_fullscreen : R.string.mobile_fullscreen));
        mTimeBar.setVisibility(isPip ? View.GONE : View.VISIBLE);
        // Shorts fill the screen like the YouTube app (vertical video on a tall screen)
        setPlayerResizeMode(mIsShorts ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : mResizeMode);
        mThumb.setScaleType(mIsShorts ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);

        // Time bar: above the full screen actions or at the bottom
        ViewGroup.MarginLayoutParams barLp = (ViewGroup.MarginLayoutParams) mTimeBar.getLayoutParams();
        int barMargin = mIsFullscreen && !mIsShorts && mIsControlsShown ? dp(66) : (mIsFullscreen && !mIsShorts && mIsControlsShown ? dp(16) : 0);
        if (mIsFullscreen && !mIsShorts) {
            barMargin = mIsControlsShown ? dp(66) : 0;
        }
        if (barLp.bottomMargin != barMargin) {
            barLp.bottomMargin = barMargin;
            mTimeBar.setLayoutParams(barLp);
        }
        // Full screen: the bar is hidden with the controls, like the YouTube app
        mTimeBar.setAlpha(mIsFullscreen && !mIsShorts && !mIsControlsShown ? 0 : 1);

        applySystemBars(playerOnly || isPip);
    }

    @SuppressLint("WrongConstant") // the modes of PlayerConstants are the AspectRatioFrameLayout ones
    private void setPlayerResizeMode(int mode) {
        mPlayerView.setResizeMode(mode);
    }

    @SuppressWarnings("deprecation")
    private void applySystemBars(boolean hide) {
        int flags = hide ? View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : 0;

        // Shorts: keep the status bar like the YouTube app
        if (hide && mIsShorts && !mIsFullscreen) {
            flags = 0;
        }

        if (getWindow().getDecorView().getSystemUiVisibility() != flags) {
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    // ------------------------------- Background play (screen off, other apps) -------------------------------

    /**
     * Phones: play the sound with the screen off by default (once, the user can turn it off on the watch page)
     */
    private void applyBackgroundPlayDefault() {
        android.content.SharedPreferences prefs = getSharedPreferences("mobile_ui", MODE_PRIVATE);

        if (!prefs.getBoolean("background_play_default", false)) {
            if (getPlayerData().getBackgroundMode() == PlayerData.BACKGROUND_MODE_DEFAULT) {
                getPlayerData().setBackgroundMode(PlayerData.BACKGROUND_MODE_SOUND);
            }
            prefs.edit().putBoolean("background_play_default", true).apply();
        }

        requestNotificationPermission();
    }

    /**
     * Android 13+: the lock screen controls of the background play need the notifications permission
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && isBackgroundPlayEnabled()
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.content.SharedPreferences prefs = getSharedPreferences("mobile_ui", MODE_PRIVATE);
            if (!prefs.getBoolean("notification_permission_asked", false)) {
                prefs.edit().putBoolean("notification_permission_asked", true).apply();
                requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 1002);
            }
        }
    }

    private boolean isBackgroundPlayEnabled() {
        return getPlayerData().getBackgroundMode() != PlayerData.BACKGROUND_MODE_DEFAULT;
    }

    private void toggleBackgroundPlay() {
        boolean enable = !isBackgroundPlayEnabled();
        getPlayerData().setBackgroundMode(enable ? PlayerData.BACKGROUND_MODE_SOUND : PlayerData.BACKGROUND_MODE_DEFAULT);
        android.widget.Toast.makeText(this, enable ? R.string.mobile_background_play_enabled : R.string.mobile_background_play_disabled,
                android.widget.Toast.LENGTH_SHORT).show();
        bindDetails();
        updatePlaybackService();
    }

    /**
     * Lock screen and notification controls while a video plays with background play on
     */
    private void updatePlaybackService() {
        if (mPlayer == null || !isBackgroundPlayEnabled() || !containsMedia()) {
            MobilePlaybackService.stop(this);
            return;
        }

        Video video = getVideo();

        if (!getPlayWhenReady() && !isEngineBlocked() && !isFinishing()) {
            // Paused while on the screen: no need to keep the app alive
            MobilePlaybackService.stop(this);
            return;
        }

        MobilePlaybackService.update(this, mServiceControls, mMediaSession != null ? mMediaSession.getSessionToken() : null,
                video != null ? video.getTitleFull() : null, video != null ? video.getAuthor() : null, getPlayWhenReady());
    }

    private final MobilePlaybackService.Controls mServiceControls = new MobilePlaybackService.Controls() {
        @Override
        public void onPlayPause() {
            togglePlayback();
            updatePlaybackService();
        }

        @Override
        public void onNext() {
            mPresenter.onNextClicked();
        }

        @Override
        public void onPrevious() {
            mPresenter.onPreviousClicked();
        }

        @Override
        public void onStop() {
            if (mPlayer != null && getPlayWhenReady()) {
                setPlayWhenReady(false);
                mPresenter.onPauseClicked();
            }
            MobilePlaybackService.stop(MobilePlaybackActivity.this);
        }
    };

    // ------------------------------- Controls -------------------------------

    private void setControlsShown(boolean show) {
        mHandler.removeCallbacks(mHideControls);

        if (mIsShorts || isInPipMode()) {
            show = false;
        }

        boolean changed = mIsControlsShown != show;
        mIsControlsShown = show;
        mControls.setVisibility(show ? View.VISIBLE : View.GONE);
        mTimeBar.setThumbShown(show);

        if (show) {
            updatePlayPause();
            updateProgress();
            scheduleHideControls();
        }

        if (changed) {
            applyLayout();
            mPresenter.onControlsShown(show);
        }
    }

    private void scheduleHideControls() {
        mHandler.removeCallbacks(mHideControls);

        if (mIsControlsShown && isPlaying()) {
            mHandler.postDelayed(mHideControls, CONTROLS_HIDE_MS);
        }
    }

    private void togglePlayback() {
        if (mPlayer == null) {
            return;
        }

        if (getPlayWhenReady()) {
            mIsPausedByUser = true;
            setPlayWhenReady(false);
            mPresenter.onPauseClicked();
        } else {
            if (mPlayer.getPlaybackState() == Player.STATE_ENDED) {
                setPositionMs(0);
            }
            setPlayWhenReady(true);
            mPresenter.onPlayClicked();
        }

        updatePlayPause();
    }

    private void seekBy(long deltaMs) {
        if (mPlayer == null) {
            return;
        }

        long duration = getDurationMs();
        long position = Math.max(0, getPositionMs() + deltaMs);
        if (duration > 0) {
            position = Math.min(position, duration);
        }
        setPositionMs(position);
        mPresenter.onSeekPositionChanged(position);
        updateProgress();
    }

    private void showSeekHint(String text, boolean atStart) {
        mSeekHint.setText(text);
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) mSeekHint.getLayoutParams();
        lp.gravity = android.view.Gravity.CENTER_VERTICAL | (mIsShorts ? android.view.Gravity.CENTER_HORIZONTAL : (atStart ? android.view.Gravity.START : android.view.Gravity.END));
        lp.setMarginStart(dp(48));
        lp.setMarginEnd(dp(48));
        mSeekHint.setLayoutParams(lp);
        mSeekHint.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideSeekHint);
        mHandler.postDelayed(mHideSeekHint, 700);
    }

    private void updatePlayPause() {
        boolean ended = mPlayer != null && mPlayer.getPlaybackState() == Player.STATE_ENDED;
        boolean playing = getPlayWhenReady() && !ended;
        mPlayPause.setImageResource(ended ? R.drawable.mobile_ic_replay_big : playing ? R.drawable.mobile_ic_pause_big : R.drawable.mobile_ic_play_arrow_big);
        mPlayPause.setContentDescription(getString(playing ? R.string.mobile_pause : R.string.mobile_play));
    }

    private void updateProgress() {
        if (mPlayer == null) {
            mTimeBar.setProgress(0, 0, 0);
            return;
        }

        long duration = Math.max(0, getDurationMs());
        long position = Math.max(0, getPositionMs());
        long buffered = mPlayer.getBufferedPosition();

        if (!mTimeBar.isScrubbing()) {
            mTimeBar.setProgress(position, buffered, duration);

            if (mIsControlsShown) {
                Video video = getVideo();
                boolean isLive = video != null && video.isLive;
                mTime.setText(isLive ? getString(R.string.mobile_live_badge) : String.format("%s / %s", formatTime(position), formatTime(duration)));
            }
        }
    }

    private static String formatTime(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        return hours > 0 ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds) : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void clickButton(int buttonId) {
        // These need an account: explain it and offer to sign in (instead of "nothing here")
        boolean needsAccount = buttonId == R.id.action_thumbs_up || buttonId == R.id.action_thumbs_down
                || buttonId == R.id.action_playlist_add || buttonId == R.id.action_subscribe;

        if (needsAccount && !MobileUi.isSignedIn()) {
            MobileUi.requireSignIn(this, () -> { });
            return;
        }

        mPresenter.onButtonClicked(buttonId, getButtonState(buttonId));
        scheduleHideControls();
    }

    private boolean longClickButton(int buttonId) {
        mPresenter.onButtonLongClicked(buttonId, getButtonState(buttonId));
        return true;
    }

    private void showVideoMenu() {
        if (getVideo() != null) {
            VideoMenuPresenter.instance(this).showMenu(getVideo());
        }
    }

    // ------------------------------- Details and related videos -------------------------------

    private void bindDetails() {
        Video video = getVideo();

        if (mDetails == null) {
            return;
        }

        TextView title = mDetails.findViewById(R.id.mobile_watch_title);
        TextView meta = mDetails.findViewById(R.id.mobile_watch_meta);
        TextView description = mDetails.findViewById(R.id.mobile_watch_description);
        TextView author = mDetails.findViewById(R.id.mobile_watch_author);
        TextView subscribers = mDetails.findViewById(R.id.mobile_watch_subscribers);
        TextView subscribe = mDetails.findViewById(R.id.mobile_watch_subscribe);
        TextView like = mDetails.findViewById(R.id.mobile_watch_like);
        ImageButton dislike = mDetails.findViewById(R.id.mobile_watch_dislike);

        String titleText = video != null ? video.getTitleFull() : null;
        title.setText(titleText);
        title.setMaxLines(mIsDescriptionShown ? 10 : 2);
        mControlsTitle.setText(titleText);
        ((TextView) findViewById(R.id.mobile_shorts_title)).setText(titleText);

        CharSequence second = video != null ? video.getSecondTitleFull() : null;
        String moreText = getString(mIsDescriptionShown ? R.string.mobile_less : R.string.mobile_more);
        meta.setText(TextUtils.isEmpty(second) ? moreText : TextUtils.concat(second, "  ", moreText));

        boolean hasDescription = video != null && !TextUtils.isEmpty(video.description);
        description.setText(hasDescription ? video.description : null);
        description.setVisibility(mIsDescriptionShown && hasDescription ? View.VISIBLE : View.GONE);

        String authorText = video != null ? video.getAuthor() : null;
        author.setText(authorText);
        ((TextView) findViewById(R.id.mobile_shorts_channel)).setText(authorText != null ? "@" + authorText.replace(" ", "") : null);
        subscribers.setText(video != null ? video.subscriberCount : null);

        int subscribeState = getButtonState(R.id.action_subscribe);
        boolean isSubscribed = subscribeState == PlayerUI.BUTTON_ON;
        subscribe.setVisibility(subscribeState == PlayerUI.BUTTON_DISABLED ? View.GONE : View.VISIBLE);
        subscribe.setText(isSubscribed ? R.string.mobile_subscribed : R.string.mobile_subscribe);
        subscribe.setBackgroundResource(isSubscribed ? R.drawable.mobile_pill_bg : R.drawable.mobile_round_button_light);
        subscribe.setTextColor(ContextCompat.getColor(this, isSubscribed ? R.color.mobile_text : R.color.mobile_text_on_light));

        boolean isLiked = getButtonState(R.id.action_thumbs_up) == PlayerUI.BUTTON_ON;
        boolean isDisliked = getButtonState(R.id.action_thumbs_down) == PlayerUI.BUTTON_ON;
        String likeCount = video != null && !TextUtils.isEmpty(video.likeCount) ? video.likeCount : getString(R.string.mobile_like);
        like.setText(likeCount);
        like.setCompoundDrawablesWithIntrinsicBounds(isLiked ? R.drawable.mobile_ic_like_filled : R.drawable.mobile_ic_like, 0, 0, 0);
        dislike.setImageResource(isDisliked ? R.drawable.mobile_ic_dislike_filled : R.drawable.mobile_ic_dislike);

        TextView shortsLike = findViewById(R.id.mobile_shorts_like);
        shortsLike.setText(likeCount);
        shortsLike.setCompoundDrawablesWithIntrinsicBounds(0, isLiked ? R.drawable.mobile_ic_like_filled : R.drawable.mobile_ic_like, 0, 0);
        ((TextView) findViewById(R.id.mobile_shorts_dislike)).setCompoundDrawablesWithIntrinsicBounds(0,
                isDisliked ? R.drawable.mobile_ic_dislike_filled : R.drawable.mobile_ic_dislike, 0, 0);

        TextView speed = mDetails.findViewById(R.id.mobile_watch_speed);
        float speedValue = mPlayer != null ? getSpeed() : 1.0f;
        speed.setText(String.format("%s %sx", getString(R.string.mobile_speed), Helpers.formatFloat(speedValue)));

        TextView background = mDetails.findViewById(R.id.mobile_watch_background);
        background.setText(isBackgroundPlayEnabled() ? R.string.mobile_background_play_on : R.string.mobile_background_play_off);

        TextView repeat = mDetails.findViewById(R.id.mobile_watch_repeat);
        repeat.setSelected(getButtonState(R.id.action_repeat) == PlayerUI.BUTTON_ON);

        FormatItem videoFormat = mPlayer != null ? getVideoFormat() : null;
        TextView quality = mDetails.findViewById(R.id.mobile_watch_quality);
        String qualityLabel = videoFormat != null && videoFormat.getHeight() > 0 ? videoFormat.getHeight() + "p" : null;
        quality.setText(qualityLabel != null ? String.format("%s %s", getString(R.string.mobile_quality), qualityLabel) : getString(R.string.mobile_quality));
    }

    private void rebuildList() {
        List<MobileFeedAdapter.Item> items = new ArrayList<>();
        items.add(MobileFeedAdapter.Item.view());
        mFeed.addItems(items, mFeed.getBlocks().size() > 1);
        mFeed.setItems(items);
    }

    private void setShorts(boolean isShorts) {
        if (mIsShorts != isShorts) {
            mIsShorts = isShorts;
            showControls(false);
            applyLayout();
        }
    }

    // ------------------------------- Player engine (as in the TV player) -------------------------------

    private void initializePlayer() {
        if (mPlayer != null) {
            Log.d(TAG, "Skip player initialization.");
            return;
        }

        createPlayer();
        createMediaSession();
        createSubtitleManager();

        mPresenter.setView(this); // replaced by the embed player?
        mPresenter.onEngineInitialized();
    }

    private void createPlayer() {
        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mExoPlayerController.setTrackSelector(trackSelector);

        DefaultRenderersFactory renderersFactory = new CustomOverridesRenderersFactory(this);
        mPlayer = mPlayerInitializer.createPlayer(this, renderersFactory, trackSelector);

        mExoPlayerController.setPlayer(mPlayer);
        mExoPlayerController.setPlayerView(new com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView() {
            @Override
            public void setQualityInfo(String info) {
                bindDetails();
            }

            @Override
            public void setVideo(Video video) {
                bindDetails();
            }
        });

        mPlayerView.setPlayer(mPlayer);
        mPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                mBuffering.setVisibility(playbackState == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);

                // A video opened from the app starts right away, like the YouTube app (unless paused by the user)
                if (playbackState == Player.STATE_READY && mIsAutoplayPending) {
                    mIsAutoplayPending = false;
                    if (!playWhenReady && !mIsPausedByUser && !isEngineBlocked()) {
                        setPlayWhenReady(true);
                        playWhenReady = true;
                    }
                }

                if (playbackState == Player.STATE_READY && playWhenReady) {
                    mThumb.setVisibility(View.GONE); // the first frame callback isn't reliable on every device
                }

                updatePlayPause();
                updatePlaybackService();

                if (playbackState == Player.STATE_ENDED) {
                    showControls(true);
                } else if (playWhenReady) {
                    scheduleHideControls();
                } else if (mIsControlsShown) {
                    mHandler.removeCallbacks(mHideControls);
                }
            }
        });
        mPlayer.addVideoListener(new VideoListener() {
            @Override
            public void onRenderedFirstFrame() {
                mThumb.setVisibility(View.GONE);
            }
        });
    }

    private void createMediaSession() {
        if (Build.VERSION.SDK_INT <= 19) {
            // Fix Android 4.4 bug: java.lang.IllegalArgumentException: MediaButtonReceiver component may not be null
            return;
        }

        // NOTE: No way to disable only a notifications. We need to disable the media session instead.
        boolean disableNotifications = getPlayerTweaksData().isPlaybackNotificationsDisabled();
        mMediaSession = new MediaSessionCompat(getApplicationContext(), getPackageName());
        mMediaSession.setActive(!disableNotifications);
        mMediaSessionConnector = new MediaSessionConnector(mMediaSession);

        try {
            mMediaSessionConnector.setPlayer(mPlayer);
        } catch (NoSuchMethodError e) {
            // Android 9, Sony
            return;
        }

        mMediaSessionConnector.setMediaMetadataProvider(player -> {
            Video video = getVideo();

            if (video == null) {
                return null;
            }

            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, video.videoId);
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, video.getTitleFull());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, video.getTitleFull());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, video.getAuthor());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, Helpers.toString(video.getSecondTitleFull()));
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, video.getCardImageUrl());
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDurationMs());

            return metadataBuilder.build();
        });

        mMediaSessionConnector.setQueueNavigator(new BackboneQueueNavigator() {
            @Override
            public long getSupportedQueueNavigatorActions(Player player) {
                return PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
            }

            @Override
            public void onSkipToPrevious(Player player, ControlDispatcher controlDispatcher) {
                mPresenter.onPreviousClicked();
            }

            @Override
            public void onSkipToNext(Player player, ControlDispatcher controlDispatcher) {
                mPresenter.onNextClicked();
            }
        });

        // Fix exoplayer pause when switching AFR
        mMediaSessionConnector.setControlDispatcher(new DefaultControlDispatcher() {
            @Override
            public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
                if (System.currentTimeMillis() - getPlayerData().getAfrSwitchTimeMs() < 5_000) {
                    return false;
                }

                return super.dispatchSetPlayWhenReady(player, playWhenReady);
            }
        });
    }

    private void createSubtitleManager() {
        if (mSubtitleManager == null && mPlayerView.getSubtitleView() != null) {
            mSubtitleManager = new SubtitleManager(mPlayerView.getSubtitleView());
        }

        if (mSubtitleManager != null && mPlayer != null && mPlayer.getTextComponent() != null) {
            mPlayer.getTextComponent().addTextOutput(mSubtitleManager);
        }
    }

    /**
     * Release the engine unless it plays in the background
     */
    private void maybeReleasePlayer() {
        if (isEngineBlocked()) {
            Log.d(TAG, "releasePlayer: Playback activity is blocked. Exiting...");
            return;
        }

        releasePlayer();
    }

    private void releasePlayer() {
        if (mPlayer == null) {
            return;
        }

        MobilePlaybackService.stop(this);

        Log.d(TAG, "releasePlayer: Start releasing player engine...");
        mPresenter.onEngineReleased();

        if (mMediaSessionConnector != null) {
            mMediaSessionConnector.setPlayer(null);
            mMediaSessionConnector.setControlDispatcher(null);
            mMediaSessionConnector.setMediaMetadataProvider(null);
            mMediaSessionConnector.setQueueNavigator(null);
            mMediaSessionConnector = null;
        }
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
        if (mSubtitleManager != null && mPlayer.getTextComponent() != null) {
            mPlayer.getTextComponent().removeTextOutput(mSubtitleManager);
        }

        mPlayerView.setPlayer(null);
        mPlayerInitializer.release();
        // Fix access calls when player isn't initialized
        mExoPlayerController.release();
        mPlayer = null;
    }

    // ------------------------------- PlayerEngine -------------------------------

    @Override
    public void openSabr(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openSabr(formatInfo);
    }

    @Override
    public void openDash(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openDash(formatInfo);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mExoPlayerController.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mExoPlayerController.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mExoPlayerController.openUrlList(urlList);
    }

    @Override
    public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(formatInfo, hlsPlaylistUrl);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController.getPositionMs();
    }

    @Override
    public void setPositionMs(long positionMs) {
        mExoPlayerController.setPositionMs(positionMs);
    }

    @Override
    public long getDurationMs() {
        return mExoPlayerController.getDurationMs();
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        mExoPlayerController.setPlayWhenReady(play);
        updatePlayPause();
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayerController.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayerController.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mExoPlayerController.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mExoPlayerController.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mExoPlayerController.getSubtitleFormats();
    }

    @Override
    public void setFormat(FormatItem option) {
        mExoPlayerController.selectFormat(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public FormatItem getAudioFormat() {
        return mExoPlayerController.getAudioFormat();
    }

    @Override
    public FormatItem getSubtitleFormat() {
        return mExoPlayerController.getSubtitleFormat();
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void restartEngine() {
        if (isDestroyed()) {
            Log.e(TAG, "Can't restart engine. Seems that player activity is being destroyed.");
            return;
        }

        releasePlayer();
        initializePlayer();
    }

    @Override
    public void reloadPlayback() {
        if (mPlayer != null) {
            mPresenter.onEngineReleased();
            mPresenter.onEngineInitialized();
        }
    }

    @Override
    public void blockEngine(boolean block) {
        mIsEngineBlocked = block;
    }

    @Override
    public boolean isEngineBlocked() {
        return mIsEngineBlocked;
    }

    @Override
    public boolean isInPIPMode() {
        return isInPipMode();
    }

    @Override
    public boolean containsMedia() {
        return mExoPlayerController.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        mExoPlayerController.setSpeed(speed);
        // NOTE: Real speed isn't changed immediately, so use supplied speed data
        setButtonState(R.id.action_video_speed, speed != 1.0f ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        TextView speedView = mDetails.findViewById(R.id.mobile_watch_speed);
        speedView.setText(String.format("%s %sx", getString(R.string.mobile_speed), Helpers.formatFloat(speed)));
    }

    @Override
    public float getSpeed() {
        return mExoPlayerController.getSpeed();
    }

    @Override
    public void setPitch(float pitch) {
        mExoPlayerController.setPitch(pitch);
    }

    @Override
    public float getPitch() {
        return mExoPlayerController.getPitch();
    }

    @Override
    public void setVolume(float volume) {
        mExoPlayerController.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return mExoPlayerController.getVolume();
    }

    @Override
    public void setResizeMode(int mode) {
        mResizeMode = mode;
        applyLayout();
    }

    @Override
    public int getResizeMode() {
        return mResizeMode;
    }

    @Override
    public void setZoomPercents(int percents) {
        // Pinch the video: not supported on the phone player yet
    }

    @Override
    public void setAspectRatio(float ratio) {
        // The video keeps its own aspect ratio on the phone player
    }

    @Override
    public void setRotationAngle(int angle) {
        mPlayerView.setRotation(angle);
    }

    @Override
    public void setVideoFlipEnabled(boolean enabled) {
        mPlayerView.setScaleX(enabled ? -1 : 1);
    }

    @Override
    public void setVideoGravity(int gravity) {
        // Centered on the phone player
    }

    @Override
    public Throwable getUnthrownError() {
        return mExoPlayerController.getUnthrownError();
    }

    // ------------------------------- PlayerUI -------------------------------

    @Override
    public void updateSuggestions(VideoGroup group) {
        if (group == null || (group.isEmpty() && group.getAction() != VideoGroup.ACTION_REPLACE)) {
            return;
        }

        mFeed.apply(group);
        rebuildList();
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
        if (group == null) {
            return;
        }

        MobileFeed.Block block = mFeed.findBlock(group.getId());

        if (block != null) {
            block.videos.removeAll(group.getVideos());

            if (block.videos.isEmpty()) {
                mFeed.removeBlock(group.getId());
            }

            rebuildList();
        }
    }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        List<MobileFeed.Block> blocks = mFeed.getBlocks();

        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).id == group.getId()) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int index) {
        if (getVideo() == null || !getVideo().hasVideo()) {
            return null;
        }

        List<MobileFeed.Block> blocks = mFeed.getBlocks();

        return index >= 0 && index < blocks.size() ? VideoGroup.from(new ArrayList<>(blocks.get(index).videos)) : null;
    }

    @Override
    public void focusSuggestedItem(int index) {
        // Touch UI: nothing to focus
    }

    @Override
    public void focusSuggestedItem(Video video) {
        // Touch UI: nothing to focus
    }

    @Override
    public void resetSuggestedPosition() {
        mList.scrollToPosition(0);
    }

    @Override
    public boolean isSuggestionsEmpty() {
        return mFeed.isEmpty();
    }

    @Override
    public void clearSuggestions() {
        mFeed.clear();
        rebuildList();
    }

    @Override
    public void showOverlay(boolean show) {
        showControls(show);
    }

    @Override
    public boolean isOverlayShown() {
        return mIsControlsShown;
    }

    @Override
    public void showSuggestions(boolean show) {
        // The related videos are always below the player
        showControls(show);
    }

    @Override
    public boolean isSuggestionsShown() {
        return false;
    }

    @Override
    public void showControls(boolean show) {
        setControlsShown(show);
    }

    @Override
    public boolean isControlsShown() {
        return mIsControlsShown;
    }

    @Override
    public int getButtonState(int buttonId) {
        return mButtonStates.get(buttonId, PlayerUI.BUTTON_OFF);
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        mButtonStates.put(buttonId, buttonState);
        bindDetails();
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        ImageView avatar = mDetails.findViewById(R.id.mobile_watch_avatar);
        Video video = getVideo();
        if (video != null) {
            MobileUi.putChannelIcon(video.channelId, iconUrl);
        }

        if (TextUtils.isEmpty(iconUrl)) {
            avatar.setImageDrawable(null);
            return;
        }

        Glide.with(this)
                .load(iconUrl)
                .apply(RequestOptions.circleCropTransform())
                .into(avatar);
    }

    @Override
    public void setSeekPreviewTitle(String title) {
        // No seek preview on the phone player
    }

    @Override
    public void setNextTitle(Video nextVideo) {
        // The next video is the first related video
    }

    @Override
    public void showDebugInfo(boolean show) {
        // Not available on the phone player
    }

    @Override
    public void showSubtitles(boolean show) {
        createSubtitleManager();

        if (mSubtitleManager != null) {
            mSubtitleManager.show(show);
        }
    }

    @Override
    public void loadStoryboard() {
        // No seek preview on the phone player
    }

    @Override
    public void setTitle(String title) {
        ((TextView) mDetails.findViewById(R.id.mobile_watch_title)).setText(title);
        mControlsTitle.setText(title);
    }

    @Override
    public void showProgressBar(boolean show) {
        // Fix interrupted progress (by suggestions, etc). The video player can handle these states correctly.
        if (mExoPlayerController.isLoading() || mExoPlayerController.isBuffering()) {
            return;
        }

        mBuffering.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        mTimeBar.setSegments(segments);
    }

    @Override
    public void updateEndingTime() {
        // Not shown on the phone player
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
        // Live chat isn't shown on the phone player yet
    }

    // ------------------------------- PlayerManager -------------------------------

    @Override
    public void setVideo(Video video) {
        Video previous = getVideo();
        if (video != null && (previous == null || !TextUtils.equals(previous.videoId, video.videoId))) {
            mIsAutoplayPending = true;
            mIsPausedByUser = false;
        }

        mExoPlayerController.setVideo(video);

        if (video != null) {
            setShorts(video.isShorts);
        }

        mIsDescriptionShown = false;
        bindDetails();
        mList.scrollToPosition(0);
    }

    @Override
    public Video getVideo() {
        return mExoPlayerController != null ? mExoPlayerController.getVideo() : null;
    }

    @Override
    public void showBackground(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }

        mThumb.setVisibility(View.VISIBLE);
        Glide.with(this).load(url).into(mThumb);
    }

    @Override
    public void showBackgroundColor(int colorResId) {
        mThumb.setVisibility(View.VISIBLE);
        mThumb.setImageDrawable(null);
        mThumb.setBackgroundColor(ContextCompat.getColor(this, colorResId));
    }

    @Override
    public void resetPlayerState() {
        mExoPlayerController.resetPlayerState();
        // Hide last frame of the previous video
        Video video = getVideo();
        showBackgroundColor(R.color.player_background);
        if (video != null) {
            showBackground(video.getCardImageUrl());
        }
        setSeekBarSegments(null);
        mTimeBar.setProgress(0, 0, 0);
    }

    @Override
    public boolean isEmbed() {
        return false;
    }

    // ------------------------------- Related videos callbacks -------------------------------

    @Override
    public void onVideoClicked(Video video) {
        mPresenter.onSuggestionItemClicked(video);
    }

    @Override
    public void onVideoLongClicked(Video video) {
        mPresenter.onSuggestionItemLongClicked(video);
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
