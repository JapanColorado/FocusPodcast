@file:JvmName("AudioPlayerHelper")

package dev.russell.yagi.playback

import androidx.appcompat.app.AppCompatActivity
import dev.russell.yagi.common.extensions.isColorLight
import dev.russell.yagi.common.extensions.setLightNavigationBar
import dev.russell.yagi.common.extensions.setLightStatusBar
import dev.russell.yagi.common.extensions.setNavigationBarColor
import dev.russell.yagi.core.playback.NowPlayingScreen
import dev.russell.yagi.core.pref.Prefs
import dev.russell.yagi.fragment.*

fun AppCompatActivity.getSelectedAudioPlayerFragment(): AudioPlayerFragment {
    val audioPlayerFragment: AudioPlayerFragment =
        when (Prefs.nowPlayingScreen) {
            NowPlayingScreen.Blur -> BlurAudioPlayerFragment()
            NowPlayingScreen.Color -> ColorAudioPlayerFragment()
            NowPlayingScreen.Circle -> CircleAudioPlayerFragment()
            NowPlayingScreen.Vinyl -> VinylAudioPlayerFragment()
            NowPlayingScreen.Adaptive -> AdapterAudioPlayerFragment()
            NowPlayingScreen.BlurCard -> BlurCardAudioPlayerFragment()
            NowPlayingScreen.Full -> FullAudioPlayerFragment()
            else -> AudioPlayerFragment(false)
        }
    return audioPlayerFragment
}

fun AppCompatActivity.onPaletteColorChanged(paletteColor: Int) {
    val isColorLight = paletteColor.isColorLight
    val nowPlayingScreen = Prefs.nowPlayingScreen
    if (Prefs.isAdapterColor && (nowPlayingScreen === NowPlayingScreen.Normal
                || nowPlayingScreen === NowPlayingScreen.Adaptive
                || nowPlayingScreen === NowPlayingScreen.Circle)
    ) {
        setLightNavigationBar(true)
        setLightStatusBar(isColorLight)
    } else if (nowPlayingScreen === NowPlayingScreen.Blur
        || nowPlayingScreen === NowPlayingScreen.Color
        || nowPlayingScreen === NowPlayingScreen.Vinyl
        || nowPlayingScreen === NowPlayingScreen.BlurCard
        || nowPlayingScreen === NowPlayingScreen.Full
    ) {
        setNavigationBarColor(paletteColor)
        setLightStatusBar(isColorLight)
    }
}