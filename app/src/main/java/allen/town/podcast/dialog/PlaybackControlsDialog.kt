package allen.town.podcast.dialog

import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.R
import allen.town.podcast.core.feed.util.AdSkipUtils
import allen.town.podcast.core.feed.util.AudioEffectUtils
import allen.town.podcast.core.feed.util.PlayableFeedPreferences
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.databinding.AudioControlsBinding
import allen.town.podcast.event.settings.AudioEffectsChangedEvent
import allen.town.podcast.fragment.pref.ClearVoicePreferences
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.model.feed.FeedPreferences
import allen.town.podcast.model.playback.ClearVoiceLevel
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

/**
 * The player's audio effects dialog: Voice, Skip silence, Stereo to mono and, for a podcast,
 * Detect ads. For an episode of a subscribed podcast every change is stored for that podcast: the
 * first change copies the global defaults into the podcast and then applies the change, so the
 * podcast keeps its own effects from then on. While it has them a card offers "Reset to default"
 * (follow Settings again) and "Set as default" (make its values the defaults, then follow them).
 * Anything else (media without a podcast, a podcast that is only being previewed) changes the
 * global defaults directly, so the card never shows.
 */
class PlaybackControlsDialog : DialogFragment() {
    private var controller: PlaybackController? = null
    private var _binding: AudioControlsBinding? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var disposable: Disposable? = null

    /** The playing episode's podcast, or null when there is none. */
    private var feed: Feed? = null

    override fun onStart() {
        super.onStart()
        val playbackController = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                setupAudioTracks()
            }
        }
        controller = playbackController
        playbackController.init()

        val feedId = requireArguments().getLong(ARG_FEED_ID)
        disposable = Maybe.create { emitter: MaybeEmitter<Feed> ->
            val loadedFeed = if (feedId != 0L) DBReader.getFeed(feedId) else null
            if (loadedFeed != null) {
                emitter.onSuccess(loadedFeed)
            } else {
                emitter.onComplete()
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { loadedFeed: Feed ->
                    feed = loadedFeed
                    setupUi()
                },
                { error: Throwable ->
                    Log.e(TAG, "could not load feed $feedId", error)
                    feed = null
                    setupUi()
                },
                {
                    feed = null
                    setupUi()
                })
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
            .setTitle(R.string.audio_effects)
            .setView(binding.root)
            .setPositiveButton(R.string.close_label, null).create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Binds the switches to the values in effect. Only call once the feed has been loaded, since
     * whether a change goes to the podcast or to the global defaults depends on it.
     */
    private fun setupUi() {
        val binding = _binding ?: return
        val loadedFeed = feed
        // non-null exactly when changes are stored for this podcast
        val feedPreferences = PlayableFeedPreferences.of(loadedFeed)

        bindVoice(binding, AudioEffectUtils.getClearVoiceLevel(feedPreferences))
        bindSwitch(binding.skipSilence, AudioEffectUtils.isSkipSilence(feedPreferences)) { checked ->
            changeEffect({ it.isSkipSilence = checked }, { Prefs.isSkipSilence = checked })
        }
        bindSwitch(binding.stereoToMono, AudioEffectUtils.isMono(feedPreferences)) { checked ->
            changeEffect({ it.isMono = checked }, { Prefs.stereoToMono(checked) })
        }

        if (loadedFeed != null && feedPreferences != null) {
            binding.detectAdsRow.visibility = View.VISIBLE
            bindSwitch(binding.detectAds, AdSkipUtils.isAdSkipEnabled(feedPreferences)) { checked ->
                AdSkipUtils.setAdSkipForFeed(loadedFeed, checked)
                updateOwnSettingsCard()
            }
            binding.ownSettingsCard.resetToDefault.setOnClickListener { resetToDefault(loadedFeed, feedPreferences) }
            binding.ownSettingsCard.setAsDefault.setOnClickListener { setAsDefault(loadedFeed, feedPreferences) }
        } else {
            binding.detectAdsRow.visibility = View.GONE
        }
        updateOwnSettingsCard()
    }

    /**
     * Original / Clear / Clear+ / Warm as a single-selection button row, with a line under the
     * title that describes the selected mode.
     */
    private fun bindVoice(binding: AudioControlsBinding, level: Int) {
        val group = binding.clearVoiceGroup
        group.clearOnButtonCheckedListeners()
        group.check(
            when (level) {
                ClearVoiceLevel.WARM -> R.id.voice_warm
                ClearVoiceLevel.STRONG -> R.id.voice_clear_plus
                ClearVoiceLevel.LIGHT -> R.id.voice_clear
                else -> R.id.voice_original
            }
        )
        binding.voiceDescription.text = ClearVoicePreferences.description(resources, level)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            val chosen = when (checkedId) {
                R.id.voice_warm -> ClearVoiceLevel.WARM
                R.id.voice_clear_plus -> ClearVoiceLevel.STRONG
                R.id.voice_clear -> ClearVoiceLevel.LIGHT
                else -> ClearVoiceLevel.OFF
            }
            binding.voiceDescription.text = ClearVoicePreferences.description(resources, chosen)
            changeEffect({ it.clearVoiceLevel = chosen }, { Prefs.setClearVoiceLevel(chosen) })
        }
    }

    private fun bindSwitch(switch: CompoundButton, checked: Boolean, onChange: (Boolean) -> Unit) {
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = checked
        switch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> onChange(isChecked) }
    }

    /**
     * Applies one effect change: to the podcast when it can have its own settings (copying the
     * current defaults into it first if it had none), otherwise to the global defaults.
     */
    private fun changeEffect(changeFeed: (FeedPreferences) -> Unit, changeGlobal: () -> Unit) {
        val loadedFeed = feed
        val feedPreferences = PlayableFeedPreferences.of(loadedFeed)
        if (loadedFeed != null && feedPreferences != null) {
            AudioEffectUtils.customize(feedPreferences)
            changeFeed(feedPreferences)
            AudioEffectUtils.saveFeedEffects(loadedFeed.id, feedPreferences)
        } else {
            changeGlobal()
            EventBus.getDefault().post(AudioEffectsChangedEvent.global())
        }
        updateOwnSettingsCard()
    }

    /** Makes the podcast follow the global effects and ad detection default again. */
    private fun resetToDefault(loadedFeed: Feed, feedPreferences: FeedPreferences) {
        if (feedPreferences.isUseFeedEffect) {
            feedPreferences.isUseFeedEffect = false
            AudioEffectUtils.saveFeedEffects(loadedFeed.id, feedPreferences)
        }
        if (feedPreferences.adSkipOverride != null) {
            AdSkipUtils.resetAdSkipForFeed(loadedFeed)
        }
        setupUi()
    }

    /**
     * Makes the podcast's effects and ad detection choice the global defaults (only what it has
     * of its own), then lets it follow them again.
     */
    private fun setAsDefault(loadedFeed: Feed, feedPreferences: FeedPreferences) {
        AudioEffectUtils.makeDefault(loadedFeed.id, feedPreferences)
        AdSkipUtils.makeDefault(loadedFeed)
        Toast.makeText(requireContext(), R.string.default_updated, Toast.LENGTH_SHORT).show()
        setupUi()
    }

    private fun updateOwnSettingsCard() {
        val binding = _binding ?: return
        val feedPreferences = PlayableFeedPreferences.of(feed)
        val hasOwnSettings = feedPreferences != null
                && (AudioEffectUtils.hasOwnEffects(feedPreferences) || feedPreferences.adSkipOverride != null)
        binding.ownSettingsCard.root.visibility = if (hasOwnSettings) View.VISIBLE else View.GONE
    }

    /** Shows a button that cycles through the audio tracks when the media has more than one. */
    private fun setupAudioTracks() {
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
        private const val ARG_FEED_ID = "feed_id"

        /** @param feedId the playing episode's feed, or 0 for media that is not a feed episode */
        @JvmStatic
        fun newInstance(feedId: Long): PlaybackControlsDialog {
            val arguments = Bundle()
            arguments.putLong(ARG_FEED_ID, feedId)
            val dialog = PlaybackControlsDialog()
            dialog.arguments = arguments
            return dialog
        }
    }
}
