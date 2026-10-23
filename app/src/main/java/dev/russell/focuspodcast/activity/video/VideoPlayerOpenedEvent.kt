package dev.russell.focuspodcast.activity.video

/**
 * A video player instance came to the front. Each launch from the notification or the mini player
 * creates a new instance; an older one still alive (in picture-in-picture, say) closes itself, so
 * it cannot detach the video surface from the new one when it stops.
 */
class VideoPlayerOpenedEvent(val instanceId: Int)
