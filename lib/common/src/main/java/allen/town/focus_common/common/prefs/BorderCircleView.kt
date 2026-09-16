package allen.town.focus_common.common.prefs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import code.name.monkey.appthemehelper.R
import kotlin.math.min

class BorderCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val mCheck: Drawable? = ContextCompat.getDrawable(context, R.drawable.ate_check)
    private val paint: Paint = Paint()
    private val paintBorder: Paint
    private val borderWidth: Int = resources.getDimension(R.dimen.ate_circleview_border).toInt()

    private var paintCheck: Paint? = null
    private var blackFilter: PorterDuffColorFilter? = null
    private var whiteFilter: PorterDuffColorFilter? = null

    init {

        paint.isAntiAlias = true

        paintBorder = Paint()
        paintBorder.isAntiAlias = true
        paintBorder.color = Color.BLACK

        setWillNotDraw(false)
    }

    override fun setBackgroundColor(color: Int) {
        paint.color = color
        requestLayout()
        invalidate()
    }

    fun setBorderColor(color: Int) {
        paintBorder.color = color
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
            val width = MeasureSpec.getSize(widthMeasureSpec)

            var height = width
            if (heightMode == MeasureSpec.AT_MOST) {
                height = min(height, MeasureSpec.getSize(heightMeasureSpec))
            }
            setMeasuredDimension(width, height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        var canvasSize = canvas.width
        if (canvas.height < canvasSize) {
            canvasSize = canvas.height
        }

        val circleCenter = (canvasSize - borderWidth * 2) / 2
        canvas.drawCircle(
            (circleCenter + borderWidth).toFloat(),
            (circleCenter + borderWidth).toFloat(),
            (canvasSize - borderWidth * 2) / 2 + borderWidth - 4.0f,
            paintBorder
        )
        canvas.drawCircle(
            (circleCenter + borderWidth).toFloat(),
            (circleCenter + borderWidth).toFloat(),
            (canvasSize - borderWidth * 2) / 2 - 4.0f,
            paint
        )

        if (isActivated) {
            // Without the check drawable there is nothing left to draw on top of the circle.
            val check = mCheck ?: return
            val offset = canvasSize / 2 - check.intrinsicWidth / 2
            val checkPaint = paintCheck ?: Paint().also {
                it.isAntiAlias = true
                paintCheck = it
            }
            if (whiteFilter == null || blackFilter == null) {
                blackFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                whiteFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            checkPaint.colorFilter = if (paint.color == Color.WHITE) blackFilter else whiteFilter
            check.setBounds(offset, offset, check.intrinsicWidth - offset, check.intrinsicHeight - offset)
            check.draw(canvas)
        }
    }
}
