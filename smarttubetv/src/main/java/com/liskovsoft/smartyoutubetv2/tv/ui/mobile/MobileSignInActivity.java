package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.os.Bundle;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Mobile build: sign in screen (instead of the TV two-pane screen)
 */
public class MobileSignInActivity extends MobileCodeActivity implements SignInView {
    private SignInPresenter mPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setScreenTitle(R.string.mobile_sign_in, R.string.mobile_sign_in_hint);
        setDescription(getString(R.string.signin_view_description, ""));

        mPresenter = SignInPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        String url = fullSignInUrl != null ? fullSignInUrl : signInUrl;
        setCode(userCode, url, R.string.login_from_browser);

        if (signInUrl != null) {
            String description = getString(R.string.signin_view_description, signInUrl);
            int start = description.indexOf(signInUrl);
            setDescription(Utils.color(description, ContextCompat.getColor(this, R.color.red), start, start + signInUrl.length()));
        }
    }

    @Override
    protected void onDoneClicked() {
        mPresenter.onActionClicked();
    }

    @Override
    public void close() {
        finish();
    }
}
