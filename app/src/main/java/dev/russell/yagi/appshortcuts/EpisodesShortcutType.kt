package dev.russell.yagi.appshortcuts

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.ShortcutInfo
import android.os.Build
import dev.russell.yagi.theme.shortcut.AppShortcutIconGenerator
import dev.russell.yagi.theme.shortcut.BaseShortcutType
import dev.russell.yagi.R
import dev.russell.yagi.activity.MainActivity
import dev.russell.yagi.activity.MainActivity.Companion.EXTRA_FRAGMENT_TAG
import dev.russell.yagi.fragment.EpisodesFragment

@TargetApi(Build.VERSION_CODES.N_MR1)
class EpisodesShortcutType(context: Context) : BaseShortcutType(context) {
    override val shortcutInfo: ShortcutInfo
        get() = ShortcutInfo.Builder(
            context,
            id
        ).setShortLabel(context.getString(R.string.episodes_label)).setIcon(
            AppShortcutIconGenerator.generateThemedIcon(
                context,
                R.drawable.ic_feed_shortcut
            )
        ).setIntent(getPlaySongsIntent(MainActivity::class.java, EXTRA_FRAGMENT_TAG, EpisodesFragment.TAG))
            .build()

    companion object {

        val id: String
            get() = ID_PREFIX + "episodes"
    }
}