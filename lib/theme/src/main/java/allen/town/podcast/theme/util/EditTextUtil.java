package allen.town.podcast.theme.util;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import java.lang.reflect.Field;

import allen.town.podcast.theme.ThemeStore;

public class EditTextUtil {
    public static final String TAG = "EditTextUtil";

    /**
     * Tint the cursor with the theme color.
     * @param searchView
     */
    public static void setCursorDrawableForSearchView(SearchView searchView) {
        setCursorDrawable(searchView.findViewById(androidx.appcompat.R.id.search_src_text));
    }

    /**
     * Tint the cursor with the theme color.
     * @param editText
     */
    public static void setCursorDrawable(EditText editText) {
        if (editText == null) {
            Log.w(TAG, "setCursorDrawable null");
            return;
        }
        int accentColor = ThemeStore.accentColor(editText.getContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Drawable cursor = editText.getTextCursorDrawable();
            if (cursor != null) {
                cursor = cursor.mutate();
                cursor.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
                editText.setTextCursorDrawable(cursor);
            }
        } else {
            setCursorDrawableLegacy(editText, accentColor);
        }

        ViewCompat.setBackgroundTintList(editText, new ColorStateList(new int[][]{new int[0]}, new int[]{accentColor}));
    }

    /**
     * Pre-API-29 there is no public API for the cursor drawable, so the private
     * TextView/Editor fields are the only option. The reflective names are hidden
     * (and blocked from API 29 on), hence the suppressions and the silent failure.
     */
    @SuppressLint({"SoonBlockedPrivateApi", "BlockedPrivateApi", "DiscouragedPrivateApi"})
    private static void setCursorDrawableLegacy(EditText editText, int accentColor) {
        try {
            Field cursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            cursorDrawableRes.setAccessible(true);
            int drawableRes = cursorDrawableRes.getInt(editText);
            Field editorField = TextView.class.getDeclaredField("mEditor");
            editorField.setAccessible(true);
            Object editor = editorField.get(editText);
            Drawable drawable = ContextCompat.getDrawable(editText.getContext(), drawableRes);
            drawable.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
            Field cursorDrawable = editor.getClass().getDeclaredField("mCursorDrawable");
            cursorDrawable.setAccessible(true);
            cursorDrawable.set(editor, new Drawable[]{drawable, drawable});
        } catch (Exception e) {
            // Best effort only: on a platform where these fields are gone the cursor keeps its
            // default color, which is cosmetic, so there is nothing to propagate.
            Log.w(TAG, "could not tint the text cursor", e);
        }
    }
}
