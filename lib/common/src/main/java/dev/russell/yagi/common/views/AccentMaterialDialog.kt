package dev.russell.yagi.common.views

import android.content.Context
import androidx.appcompat.app.AlertDialog
import dev.russell.yagi.common.extensions.colorButtons
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * materialAlertDialogTheme is not actually used; the Music project only applies that theme in debug
 * builds and otherwise keeps the default one.
 */
open class AccentMaterialDialog(context: Context, materialAlertDialogTheme: Int = 0) :
    MaterialAlertDialogBuilder(context, materialAlertDialogTheme) {
    override fun create(): AlertDialog {
        val alertDialog = super.create()
        alertDialog.colorButtons(context)
        return alertDialog
    }
}