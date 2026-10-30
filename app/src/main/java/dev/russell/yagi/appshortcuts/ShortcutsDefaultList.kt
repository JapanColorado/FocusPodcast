package dev.russell.yagi.appshortcuts

import android.content.Context
import android.content.pm.ShortcutInfo

class ShortcutsDefaultList(private val context: Context) {
    public val defaultShortcuts: List<ShortcutInfo>
        get() = listOf(
//            RefreshShortcutType(context).shortcutInfo,
            EpisodesShortcutType(context).shortcutInfo,
            QueueShortcutType(context).shortcutInfo,
            SubscriptionsShortcutType(context).shortcutInfo
        )
}