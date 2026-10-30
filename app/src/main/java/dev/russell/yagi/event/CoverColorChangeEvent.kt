package dev.russell.yagi.event

import android.graphics.Bitmap
import dev.russell.yagi.fragment.CoverFragment
import dev.russell.yagi.util.MediaNotificationProcessor

class CoverColorChangeEvent(val color: MediaNotificationProcessor, val bitmap: Bitmap?,val coverViewHolder: CoverFragment.CoverViewHolder?)