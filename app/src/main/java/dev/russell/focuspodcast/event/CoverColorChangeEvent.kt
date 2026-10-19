package dev.russell.focuspodcast.event

import android.graphics.Bitmap
import dev.russell.focuspodcast.fragment.CoverFragment
import dev.russell.focuspodcast.util.MediaNotificationProcessor

class CoverColorChangeEvent(val color: MediaNotificationProcessor, val bitmap: Bitmap?,val coverViewHolder: CoverFragment.CoverViewHolder?)