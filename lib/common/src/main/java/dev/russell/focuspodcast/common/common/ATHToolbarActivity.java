package dev.russell.focuspodcast.common.common;

import android.graphics.Color;
import android.view.Menu;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import dev.russell.focuspodcast.theme.ATHActivity;
import dev.russell.focuspodcast.theme.R;
import dev.russell.focuspodcast.theme.util.ATHUtil;
import dev.russell.focuspodcast.theme.util.ToolbarContentTintHelper;


public class ATHToolbarActivity extends ATHActivity {

    private Toolbar toolbar;

    public static int getToolbarBackgroundColor(@Nullable Toolbar toolbar) {
        if (toolbar != null) {
            return ATHUtil.INSTANCE.resolveColor(toolbar.getContext(), com.google.android.material.R.attr.colorSurface);
        }
        return Color.BLACK;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Toolbar toolbar = getATHToolbar();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        this.toolbar = toolbar;
        super.setSupportActionBar(toolbar);
    }

    protected Toolbar getATHToolbar() {
        return toolbar;
    }
}