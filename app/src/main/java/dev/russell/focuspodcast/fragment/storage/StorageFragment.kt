package dev.russell.focuspodcast.fragment.storage

import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.activity.MainActivity
import dev.russell.focuspodcast.activity.SettingsActivity
import dev.russell.focuspodcast.common.util.TopSnackbarUtil
import dev.russell.focuspodcast.common.views.AccentMaterialDialog
import dev.russell.focuspodcast.core.pref.Prefs
import dev.russell.focuspodcast.core.storage.DBWriter
import dev.russell.focuspodcast.core.storage.StorageUsage
import dev.russell.focuspodcast.databinding.StorageFragmentBinding
import dev.russell.focuspodcast.event.FeedItemEvent
import dev.russell.focuspodcast.fragment.DownloadPagerFragment
import dev.russell.focuspodcast.common.util.ThemeUtils
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import dev.chrisbanes.insetter.applyInsetter
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Settings > Downloads > Storage usage (also reachable from the Downloads screen's menu): how much
 * space the downloads take, how much is free, and the podcasts by size. Tapping a podcast offers
 * to delete its played or all of its downloads and to open it. Hosted by [SettingsActivity].
 */
class StorageFragment : Fragment(), StorageAdapter.Listener {

    private var _binding: StorageFragmentBinding? = null
    private lateinit var adapter: StorageAdapter
    private var loadDisposable: Disposable? = null
    private var deleteDisposable: Disposable? = null
    private var playerEpisodeTitle: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val reloadRunnable = Runnable { load() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = StorageFragmentBinding.inflate(inflater, container, false)
        _binding = binding
        adapter = StorageAdapter(requireContext(), this)
        binding.storageList.layoutManager = LinearLayoutManager(requireContext())
        binding.storageList.adapter = adapter
        binding.storageList.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }
        EventBus.getDefault().register(this)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (activity as? SettingsActivity)?.setTitle(R.string.storage_usage_title)
        load()
    }

    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        handler.removeCallbacks(reloadRunnable)
        loadDisposable?.dispose()
        deleteDisposable?.dispose()
        _binding = null
        super.onDestroyView()
    }

    private fun load() {
        loadDisposable?.dispose()
        loadDisposable = Single.fromCallable { StorageSnapshot.load() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ snapshot ->
                val binding = _binding ?: return@subscribe
                binding.storageProgress.visibility = View.GONE
                binding.storageList.visibility = View.VISIBLE
                playerEpisodeTitle = snapshot.playerEpisodeTitle
                adapter.update(snapshot)
            }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    /** Deleting many episodes posts one event each; reload once they have settled. */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedItemEvent(@Suppress("UNUSED_PARAMETER") event: FeedItemEvent) {
        handler.removeCallbacks(reloadRunnable)
        handler.postDelayed(reloadRunnable, RELOAD_DELAY_MS)
    }

    override fun onFeedClicked(summary: StorageUsage.FeedSummary) {
        val context = requireContext()
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        if (summary.playedCount > 0) {
            labels.add(getString(R.string.storage_delete_played, summary.playedCount,
                Formatter.formatShortFileSize(context, summary.playedBytes)))
            actions.add { confirmDeletePlayed(summary) }
        }
        if (summary.deletableCount > 0) {
            labels.add(getString(R.string.storage_delete_all, summary.deletableCount,
                Formatter.formatShortFileSize(context, summary.deletableBytes)))
            actions.add { confirmDeleteAll(summary) }
        }
        if (summary.isUnsubscribedBucket) {
            labels.add(getString(R.string.storage_show_downloads))
            actions.add { showDownloads() }
        } else {
            labels.add(getString(R.string.storage_open_podcast))
            actions.add { openPodcast(summary.bucketId) }
        }
        val dialog = AccentMaterialDialog(context, R.style.MaterialAlertDialogTheme)
            .setTitle(summary.title ?: getString(R.string.storage_unsubscribed_title))
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(R.string.cancel_label, null)
        if (summary.holdsPlayerEpisode) {
            // shown under the actions, so it is clear why one download is not offered
            dialog.setView(playerEpisodeNote(context))
        }
        dialog.show()
    }

    private fun playerEpisodeNote(context: Context): TextView {
        val title = playerEpisodeTitle
        val note = TextView(context)
        note.text = if (title.isNullOrBlank()) {
            getString(R.string.storage_player_episode_kept)
        } else {
            getString(R.string.storage_player_episode_kept_titled, title)
        }
        note.setTextColor(ThemeUtils.getColorFromAttr(context, android.R.attr.textColorSecondary))
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, NOTE_TEXT_SIZE_SP)
        val horizontal = resources.getDimensionPixelSize(R.dimen.storage_dialog_note_padding_horizontal)
        val vertical = resources.getDimensionPixelSize(R.dimen.storage_dialog_note_padding_vertical)
        note.setPadding(horizontal, vertical, horizontal, vertical)
        return note
    }

    private fun confirmDeletePlayed(summary: StorageUsage.FeedSummary) {
        val context = requireContext()
        val message = if (Prefs.shouldFavoriteKeepEpisode()) {
            getString(R.string.delete_played_confirm_msg)
        } else {
            getString(R.string.storage_delete_played_confirm_msg_favorites_too,
                getString(R.string.pref_favorite_keeps_episodes_title))
        }
        AccentMaterialDialog(context, R.style.MaterialAlertDialogTheme)
            .setTitle(resources.getQuantityString(R.plurals.delete_played_confirm,
                summary.playedCount, summary.playedCount,
                Formatter.formatShortFileSize(context, summary.playedBytes)))
            .setMessage(message)
            .setPositiveButton(R.string.delete_label) { _, _ -> delete(summary.bucketId, true) }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun confirmDeleteAll(summary: StorageUsage.FeedSummary) {
        val context = requireContext()
        AccentMaterialDialog(context, R.style.MaterialAlertDialogTheme)
            .setTitle(resources.getQuantityString(R.plurals.storage_delete_all_confirm,
                summary.deletableCount, summary.deletableCount,
                Formatter.formatShortFileSize(context, summary.deletableBytes)))
            .setMessage(R.string.storage_delete_all_confirm_msg)
            .setPositiveButton(R.string.delete_label) { _, _ -> delete(summary.bucketId, false) }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun delete(bucketId: Long, playedOnly: Boolean) {
        val appContext = requireContext().applicationContext
        deleteDisposable?.dispose()
        deleteDisposable = Single.fromCallable { DBWriter.deleteStorageBucket(appContext, bucketId, playedOnly).get() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ deleted ->
                val activity = activity ?: return@subscribe
                TopSnackbarUtil.showSnack(activity, activity.resources.getQuantityString(
                    R.plurals.deleted_multi_episode_batch_label, deleted, deleted), Toast.LENGTH_SHORT)
                load()
            }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun openPodcast(feedId: Long) {
        val intent = MainActivity.getIntentToOpenFeedWithId(requireContext(), feedId)
        // as a child page, so back returns to where the user was in the main screen
        intent.putExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH, true)
        startActivity(intent)
    }

    private fun showDownloads() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_FRAGMENT_TAG, DownloadPagerFragment.TAG)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    override fun onCleanupClicked() {
        val settings = activity as? SettingsActivity ?: return
        settings.openScreen(R.xml.pref_downloads)?.scrollToPreference(Prefs.PREF_EPISODE_CLEANUP)
    }

    companion object {
        private const val TAG = "StorageFragment"
        private const val RELOAD_DELAY_MS = 400L
        private const val NOTE_TEXT_SIZE_SP = 14f
    }
}
