package allen.town.podcast.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

public class ReaderListView extends ListView {
    public ReaderListView(Context context) {
        super(context);
    }
    public ReaderListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public ReaderListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    @Override
    /**
     * Overridden so the ListView sizes itself to fit inside a ScrollView.
     */
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //the measure spec is a 32-bit value: the top 2 bits are the mode and the low 30 bits the size, so shift right by 2 to get the size
        int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, expandSpec);
    }
}