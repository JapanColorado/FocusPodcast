package allen.town.podcast.dialog

import allen.town.focus_common.views.AccentMaterialDialog
import allen.town.podcast.R
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.databinding.AudioControlsBinding
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.util.NavigationUtil
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class PlaybackControlsDialog : DialogFragment() {
    private var controller: PlaybackController? = null
    private var _binding: AudioControlsBinding? = null
    private val binding get() = _binding ?: error("binding accessed outside of view lifecycle")
    private var feedId = 0L
    private val uiHandler = Handler(Looper.getMainLooper())
    private var disposable: Disposable? = null
    private var feed: Feed? = null

    override fun onStart() {
        super.onStart()
        val playbackController = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
            }
        }
        controller = playbackController
        playbackController.init()

        disposable = Maybe.create { emitter: MaybeEmitter<Feed> ->
            val loadedFeed = DBReader.getFeed(feedId)
            feed = loadedFeed
            if (loadedFeed != null) {
                emitter.onSuccess(loadedFeed)
            } else {
                emitter.onComplete()
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { setupUi() },
                { error: Throwable? ->
                    Log.d(
                        TAG,
                        Log.getStackTraceString(error)
                    )
                }) {}
    }

    override fun onStop() {
        super.onStop()
        uiHandler.removeCallbacksAndMessages(null)
        disposable?.dispose()
        disposable = null
        controller?.release()
        controller = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = AudioControlsBinding.inflate(layoutInflater)
        _binding = binding
        return AccentMaterialDialog(
            requireContext(),
            R.style.MaterialAlertDialogTheme
        )
            .setTitle( /*R.string.audio_controls*/R.string.audio_effects) //odd issue: writing R.layout.audio_controls directly here gives the switch the wrong color in its off state
            .setView(binding.root)
            .setPositiveButton(R.string.close_label, null).create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Only call once the database has been queried for whether the feed uses per-feed settings; otherwise the state is wrong and onCheckedChanged leaves things inconsistent.
     */
    private fun setupUi() {
        val binding = _binding ?: return

        val loadedFeed = feed
        val feedPreferences = loadedFeed?.preferences
        var useFeedEffect = false
        if (loadedFeed != null && feedPreferences != null) {
            binding.customEffectClear.setOnClickListener {
                feedPreferences.isUseFeedEffect = false
                DBWriter.setFeedPreferences(feedPreferences)
                setupUi()
            }

            if (loadedFeed.isSubscribed && feedPreferences.isUseFeedEffect) {
                binding.customEffectL.visibility = View.VISIBLE
                useFeedEffect = true
            } else {
                binding.customEffectL.visibility = View.GONE
            }
        }
        // non-null exactly when the per-feed effect settings are the ones in use
        val effectPreferences = if (useFeedEffect) feedPreferences else null

        //skip silence
        binding.skipSilence.isChecked = effectPreferences?.isSkipSilence ?: Prefs.isSkipSilence
        binding.skipSilence.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (effectPreferences != null) {
                effectPreferences.isSkipSilence = isChecked
                DBWriter.setFeedPreferences(effectPreferences)
            } else {
                Prefs.isSkipSilence = isChecked
            }

            controller?.setSkipSilence(isChecked)
        }

        //mono
        binding.stereoToMono.isChecked = effectPreferences?.isMono ?: Prefs.stereoToMono()
        binding.stereoToMono.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (effectPreferences != null) {
                effectPreferences.isMono = isChecked
                DBWriter.setFeedPreferences(effectPreferences)
            } else {
                Prefs.stereoToMono(isChecked)
            }

            controller?.setDownmix(isChecked)
        }

        //vocal enhancement
        binding.vocalEnhancement.isChecked = effectPreferences?.isLoudness ?: Prefs.audioLoudness()
        binding.vocalEnhancement.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (effectPreferences != null) {
                effectPreferences.isLoudness = isChecked
                DBWriter.setFeedPreferences(effectPreferences)
            } else {
                Prefs.setAudioLoudness(isChecked)
            }

            controller?.setLoudness(isChecked)
        }
        binding.equalizer.setOnClickListener { NavigationUtil.openEqualizer(requireActivity()) }
    }


    private fun setupAudioTracks() {
        //unclear what this is for; this branch is never reached
        val playbackController = controller ?: return
        val binding = _binding ?: return
        val audioTracks = playbackController.audioTracks
        val selectedAudioTrack = playbackController.selectedAudioTrack
        val butAudioTracks = binding.audioTracks
        if (audioTracks.size < 2 || selectedAudioTrack < 0) {
            butAudioTracks.visibility = View.GONE
            return
        }
        butAudioTracks.visibility = View.VISIBLE
        butAudioTracks.text = audioTracks[selectedAudioTrack]
        butAudioTracks.setOnClickListener {
            playbackController.setAudioTrack((selectedAudioTrack + 1) % audioTracks.size)
            uiHandler.postDelayed({ setupAudioTracks() }, 500)
        }
    }

    companion object {
        const val TAG = "PlaybackControlsDialog"

        @JvmStatic
        fun newInstance(feedId: Long): PlaybackControlsDialog {
            val arguments = Bundle()
            val dialog = PlaybackControlsDialog()
            dialog.feedId = feedId
            dialog.arguments = arguments
            return dialog
        }
    }
}
