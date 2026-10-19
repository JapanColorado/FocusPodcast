package dev.russell.focuspodcast.fragment

import android.os.Bundle
import android.view.View
import dev.russell.focuspodcast.theme.ThemeStore.Companion.accentColor
import dev.russell.focuspodcast.theme.util.TintHelper
import dev.russell.focuspodcast.common.extensions.applyColor
import dev.russell.focuspodcast.common.extensions.ripAlpha
import dev.russell.focuspodcast.core.pref.Prefs
import dev.russell.focuspodcast.event.CoverColorChangeEvent

/**
 * Known bug: when chapters are present, tapping the overflow menu and then switching the color has no effect; tapping overflow again fixes it.
 */
class CircleAudioPlayerFragment : AudioPlayerFragment(false) {
    override fun coverColorUpdate(event: CoverColorChangeEvent) {
        super.coverColorUpdate(event)
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


        applyControlsColor(colorFinal)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }



}