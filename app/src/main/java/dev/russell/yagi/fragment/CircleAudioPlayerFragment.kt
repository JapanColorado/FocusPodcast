package dev.russell.yagi.fragment

import android.os.Bundle
import android.view.View
import dev.russell.yagi.theme.ThemeStore.Companion.accentColor
import dev.russell.yagi.theme.util.TintHelper
import dev.russell.yagi.common.extensions.applyColor
import dev.russell.yagi.common.extensions.ripAlpha
import dev.russell.yagi.core.pref.Prefs
import dev.russell.yagi.event.CoverColorChangeEvent

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