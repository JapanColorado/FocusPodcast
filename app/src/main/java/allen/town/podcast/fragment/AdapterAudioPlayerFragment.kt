package allen.town.podcast.fragment

import android.os.Bundle
import android.view.View
import allen.town.podcast.event.CoverColorChangeEvent

/**
 * Known bug: when chapters are present, tapping the overflow menu and then switching the color has no effect; tapping overflow again fixes it.
 */
class AdapterAudioPlayerFragment : AudioPlayerFragment(false) {
    override fun coverColorUpdate(event: CoverColorChangeEvent) {
        super.coverColorUpdate(event)
//        colorGradientBackground.visibility = GONE
//        colorBackground.visibility = VISIBLE
//        colorBackground.setBackgroundColor(event.color.backgroundColor)
//        ToolbarContentTintHelper.colorizeToolbar(toolbar, event.color.secondaryTextColor, activity)


//        val colorFinal = if (UserPreferences.isAdapterColor()) {
//            event.color.primaryTextColor
//        } else {
//            ThemeStore.accentColor(requireContext())
//        }.ripAlpha()
        //with this line there is a reproducible issue: setting a "custom color" during playback and coming back leaves the play button animation unfinished
//        TintHelper.setTintAuto(
//            butPlay,
//            getPrimaryTextColor(
//                requireContext(),
//                isColorLight(colorFinal)
//            ),
//            false
//        )
//        TintHelper.setTintAuto(playPauseButton, colorFinal, true)
//        sbPosition.applyColor(colorFinal)

//        butFF.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN)
//        butRev.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN)
//        butSkip.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
//        txtvRev.setTextColor(accentColor)
//        txtvFF.setTextColor(accentColor)
//        txtvPlaybackSpeed.setTextColor(accentColor)
//        txtvPosition.setTextColor(lastPlaybackControlsColor)
//        txtvLength.setTextColor(lastPlaybackControlsColor)

//        butRev.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
//        butFF.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        ToolbarContentTintHelper.colorizeToolbar(toolbar, Color.WHITE, activity)
    }



}