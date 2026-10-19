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
package dev.russell.focuspodcast.activity

import dev.russell.focuspodcast.common.extensions.setEdgeToEdgeOrImmersive
import android.graphics.Color
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import dev.russell.focuspodcast.theme.ThemeStore
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.core.glide.GlideApp
import dev.russell.focuspodcast.databinding.ActivityDriveModeBinding
import dev.russell.focuspodcast.event.CoverColorChangeEvent
import dev.russell.focuspodcast.fragment.AudioPlayerFragment
import dev.russell.focuspodcast.glide.BlurTransformation
import dev.russell.focuspodcast.playback.LibraryViewModel
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


/**
 * Created by hemanths on 2020-02-02.
 */

class DriveModeActivity : SimpleToolbarActivity() {

    private lateinit var binding: ActivityDriveModeBinding
    private var lastPlaybackControlsColor: Int = Color.GRAY
    private lateinit var libraryViewModel: LibraryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriveModeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setEdgeToEdgeOrImmersive(R.id.status_bar, true)
        lastPlaybackControlsColor = ThemeStore.accentColor(this)

        libraryViewModel = ViewModelProvider(this).get(
            LibraryViewModel::class.java
        )

        val transaction = getSupportFragmentManager().beginTransaction()
        transaction.replace(
            R.id.audioplayerFragment,
            AudioPlayerFragment(true),
            AudioPlayerFragment.TAG
        )
        transaction.commit()

        updateColor()
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    fun coverColorUpdate(event: CoverColorChangeEvent) {
        GlideApp.with(this)
            .load(event.bitmap)
            .transform(
                BlurTransformation.Builder(this).build()
            ).into(binding.image)
    }

    private fun updateColor() {
        libraryViewModel.paletteColor.observe(this) { color: Int ->
/*            paletteColor = color
            val isColorLight = paletteColor.isColorLight
            setNavigationBarColor(paletteColor)
            setLightStatusBar(isColorLight)*/
        }
    }

}
