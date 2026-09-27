package allen.town.podcast.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Draws an icon in place of its text, sized to the current text size and centred on the line.
 * Unlike ImageSpan it follows auto-sized text, and it leaves the line height to the font.
 */
public class TextSizedIconSpan extends ReplacementSpan {
    private final Drawable icon;
    private final float scale;

    /**
     * @param scale icon edge as a multiple of the text size
     */
    public TextSizedIconSpan(@NonNull Drawable icon, float scale) {
        this.icon = icon;
        this.scale = scale;
    }

    private int iconSize(Paint paint) {
        return Math.round(paint.getTextSize() * scale);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                       @Nullable Paint.FontMetricsInt fm) {
        if (fm != null) {
            paint.getFontMetricsInt(fm);
        }
        return iconSize(paint);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
        int size = iconSize(paint);
        // Centre on the middle of the glyphs, which is where digits sit, not on the line box.
        float glyphCentre = y + (paint.ascent() + paint.descent()) / 2f;
        int left = Math.round(x);
        int iconTop = Math.round(glyphCentre - size / 2f);
        icon.setBounds(left, iconTop, left + size, iconTop + size);
        icon.draw(canvas);
    }
}
