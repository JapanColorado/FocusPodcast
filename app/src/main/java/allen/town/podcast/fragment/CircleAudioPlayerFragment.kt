package allen.town.podcast.fragment

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import code.name.monkey.appthemehelper.ThemeStore.Companion.accentColor
import code.name.monkey.appthemehelper.util.TintHelper
import allen.town.focus_common.extensions.applyColor
import allen.town.focus_common.extensions.ripAlpha
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.event.CoverColorChangeEvent

/**
 * Known bug: when chapters are present, tapping the overflow menu and then switching the color has no effect; tapping overflow again fixes it.
 */
class CircleAudioPlayerFragment : AudioPlayerFragment(false) {
    override fun coverColorUpdate(event: CoverColorChangeEvent) {
        super.coverColorUpdate(event)
//        colorGradientBackground.visibility = GONE
//        _binding.colorBackground.visibility = VISIBLE
//        _binding.colorBackground.setBackgroundColor(event.color.backgroundColor)
//        ToolbarContentTintHelper.colorizeToolbar(_binding.toolbar, event.color.secondaryTextColor, activity)
//        sbPosition.applyColor(accentColor(requireContext()))
//        ViewUtil.setProgressDrawable(
//            sbPosition,
//            accentColor(requireContext()),
//            false
//        )
        var colorFinal: Int
        colorFinal = if (Prefs.isAdapterColor) {
            event.color.primaryTextColor
        } else {
            accentColor(requireContext())
        }
        colorFinal = colorFinal.ripAlpha()

        TintHelper.setTintAuto(playPauseButton, colorFinal, true)
        TintHelper.setTintAuto(butPlay, event.color.backgroundColor, false)
        sbPosition.applyColor(colorFinal)

//        lastPlaybackControlsColor = event.color.secondaryTextColor

        butFF.setColorFilter(colorFinal, PorterDuff.Mode.SRC_IN)
        butRev.setColorFilter(colorFinal, PorterDuff.Mode.SRC_IN)
        butSkip.setColorFilter(colorFinal, PorterDuff.Mode.SRC_IN)
        txtvRev.setTextColor(colorFinal)
        txtvFF.setTextColor(colorFinal)
        txtvPlaybackSpeed.setTextColor(colorFinal)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        ToolbarContentTintHelper.colorizeToolbar(_binding.toolbar, Color.WHITE, activity)
    }



}