package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile build: search screen like the YouTube app.<br/>
 * Search field with voice input, history and suggestions while typing, results as a feed.
 */
public class MobileSearchActivity extends MobileActivity implements SearchView, MobileFeedAdapter.Callback {
    private static final int REQUEST_VOICE = 1001;
    private SearchPresenter mPresenter;
    private EditText mField;
    private View mClearButton;
    private View mEmptyView;
    private View mProgress;
    private RecyclerView mList;
    private MobileFeedAdapter mAdapter;
    private MobileFeed mFeed;
    private MediaServiceSearchTagProvider mTagsProvider;
    private final List<Tag> mTags = new ArrayList<>();
    private String mResultsQuery;
    private boolean mIsResultsShown;
    private boolean mIsLoading;
    private boolean mIgnoreTextChange;
    private boolean mIsCreated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_activity_search);

        mField = findViewById(R.id.mobile_search_field);
        mClearButton = findViewById(R.id.mobile_search_clear);
        mEmptyView = findViewById(R.id.mobile_search_empty);
        mProgress = findViewById(R.id.mobile_search_progress);
        mList = findViewById(R.id.mobile_search_list);

        mAdapter = new MobileFeedAdapter(this);
        mFeed = new MobileFeed(mList, mAdapter, new MobileFeed.LoadMoreListener() {
            @Override
            public boolean isLoading() {
                return !mIsResultsShown || mPresenter.hasPendingActions();
            }

            @Override
            public void onLoadMore(Video lastVideo) {
                mPresenter.onScrollEnd(lastVideo);
            }
        });

        findViewById(R.id.mobile_search_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.mobile_search_mic).setOnClickListener(v -> startVoiceRecognition());
        findViewById(R.id.mobile_search_options).setOnClickListener(v -> mPresenter.onSearchSettingsClicked());
        mClearButton.setOnClickListener(v -> {
            mField.setText("");
            showKeyboard();
        });

        mField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mClearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);

                if (!mIgnoreTextChange) {
                    showSuggestions(s.toString());
                }
            }
        });
        mField.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnter) {
                submit(mField.getText().toString());
                return true;
            }
            return false;
        });
        // Tap on the field above the results: edit the query again
        mField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && mIsResultsShown) {
                showSuggestions(mField.getText().toString());
            }
        });

        mPresenter = SearchPresenter.instance(this);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mPresenter.getView() == this) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public void onBackPressed() {
        // Suggestions over the results: back to the results
        if (!mIsResultsShown && mResultsQuery != null && !mFeed.isEmpty()) {
            setText(mResultsQuery);
            showResults();
            return;
        }

        hideKeyboard();
        super.onBackPressed();
    }

    @Override
    public void finishReally() {
        super.finishReally();

        mPresenter.onFinish();
    }

    // ------------------------------- Search -------------------------------

    private void submit(String query) {
        query = query != null ? query.trim() : null;

        if (TextUtils.isEmpty(query)) {
            return;
        }

        setText(query);
        hideKeyboard();
        mField.clearFocus();
        mList.requestFocus();

        mResultsQuery = query;
        mFeed.clear();
        showResults();
        mPresenter.onSearch(query);
    }

    private void showResults() {
        mIsResultsShown = true;
        mEmptyView.setVisibility(View.GONE);
        rebuildResults();
    }

    private void rebuildResults() {
        if (!mIsResultsShown) {
            return;
        }

        List<MobileFeedAdapter.Item> items = new ArrayList<>();
        mFeed.addItems(items, false);
        mFeed.setItems(items);
        mEmptyView.setVisibility(items.isEmpty() && !mIsLoading && mResultsQuery != null ? View.VISIBLE : View.GONE);
    }

    private void showSuggestions(String query) {
        mIsResultsShown = false;
        mEmptyView.setVisibility(View.GONE);
        // Show the last list right away, then the fresh one
        rebuildSuggestions();

        if (mTagsProvider == null) {
            return;
        }

        mTagsProvider.search(query, results -> runOnUiThread(() -> {
            mTags.clear();
            if (results != null) {
                mTags.addAll(results);
            }
            rebuildSuggestions();
        }));
    }

    private void rebuildSuggestions() {
        if (mIsResultsShown) {
            return;
        }

        List<MobileFeedAdapter.Item> items = new ArrayList<>();
        String text = mField.getText().toString().trim();

        for (Tag tag : mTags) {
            if (!TextUtils.isEmpty(tag.tag)) {
                // History items are returned for the empty query
                items.add(MobileFeedAdapter.Item.suggestion(tag.tag, text.isEmpty()));
            }
        }

        mAdapter.setItems(items);
    }

    private void setText(String text) {
        mIgnoreTextChange = true;
        mField.setText(text);
        mField.setSelection(mField.getText().length());
        mIgnoreTextChange = false;
    }

    // ------------------------------- Keyboard and voice -------------------------------

    private void showKeyboard() {
        mField.requestFocus();
        mField.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(mField, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mField.getWindowToken(), 0);
        }
    }

    @Override
    public void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.mobile_search_hint));

        try {
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (ActivityNotFoundException | SecurityException e) {
            // No voice input on this device: type the query
            showKeyboard();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VOICE && resultCode == RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                submit(results.get(0));
            }
        }
    }

    // ------------------------------- SearchView -------------------------------

    @Override
    public void updateSearch(VideoGroup group) {
        mFeed.apply(group);

        if (group != null && group.getAction() != VideoGroup.ACTION_SYNC) {
            mIsResultsShown = true;
        }

        rebuildResults();
    }

    @Override
    public void clearSearch() {
        mFeed.clear();
        rebuildResults();
    }

    @Override
    public void clearSearchTags() {
        mTags.clear();
        rebuildSuggestions();
    }

    @Override
    public void removeSearchTag(Tag tag) {
        mTags.remove(tag);
        rebuildSuggestions();
    }

    @Override
    public void setTagsProvider(MediaServiceSearchTagProvider provider) {
        mTagsProvider = provider;
    }

    @Override
    public void showProgressBar(boolean show) {
        mIsLoading = show;
        mProgress.setVisibility(show && mIsResultsShown && mFeed.isEmpty() ? View.VISIBLE : View.GONE);

        if (!show) {
            rebuildResults(); // "no results"
        }
    }

    @Override
    public void startSearch(String searchText) {
        if (TextUtils.isEmpty(searchText)) {
            mResultsQuery = null;
            mFeed.clear();
            setText("");
            showSuggestions("");
            showKeyboard();
        } else {
            submit(searchText);
        }
    }

    @Override
    public String getSearchText() {
        return mField.getText().toString();
    }

    // ------------------------------- Feed callbacks -------------------------------

    @Override
    public void onSuggestionClicked(String text) {
        submit(text);
    }

    @Override
    public void onSuggestionFillClicked(String text) {
        setText(text + " ");
        showSuggestions(mField.getText().toString());
        showKeyboard();
    }

    @Override
    public void onSuggestionLongClicked(String text) {
        for (Tag tag : mTags) {
            if (TextUtils.equals(tag.tag, text)) {
                mPresenter.onTagLongClicked(tag);
                break;
            }
        }
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
