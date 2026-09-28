package allen.town.podcast.activity.main

import allen.town.podcast.R
import allen.town.podcast.common.util.Timber
import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.core.event.DownloadEvent
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.util.NotificationPermissionGate
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle

/**
 * Asks for the notification permission (Android 13+) in context instead of at startup: the first
 * time an episode download is running while the main screen is in front, it explains what the
 * notifications are for and requests the permission only if the user taps Allow. The explanation
 * is shown once, whatever the answer; after that the system settings are the way back. If the
 * system refuses without asking (the permission was denied twice before), Allow opens those
 * settings directly.
 * [NotificationPermissionGate] holds the decision.
 *
 * Create it as a field initializer of the activity: the permission launcher has to be registered
 * before the activity is created.
 */
class NotificationPermissionPrompter(private val activity: AppCompatActivity) {

    private val permissionLauncher = activity.registerForActivityResult(RequestPermission()) { granted ->
        Timber.d("notification permission granted: %s", granted)
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            // After two refusals (e.g. the startup prompt of older versions) the system denies
            // without asking. The user just tapped Allow, so take them to the settings instead.
            openNotificationSettings()
        }
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

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "no notification settings screen")
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
