package allen.town.focus_common.util

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
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
            FileWriter(file).use { it.write(sb.toString()) }
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

    @JvmStatic
    fun delegateFeedback(
        appName: String,
        versionName: String, to: String,
        activity: FragmentActivity,
        providerAuth:String
    ) {
        Observable.create<Uri> { subscriber ->
            logToSd(
                subscriber,
                activity,
                providerAuth
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ uri ->
                EmailUtils.emailToMe(
                    uri,
                    appName,
                    versionName,
                    activity,
                    to
                )
            }, { throwable ->
                // Without an onError consumer RxJava would rethrow this as an
                // OnErrorNotImplementedException and crash the app.
                Timber.e(throwable, "could not attach the log to the feedback mail")
            })
    }
}