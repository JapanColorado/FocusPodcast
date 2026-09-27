package allen.town.podcast.activity.main

import allen.town.podcast.R
import allen.town.podcast.common.util.Timber
import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.core.event.DownloadEvent
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.util.NotificationPermissionGate
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle

/**
 * Asks for the notification permission (Android 13+) in context instead of at startup: the first
 * time an episode download is running while the main screen is in front, it explains what the
 * notifications are for and requests the permission only if the user taps Allow. The explanation
 * is shown once, whatever the answer; after that the system settings are the way back.
 * [NotificationPermissionGate] holds the decision.
 *
 * Create it as a field initializer of the activity: the permission launcher has to be registered
 * before the activity is created.
 */
class NotificationPermissionPrompter(private val activity: AppCompatActivity) {

    private val permissionLauncher = activity.registerForActivityResult(RequestPermission()) { granted ->
        Timber.d("notification permission granted: %s", granted)
    }

    private var dialogShowing = false

    fun onDownloadEvent(event: DownloadEvent) {
        if (dialogShowing || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Progress events keep coming while the download runs, so a later one retries
            return
        }
        val explain = NotificationPermissionGate.shouldExplain(
            Build.VERSION.SDK_INT,
            NotificationManagerCompat.from(activity).areNotificationsEnabled(),
            Prefs.notificationRationaleShown,
            event.update.mediaIds.isNotEmpty()
        )
        if (!explain) {
            return
        }
        Prefs.notificationRationaleShown = true
        dialogShowing = true
        AccentMaterialDialog(activity, R.style.MaterialAlertDialogTheme)
            .setTitle(R.string.notification_rationale_title)
            .setMessage(R.string.notification_rationale_message)
            .setPositiveButton(R.string.allow_label) { _, _ -> requestPermission() }
            .setNegativeButton(R.string.not_now_label, null)
            .setOnDismissListener { dialogShowing = false }
            .show()
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
