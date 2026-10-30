package dev.russell.yagi.util

import dev.russell.yagi.R
import dev.russell.yagi.activity.ImportOPMLActivity
import dev.russell.yagi.common.util.TopSnackbarUtil.showSnack
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.fragment.app.Fragment

/**
 * Lets the user pick an OPML file and hands it to [ImportOPMLActivity].
 *
 * The picker result is registered in the constructor, and a fragment may only register before it
 * is created, so create this as a field initializer (or in `onCreate`), not in a click handler.
 */
class OpmlImportLauncher(private val fragment: Fragment) {

    private val launcher = fragment.registerForActivityResult(GetContent()) { uri: Uri? ->
        onFileChosen(uri)
    }

    fun launch() {
        try {
            launcher.launch("*/*")
        } catch (ignored: ActivityNotFoundException) {
            // no document picker on this device
            showSnack(fragment.activity, R.string.unable_to_start_system_file_manager, Toast.LENGTH_LONG)
        }
    }

    private fun onFileChosen(uri: Uri?) {
        val context = fragment.context
        if (uri == null || context == null) {
            return
        }
        val intent = Intent(context, ImportOPMLActivity::class.java)
        intent.data = uri
        fragment.startActivity(intent)
    }
}
