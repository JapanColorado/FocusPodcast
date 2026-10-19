package dev.russell.focuspodcast.appshortcuts

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.ShortcutInfo
import android.os.Build
import dev.russell.focuspodcast.theme.shortcut.AppShortcutIconGenerator
import dev.russell.focuspodcast.theme.shortcut.BaseShortcutType
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.activity.MainActivity
import dev.russell.focuspodcast.activity.MainActivity.Companion.EXTRA_FRAGMENT_TAG
import dev.russell.focuspodcast.fragment.SubFeedsFragment

@TargetApi(Build.VERSION_CODES.N_MR1)
class SubscriptionsShortcutType(context: Context) : BaseShortcutType(context) {
    override val shortcutInfo: ShortcutInfo
        get() = ShortcutInfo.Builder(
            context,
            id
        ).setShortLabel(context.getString(R.string.library_label)).setIcon(
            AppShortcutIconGenerator.generateThemedIcon(
                context,
                R.drawable.ic_folder_shortcut
            )
        ).setIntent(getPlaySongsIntent(MainActivity::class.java, EXTRA_FRAGMENT_TAG, SubFeedsFragment.TAG))
            .build()

    companion object {

        val id: String
            get() = ID_PREFIX + "subscriptions"
    }
}