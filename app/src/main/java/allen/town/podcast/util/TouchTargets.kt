package allen.town.podcast.util

import android.graphics.Rect
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/**
 * Grows a small view's touch area to the 48dp accessibility minimum without resizing it.
 *
 * Only for views that cannot simply be made larger, e.g. because they sit in a row shorter
 * than 48dp or their drawn background scales with their size. Everywhere else, prefer a
 * 48dp view with padding (and negative margins where the surrounding layout must not move).
 */
object TouchTargets {
    private const val MIN_TOUCH_TARGET_DP = 48

    /**
     * Installs a [TouchDelegate] on [host] that routes touches in a 48dp square centred on
     * [target] to it. The rectangle is recomputed whenever [host] is laid out, so it follows
     * [target] when a list row is rebound or the target is shown or hidden.
     *
     * [host] must be an ancestor of [target] and should not be clickable itself, since a view
     * holds only one delegate and its children still get the first chance at every touch.
     * Touches outside [host]'s own bounds never reach the delegate.
     */
    @JvmStatic
    fun expandTouchArea(target: View, host: ViewGroup) {
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update(target, host) }
    }

    private fun update(target: View, host: ViewGroup) {
        if (target.visibility != View.VISIBLE || !isDescendant(target, host)) {
            host.touchDelegate = null
            return
        }
        val bounds = Rect()
        target.getDrawingRect(bounds)
        host.offsetDescendantRectToMyCoords(target, bounds)
        val minSize = (MIN_TOUCH_TARGET_DP * host.resources.displayMetrics.density).roundToInt()
        val growX = ((minSize - bounds.width()) / 2).coerceAtLeast(0)
        val growY = ((minSize - bounds.height()) / 2).coerceAtLeast(0)
        bounds.inset(-growX, -growY)
        host.touchDelegate = TouchDelegate(bounds, target)
    }

    private fun isDescendant(view: View, ancestor: ViewGroup): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === ancestor) {
                return true
            }
            parent = parent.parent
        }
        return false
    }
}
