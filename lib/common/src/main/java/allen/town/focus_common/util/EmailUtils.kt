package allen.town.focus_common.util

import allen.town.focus_common.R
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar

object EmailUtils {
    fun emailToMe(
        uri: Uri?,
        appName: String,
        versionName: String,
        activity: FragmentActivity,
        to: String
    ) {

        val intent = Intent(
            Intent.ACTION_SEND
        )
        intent.type = "application/octet-stream"
        intent.putExtra(Intent.EXTRA_SUBJECT, "$appName $versionName")
        intent.putExtra(
            Intent.EXTRA_TEXT,
            "Android: ${Build.VERSION.RELEASE} ${Build.MANUFACTURER} ${Build.MODEL}"
        )
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))

        if (uri != null) {
            intent.putExtra(Intent.EXTRA_STREAM, uri)
        }
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.send_file)))
        TopSnackbarUtil.showSnack(activity, R.string.send_file, Snackbar.LENGTH_LONG)
    }
}