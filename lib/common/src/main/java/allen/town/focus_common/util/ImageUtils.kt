package allen.town.focus_common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import code.name.monkey.appthemehelper.util.ColorUtil
import java.io.FileDescriptor


object ImageUtils {


    fun getSampleSize(context: Context, uri: Uri, i: Int, i2: Int): Int {

        val parcelFileDescriptor: ParcelFileDescriptor =
                context.contentResolver.openFileDescriptor(uri, "r")!!
        val fileDescriptor: FileDescriptor = parcelFileDescriptor.fileDescriptor

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options)
        var i3 = options.outWidth / i
        val i4 = options.outHeight / i2
        if (i3 > i4) {
            i3 = i4
        }
        return if (i3 == 0) {
            1
        } else i3
    }

    fun getSampleBitmap(context: Context, uri: Uri, i: Int): Bitmap {

        val parcelFileDescriptor: ParcelFileDescriptor =
                context.contentResolver.openFileDescriptor(uri, "r")!!
        val fileDescriptor: FileDescriptor = parcelFileDescriptor.fileDescriptor

        val options = BitmapFactory.Options()
        options.inSampleSize = i
        return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options)
    }

    fun zoomBitmap(bitmap: Bitmap, i: Int): Bitmap? {
        val matrix = Matrix()
        val f = (1.0 / i.toDouble()).toFloat()
        matrix.postScale(f, f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun clipBitmap(bitmap: Bitmap, i: Int, i2: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        return if (width < i || height < i2) bitmap else Bitmap.createBitmap(bitmap, (width - i) / 2, (height - i2) / 2, i, i2)
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable): Bitmap? {
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        val createBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, if (drawable.opacity != -1) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        val canvas = Canvas(createBitmap)
        drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        drawable.draw(canvas)
        return createBitmap
    }

    fun drawableToBitmap(drawable: Drawable?, i: Int, i2: Int): Bitmap? {
        val createBitmap = Bitmap.createBitmap(i, i2, if (drawable!!.opacity != -1) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        val canvas = Canvas(createBitmap)
        drawable.setBounds(0, 0, i, i2)
        drawable.draw(canvas)
        return createBitmap
    }

    fun getRoundedCornerBitmap(bitmap: Bitmap, f: Float): Bitmap? {
        val createBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(createBitmap)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        paint.setAntiAlias(true)
        canvas.drawARGB(0, 0, 0, 0)
        paint.setColor(-12434878)
        canvas.drawRoundRect(rectF, f, f, paint)
        paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return createBitmap
    }

    fun createReflectionImageWithOrigin(bitmap: Bitmap): Bitmap? {
        val width = bitmap.width
        val height = bitmap.height
        val matrix = Matrix()
        matrix.preScale(1.0f, -1.0f)
        val i = height / 2
        val createBitmap = Bitmap.createBitmap(bitmap, 0, i, width, i, matrix, false)
        val createBitmap2 = Bitmap.createBitmap(width, i + height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(createBitmap2)
        canvas.drawBitmap(bitmap, 0.0f, 0.0f, null as Paint?)
        val f = height.toFloat()
        val f2 = width.toFloat()
        val f3 = (height + 4) as Float
        canvas.drawRect(0.0f, f, f2, f3, Paint())
        canvas.drawBitmap(createBitmap, 0.0f, f3, null as Paint?)
        val paint = Paint()
        paint.setShader(LinearGradient(0.0f, bitmap.height.toFloat(), 0.0f, (createBitmap2.height + 4).toFloat(), 1895825407, ViewCompat.MEASURED_SIZE_MASK, Shader.TileMode.CLAMP))
        paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.DST_IN))
        canvas.drawRect(0.0f, f, f2, (createBitmap2.height + 4).toFloat(), paint)
        return createBitmap2
    }

    fun drawShadow(bitmap: Bitmap, i: Int): Bitmap? {
        val paint = Paint()
        paint.setAntiAlias(true)
        val f = (i.toDouble() * 0.5).toFloat()
        paint.setShadowLayer(f, f, f, -2013265920)
        val width = bitmap.width
        val height = bitmap.height
        val createBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(createBitmap)
        val rect = Rect(0, 0, width - i, height - i)
        val rect2 = Rect(0, 0, width, height)
        canvas.drawRoundRect(RectF(rect), 10.0f, 10.0f, paint)
        canvas.setDrawFilter(PaintFlagsDrawFilter(0, 3))
        canvas.drawBitmap(bitmap, rect2, rect, null as Paint?)
        return createBitmap
    }


    fun zoomBitmap(bitmap: Bitmap, i: Int, i2: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val matrix = Matrix()
        matrix.postScale(i.toFloat() / width.toFloat(), i2.toFloat() / height.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    /**
     * 将图片放大或缩小到指定尺寸
     */
    @JvmStatic
    fun resizeImage(source: Bitmap?, dstWidth: Int, dstHeight: Int): Bitmap? {
        if (source == null) {
            return null
        }
        return if (source.width == dstWidth && source.height == dstHeight) {
            source
        } else Bitmap.createScaledBitmap(source, dstWidth, dstHeight, true)
    }

    /**
     * 将图片剪裁为圆形
     */
    @JvmStatic
    fun createCircleImage(source: Bitmap?): Bitmap? {
        if (source == null) {
            return null
        }
        val length = Math.min(source.width, source.height)
        val paint = Paint()
        paint.isAntiAlias = true
        val target = Bitmap.createBitmap(length, length, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawCircle(
            (source.width / 2).toFloat(),
            (source.height / 2).toFloat(),
            (length / 2).toFloat(),
            paint
        )
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return target
    }

    fun centerCropBitmap(context: Context, uri: Uri, i: Int, i2: Int): Bitmap {
        val sampleBitmap = getSampleBitmap(context, uri, getSampleSize(context, uri, i, i2))
        val width = sampleBitmap.width
        val height = sampleBitmap.height
        return if (width < i || height < i2) {
            zoomBitmap(sampleBitmap, i, i2)
        } else clipBitmap(sampleBitmap, i, i2)
    }

    @JvmStatic
    fun getColoredDrawable(context: Context?, drawableRes: Int, mul: Int): Drawable? {
        val drawable = ContextCompat.getDrawable(context!!, drawableRes)
        if (mul != -1) {
            drawable!!.mutate().colorFilter = LightingColorFilter(mul, 0)
            drawable.alpha = Color.alpha(mul)
        }
        return drawable
    }

    @JvmStatic
    fun getColoredDrawable(drawable: Drawable, mul: Int): Drawable? {
        if (mul != -1) {
            drawable.mutate().colorFilter = LightingColorFilter(mul, 0)
            drawable.alpha = Color.alpha(mul)
        }
        return drawable
    }

    @JvmStatic
    fun getColoredSelector(i: Int): Drawable {
        return RippleDrawable(
            ColorStateList(
                arrayOf(IntArray(0)),
                intArrayOf(ColorUtil.adjustAlpha(i, 0.2f))
            ), null, null
        )
    }

    @JvmStatic
    fun getColoredVectorDrawable(context: Context, i: Int, i2: Int): Drawable? {
        val create = VectorDrawableCompat.create(context.resources, i, context.theme)
        create!!.mutate().colorFilter = LightingColorFilter(i2, 0)
        create.alpha = Color.alpha(i2)
        return create
    }

    @JvmStatic
    fun mask(context: Context, uri: Uri, i: Int, i2: Int): Bitmap? {
        return maskSub(context, centerCropBitmap(context, uri, i2, i2), i, i2)
    }

    @JvmStatic
    fun mask(context: Context, drawable: Drawable?, i: Int, px: Int): Bitmap? {
        return maskSub(context, drawableToBitmap(drawable, px, px), i, px)
    }

    private fun maskSub(context: Context, bitmap: Bitmap?, i: Int, px: Int): Bitmap? {
        val drawableToBitmap = drawableToBitmap(ContextCompat.getDrawable(context, i), px, px)
        val paint = Paint(1)
        paint.isDither = true
        val createBitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(createBitmap)
        canvas.drawBitmap(drawableToBitmap!!, Matrix(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap!!, Matrix(), paint)
        return createBitmap
    }

    /**
     * Utility function to check if system picker is available or not on Android 11+.
     * The function is provided by google to check whether the photo picker is available or not
     * [More Details](https://developer.android.com/training/data-storage/shared/photopicker#check-availability)
     *
     * Using SuppressLint to remove warning about the getExtensionVersion method.
     */
    @SuppressLint("NewApi")
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    @JvmStatic
    fun isPhotoPickerAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2
        } else {
            false
        }
    }
}