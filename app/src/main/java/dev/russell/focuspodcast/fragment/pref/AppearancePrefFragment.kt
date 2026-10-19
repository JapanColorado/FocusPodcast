/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package dev.russell.focuspodcast.fragment.pref

import dev.russell.focuspodcast.theme.constants.ThemeConstants.ACCENT_COLOR
import dev.russell.focuspodcast.theme.constants.ThemeConstants.BLACK_THEME
import dev.russell.focuspodcast.theme.constants.ThemeConstants.CUSTOM_LAUNCHER
import dev.russell.focuspodcast.theme.constants.ThemeConstants.DESATURATED_COLOR
import dev.russell.focuspodcast.theme.constants.ThemeConstants.GENERAL_THEME
import dev.russell.focuspodcast.theme.constants.ThemeConstants.LANGUAGE_NAME
import dev.russell.focuspodcast.theme.constants.ThemeConstants.MATERIAL_YOU
import dev.russell.focuspodcast.theme.constants.ThemeConstants.SHOULD_COLOR_APP_SHORTCUTS
import dev.russell.focuspodcast.theme.constants.ThemeConstants.WALLPAPER_ACCENT
import dev.russell.focuspodcast.common.theme.CustomLauncherIconMakerDialog
import dev.russell.focuspodcast.common.util.BasePreferenceUtil
import dev.russell.focuspodcast.common.util.PackageUtils
import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import dev.russell.focuspodcast.theme.ACCENT_COLORS
import dev.russell.focuspodcast.theme.ACCENT_COLORS_SUB
import dev.russell.focuspodcast.theme.ThemeStore
import dev.russell.focuspodcast.common.common.prefs.supportv7.ATEColorPreference
import dev.russell.focuspodcast.common.common.prefs.supportv7.ATESwitchPreference
import dev.russell.focuspodcast.theme.util.ColorUtil
import dev.russell.focuspodcast.theme.util.VersionUtils
import dev.russell.focuspodcast.theme.shortcut.DynamicShortcutManager
import dev.russell.focuspodcast.common.extensions.materialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.google.android.material.color.DynamicColors
import dev.russell.focuspodcast.BuildConfig
import dev.russell.focuspodcast.MyApp
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.activity.SettingsActivity
import dev.russell.focuspodcast.appshortcuts.ShortcutsDefaultList
import dev.russell.focuspodcast.core.playback.NowPlayingScreen
import dev.russell.focuspodcast.core.pref.Prefs
import dev.russell.focuspodcast.dialog.SwipeActionsDialog
import dev.russell.focuspodcast.event.PlayerStatusEvent
import dev.russell.focuspodcast.event.UnreadItemsUpdateEvent
import dev.russell.focuspodcast.fragment.EpisodesFragment
import dev.russell.focuspodcast.fragment.FavoriteEpisodesFragment
import dev.russell.focuspodcast.fragment.FeedItemlistFragment
import dev.russell.focuspodcast.fragment.PlaylistFragment
import dev.russell.focuspodcast.pref.AppLanguage
import org.greenrobot.eventbus.EventBus

/**
 * Everything about how the app looks: theme and colors, layout and navigation, episode lists,
 * the now-playing and lock screens, and the list swipe actions.
 */
class AppearancePrefFragment : AbsSettingsFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_appearance)
        setupLayoutPreferences()
        setupNowPlayingPreferences()
        setupSwipeActions()
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as SettingsActivity).setTitle(R.string.appearance)
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (Prefs.NOW_PLAYING_SCREEN_ID == key) {
            updateAdaptiveColorEnabled()
        }
    }

    /** Preferences declared in `pref_appearance.xml`; a missing key is a programming error. */
    private fun requirePreference(key: String): Preference =
        checkNotNull(findPreference(key)) { "missing preference $key in pref_appearance.xml" }

    private fun setupLayoutPreferences() {
        //show remaining time or total duration on the player screen
        requirePreference(Prefs.PREF_SHOW_TIME_LEFT).onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                Prefs.setShowRemainTimeSetting(newValue as Boolean?)
                EventBus.getDefault().post(UnreadItemsUpdateEvent())
                EventBus.getDefault().post(PlayerStatusEvent())
                true
            }
        // Not persisted: the per-app locale is the source of truth, and it can also be changed in
        // the system's app language settings. AppCompat recreates the activities on a change.
        val language = requirePreference(LANGUAGE_NAME) as ListPreference
        language.value = AppLanguage.currentCode(language.entryValues)
        language.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                AppLanguage.apply(newValue.toString())
                true
            }
        requirePreference(Prefs.PREF_HOME_PAGE).onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ ->
                restartActivity()
                true
            }
    }

    private fun setupNowPlayingPreferences() {
        //lock screen backgrounds are not supported from Android 11 on
        requirePreference(Prefs.PREF_LOCKSCREEN_BACKGROUND).isVisible =
            BuildConfig.DEBUG || !VersionUtils.hasR()
        updateAdaptiveColorEnabled()
    }

    /** Only the now-playing screens that tint themselves from the cover can color the app. */
    private fun updateAdaptiveColorEnabled() {
        requirePreference(Prefs.PREF_ADAPTIVE_COLOR_APP).isEnabled =
            Prefs.nowPlayingScreen in ADAPTIVE_COLOR_SCREENS
    }

    private fun setupSwipeActions() {
        bindSwipeActions(PREF_SWIPE_FEED, FeedItemlistFragment.TAG)
        bindSwipeActions(PREF_SWIPE_QUEUE, PlaylistFragment.TAG)
        bindSwipeActions(PREF_SWIPE_EPISODES, EpisodesFragment.TAG)
        bindSwipeActions(PREF_SWIPE_FAVORITE, FavoriteEpisodesFragment.TAG)
    }

    private fun bindSwipeActions(key: String, listTag: String) {
        requirePreference(key).onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), listTag).show(object : SwipeActionsDialog.Callback {
                override fun onCall() {
                    // the lists read the swipe actions when they are shown
                }
            })
            true
        }
    }

    /** Theme and color wiring; runs from [onViewCreated] through the base class. */
    @SuppressLint("CheckResult")
    override fun invalidateSettings() {
        val generalTheme: Preference? = findPreference(GENERAL_THEME)
        generalTheme?.let {
            setSummary(it)
            it.setOnPreferenceChangeListener { _, newValue ->
                setSummary(it, newValue)
                ThemeStore.markChanged(requireContext())

                if (VersionUtils.hasNougatMR()) {
                    DynamicShortcutManager(
                        requireContext(),
                        ShortcutsDefaultList(requireContext()).defaultShortcuts
                    ).updateDynamicShortcuts()
                }
                restartActivity()
                true
            }
        }

        val accentColorPref: ATEColorPreference? = findPreference(ACCENT_COLOR)
        val accentColor = ThemeStore.accentColor(requireContext())
        accentColorPref?.setColor(accentColor, ColorUtil.darkenColor(accentColor))
        accentColorPref?.setOnPreferenceClickListener {
            materialDialog().show {
                colorChooser(
                    initialSelection = accentColor,
                    showAlphaSelector = false,
                    colors = ACCENT_COLORS,
                    subColors = ACCENT_COLORS_SUB, allowCustomArgb = true
                ) { _, color ->
                    ThemeStore.editTheme(requireContext()).accentColor(color).commit()
                    if (VersionUtils.hasNougatMR())
                        DynamicShortcutManager(
                            requireContext(),
                            ShortcutsDefaultList(requireContext()).defaultShortcuts
                        ).updateDynamicShortcuts()
                    restartActivity()
                }
            }
            return@setOnPreferenceClickListener true
        }
        val blackTheme: ATESwitchPreference? = findPreference(BLACK_THEME)
        blackTheme?.setOnPreferenceChangeListener { _, value ->
            ThemeStore.markChanged(requireContext())
            if (VersionUtils.hasNougatMR()) {
                DynamicShortcutManager(
                    requireContext(),
                    ShortcutsDefaultList(requireContext()).defaultShortcuts
                ).updateDynamicShortcuts()
            }
            restartActivity()
            true
        }

        val desaturatedColor: ATESwitchPreference? = findPreference(DESATURATED_COLOR)
        desaturatedColor?.setOnPreferenceChangeListener { _, value ->
            val desaturated = value as Boolean
            ThemeStore.prefs(requireContext())
                .edit()
                .putBoolean(DESATURATED_COLOR, desaturated)
                .apply()
            BasePreferenceUtil.isDesaturatedColor = desaturated
            restartActivity()
            true
        }

        val colorAppShortcuts: TwoStatePreference? = findPreference(SHOULD_COLOR_APP_SHORTCUTS)
        if (!VersionUtils.hasNougatMR()) {
            colorAppShortcuts?.isVisible = false
        } else {
            colorAppShortcuts?.isChecked = BasePreferenceUtil.isColoredAppShortcuts
            colorAppShortcuts?.setOnPreferenceChangeListener { _, newValue ->
                BasePreferenceUtil.isColoredAppShortcuts = newValue as Boolean
                DynamicShortcutManager(
                    requireContext(),
                    ShortcutsDefaultList(requireContext()).defaultShortcuts
                ).updateDynamicShortcuts()
                true
            }
        }

        val materialYou: ATESwitchPreference? = findPreference(MATERIAL_YOU)
        materialYou?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                DynamicColors.applyToActivitiesIfAvailable(MyApp.instance as Application)
            }
            restartActivity()
            true
        }
        val wallpaperAccent: ATESwitchPreference? = findPreference(WALLPAPER_ACCENT)
        wallpaperAccent?.setOnPreferenceChangeListener { _, _ ->
            restartActivity()
            true
        }

        val customLauncher: Preference? = findPreference(CUSTOM_LAUNCHER)
        if (VersionUtils.hasOreo()) {
            customLauncher?.onPreferenceClickListener =
                object : Preference.OnPreferenceClickListener {
                    override fun onPreferenceClick(preference: Preference): Boolean {
                        CustomLauncherIconMakerDialog(
                            R.string.pref_custom_launcher_title,
                            PackageUtils.getPackageName(context), "dev.russell.focuspodcast.activity.SplashActivity"
                            ,R.color.white,R.drawable.ic_launcher_foreground, R.color.ic_launcher_background,
                            PackageUtils.getAppName(context)
                        ).show(
                            requireActivity().supportFragmentManager,
                            null
                        )
                        return true
                    }

                }
        } else {
            customLauncher?.isVisible = false
        }
    }

    companion object {
        private const val PREF_SWIPE_FEED = "prefSwipeFeed"
        private const val PREF_SWIPE_QUEUE = "prefSwipeQueue"
        private const val PREF_SWIPE_EPISODES = "pref_swipe_episodes"
        private const val PREF_SWIPE_FAVORITE = "pref_swipe_favorite"
        private val ADAPTIVE_COLOR_SCREENS =
            listOf(NowPlayingScreen.Normal, NowPlayingScreen.Adaptive, NowPlayingScreen.Circle)
    }
}
