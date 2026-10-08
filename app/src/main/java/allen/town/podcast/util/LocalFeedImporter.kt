package allen.town.podcast.util

import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.core.feed.LocalFeedCreator
import allen.town.podcast.fragment.FeedItemlistFragment
import allen.town.podcast.model.feed.Feed
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

/**
 * Adds feeds from the device's own storage: a podcast folder, or an audiobook picked either as a
 * single file (.m4b, .m4a, .mp3, ...) or as a folder of tracks. The new feed opens once it is
 * stored; a failure is shown as a snackbar.
 *
 * The pickers are registered in the constructor, and a fragment may only register before it is
 * created, so create this as a field initializer (or in `onCreate`), not in a click handler.
 * Call [dispose] from the fragment's `onDestroy`.
 */
class LocalFeedImporter(private val fragment: Fragment) {

    private val disposables = CompositeDisposable()

    private val podcastFolderLauncher =
        fragment.registerForActivityResult(OpenFolder()) { uri: Uri? ->
            if (uri != null) add(uri) { context -> LocalFeedCreator.addLocalFolder(context, uri, false) }
        }

    private val audiobookFolderLauncher =
        fragment.registerForActivityResult(OpenFolder()) { uri: Uri? ->
            if (uri != null) add(uri) { context -> LocalFeedCreator.addLocalFolder(context, uri, true) }
        }

    private val audiobookFileLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) add(uri) { context -> LocalFeedCreator.addLocalFile(context, uri, true) }
        }

    /** Picks a folder and adds it as a podcast. */
    fun addPodcastFolder() {
        launch { podcastFolderLauncher.launch(null) }
    }

    /** Asks whether the audiobook is one file or a folder of tracks, then picks it. */
    fun addAudiobook() {
        val context = fragment.context ?: return
        val choices = arrayOf(
            context.getString(R.string.audiobook_file),
            context.getString(R.string.audiobook_folder)
        )
        AccentMaterialDialog(context, R.style.MaterialAlertDialogTheme)
            .setTitle(R.string.add_audiobook)
            .setItems(choices) { dialog, which ->
                dialog.dismiss()
                if (which == 0) {
                    launch { audiobookFileLauncher.launch(AUDIOBOOK_MIME_TYPES) }
                } else {
                    launch { audiobookFolderLauncher.launch(null) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun dispose() {
        disposables.dispose()
    }

    private fun launch(start: () -> Unit) {
        try {
            start()
        } catch (ignored: ActivityNotFoundException) {
            // no document picker on this device
            showSnack(fragment.activity, R.string.unable_to_start_system_file_manager, Toast.LENGTH_LONG)
        }
    }

    private fun add(uri: Uri, create: (Context) -> Feed) {
        val context = fragment.context?.applicationContext ?: return
        disposables.add(
            Observable.fromCallable { create(context) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ feed: Feed ->
                    val activity = fragment.activity as? MainActivity ?: return@subscribe
                    activity.loadChildFragment(FeedItemlistFragment.newInstance(feed.id))
                }, { error: Throwable ->
                    Log.e(TAG, "adding $uri failed", error)
                    showSnack(fragment.activity, error.localizedMessage, Toast.LENGTH_LONG)
                })
        )
    }

    private class OpenFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private companion object {
        private const val TAG = "LocalFeedImporter"

        /**
         * Audiobook files often lack a proper audio type: .m4b is reported as audio/x-m4b,
         * audio/mp4 or, by many file managers, as a generic binary.
         */
        private val AUDIOBOOK_MIME_TYPES = arrayOf(
            "audio/*",
            "audio/mp4",
            "audio/x-m4b",
            "audio/m4b",
            "application/octet-stream"
        )
    }
}
