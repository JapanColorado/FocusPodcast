package allen.town.podcast.dialog

import allen.town.focus_common.views.AccentMaterialDialog
import allen.town.podcast.R
import allen.town.podcast.adapter.ChooseStorageFolderAdapter
import android.content.Context
import android.view.View
import androidx.core.util.Consumer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

object ChooseStorageFolderDialog {
    @JvmStatic
    fun showDialog(context: Context?, handlerFunc: Consumer<String?>) {
        val ctx = requireNotNull(context) { "ChooseStorageFolderDialog needs a context" }
        val content = View.inflate(ctx, R.layout.choose_data_folder_dialog, null)
        val dialog = AccentMaterialDialog(
            ctx,
            R.style.MaterialAlertDialogTheme
        )
            .setView(content)
            .setTitle(R.string.choose_data_directory)
            .setNegativeButton(R.string.cancel_label, null)
            .create()
        (content.findViewById<View>(R.id.recyclerView) as RecyclerView).layoutManager =
            LinearLayoutManager(ctx)
        val adapter = ChooseStorageFolderAdapter(ctx) { path: String? ->
            dialog.dismiss()
            handlerFunc.accept(path)
        }
        (content.findViewById<View>(R.id.recyclerView) as RecyclerView).adapter = adapter
        if (adapter.itemCount > 0) {
            dialog.show()
        } else {
            AccentMaterialDialog(
                ctx,
                R.style.MaterialAlertDialogTheme
            )
                .setTitle(R.string.error_label)
                .setMessage(R.string.external_storage_error_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}