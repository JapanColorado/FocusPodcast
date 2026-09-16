package allen.town.focus_common.activity

import androidx.appcompat.app.AppCompatActivity
import allen.town.focus_common.common.ATHToolbarActivity
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import java.lang.ref.WeakReference
import java.util.HashMap
import java.util.HashSet

open class ToolbarBaseActivity : ATHToolbarActivity(),ClearAllActivityInterface {
    private val entranceActivityNames = HashSet<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activities[hashCode()] =
            WeakReference(this)
    }


    /**
     * 设置主activity，退出时将关闭所有activity
     *
     * @param entranceActivityName
     */
    protected fun addEntranceActivityName(entranceActivityName: String) {
        entranceActivityNames.add(entranceActivityName)
    }

    protected fun removeEntranceActivityName(entranceActivityName: String) {
        entranceActivityNames.remove(entranceActivityName)
    }

    override fun onDestroy() {
        super.onDestroy()
        val removed = activities.remove(this.hashCode())!!
    }

    override fun finish() {
        super.finish()
//        if (entranceActivityNames.contains(javaClass.simpleName)) {
//            clearAllAppcompactActivities(false)
//        }
    }

    /**
     * finish all activitys
     */
    override fun clearAllAppcompactActivities(recreate: Boolean) {
        val leftActivities = HashMap(activities)
        val N = leftActivities.size
        if (DEBUG) {
            Log.d(THIS_FILE, "left activities: $N")
        }
        val iter: Iterator<WeakReference<AppCompatActivity>> = leftActivities.values.iterator()
        var leftActivity: AppCompatActivity?
        var ref: WeakReference<AppCompatActivity>
        var isFinishing = false
        while (iter.hasNext()) {
            ref = iter.next()
            leftActivity = if (ref != null) ref.get() else null
            if (leftActivity != null) {
                isFinishing = leftActivity.isFinishing
                if (!isFinishing) {
                    if (recreate) {
                        ActivityCompat.recreate(leftActivity)
                    } else {
                        leftActivity.finish()
                    }
                }
                if (DEBUG) {
                    Log.d(THIS_FILE, "left activity: $leftActivity is finishing? $isFinishing")
                }
            }
        }
        if (DEBUG) {
            Log.d(THIS_FILE, "clearAllBasicAppComapctActivites DONE!!!")
        }
    }

    companion object {
        private const val DEBUG = false
        private const val THIS_FILE = "BasicAppComapctActivity"
        private val activities = HashMap<Int, WeakReference<AppCompatActivity>>()
    }
}