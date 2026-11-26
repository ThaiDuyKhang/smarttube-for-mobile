package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

/**
 * Mobile build: base of the phone screens.<br/>
 * System density, system bars visible, own dark theme. Everything else (view stack, exit, keys) as in the other screens.
 */
public abstract class MobileActivity extends LeanbackActivity {
    @Override
    public boolean isNativeUi() {
        return true;
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.MobileTheme);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        MobileUi.applySystemBarColors(this);
    }
}
