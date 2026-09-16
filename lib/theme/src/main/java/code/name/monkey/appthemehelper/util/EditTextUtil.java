package code.name.monkey.appthemehelper.util;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import java.lang.reflect.Field;

import code.name.monkey.appthemehelper.ThemeStore;

public class EditTextUtil {
    public static final String TAG = "EditTextUtil";

    /**
     * 设置光标为主题色
     * @param searchView
     */
    public static void setCursorDrawableForSearchView(SearchView searchView) {
        setCursorDrawable(searchView.findViewById(androidx.appcompat.R.id.search_src_text));
    }

    /**
     * 设置光标为主题色
     * @param editText
     */
    public static void setCursorDrawable(EditText editText) {
        if (editText == null) {
            Log.w(TAG, "setCursorDrawable null");
            return;
        }
        try {
            Field declaredField = TextView.class.getDeclaredField("mCursorDrawableRes");
            declaredField.setAccessible(true);
            int i = declaredField.getInt(editText);
            Field declaredField2 = TextView.class.getDeclaredField("mEditor");
            declaredField2.setAccessible(true);
            Object obj = declaredField2.get(editText);
            Drawable drawable = ContextCompat.getDrawable(editText.getContext(), i);
            drawable.setColorFilter(ThemeStore.accentColor(editText.getContext()), PorterDuff.Mode.SRC_IN);
            Drawable[] drawableArr = {drawable, drawable};
            Field declaredField3 = obj.getClass().getDeclaredField("mCursorDrawable");
            declaredField3.setAccessible(true);
            declaredField3.set(obj, drawableArr);
        } catch (Exception unused) {
            Log.w(TAG, unused);

            try {
                Field declaredField = TextView.class.getDeclaredField("mCursorDrawableRes");
                declaredField.setAccessible(true);
                int i = declaredField.getInt(editText);
                Drawable drawable = ContextCompat.getDrawable(editText.getContext(), i);
                drawable.setColorFilter(ThemeStore.accentColor(editText.getContext()), PorterDuff.Mode.SRC_IN);
                Drawable[] drawableArr = {drawable, drawable};
                Field declaredField3 = TextView.class.getDeclaredField("mCursorDrawable");
                declaredField3.setAccessible(true);
                declaredField3.set(editText, drawable);
            } catch (Exception unused2) {
                Log.w(TAG, unused2);
            }
        }

        ViewCompat.setBackgroundTintList(editText, new ColorStateList(new int[][]{new int[0]}, new int[]{ThemeStore.accentColor(editText.getContext())}));
    }


}