package allen.town.podcast.activity.main

import allen.town.podcast.common.util.BasePreferenceUtil.materialYou
import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.fragment.AudioPlayerFragment
import allen.town.podcast.playback.LibraryViewModel
import allen.town.podcast.playback.onPaletteColorChanged
import android.graphics.Color
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import allen.town.podcast.theme.ThemeStore.Companion.accentColor
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.snackbar.Snackbar

/**
 * Owns the mini/full player bottom sheet of MainActivity: the [BottomSheetBehavior] over the
 * audio player fragment, its [BottomSheetCallback] (which cross-fades the mini player and pushes
 * the collapsed/expanded system-bar colours), the margin bookkeeping that keeps the main content
 * clear of the mini player, the palette colour observed from [LibraryViewModel], and the snackbars
 * that have to be anchored above the sheet. Everything here is view/state manipulation on the
 * activity it is constructed with; it owns no fragments and starts no work of its own.
 */
internal class MainPlayerSheet(
    private val activity: AppCompatActivity,
    /** Unused by the callback, but [BottomSheetCallback] does not accept a null view. */
    private val anyView: View
) {
    val bottomSheet: BottomSheetBehavior<View> =
        BottomSheetBehavior.from(activity.findViewById<View>(R.id.audioplayerFragment))

    private var paletteColor = Color.WHITE

    private lateinit var libraryViewModel: LibraryViewModel

    private val bottomSheetCallback: BottomSheetCallback = object : BottomSheetCallback() {
        /**
         * Called when the state changes
         * @param view
         * @param state
         */
        override fun onStateChanged(view: View, state: Int) {
            if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                onSlide(view, 0.0f)
                MainActivity.onPanelCollapsed(activity)
            } else if (state == BottomSheetBehavior.STATE_EXPANDED) {
                onSlide(view, 1.0f)
                onPaletteColorChanged()
            }
        }

        /**
         * Called while sliding
         * @param view
         * @param slideOffset
         */
        override fun onSlide(view: View, slideOffset: Float) {
            val audioPlayer = activity.supportFragmentManager
                .findFragmentByTag(AudioPlayerFragment.TAG) as AudioPlayerFragment? ?: return
            if (slideOffset == 0.0f) { //STATE_COLLAPSED
                audioPlayer.scrollToPage(AudioPlayerFragment.POS_COVER)
            }
            val condensedSlideOffset = Math.max(0.0f, Math.min(0.2f, slideOffset - 0.2f)) / 0.2f
            audioPlayer.externalPlayerHolder.alpha = 1 - condensedSlideOffset
            audioPlayer.externalPlayerHolder.visibility =
                if (condensedSlideOffset > 0.99f) View.GONE else View.VISIBLE
        }
    }

    init {
        bottomSheet.setPeekHeight(
            activity.resources.getDimension(R.dimen.external_player_height).toInt()
        )
        bottomSheet.setHideable(false)
        bottomSheet.setBottomSheetCallback(bottomSheetCallback)
    }

    fun observePaletteColor() {
        libraryViewModel = ViewModelProvider(activity).get(
            LibraryViewModel::class.java
        )
        libraryViewModel.paletteColor.observe(activity) { color: Int ->
            paletteColor = color
            onPaletteColorChanged()
        }
    }

    /**
     * Album cover color changed
     */
    private fun onPaletteColorChanged() {
        if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            activity.onPaletteColorChanged(paletteColor)
        }
    }

    /** Re-run the expanded layout after a configuration change restored an expanded sheet. */
    fun onRestoreInstanceState() {
        if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            //re-open
            bottomSheetCallback.onSlide(anyView, 1.0f)
        }
    }

    /** Expand the sheet, e.g. because an intent asked for the player. */
    fun expand() {
        bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetCallback.onSlide(anyView, 1.0f)
    }

    fun collapse() {
        bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
    }

    fun setPlayerVisible(visible: Boolean) {
        if (visible) {
            //the first argument is unused but must not be null, so anything is passed
            bottomSheetCallback.onStateChanged(anyView, bottomSheet.state) // Update toolbar visibility
        } else {
            bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }

        val mainView = activity.findViewById<FragmentContainerView>(R.id.main_view)
        val params = mainView.layoutParams as MarginLayoutParams
        params.setMargins(
            0,
            0,
            0,
            if (visible) activity.resources.getDimension(R.dimen.external_player_height)
                .toInt() else 0
        )
        mainView.layoutParams = params
        activity.findViewById<View>(R.id.audioplayerFragment).visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Show a snackbar above the player
     * @param text
     * @param duration
     * @return
     */
    fun showSnackbarAbovePlayer(text: CharSequence?, duration: Int): Snackbar {
        val message = text ?: ""
        val s: Snackbar
        if (bottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
            s = Snackbar.make(activity.findViewById(R.id.main_view), message, duration)
            if (activity.findViewById<View>(R.id.audioplayerFragment).visibility == View.VISIBLE) {
                s.anchorView = activity.findViewById(R.id.audioplayerFragment)
            }
        } else {
            s = Snackbar.make(activity.findViewById(android.R.id.content), message, duration)
        }
        if (!materialYou) {
            s.setActionTextColor(accentColor(activity))
        }
        s.show()
        return s
    }
}
