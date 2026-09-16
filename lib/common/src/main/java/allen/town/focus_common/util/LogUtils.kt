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
        val uri: Uri?
        val sb = StringBuilder()
        uri = try {
            val bufferedReader = BufferedReader(
                InputStreamReader(
                    Runtime.getRuntime()
                        .exec(arrayOf("logcat", "-d", "-v", "threadtime")).inputStream
                )
            )
            while (true) {
                val readLine = bufferedReader.readLine() ?: break
                sb.append(readLine)
                sb.append("\n")
            }
            val file = File(context.getExternalCacheDir(), "log.txt")
            Timber.d("log path " + file.absolutePath)
            val fileWriter = FileWriter(file)
            fileWriter.write(sb.toString())
            fileWriter.close()
            if (Build.VERSION.SDK_INT <= 22) {
//                6.0及以下
                Uri.fromFile(file)
            } else {
                //7.0及以上
                FileProvider.getUriForFile(
                    context,
                    providerAuth,
                    file
                )
            }
        } catch (e: IOException) {
            Timber.e(e, "Cannot retrieve logcat", *arrayOfNulls(0))
            null
        }
        subscriber.onNext(uri!!)
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
            .observeOn(AndroidSchedulers.mainThread()).subscribe { uri ->
                EmailUtils.emailToMe(
                    uri as Uri,
                    appName,
                    versionName,
                    activity,
                    to
                )
            }
    }
}