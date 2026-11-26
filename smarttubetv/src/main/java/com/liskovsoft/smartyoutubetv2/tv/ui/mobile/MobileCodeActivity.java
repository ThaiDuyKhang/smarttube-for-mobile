package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Mobile build: base of the sign in and link device screens.<br/>
 * Big code with copy button, open in the browser (the code is copied first), QR code for another device.
 */
public abstract class MobileCodeActivity extends MobileActivity {
    private TextView mCode;
    private TextView mDescription;
    private ImageView mQr;
    private View mQrHint;
    private View mCopy;
    private Button mBrowser;
    private String mUserCode;
    private String mUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_activity_code);

        mCode = findViewById(R.id.mobile_code);
        mDescription = findViewById(R.id.mobile_code_description);
        mQr = findViewById(R.id.mobile_code_qr);
        mQrHint = findViewById(R.id.mobile_code_qr_hint);
        mCopy = findViewById(R.id.mobile_code_copy);
        mBrowser = findViewById(R.id.mobile_code_browser);
        Button done = findViewById(R.id.mobile_code_done);

        findViewById(R.id.mobile_code_back).setOnClickListener(v -> onBackPressed());
        mCopy.setOnClickListener(v -> copyCode(true));
        mBrowser.setOnClickListener(v -> {
            copyCode(true);
            if (mUrl != null) {
                Utils.openLinkExt(this, mUrl);
            }
        });
        done.setText(R.string.signin_view_action_text);
        done.setOnClickListener(v -> onDoneClicked());

        mCode.setText(R.string.signin_view_title); // loading...
        mCode.setTextSize(16);
        mCopy.setVisibility(View.GONE);
        mBrowser.setVisibility(View.GONE);
        mQr.setVisibility(View.GONE);
        mQrHint.setVisibility(View.GONE);
    }

    protected void setScreenTitle(int titleResId, int hintResId) {
        ((TextView) findViewById(R.id.mobile_code_screen_title)).setText(titleResId);
        ((TextView) findViewById(R.id.mobile_code_hint)).setText(hintResId);
    }

    protected void setDescription(CharSequence description) {
        mDescription.setText(description);
    }

    /**
     * @param url page to open in the browser (and in the QR code), null when there is none
     */
    protected void setCode(String userCode, String url, int browserTitleResId) {
        if (TextUtils.isEmpty(userCode)) {
            return;
        }

        mUserCode = userCode;
        mUrl = url;
        mCode.setText(userCode);
        mCode.setTextSize(32);
        mCopy.setVisibility(View.VISIBLE);

        boolean hasUrl = !TextUtils.isEmpty(url);
        mBrowser.setVisibility(hasUrl ? View.VISIBLE : View.GONE);
        mBrowser.setText(browserTitleResId);
        mQr.setVisibility(hasUrl ? View.VISIBLE : View.GONE);
        mQrHint.setVisibility(hasUrl ? View.VISIBLE : View.GONE);

        if (hasUrl) {
            Glide.with(this)
                    .load(Utils.toQrCodeLink(url))
                    .placeholder(R.drawable.activate_account_qrcode)
                    .error(R.drawable.activate_account_qrcode)
                    .into(mQr);
        }
    }

    private void copyCode(boolean showMessage) {
        if (mUserCode == null) {
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(mUserCode, mUserCode));
            if (showMessage) {
                Toast.makeText(this, R.string.mobile_code_copied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    protected abstract void onDoneClicked();
}
