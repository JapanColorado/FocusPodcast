package dev.russell.focuspodcast.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import dev.russell.focuspodcast.theme.util.ColorUtil
import dev.russell.focuspodcast.theme.util.TintHelper
import dev.russell.focuspodcast.theme.util.ToolbarContentTintHelper
import dev.russell.focuspodcast.common.extensions.applyColor
import dev.russell.focuspodcast.common.extensions.colorControlNormal
import dev.russell.focuspodcast.common.extensions.isColorLight
import dev.russell.focuspodcast.event.CoverColorChangeEvent

/**
 * Known bug: when chapters are present, tapping the overflow menu and then switching the color has no effect; tapping overflow again fixes it.
 */
class FullAudioPlayerFragment : AudioPlayerFragment(false) {
    override fun coverColorUpdate(event: CoverColorChangeEvent) {
        super.coverColorUpdate(event)
        val thirdColor = if(event.color.backgroundColor.isColorLight) colorControlNormal() else  Color.WHITE
        ToolbarContentTintHelper.colorizeToolbar(toolbar, thirdColor, activity)

        TintHelper.setTintAuto(playPauseButton, event.color.primaryTextColor, true)
        TintHelper.setTintAuto(butPlay, event.color.backgroundColor, false)
        sbPosition.applyColor(event.color.primaryTextColor)

        lastPlaybackControlsColor = event.color.primaryTextColor
        lastDisabledPlaybackControlsColor = ColorUtil.withAlpha(event.color.primaryTextColor, 0.5f)

        applyControlsColor(lastPlaybackControlsColor)
        txtvPosition.setTextColor(lastDisabledPlaybackControlsColor)
        txtvLength.setTextColor(lastDisabledPlaybackControlsColor)

        event.coverViewHolder?.chapterTv?.setTextColor(thirdColor)
        event.coverViewHolder?.episodeTv?.setTextColor(thirdColor)
        event.coverViewHolder?.podcastTv?.setTextColor(thirdColor)
        event.coverViewHolder?.preChapterIv?.setColorFilter(thirdColor, PorterDuff.Mode.SRC_IN)
        event.coverViewHolder?.nextChapterIv?.setColorFilter(thirdColor, PorterDuff.Mode.SRC_IN)

        mask.backgroundTintList = ColorStateList.valueOf(event.color.backgroundColor)

        if (isFromScreenActivity) {
            swipeUpIv.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN)
            swipeUpTipTv.setTextColor(lastPlaybackControlsColor)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }




}