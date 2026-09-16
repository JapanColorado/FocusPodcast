package allen.town.focus_common.views

import android.content.Context
import androidx.appcompat.app.AlertDialog
import code.name.monkey.retromusic.extensions.colorButtons
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 实际并没有用到materialAlertDialogTheme，我看Music这个项目是debug用这个主题否则还是默认主题
 */
open class AccentMaterialDialog(context: Context, materialAlertDialogTheme: Int=0) : MaterialAlertDialogBuilder(context) {
    override fun create(): AlertDialog {
        val alertDialog = super.create()
        alertDialog.colorButtons(context)
        return alertDialog
    }
}