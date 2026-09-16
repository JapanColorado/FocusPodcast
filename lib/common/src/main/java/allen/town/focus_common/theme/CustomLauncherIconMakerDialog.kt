package allen.town.focus_common.theme

import allen.town.focus_common.R
import allen.town.focus_common.databinding.DialogCustomLauncherIconMakerBinding
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
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.content.ContextCompat
import code.name.monkey.appthemehelper.util.ATHUtil.resolveColor
import allen.town.focus_common.extensions.addAccentColor

class CustomLauncherIconMakerDialog(
    private val custom_launcher_title: Int,
    private val packageName: String,
    private val className: String,
    private val backgroundColor: Int,
    private val foregroundLogo: Int,
    private val backgroundLogo: Int,
    private val appName: String
) : AppCompatDialogFragment(), OnSeekBarChangeListener {
    private var binding: DialogCustomLauncherIconMakerBinding? = null
    private var b = 0

    /* access modifiers changed from: private */
    var background: Drawable? = null

    /* access modifiers changed from: private */
    var custom = false

    /* access modifiers changed from: private */
    var foreground: Drawable? = null
    private var g = 0
    private var maskResId = 0
    private var path: Uri? = null
    var photoSelectUtil: PhotoSelectUtil? = null
    private var r = 0

    /* access modifiers changed from: private */
    var shape = 3
    override fun onStartTrackingTouch(seekBar: SeekBar) {}
    override fun onStopTrackingTouch(seekBar: SeekBar) {}

    // android.support.v4.app.Fragment
    override fun onCreateView(
        layoutInflater: LayoutInflater, viewGroup: ViewGroup?,
        bundle: Bundle?
    ): View? {
        requireDialog().setCanceledOnTouchOutside(true)
        return super.onCreateView(layoutInflater, viewGroup, bundle)
    }

    // android.support.v7.app.AppCompatDialogFragment, android.support.v4.app.DialogFragment
    override fun onCreateDialog(bundle: Bundle?): Dialog {
        val views = DialogCustomLauncherIconMakerBinding
            .inflate(LayoutInflater.from(requireActivity()))
        binding = views
        val alertDialog = AccentMaterialDialog(requireActivity(), R.style.MaterialAlertDialogTheme)
            .setTitle(custom_launcher_title)
            .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, _: Int ->
                onConfirmDialog(dialogInterface)
            }
            .setView(views.root).create()
        views.rSeekView.addAccentColor()
        views.gSeekView.addAccentColor()
        views.bSeekView.addAccentColor()
        views.nameView.text = appName
        onCreateContentLayout()
        return alertDialog
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    fun onConfirmDialog(dialogInterface: DialogInterface) {
        val views = binding
        val obj = views?.nameView?.text?.toString().orEmpty()
        var drawable: Drawable? = views?.iconView?.drawable
        if (!TextUtils.isEmpty(obj) && drawable != null) {
            val intent = Intent()
            intent.setClassName(packageName, className)
            if (!custom && Build.VERSION.SDK_INT >= 26) {
                drawable = AdaptiveIconDrawable(background, foreground)
            }
            ShortCutUtils.install(context, obj, drawable, intent, false)
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
        val views = binding ?: return
        views.shapeLayout.visibility = View.GONE
    }

    private fun showColorLayout() {
        val views = binding ?: return
        views.colorLayout.visibility = if (custom) View.GONE else View.VISIBLE
    }

    fun onCreateContentLayout() {
        val views = binding ?: return
        background = ContextCompat.getDrawable(requireContext(), backgroundLogo)
        foreground = ContextCompat.getDrawable(requireContext(), foregroundLogo)

        views.iconView.setOnClickListener {
            val selectUtil = photoSelectUtil
                ?: PhotoSelectUtil(activity).also { util -> photoSelectUtil = util }
            selectUtil.pickPhoto(this@CustomLauncherIconMakerDialog)
        }
        showShapeLayout()
        showColorLayout()
        views.roundMaskView.setOnClickListener {
            shape = 1
            updateMaskShape()
        }
        views.roundedSquareMaskView.setOnClickListener {
            shape = 2
            updateMaskShape()
        }
        views.SquareMaskView.setOnClickListener {
            shape = 3
            updateMaskShape()
        }
        views.rSeekView.progress = r
        views.gSeekView.progress = g
        views.bSeekView.progress = b
        views.rSeekView.setOnSeekBarChangeListener(this)
        views.gSeekView.setOnSeekBarChangeListener(this)
        views.bSeekView.setOnSeekBarChangeListener(this)
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
        background?.colorFilter = ColorMatrixColorFilter(
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
        val views = binding ?: return
        views.roundMaskView.setColorFilter(
            resolveColor(requireContext(), android.R.attr.textColorSecondary)
        )
        views.roundedSquareMaskView.setColorFilter(
            resolveColor(
                requireContext(),
                android.R.attr.textColorSecondary
            )
        )
        views.SquareMaskView.setColorFilter(
            resolveColor(requireContext(), android.R.attr.textColorSecondary)
        )
        val i = shape
        if (i == 1) {
            views.roundMaskView.setColorFilter(
                resolveColor(requireContext(), androidx.appcompat.R.attr.colorAccent)
            )
            maskResId = R.drawable.mask_round
            maskShape()
        } else if (i == 2) {
            views.roundedSquareMaskView.setColorFilter(
                resolveColor(requireContext(), androidx.appcompat.R.attr.colorAccent)
            )
            maskResId = R.drawable.mask_rounded_square
            maskShape()
        } else if (i == 3) {
            views.SquareMaskView.setColorFilter(
                resolveColor(requireContext(), androidx.appcompat.R.attr.colorAccent)
            )
            maskResId = R.drawable.mask_square
            maskShape()
        }
    }

    private fun maskShape() {
        val views = binding ?: return
        val dip2px = dp2Px(requireContext(), ICON_SIZE.toFloat())
        val selectedPath = path
        if (selectedPath != null) {
            views.iconView.setImageBitmap(mask(requireContext(), selectedPath, maskResId, dip2px))
            return
        }
        views.iconView.setImageBitmap(
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
        val selectUtil = photoSelectUtil ?: return
        onSelected(selectUtil.handleActivityResult(i, i2, intent))
    }

    companion object {
        private const val ICON_SIZE = 56
    }
}
