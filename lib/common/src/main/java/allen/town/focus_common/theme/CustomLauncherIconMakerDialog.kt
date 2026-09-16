package allen.town.focus_common.theme

import allen.town.core.service.PayService
import allen.town.focus_common.R
import allen.town.focus_common.util.ImageUtils.mask
import allen.town.focus_common.util.PhotoSelectUtil
import allen.town.focus_common.util.ShortCutUtils
import allen.town.focus_common.util.Util.dp2Px
import allen.town.focus_common.views.AccentMaterialDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import code.name.monkey.appthemehelper.util.ATHUtil.resolveColor
import allen.town.focus_common.extensions.addAccentColor
import com.wyjson.router.GoRouter

class CustomLauncherIconMakerDialog(
    private val custom_launcher_title: Int,
    private val packageName: String,
    private val className: String,
    private val backgroundColor: Int,
    private val foregroundLogo: Int,
    private val backgroundLogo: Int,
    private val appName: String
) : AppCompatDialogFragment(), OnSeekBarChangeListener {
    private var SquareMaskView: ImageView? = null
    private var b = 0
    private var bSeekView: AppCompatSeekBar? = null

    /* access modifiers changed from: private */
    var background: Drawable? = null

    /* access modifiers changed from: private */
    var custom = false

    /* access modifiers changed from: private */
    var foreground: Drawable? = null
    private var g = 0
    private var gSeekView: AppCompatSeekBar? = null

    /* access modifiers changed from: private */
    var iconView: ImageView? = null
    private var maskResId = 0
    private var path: Uri? = null
    var photoSelectUtil: PhotoSelectUtil? = null
    private var r = 0
    private var rSeekView: AppCompatSeekBar? = null
    private var roundMaskView: ImageView? = null
    private var roundedSquareMaskView: ImageView? = null

    /* access modifiers changed from: private */
    var shape = 3
    override fun onStartTrackingTouch(seekBar: SeekBar) {}
    override fun onStopTrackingTouch(seekBar: SeekBar) {}

    // android.support.v4.app.Fragment
    override fun onCreateView(
        layoutInflater: LayoutInflater, viewGroup: ViewGroup?,
        bundle: Bundle?
    ): View? {
        dialog!!.setCanceledOnTouchOutside(true)
        return super.onCreateView(layoutInflater, viewGroup, bundle)
    }

    var imageView: ImageView? = null
    var imageView2: ImageView? = null
    var imageView3: ImageView? = null
    var imageView4: ImageView? = null
    var shapeView: View? = null
    var colorView: View? = null

    // android.support.v7.app.AppCompatDialogFragment, android.support.v4.app.DialogFragment
    override fun onCreateDialog(bundle: Bundle?): Dialog {
        val rootView = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_custom_launcher_icon_maker, null as ViewGroup?)
        val alertDialog = AccentMaterialDialog(requireActivity(), R.style.MaterialAlertDialogTheme)
            .setTitle(custom_launcher_title)
            .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, i: Int ->
                onConfirmDialog(
                    dialogInterface,
                    i
                )
            }
            .setView(rootView).create()
        imageView = rootView.findViewById(R.id.iconView)
        imageView2 = rootView.findViewById(R.id.roundMaskView)
        imageView3 = rootView.findViewById(R.id.roundedSquareMaskView)
        imageView4 = rootView.findViewById(R.id.SquareMaskView)
        rSeekView = rootView.findViewById(R.id.rSeekView)
        rSeekView!!.addAccentColor()
        gSeekView = rootView.findViewById(R.id.gSeekView)
        gSeekView!!.addAccentColor()
        bSeekView = rootView.findViewById(R.id.bSeekView)
        bSeekView!!.addAccentColor()
        shapeView = rootView.findViewById(R.id.shapeLayout)
        colorView = rootView.findViewById(R.id.colorLayout)
        (rootView.findViewById<View>(R.id.nameView) as TextView).text = appName
        onCreateContentLayout()
        return alertDialog
    }

    fun onConfirmDialog(dialogInterface: DialogInterface, i: Int) {
        if(GoRouter.getInstance().getService(PayService::class.java)!!.isPurchase(requireContext())){
            var drawable: Drawable? = null
            val obj = (dialog!!.findViewById<View>(R.id.nameView) as TextView).text.toString()
            if (!TextUtils.isEmpty(obj) && iconView!!.drawable.also { drawable = it } != null) {
                val intent = Intent()
                intent.setClassName(packageName, className)
                if (!custom && Build.VERSION.SDK_INT >= 26) {
                    drawable = AdaptiveIconDrawable(background, foreground)
                }
                ShortCutUtils.install(context, obj, drawable, intent, false)
            }
        }

        dialogInterface.dismiss()
    }

    /* access modifiers changed from: protected */
    // view.dialog.BaseDialog
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        r = Color.red(ContextCompat.getColor(requireContext(), backgroundColor))
        g = Color.green(ContextCompat.getColor(requireContext(), backgroundColor))
        b = Color.blue(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    /* access modifiers changed from: package-private */
    fun onSelected(str: Uri?) {
        custom = true
        showShapeLayout()
        showColorLayout()
        path = str
        maskShape()
    }

    private fun showShapeLayout() {
//        if (this.custom || Build.VERSION.SDK_INT < 26) {
//            shapeView.setVisibility(View.VISIBLE);
//        } else {
        shapeView!!.visibility = View.GONE
        //        }
    }

    private fun showColorLayout() {
        colorView!!.visibility = if (custom) View.GONE else View.VISIBLE
    }

    fun onCreateContentLayout() {
//        if (Build.VERSION.SDK_INT >= 26) {
        background = ContextCompat.getDrawable(requireContext(), backgroundLogo)
        foreground = ContextCompat.getDrawable(requireContext(), foregroundLogo)

//        this.background = ContextCompat.getDrawable(getContext(), R.mipmap.ic_launcher_background);
//        this.foreground = ContextCompat.getDrawable(getContext(), R.drawable.ic_launcher_foreground);
//        }
//        else {
//            this.background = ContextCompat.getDrawable(getContext(), R.drawable.ic_launcher_background_legacy);
//            this.foreground = ContextCompat.getDrawable(getContext(), R.drawable.ic_launcher_foreground_legacy);
//        }
        iconView = imageView
        imageView!!.setOnClickListener {
            if (photoSelectUtil == null) {
                val customLauncherIconMakerDialog = this@CustomLauncherIconMakerDialog
                customLauncherIconMakerDialog.photoSelectUtil = PhotoSelectUtil(activity)
            }
            photoSelectUtil!!.pickPhoto(this@CustomLauncherIconMakerDialog)
        }
        showShapeLayout()
        showColorLayout()
        roundMaskView = imageView2
        imageView2!!.setOnClickListener {
            shape = 1
            val unused = shape
            updateMaskShape()
        }
        roundedSquareMaskView = imageView3
        imageView3!!.setOnClickListener {
            shape = 2
            val unused = shape
            updateMaskShape()
        }
        SquareMaskView = imageView4
        imageView4!!.setOnClickListener {
            shape = 3
            val unused = shape
            updateMaskShape()
        }
        rSeekView!!.progress = r
        gSeekView!!.progress = g
        bSeekView!!.progress = b
        rSeekView!!.setOnSeekBarChangeListener(this)
        gSeekView!!.setOnSeekBarChangeListener(this)
        bSeekView!!.setOnSeekBarChangeListener(this)
        maskColor()
        updateMaskShape()
    }

    override fun onProgressChanged(seekBar: SeekBar, i: Int, z: Boolean) {
        val id = seekBar.id
        if (id == R.id.bSeekView) {
            b = i
            mask()
        } else if (id == R.id.gSeekView) {
            g = i
            mask()
        } else if (id == R.id.rSeekView) {
            r = i
            mask()
        }
    }

    private fun mask() {
        maskColor()
        maskShape()
    }

    private fun maskColor() {
        background!!.colorFilter = ColorMatrixColorFilter(
            floatArrayOf(
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                r.toFloat(),
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                g.toFloat(),
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                b.toFloat(),
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                255.0f
            )
        )
    }

    /* access modifiers changed from: private */
    fun updateMaskShape() {
        roundMaskView!!.setColorFilter(resolveColor(requireContext(), android.R.attr.textColorSecondary))
        roundedSquareMaskView!!.setColorFilter(
            resolveColor(
                requireContext(),
                android.R.attr.textColorSecondary
            )
        )
        SquareMaskView!!.setColorFilter(resolveColor(requireContext(), android.R.attr.textColorSecondary))
        val i = shape
        if (i == 1) {
            roundMaskView!!.setColorFilter(resolveColor(requireContext(), androidx.appcompat.R.attr.colorAccent))
            maskResId = R.drawable.mask_round
            maskShape()
        } else if (i == 2) {
            roundedSquareMaskView!!.setColorFilter(resolveColor(requireContext(), androidx.appcompat.R.attr.colorAccent))
            maskResId = R.drawable.mask_rounded_square
            maskShape()
        } else if (i == 3) {
            SquareMaskView!!.setColorFilter(resolveColor(requireContext(), androidx.appcompat.R.attr.colorAccent))
            maskResId = R.drawable.mask_square
            maskShape()
        }
    }

    private fun maskShape() {
        val dip2px = dp2Px(requireContext(), ICON_SIZE.toFloat())
        if (path != null) {
            iconView!!.setImageBitmap(mask(requireContext(), path!!, maskResId, dip2px))
            return
        }
        iconView!!.setImageBitmap(
            mask(
                requireContext(),
                LayerDrawable(arrayOf(background, foreground)),
                maskResId,
                dip2px
            )
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handleActivityResult(requestCode, resultCode, data)
    }

    fun handleActivityResult(i: Int, i2: Int, intent: Intent?) {
        onSelected(photoSelectUtil!!.handleActivityResult(i, i2, intent))
    }

    companion object {
        private const val ICON_SIZE = 56
    }
}