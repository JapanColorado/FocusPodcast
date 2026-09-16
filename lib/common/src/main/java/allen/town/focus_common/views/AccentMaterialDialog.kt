package allen.town.focus_common.views

import android.content.Context
import androidx.appcompat.app.AlertDialog
import code.name.monkey.retromusic.extensions.colorButtons
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * materialAlertDialogTheme is not actually used; the Music project only applies that theme in debug
 * builds and otherwise keeps the default one.
 */
open class AccentMaterialDialog(context: Context, materialAlertDialogTheme: Int=0) : MaterialAlertDialogBuilder(context) {
    override fun create(): AlertDialog {
        val alertDialog = super.create()
        alertDialog.colorButtons(context)
        return alertDialog
    }
}