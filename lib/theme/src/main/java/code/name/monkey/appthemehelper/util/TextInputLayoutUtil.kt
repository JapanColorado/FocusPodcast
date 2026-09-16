package code.name.monkey.appthemehelper.util

import android.content.res.ColorStateList
import android.util.Log
import androidx.annotation.ColorInt
import com.google.android.material.textfield.TextInputLayout

/**
 * @author Aidan Follestad (afollestad)
 */
object TextInputLayoutUtil {
    val TAG = "TextInputLayoutUtil"

    fun setHint(view: TextInputLayout, @ColorInt hintColor: Int) {
        try {
            val mDefaultTextColorField = TextInputLayout::class.java.getDeclaredField("mDefaultTextColor")
            mDefaultTextColorField.isAccessible = true
            mDefaultTextColorField.set(view, ColorStateList.valueOf(hintColor))
        } catch (t: Throwable) {
            Log.e(TAG,t.stackTraceToString())
        }

    }

    fun setAccent(view: TextInputLayout, @ColorInt accentColor: Int) {
        try {
            val mFocusedTextColorField = TextInputLayout::class.java.getDeclaredField("mFocusedTextColor")
            mFocusedTextColorField.isAccessible = true
            mFocusedTextColorField.set(view, ColorStateList.valueOf(accentColor))
        } catch (t: Throwable) {
            Log.e(TAG,t.stackTraceToString())
        }

    }
}