package dev.russell.focuspodcast.common.common.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import dev.russell.focuspodcast.theme.ThemeStore;

public class AccentSwipeRefreshLayout extends SwipeRefreshLayout {

    public AccentSwipeRefreshLayout(Context context) {
        super(context);
    }

    public AccentSwipeRefreshLayout(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        // Use the accent color
        setColorSchemeColors(ThemeStore.accentColor(context));
    }

}
