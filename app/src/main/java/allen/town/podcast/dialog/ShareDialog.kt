package allen.town.podcast.dialog

import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.R
import allen.town.podcast.core.util.ShareUtils
import allen.town.podcast.databinding.ShareEpisodeDialogBinding
import allen.town.podcast.model.feed.FeedItem
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class ShareDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx: Context = requireActivity()
        val item = checkNotNull(requireArguments().getSerializable(ARGUMENT_FEED_ITEM) as FeedItem?) {
            "ShareDialog was created without a $ARGUMENT_FEED_ITEM argument"
        }
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val binding = ShareEpisodeDialogBinding.inflate(layoutInflater)
        val builder: AlertDialog.Builder = AccentMaterialDialog(
            ctx,
            R.style.MaterialAlertDialogTheme
        )
        builder.setTitle(R.string.share_label)
        builder.setView(binding.root)
        binding.shareDialogRadioGroup.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            binding.shareStartAtTimerDialog.isEnabled = checkedId != R.id.share_media_file_radio
        }
        setupOptions(binding, item, prefs)
        builder.setPositiveButton(R.string.share_label) { _: DialogInterface?, _: Int ->
            val includePlaybackPosition = binding.shareStartAtTimerDialog.isChecked
            if (binding.shareLinkToEpisodeRadio.isChecked) {
                ShareUtils.shareFeedItemLinkWithDownloadLink(ctx, item, includePlaybackPosition)
            } else if (binding.shareMediaFileRadio.isChecked) {
                ShareUtils.shareFeedItemFile(ctx, item.media)
            } else {
                throw IllegalStateException("Unknown share method")
            }
            prefs.edit().putBoolean(PREF_SHARE_EPISODE_START_AT, includePlaybackPosition).apply()
        }
            .setNegativeButton(R.string.cancel_label) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        return builder.create()
    }

    private fun setupOptions(
        binding: ShareEpisodeDialogBinding,
        item: FeedItem,
        prefs: SharedPreferences
    ) {
        val media = item.media
        val downloaded = media != null && media.isDownloaded
        binding.shareMediaFileRadio.visibility = if (downloaded) View.VISIBLE else View.GONE
        val hasDownloadUrl = media != null && media.download_url != null
        if (!ShareUtils.hasLinkToShare(item) && !hasDownloadUrl) {
            binding.shareLinkToEpisodeRadio.visibility = View.GONE
        }
        binding.shareMediaFileRadio.isChecked = false
        binding.shareStartAtTimerDialog.isChecked =
            prefs.getBoolean(PREF_SHARE_EPISODE_START_AT, false)
    }

    companion object {
        private const val ARGUMENT_FEED_ITEM = "feedItem"
        private const val PREF_NAME = "ShareDialog"
        private const val PREF_SHARE_EPISODE_START_AT = "prefShareEpisodeStartAt"
        @JvmStatic
        fun newInstance(item: FeedItem?): ShareDialog {
            val arguments = Bundle()
            arguments.putSerializable(ARGUMENT_FEED_ITEM, item)
            val dialog = ShareDialog()
            dialog.arguments = arguments
            return dialog
        }
    }
}
