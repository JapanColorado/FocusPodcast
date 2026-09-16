package allen.town.podcast.dialog

import allen.town.focus_common.views.AccentMaterialDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.beloo.widget.chipslayoutmanager.ChipsLayoutManager
import com.google.android.material.chip.Chip
import allen.town.podcast.R
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.core.storage.NavDrawerData.DrawerItem
import allen.town.podcast.databinding.EditTagsDialogLayoutBinding
import allen.town.podcast.model.feed.FeedPreferences
import allen.town.focus_common.views.ItemOffsetDecoration
import allen.town.podcast.MyApp
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class TagEditDialog : DialogFragment() {
    //selected tags
    protected var selectedTags: ArrayList<String> = ArrayList()

    //all tags
    private var allTags: ArrayList<String> = ArrayList()
    private lateinit var viewBinding: EditTagsDialogLayoutBinding
    private lateinit var adapter: TagSelectionAdapter
    private var loadTagsDisposable: Disposable? = null

    override fun onDestroy() {
        loadTagsDisposable?.dispose()
        super.onDestroy()
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @Suppress("UNCHECKED_CAST")
        val feedPreferencesList =
            requireArguments().getSerializable(ARG_FEED_PREFERENCES) as? ArrayList<FeedPreferences>
        check(!feedPreferencesList.isNullOrEmpty()) {
            "TagEditDialog was created without a non-empty $ARG_FEED_PREFERENCES argument"
        }
        val commonTags: MutableSet<String> = HashSet(
            feedPreferencesList[0].tags
        )
        for (preference in feedPreferencesList) {
            commonTags.retainAll(preference.tags)
        }
        selectedTags = ArrayList(commonTags)
        selectedTags.remove(FeedPreferences.TAG_ROOT)
        viewBinding = EditTagsDialogLayoutBinding.inflate(
            layoutInflater
        )
        //https://github.com/BelooS/ChipsLayoutManager
        val chipsLayoutManager = ChipsLayoutManager.newBuilder(context).build()
        viewBinding.tagsRecycler.layoutManager = chipsLayoutManager
        viewBinding.tagsRecycler.addItemDecoration(
            ItemOffsetDecoration(
                requireContext(),
                4
            )
        )
        adapter = TagSelectionAdapter()
        adapter.setHasStableIds(true)
        viewBinding.tagsRecycler.adapter = adapter
        viewBinding.newTagButton.setOnClickListener { v: View? ->
            addTag(
                viewBinding.newTagEditText.text.toString().trim { it <= ' ' })
        }
        loadTags()
        if (feedPreferencesList.size > 1) {
        }
        val dialog: AlertDialog.Builder = AccentMaterialDialog(
            requireContext(),
            R.style.MaterialAlertDialogTheme
        )
        dialog.setView(viewBinding.root)
        dialog.setTitle(R.string.feed_tags_label)
        dialog.setPositiveButton(android.R.string.ok) { d: DialogInterface?, input: Int ->
            updatePreferencesTags(feedPreferencesList, commonTags)
        }
        dialog.setNegativeButton(R.string.cancel_label, null)
        return dialog.create()
    }

    private fun loadTags() {
        loadTagsDisposable?.dispose()
        loadTagsDisposable = Observable.fromCallable<ArrayList<String>> {
            val data = DBReader.getNavDrawerData(true)
            val items = data.items
            val folders: ArrayList<String> = ArrayList()
            for (item in items) {
                if (item.type == DrawerItem.Type.TAG) {
                    folders.add(item.title)
                }
            }
            folders
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: ArrayList<String> ->
                    allTags = result
                    adapter.notifyDataSetChanged()
                }) { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) }
    }

    private fun addTag(name: String) {
        if (TextUtils.isEmpty(name) || selectedTags.contains(name)) {
            return
        }
        selectedTags.add(name)
        allTags.add(name)
        viewBinding.newTagEditText.setText("")
        adapter.notifyDataSetChanged()
    }

    private fun updatePreferencesTags(
        feedPreferencesList: List<FeedPreferences>,
        commonTags: Set<String>
    ) {
        //always include the root tag
        selectedTags.add(FeedPreferences.TAG_ROOT)
        for (preferences in feedPreferencesList) {
            preferences.tags.removeAll(commonTags)
            preferences.tags.addAll(selectedTags)
            DBWriter.setFeedPreferences(preferences)
        }
    }

    inner class TagSelectionAdapter : RecyclerView.Adapter<TagSelectionAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val entryView = inflater.inflate(R.layout.single_tag_chip, parent, false)
            val chip: Chip = entryView.findViewById(R.id.chip)
            return ViewHolder(entryView as Chip)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tag = allTags[position]
            holder.chip.text = tag
            holder.chip.isChecked = selectedTags.contains(tag)
            holder.chip.isCheckedIconVisible = holder.chip.isChecked
            holder.chip.setOnClickListener {
                if (selectedTags.contains(tag)) {
                    selectedTags.remove(tag)
                } else {
                    selectedTags.add(tag)
                }
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int {
            return allTags.size
        }

        override fun getItemId(position: Int): Long {
            return allTags[position].hashCode().toLong()
        }

        inner class ViewHolder internal constructor(var chip: Chip) : RecyclerView.ViewHolder(
            chip
        )
    }

    companion object {
        const val TAG = "TagEditDialog"
        private const val ARG_FEED_PREFERENCES = "feed_preferences"

        @JvmStatic
        fun newInstance(preferencesList: List<FeedPreferences>?): TagEditDialog {
            val fragment = TagEditDialog()
            val args = Bundle()
            args.putSerializable(ARG_FEED_PREFERENCES, ArrayList(preferencesList))
            fragment.arguments = args
            return fragment
        }
    }
}