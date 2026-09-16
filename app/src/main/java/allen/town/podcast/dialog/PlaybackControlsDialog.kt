package allen.town.podcast.dialog

import allen.town.focus_common.util.Timber
import allen.town.focus_common.views.AccentMaterialDialog
import allen.town.podcast.R
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.util.playback.PlaybackController
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.model.feed.FeedPreferences
import allen.town.podcast.util.NavigationUtil
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class PlaybackControlsDialog : DialogFragment() {
    private var controller: PlaybackController? = null
    private var dialog: AlertDialog? = null
    private var feedId = 0L
    private var feed: Feed? = null
        set(value) {
            field = value
        }

    override fun onStart() {
        super.onStart()
        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
            }
        }
        controller!!.init()

        Maybe.create { emitter: MaybeEmitter<Feed?> ->
            feed = DBReader.getFeed(feedId)
            if (feed != null) {
                emitter.onSuccess(feed!!)
            } else {
                emitter.onComplete()
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: Feed? ->
                    setupUi()
                },
                { error: Throwable? ->
                    Log.d(
                        TAG,
                        Log.getStackTraceString(error)
                    )
                }) {}
    }

    override fun onStop() {
        super.onStop()
        controller!!.release()
        controller = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = View.inflate(context, R.layout.audio_controls, null)
        dialog = AccentMaterialDialog(
            requireContext(),
            R.style.MaterialAlertDialogTheme
        )
            .setTitle( /*R.string.audio_controls*/R.string.audio_effects) //odd issue: writing R.layout.audio_controls directly here gives the switch the wrong color in its off state
            .setView(content)
            .setPositiveButton(R.string.close_label, null).create()
        return dialog!!
    }

    /**
     * Only call once the database has been queried for whether the feed uses per-feed settings; otherwise the state is wrong and onCheckedChanged leaves things inconsistent.
     */
    private fun setupUi() {

        var useFeedEffect = false
        var feedPreferences: FeedPreferences? = null
        feed?.run {
            feedPreferences = this.preferences

            val customEffectLayout = dialog!!.findViewById<View>(R.id.custom_effect_l)
            val customEffectClear = dialog!!.findViewById<View>(R.id.custom_effect_clear)
            customEffectClear!!.setOnClickListener {
                feedPreferences!!.isUseFeedEffect = false
                DBWriter.setFeedPreferences(feedPreferences)
                setupUi()
            }

            if (isSubscribed && feedPreferences!!.isUseFeedEffect) {
                customEffectLayout!!.visibility = View.VISIBLE
                useFeedEffect = true
            } else {
                customEffectLayout!!.visibility = View.GONE
                useFeedEffect = false
            }
        }

        //skip silence
        val skipSilence = dialog!!.findViewById<SwitchCompat>(R.id.skipSilence)
        skipSilence!!.isChecked =
            if (useFeedEffect) feedPreferences!!.isSkipSilence else Prefs.isSkipSilence
        skipSilence.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (useFeedEffect) {
                feedPreferences!!.isSkipSilence = isChecked
                DBWriter.setFeedPreferences(feedPreferences)
            } else {
                Prefs.isSkipSilence = isChecked
            }

            if (controller != null) {
                controller!!.setSkipSilence(isChecked)
            }
        }

        //mono
        val stereoToMono = dialog!!.findViewById<SwitchCompat>(R.id.stereo_to_mono)
        stereoToMono!!.isChecked =
            if (useFeedEffect) feedPreferences!!.isMono else Prefs.stereoToMono()
        stereoToMono.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (useFeedEffect) {
                feedPreferences!!.isMono = isChecked
                DBWriter.setFeedPreferences(feedPreferences)
            } else {
                Prefs.stereoToMono(isChecked)
            }

            if (controller != null) {
                controller!!.setDownmix(isChecked)
            }
        }

        //vocal enhancement
        val vocal_enhancement = dialog!!.findViewById<SwitchCompat>(R.id.vocal_enhancement)
        vocal_enhancement!!.isChecked =
            if (useFeedEffect) feedPreferences!!.isLoudness else Prefs.audioLoudness()
        vocal_enhancement.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (useFeedEffect) {
                feedPreferences!!.isLoudness = isChecked
                DBWriter.setFeedPreferences(feedPreferences)
            } else {
                Prefs.setAudioLoudness(isChecked)
            }

            if (controller != null) {
                controller!!.setLoudness(isChecked)
            }
        }
        val equalizer = dialog!!.findViewById<View>(R.id.equalizer)
        equalizer!!.setOnClickListener { NavigationUtil.openEqualizer(requireActivity()) }
    }


    private fun setupAudioTracks() {
        //unclear what this is for; this branch is never reached
        val audioTracks = controller!!.audioTracks
        val selectedAudioTrack = controller!!.selectedAudioTrack
        val butAudioTracks = dialog!!.findViewById<Button>(R.id.audio_tracks)
        if (audioTracks.size < 2 || selectedAudioTrack < 0) {
            butAudioTracks!!.visibility = View.GONE
            return
        }
        butAudioTracks!!.visibility = View.VISIBLE
        butAudioTracks.text = audioTracks[selectedAudioTrack]
        butAudioTracks.setOnClickListener { v: View? ->
            controller!!.setAudioTrack((selectedAudioTrack + 1) % audioTracks.size)
            Handler(Looper.getMainLooper()).postDelayed({ setupAudioTracks() }, 500)
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