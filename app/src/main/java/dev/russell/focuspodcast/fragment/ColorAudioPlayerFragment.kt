package dev.russell.focuspodcast.fragment

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import dev.russell.focuspodcast.theme.util.TintHelper
import dev.russell.focuspodcast.theme.util.ToolbarContentTintHelper
import dev.russell.focuspodcast.common.extensions.applyColor
import dev.russell.focuspodcast.event.CoverColorChangeEvent

/**
 * Known bug: when chapters are present, tapping the overflow menu and then switching the color has no effect; tapping overflow again fixes it.
 */
class ColorAudioPlayerFragment : AudioPlayerFragment(false) {
    override fun coverColorUpdate(event: CoverColorChangeEvent) {
        super.coverColorUpdate(event)
        gradientBackgroundEnabled = false
        colorGradientBackground.visibility = GONE
        colorBackground.visibility = VISIBLE
        colorBackground.setBackgroundColor(event.color.backgroundColor)
        ToolbarContentTintHelper.colorizeToolbar(toolbar, event.color.secondaryTextColor, activity)

        TintHelper.setTintAuto(playPauseButton, event.color.primaryTextColor, true)
        TintHelper.setTintAuto(butPlay, event.color.backgroundColor, false)
        sbPosition.applyColor(event.color.primaryTextColor)

        lastPlaybackControlsColor = event.color.secondaryTextColor

        applyControlsColor(lastPlaybackControlsColor)
        txtvPosition.setTextColor(lastPlaybackControlsColor)
        txtvLength.setTextColor(lastPlaybackControlsColor)

        event.coverViewHolder?.chapterTv?.setTextColor(lastPlaybackControlsColor)
        event.coverViewHolder?.episodeTv?.setTextColor(lastPlaybackControlsColor)
        event.coverViewHolder?.podcastTv?.setTextColor(lastPlaybackControlsColor)
        event.coverViewHolder?.preChapterIv?.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN)
        event.coverViewHolder?.nextChapterIv?.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN)
        if (isFromScreenActivity) {
            swipeUpIv.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN)
            swipeUpTipTv.setTextColor(lastPlaybackControlsColor)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }




}