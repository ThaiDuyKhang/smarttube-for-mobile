package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Mobile build: link device (remote control) screen (instead of the TV two-pane screen)
 */
public class MobileAddDeviceActivity extends MobileCodeActivity implements AddDeviceView {
    private AddDevicePresenter mPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setScreenTitle(R.string.mobile_link_device, R.string.mobile_link_device_hint);
        setDescription(getString(R.string.add_device_view_description));

        mPresenter = AddDevicePresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode) {
        setCode(userCode, null, R.string.login_from_browser);
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
