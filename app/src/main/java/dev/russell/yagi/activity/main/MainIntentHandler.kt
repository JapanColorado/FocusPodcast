package dev.russell.yagi.activity.main

import dev.russell.yagi.common.util.TopSnackbarUtil.showSnack
import dev.russell.yagi.R
import dev.russell.yagi.activity.MainActivity
import dev.russell.yagi.fragment.DownloadPagerFragment
import dev.russell.yagi.fragment.EpisodesFragment
import dev.russell.yagi.fragment.BookmarksFragment
import dev.russell.yagi.fragment.FavoriteEpisodesFragment
import dev.russell.yagi.fragment.FeedItemlistFragment
import dev.russell.yagi.fragment.LocalSearchFragment
import dev.russell.yagi.fragment.PlaybackHistoryFragment
import dev.russell.yagi.fragment.PlaylistFragment
import dev.russell.yagi.fragment.SubFeedsFragment
import dev.russell.yagi.fragment.AudiobooksFragment
import dev.russell.yagi.fragment.TransitionEffect
import dev.russell.yagi.model.feed.Feed
import dev.russell.yagi.ui.startintent.MainActivityStarter
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * Turns the intents MainActivity is launched or re-launched with into navigation: the
 * `EXTRA_FRAGMENT_TAG` / `EXTRA_FEED_ID` / `EXTRA_FEED` extras produced by
 * [dev.russell.yagi.ui.startintent.MainActivityStarter] and widgets, the "open the player"
 * extra, and the App Actions `ACTION_VIEW` deep links (`/deeplink/search`, `/deeplink/main`).
 * It resolves each to a call on the fragment navigator or the player sheet, and finally replaces
 * the activity's intent with a blank one so a configuration change does not handle it twice.
 */
internal class MainIntentHandler(
    private val activity: MainActivity,
    private val navigator: MainFragmentNavigator,
    private val playerSheet: MainPlayerSheet
) {
    fun handleNavIntent() {
        val intent = activity.intent
        if (intent.hasExtra(MainActivity.EXTRA_FEED_ID)
            || intent.hasExtra(MainActivity.EXTRA_FRAGMENT_TAG)
            || intent.hasExtra(MainActivity.EXTRA_FEED)
        ) {
            Log.d(TAG, "handle NavIntent()")
            val tag = intent.getStringExtra(MainActivity.EXTRA_FRAGMENT_TAG)
            val args = intent.getBundleExtra(MainActivity.EXTRA_FRAGMENT_ARGS)

            val feedId = intent.getLongExtra(MainActivity.EXTRA_FEED_ID, 0)
            val feed = intent.getParcelableExtra<Feed>(MainActivity.EXTRA_FEED)
            if (tag != null) {
                navigator.loadFragment(tag, args)
            } else if (feedId > 0) {
                if (intent.getBooleanExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH, false)) {
                    loadChild(FeedItemlistFragment.newInstance(feedId))
                } else {
                    navigator.loadFeedFragmentById(feedId, args)
                }
            } else if (feed != null) {
                loadChild(FeedItemlistFragment.newInstance(feed))
            }
            playerSheet.collapse()
        } else if (intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_PLAYER, false)) {
            playerSheet.expand()
        } else if (Intent.ACTION_VIEW == intent.action) {
            handleDeeplink(intent.data)
        }
        // to avoid handling the intent twice when the configuration changes
        activity.intent = Intent(activity, MainActivity::class.java)
    }

    private fun loadChild(fragment: androidx.fragment.app.Fragment) {
        navigator.loadChildFragment(fragment, TransitionEffect.FADE_AND_SCALE)
    }

    /**
     * Handles the deep link incoming via App Actions.
     * Performs an in-app search or opens the relevant feature of the app
     * depending on the query.
     *
     * @param uri incoming deep link
     */
    private fun handleDeeplink(uri: Uri?) {
        if (uri == null || uri.path == null) {
            return
        }
        Log.d(TAG, "handle deeplink -> $uri")
        when (uri.path) {
            "/deeplink/search" -> {
                val query = uri.getQueryParameter("query") ?: return
                loadChild(LocalSearchFragment.newInstance(query))
            }
            "/deeplink/main" -> {
                val feature = uri.getQueryParameter("page") ?: return
                when (feature) {
                    "downloads" -> navigator.loadFragment(DownloadPagerFragment.TAG, null)
                    "history" -> {
                        // not a drawer page: open it from the Playlist, like its toolbar menu does
                        navigator.loadFragment(PlaylistFragment.TAG, null)
                        loadChild(PlaybackHistoryFragment())
                    }
                    "episodes" -> navigator.loadFragment(EpisodesFragment.TAG, null)
                    "playlist" -> navigator.loadFragment(PlaylistFragment.TAG, null)
                    "subscriptions", "library" -> navigator.loadFragment(SubFeedsFragment.TAG, null)
                    "audiobooks" -> {
                        // not a drawer page any more: open it from the Library, like its shelf does
                        navigator.loadFragment(SubFeedsFragment.TAG, null)
                        loadChild(AudiobooksFragment())
                    }
                    "favorite" -> navigator.loadFragment(FavoriteEpisodesFragment.TAG, null)
                    "bookmarks" -> navigator.loadFragment(BookmarksFragment.TAG, null)
                    else -> {
                        showSnack(
                            activity, activity.getString(R.string.app_action_not_found, feature),
                            Toast.LENGTH_LONG
                        )
                        return
                    }
                }
            }
            else -> {}
        }
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}
