package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Mobile build: "buy the author a coffee" screen. VietQR code (MB Bank) that every Vietnamese banking app can scan.
 */
public class MobileDonateActivity extends Activity {
    private static final String ACCOUNT = "6006101991";

    @Override
    protected void attachBaseContext(Context base) {
        // Phone density (not the TV one) and the app language
        android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
        config.densityDpi = android.content.res.Resources.getSystem().getDisplayMetrics().densityDpi;
        java.util.Locale locale = com.liskovsoft.sharedutils.locale.LocaleUpdater.getSavedLocale(base);
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
        setContentView(R.layout.mobile_activity_donate);
        MobileUi.applySystemBarColors(this);

        findViewById(R.id.mobile_donate_back).setOnClickListener(v -> finish());
        findViewById(R.id.mobile_donate_copy).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(ACCOUNT, ACCOUNT));
                Toast.makeText(this, R.string.mobile_account_copied, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
