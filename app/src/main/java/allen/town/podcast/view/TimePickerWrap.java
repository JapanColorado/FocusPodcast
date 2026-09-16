package allen.town.podcast.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

/**
 * Samsung's Android 6.0.1 has a bug that crashes the app when inflating a time picker.
 * This class serves as a workaround for affected devices.
 */
public class TimePickerWrap extends android.widget.TimePicker {
    private static final String TAG = "TimePickerWrap";

    public TimePickerWrap(Context context) {
        super(context);
    }

    public TimePickerWrap(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TimePickerWrap(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        try {
            super.onRtlPropertiesChanged(layoutDirection);
        } catch (Exception e) {
            // safe to continue: the platform TimePicker throws here on some OEM builds and the
            // only consequence is that the layout keeps its previous direction
            Log.w(TAG, "the platform TimePicker refused the RTL change", e);
        }
    }
}
