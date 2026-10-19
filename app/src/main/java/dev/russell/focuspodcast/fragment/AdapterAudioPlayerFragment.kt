package dev.russell.focuspodcast.fragment

import android.os.Bundle
import android.view.View
import dev.russell.focuspodcast.event.CoverColorChangeEvent

/**
 * Known bug: when chapters are present, tapping the overflow menu and then switching the color has no effect; tapping overflow again fixes it.
 */
class AdapterAudioPlayerFragment : AudioPlayerFragment(false) {
    override fun coverColorUpdate(event: CoverColorChangeEvent) {
        super.coverColorUpdate(event)





    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }



}