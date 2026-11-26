package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mobile build: in-app updates from the GitHub releases of the project.<br/>
 * Checks the latest release, downloads the APK for this device (checked against SHA256SUMS.txt) and opens the Android installer.
 * The app is updated in place: settings and accounts are kept.
 */
@androidx.annotation.RequiresApi(21) // the mobile build starts at Android 5
public class MobileUpdateActivity extends Activity {
    private static final String REPO = "ThaiDuyKhang/smarttube-for-mobile";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";
    private static final Pattern RELEASE_NUMBER = Pattern.compile("mobile\\.(\\d+)$");
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TextView mStatus;
    private TextView mButton;
    private TextView mNotes;
    private View mNotesTitle;
    private ProgressBar mProgress;
    private String mApkName;
    private String mApkUrl;
    private String mSumsUrl;
    private String mReleaseUrl;
    private File mDownloadedApk;
    private boolean mIsBusy;
    private boolean mIsWaitingForPermission;

    /**
     * Release tag of this build (v32.83-mobile.N), empty for local test builds
     */
    public static String getCurrentTag(Context context) {
        // Defined by the mobile flavor only (see build.gradle)
        int id = context.getResources().getIdentifier("mobile_release_tag", "string", context.getPackageName());
        return id != 0 ? context.getString(id) : "";
    }

    @Override
    protected void attachBaseContext(Context base) {
        // Phone density (not the TV one) and the app language
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.densityDpi = Resources.getSystem().getDisplayMetrics().densityDpi;
        Locale locale = LocaleUpdater.getSavedLocale(base);
        if (locale != null) {
            config.setLocale(locale);
        }
        com.liskovsoft.smartyoutubetv2.common.utils.MobileThemeHelper.apply(base, config);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.MobileTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_activity_update);
        MobileUi.applySystemBarColors(this);

        mStatus = findViewById(R.id.mobile_update_status);
        mButton = findViewById(R.id.mobile_update_button);
        mNotes = findViewById(R.id.mobile_update_notes);
        mNotesTitle = findViewById(R.id.mobile_update_notes_title);
        mProgress = findViewById(R.id.mobile_update_progress);
        findViewById(R.id.mobile_update_back).setOnClickListener(v -> finish());

        String tag = getCurrentTag(this);
        ((TextView) findViewById(R.id.mobile_update_current)).setText(getString(R.string.mobile_update_current,
                TextUtils.isEmpty(tag) ? getString(R.string.mobile_update_dev) : tag));

        checkForUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Back from the "install unknown apps" settings
        if (mIsWaitingForPermission && mDownloadedApk != null) {
            mIsWaitingForPermission = false;
            install();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }

    // ------------------------------- Check -------------------------------

    private void checkForUpdates() {
        if (mIsBusy) {
            return;
        }

        mIsBusy = true;
        showStatus(getString(R.string.mobile_update_checking), true);
        mButton.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(readText(LATEST_RELEASE_API));
                String latestTag = release.optString("tag_name");
                String notes = release.optString("body");
                mReleaseUrl = release.optString("html_url", RELEASES_PAGE);
                JSONArray assets = release.optJSONArray("assets");
                long apkSize = findAssets(assets);

                mHandler.post(() -> onChecked(latestTag, notes, apkSize));
            } catch (Exception e) {
                com.liskovsoft.sharedutils.mylogger.Log.e("MobileUpdate", "Update check failed: %s", e);
                mHandler.post(() -> {
                    mIsBusy = false;
                    showStatus(getString(R.string.mobile_update_error), false);
                    showButton(getString(R.string.mobile_retry), this::checkForUpdates);
                });
            }
        }).start();
    }

    /**
     * The APK for this device (the most specific ABI first, the universal one otherwise) and the checksums
     */
    private long findAssets(JSONArray assets) {
        mApkName = null;
        mApkUrl = null;
        mSumsUrl = null;
        long size = 0;

        if (assets == null) {
            return 0;
        }

        for (String abi : Build.SUPPORTED_ABIS) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                String name = asset != null ? asset.optString("name") : "";
                if (name.endsWith("_" + abi + ".apk") && mApkUrl == null) {
                    mApkName = name;
                    mApkUrl = asset.optString("browser_download_url");
                    size = asset.optLong("size");
                }
            }
        }

        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            String name = asset != null ? asset.optString("name") : "";
            if (mApkUrl == null && name.endsWith("_universal.apk")) {
                mApkName = name;
                mApkUrl = asset.optString("browser_download_url");
                size = asset.optLong("size");
            }
            if (name.equals("SHA256SUMS.txt")) {
                mSumsUrl = asset.optString("browser_download_url");
            }
        }

        return size;
    }

    private void onChecked(String latestTag, String notes, long apkSize) {
        mIsBusy = false;
        int current = getReleaseNumber(getCurrentTag(this));
        int latest = getReleaseNumber(latestTag);

        if (latest <= current || mApkUrl == null) {
            showStatus(getString(R.string.mobile_update_latest), false);
            showButton(getString(R.string.mobile_check_updates), this::checkForUpdates);
            showNotes(null);
            return;
        }

        showStatus(getString(R.string.mobile_update_available, latestTag), false);
        showNotes(notes);
        showButton(getString(R.string.mobile_update_download, Formatter.formatShortFileSize(this, apkSize)), this::download);
    }

    private static int getReleaseNumber(String tag) {
        Matcher matcher = tag != null ? RELEASE_NUMBER.matcher(tag) : null;
        return matcher != null && matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    // ------------------------------- Download -------------------------------

    private void download() {
        if (mIsBusy || mApkUrl == null) {
            return;
        }

        mIsBusy = true;
        mButton.setVisibility(View.GONE);
        showProgress(0);

        new Thread(() -> {
            File dir = new File(getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir(), "updates");
            File apk = new File(dir, mApkName);

            try {
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("Can't create " + dir);
                }

                downloadFile(mApkUrl, apk);

                mHandler.post(() -> showStatus(getString(R.string.mobile_update_verifying), true));

                // The checksum published with the release must match
                if (mSumsUrl != null) {
                    String expected = findChecksum(readText(mSumsUrl), mApkName);
                    if (expected != null && !expected.equalsIgnoreCase(sha256(apk))) {
                        //noinspection ResultOfMethodCallIgnored
                        apk.delete();
                        mHandler.post(() -> onDownloadFailed(R.string.mobile_update_corrupted));
                        return;
                    }
                }

                mHandler.post(() -> {
                    mIsBusy = false;
                    mDownloadedApk = apk;
                    mProgress.setVisibility(View.GONE);
                    showButton(getString(R.string.mobile_update_install), this::install);
                    install();
                });
            } catch (Exception e) {
                com.liskovsoft.sharedutils.mylogger.Log.e("MobileUpdate", "Update download failed: %s", e);
                mHandler.post(() -> onDownloadFailed(R.string.mobile_update_download_error));
            }
        }).start();
    }

    private void onDownloadFailed(int messageResId) {
        mIsBusy = false;
        showStatus(getString(messageResId), false);
        showButton(getString(R.string.mobile_retry), this::download);
    }

    private void downloadFile(String url, File file) throws IOException {
        HttpURLConnection connection = open(url);
        long total = connection.getContentLength();

        try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            long done = 0;
            int lastPercent = -1;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                done += read;
                int percent = total > 0 ? (int) (done * 100 / total) : 0;
                if (percent != lastPercent) {
                    lastPercent = percent;
                    mHandler.post(() -> showProgress(percent));
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String findChecksum(String sums, String fileName) {
        for (String line : sums.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2 && parts[parts.length - 1].replace("*", "").endsWith(fileName)) {
                return parts[0];
            }
        }

        return null;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    // ------------------------------- Install -------------------------------

    private void install() {
        if (mDownloadedApk == null || !mDownloadedApk.exists()) {
            return;
        }

        // Android 8+: the user allows SmartTube to install apps once
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            mIsWaitingForPermission = true;
            showStatus(getString(R.string.mobile_update_permission), false);
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                mIsWaitingForPermission = false;
            }
            return;
        }

        Uri uri = Build.VERSION.SDK_INT >= 24 ?
                FileProvider.getUriForFile(this, getPackageName() + ".update_provider", mDownloadedApk) : Uri.fromFile(mDownloadedApk);

        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            openReleasePage();
        }
    }

    private void openReleasePage() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mReleaseUrl != null ? mReleaseUrl : RELEASES_PAGE)));
        } catch (ActivityNotFoundException e) {
            // No browser
        }
    }

    // ------------------------------- Network -------------------------------

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true); // GitHub downloads redirect to their storage
        connection.setRequestProperty("User-Agent", "SmartTube-Mobile-Updater");
        connection.setRequestProperty("Accept", "application/vnd.github+json, */*");

        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }

        return connection;
    }

    private static String readText(String url) throws IOException {
        HttpURLConnection connection = open(url);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
            return text.toString();
        } finally {
            connection.disconnect();
        }
    }

    // ------------------------------- UI -------------------------------

    private void showStatus(String text, boolean isLoading) {
        mStatus.setText(text);
        mProgress.setIndeterminate(true);
        mProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showProgress(int percent) {
        mStatus.setText(getString(R.string.mobile_update_downloading, percent));
        mProgress.setVisibility(View.VISIBLE);
        mProgress.setIndeterminate(false);
        mProgress.setMax(100);
        mProgress.setProgress(percent);
    }

    private void showButton(String text, Runnable action) {
        mButton.setText(text);
        mButton.setVisibility(View.VISIBLE);
        mButton.setOnClickListener(v -> action.run());
    }

    private void showNotes(String notes) {
        // Release notes are markdown: keep them readable as plain text
        String text = notes != null ? notes.replaceAll("(?m)^#+\\s*", "").replaceAll("\\*\\*", "").trim() : "";
        mNotes.setText(text);
        mNotesTitle.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
