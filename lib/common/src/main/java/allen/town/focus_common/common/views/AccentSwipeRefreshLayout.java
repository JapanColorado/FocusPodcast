package allen.town.focus_common.common.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import code.name.monkey.appthemehelper.ThemeStore;

public class AccentSwipeRefreshLayout extends SwipeRefreshLayout {

    public AccentSwipeRefreshLayout(Context context) {
        super(context);
    }

    public AccentSwipeRefreshLayout(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        //设置为强调色
        setColorSchemeColors(ThemeStore.accentColor(context));
    }

}
