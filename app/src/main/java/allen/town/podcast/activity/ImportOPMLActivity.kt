package allen.town.podcast.activity

import allen.town.podcast.common.adapter.BindableViewHolder
import allen.town.podcast.common.adapter.ReactiveListAdapter
import allen.town.podcast.common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.common.views.AccentMaterialDialog
import allen.town.podcast.R
import allen.town.podcast.core.export.opml.OpmlElement
import allen.town.podcast.core.export.opml.OpmlReader
import allen.town.podcast.core.pref.Prefs
import allen.town.podcast.core.service.download.DownloadRequest
import allen.town.podcast.core.service.download.DownloadRequestCreator
import allen.town.podcast.core.service.download.DownloadService
import allen.town.podcast.core.storage.DBReader
import allen.town.podcast.databinding.OpmlSelectionBinding
import allen.town.podcast.model.feed.Feed
import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import allen.town.podcast.theme.util.scroll.ThemedFastScroller.create
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.io.input.BOMInputStream
import java.io.*

/**
 * Activity for Opml Import.
 */
class ImportOPMLActivity : SimpleToolbarActivity() {
    private var uri: Uri? = null
    lateinit var viewBinding: OpmlSelectionBinding
    private var listAdapter: FeedAdapter? = null
    private var selectAll: MenuItem? = null
    private var deselectAll: MenuItem? = null
    private var readElements: List<OpmlElement> = emptyList()
    private val checked: ArrayList<Boolean> = ArrayList()

    /** Parse and import runs; both touch the view binding, so they die with the activity. */
    private val pendingWork = CompositeDisposable()

    override fun onDestroy() {
        pendingWork.dispose()
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.theme)
        super.onCreate(savedInstanceState)
        viewBinding = OpmlSelectionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        setSupportActionBar(viewBinding.appBarLayout.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        create(viewBinding.scrollView)
        viewBinding.butConfirm.setOnClickListener { v: View? ->
            viewBinding.progressBar.visibility = View.VISIBLE
            pendingWork.add(Completable.fromAction {
                val toAdd: MutableList<DownloadRequest> = ArrayList()
                for (i in checked.indices) {
                    if (!checked[i]) {
                        continue
                    }
                    val element = readElements[i]
                    val feed = Feed(element.xmlUrl, null, element.text)
                    feed.isNeedAutoSubscribe = true
                    toAdd.add(DownloadRequestCreator.create(feed).build())
                }
                if (toAdd.size > 0) {
                    DownloadService.download(this, false, *toAdd.toTypedArray())
                }
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        viewBinding.progressBar.visibility = View.GONE
                        val intent = Intent(this@ImportOPMLActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    }) { e: Throwable ->
                    Log.e(TAG, "importing the selected feeds failed", e)
                    viewBinding.progressBar.visibility = View.GONE
                    showSnack(this, e.message, Toast.LENGTH_LONG)
                })
        }
        var uri = intent.data
        if (uri != null && uri.toString().startsWith("/")) {
            uri = Uri.parse("file://$uri")
        } else {
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val clipData = intent.clipData
            if (extraText != null) {
                uri = Uri.parse(extraText)
            } else if (clipData != null) {
                uri = clipData.getItemAt(0)?.uri
            }
        }
        importUri(uri)
    }

    fun importUri(uri: Uri?) {
        if (uri == null) {
            AccentMaterialDialog(
                this,
                R.style.MaterialAlertDialogTheme
            )
                .setMessage(R.string.opml_import_error_no_file)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        this.uri = uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && uri.toString().contains(Environment.getExternalStorageDirectory().toString())
        ) {
            val permission = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestPermission()
                return
            }
        }
        startImport()
    }

    val titleList: List<String>
        get() {
            val result: MutableList<String> = ArrayList()
            for (element in readElements) {
                result.add(element.text)
            }
            return result
        }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.opml_selection_options, menu)
        val selectAllItem = menu.findItem(R.id.select_all_item)
        selectAll = selectAllItem
        deselectAll = menu.findItem(R.id.deselect_all_item)
        selectAllItem.setVisible(false)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.select_all_item) {
            selectAll?.isVisible = false
            selectAllItems(true)
            deselectAll?.isVisible = true
            return true
        } else if (itemId == R.id.deselect_all_item) {
            deselectAll?.isVisible = false
            selectAllItems(false)
            selectAll?.isVisible = true
            return true
        } else if (itemId == android.R.id.home) {
            finish()
        }
        return false
    }

    private fun selectAllItems(b: Boolean) {
        val adapter = listAdapter ?: return
        val itemCount = adapter.itemCount
        checked.clear()
        for (i in 0 until itemCount) {
            checked.add(i, b)
        }
        updateSum()
        adapter.notifyDataSetChanged()
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }


    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult<String, Boolean>(
            RequestPermission(),
            ActivityResultCallback { isGranted: Boolean ->
                if (isGranted) {
                    startImport()
                } else {
                    AccentMaterialDialog(
                        this,
                        R.style.MaterialAlertDialogTheme
                    )
                        .setMessage(R.string.opml_import_ask_read_permission)
                        .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> requestPermission() }
                        .setNegativeButton(R.string.cancel_label) { dialog: DialogInterface?, which: Int -> finish() }
                        .show()
                }
            })
    private var subCount = 0

    /**
     * Starts the import process.
     */
    private fun startImport() {
        val uri = requireNotNull(this.uri) { "startImport() called before an OPML uri was set" }
        viewBinding.progressBar.visibility = View.VISIBLE
        pendingWork.add(Observable.fromCallable {
            val opmlFileStream: InputStream?
            opmlFileStream = if ("content" != uri.scheme) {
                FileInputStream(File(uri.encodedPath))
            } else {
                contentResolver.openInputStream(uri)
            }
            val bomInputStream = BOMInputStream(opmlFileStream)
            val bom = bomInputStream.bom
            val charsetName = if (bom == null) "UTF-8" else bom.charsetName
            val reader: Reader = InputStreamReader(bomInputStream, charsetName)
            val opmlReader = OpmlReader()
            val result = opmlReader.readDocument(reader)
            reader.close()
            val subList: List<*> = DBReader.getFeedList()
            if (subList != null && subList.size > 0) {
                subCount = subList.size
            }
            result
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: ArrayList<OpmlElement>? ->
                    viewBinding.progressBar.visibility = View.GONE
                    Log.d(TAG, "parse successful")
                    readElements = result ?: emptyList()
                    val adapter = FeedAdapter(this@ImportOPMLActivity)
                    listAdapter = adapter
                    adapter.call(titleList)
                    viewBinding.feedlist.layoutManager =
                        LinearLayoutManager(this@ImportOPMLActivity)
                    viewBinding.feedlist.adapter = listAdapter
                    //select all by default
                    selectAllItems(true)
                }) { e: Throwable ->
                Log.e(TAG, "reading the OPML file failed", e)
                viewBinding.progressBar.visibility = View.GONE
                val alert: AlertDialog.Builder = AccentMaterialDialog(
                    this,
                    R.style.MaterialAlertDialogTheme
                )
                alert.setTitle(R.string.error_label)
                alert.setMessage(getString(R.string.opml_reader_error) + e.message)
                alert.setNeutralButton(android.R.string.ok) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
                alert.create().show()
            })
    }

    inner class FeedAdapter(context: Context?) :
        ReactiveListAdapter<String?, FeedViewHolder?>(context) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
            return FeedViewHolder(
                layoutInflater.inflate(
                    R.layout.multipe_list_item, parent,
                    false
                )
            )
        }
    }

    fun updateSum() {
        viewBinding.opmlSumTv.visibility = View.VISIBLE
        viewBinding.opmlSumTv.setText(R.string.opml_sum_pro)
    }

    inner class FeedViewHolder(view: View) : BindableViewHolder<String?>(view) {
        private val title: TextView
        private val cb: CheckBox
        override fun bindTo(s: String?) {
            title.text = s
            val boundPosition = bindingAdapterPosition
            if (boundPosition == RecyclerView.NO_POSITION || boundPosition >= checked.size) {
                return
            }
            cb.isChecked = checked[boundPosition]
            itemView.setOnClickListener {
                // the list is rebuilt on every parse, so re-read the position at click time
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= checked.size) {
                    return@setOnClickListener
                }
                cb.isChecked = !cb.isChecked
                checked[pos] = cb.isChecked
                updateSum()
                var checkedCount = 0
                for (i in checked.indices) {
                    if (checked[i]) {
                        checkedCount++
                    }
                }
                if (checkedCount == listAdapter?.itemCount) {
                    selectAll?.isVisible = false
                    deselectAll?.isVisible = true
                } else {
                    deselectAll?.isVisible = false
                    selectAll?.isVisible = true
                }
            }
        }

        init {
            title = view.findViewById(R.id.title)
            cb = view.findViewById(R.id.cb)
        }
    }

    companion object {
        private const val TAG = "OpmlImportBaseActivity"
    }
}