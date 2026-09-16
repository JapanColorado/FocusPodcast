package allen.town.podcast.adapter

import allen.town.focus_common.util.MenuIconUtil.showMenuIcon
import allen.town.podcast.R
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.RecyclerView

/**
 * Used by Recyclerviews that need to provide ability to select items.
 * The toolbar is only overlaid correctly with the system default action bar theme plus startSupportActionMode (which also lets the menu show more than 2 items; otherwise only 2 are shown and the rest collapse).
 */
abstract class MultiSelectAdapter<T : RecyclerView.ViewHolder?>(
    private val activity: AppCompatActivity, //0 means none
    @param:MenuRes private val menuResId: Int
) : RecyclerView.Adapter<T>() {
    private var actionMode: ActionMode? = null
    private val selectedIds = HashSet<Long>()
    private var onSelectModeListener: OnSelectModeListener? = null
    private var onPrepareActionModeListener: OnPrepareActionModeListener? = null
    private var onMenuItemClickListener: OnMenuItemClickListener? = null
    /**
     * A holder's bindingAdapterPosition is NO_POSITION while it is detached, and stale positions
     * outlive a list change; every position that reaches getItemId() has to be checked first or
     * the backing list throws.
     */
    protected fun isValidPosition(pos: Int): Boolean {
        return pos != RecyclerView.NO_POSITION && pos >= 0 && pos < itemCount
    }

    fun startSelectMode(pos: Int) {
        if (!isValidPosition(pos)) {
            return
        }
        if (inActionMode()) {
            endSelectMode()
        }
        if (onSelectModeListener != null) {
            onSelectModeListener!!.onStartSelectMode()
        }
        selectedIds.clear()
        selectedIds.add(getItemId(pos))
        notifyDataSetChanged()
        actionMode = activity.startSupportActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val inflater = mode.menuInflater
                if (menuResId > 0) {
                    inflater.inflate(menuResId, menu)
                }
                showMenuIcon(menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                updateTitle()
                toggleSelectAllIcon(
                    menu.findItem(R.id.select_toggle),
                    selectedIds.size == itemCount
                )
                if (onPrepareActionModeListener != null) {
                    onPrepareActionModeListener!!.onPrepareActionMode(mode, menu)
                }
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId == R.id.select_toggle) {
                    val allSelected = selectedIds.size == itemCount
                    setSelected(0, itemCount, !allSelected)
                    toggleSelectAllIcon(item, !allSelected)
                    updateTitle()
                    return true
                } else {
                    if (onMenuItemClickListener != null) {
                        onMenuItemClickListener!!.onMenuItemClick(item)
                    }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                callOnEndSelectMode()
                actionMode = null
                selectedIds.clear()
                notifyDataSetChanged()
            }
        })
        updateTitle()
    }

    /**
     * End action mode if currently in select mode, otherwise do nothing
     */
    fun endSelectMode() {
        if (inActionMode()) {
            callOnEndSelectMode()
            actionMode!!.finish()
        }
    }

    fun isSelected(pos: Int): Boolean {
        return isValidPosition(pos) && selectedIds.contains(getItemId(pos))
    }

    /**
     * Set the selected state of item at given position
     *
     * @param pos      the position to select
     * @param selected true for selected state and false for unselected
     */
    open fun setSelected(pos: Int, selected: Boolean) {
        if (!isValidPosition(pos)) {
            return
        }
        if (selected) {
            selectedIds.add(getItemId(pos))
        } else {
            selectedIds.remove(getItemId(pos))
        }
        updateTitle()
    }

    /**
     * Set the selected state of item for a given range
     *
     * @param startPos start position of range, inclusive
     * @param endPos   end position of range, inclusive
     * @param selected indicates the selection state
     * @throws IllegalArgumentException if start and end positions are not valid
     */
    @Throws(IllegalArgumentException::class)
    fun setSelected(startPos: Int, endPos: Int, selected: Boolean) {
        var i = startPos
        while (i < endPos && i < itemCount) {
            setSelected(i, selected)
            i++
        }
        notifyItemRangeChanged(startPos, endPos - startPos)
    }

    protected fun toggleSelection(pos: Int) {
        if (!isValidPosition(pos)) {
            return
        }
        setSelected(pos, !isSelected(pos))
        notifyItemChanged(pos)
        if (selectedIds.size == 0) {
            endSelectMode()
        }
    }

    fun inActionMode(): Boolean {
        return actionMode != null
    }

    val selectedCount: Int
        get() = selectedIds.size

    private fun toggleSelectAllIcon(selectAllItem: MenuItem?, allSelected: Boolean) {
        if (selectAllItem == null) {
            return
        }
        if (allSelected) {
            selectAllItem.setIcon(R.drawable.ic_select_none)
            selectAllItem.setTitle(R.string.deselect_all_label)
        } else {
            selectAllItem.setIcon(R.drawable.ic_select_all)
            selectAllItem.setTitle(R.string.select_all_label)
        }
    }

    private fun updateTitle() {
        if (actionMode == null) {
            return
        }
        actionMode!!.title = selectedIds.size.toString() + ""
    }

    fun setOnSelectModeListener(onSelectModeListener: OnSelectModeListener?) {
        this.onSelectModeListener = onSelectModeListener
    }

    fun setonPrepareActionListener(onPrepareActionModeListener: OnPrepareActionModeListener?) {
        this.onPrepareActionModeListener = onPrepareActionModeListener
    }

    private fun callOnEndSelectMode() {
        if (onSelectModeListener != null) {
            onSelectModeListener!!.onEndSelectMode()
        }
    }

    interface OnSelectModeListener {
        fun onStartSelectMode()
        fun onEndSelectMode()
    }

    interface OnMenuItemClickListener {
        fun onMenuItemClick(item: MenuItem?)
    }

    interface OnPrepareActionModeListener {
        fun onPrepareActionMode(mode: ActionMode?, item: Menu?)
    }

    fun setOnMenuItemClickListener(onMenuItemClickListener: OnMenuItemClickListener?) {
        this.onMenuItemClickListener = onMenuItemClickListener
    }
}