package dev.russell.yagi.common.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dev.russell.yagi.common.R
import io.reactivex.ObservableEmitter
import java.io.*

object LogUtils {
    @JvmStatic
    fun logToSd(subscriber: ObservableEmitter<in Uri>, context: Context,providerAuth:String) {
        val uri: Uri
        val sb = StringBuilder()
        uri = try {
            BufferedReader(
                InputStreamReader(
                    Runtime.getRuntime()
                        .exec(arrayOf("logcat", "-d", "-v", "threadtime")).inputStream
                )
            ).use { reader ->
                while (true) {
                    val readLine = reader.readLine() ?: break
                    sb.append(readLine)
                    sb.append("\n")
                }
            }
            val file = File(context.getExternalCacheDir(), "log.txt")
            Timber.d("log path " + file.absolutePath)
            FileWriter(file).use { it.write(LogRedaction.redactUrls(sb.toString())) }
            if (Build.VERSION.SDK_INT <= 22) {
//                6.0 and below
                Uri.fromFile(file)
            } else {
                // 7.0 and above
                FileProvider.getUriForFile(
                    context,
                    providerAuth,
                    file
                )
            }
        } catch (e: IOException) {
            // The caller cannot do anything with a half-written log, so fail the stream instead
            // of emitting a null Uri that the subscriber would have to dereference anyway.
            Timber.e(e, "Cannot retrieve logcat")
            subscriber.onError(e)
            return
        }
        subscriber.onNext(uri)
        subscriber.onComplete()
    }

    /**
     * A share sheet with the log written by [logToSd] attached, so it can be saved or sent and then
     * attached to a bug report.
     */
    @JvmStatic
    fun shareLogIntent(context: Context, logUri: Uri, subject: String): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(
            Intent.EXTRA_TEXT,
            "Android: ${Build.VERSION.RELEASE} ${Build.MANUFACTURER} ${Build.MODEL}"
        )
        intent.putExtra(Intent.EXTRA_STREAM, logUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, context.getString(R.string.bug_report_share_log))
    }
}