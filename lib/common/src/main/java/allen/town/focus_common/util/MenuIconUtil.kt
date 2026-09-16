package allen.town.focus_common.util

import android.annotation.SuppressLint
import android.util.Log
import android.view.Menu
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import java.lang.Boolean
import java.lang.reflect.Field
import kotlin.Any
import kotlin.Exception

object MenuIconUtil {
    /**
     * 显示toolbar方式的option menu图标，即使是never
     * @param toolbar
     */
    @SuppressLint("RestrictedApi")
    @JvmStatic
    fun showToolbarMenuIcon(toolbar: Toolbar) {
        try {
            if (toolbar.menu is MenuBuilder) {
                (toolbar.menu as MenuBuilder).setOptionalIconsVisible(true)
            }
        } catch (e: Exception) {
        }
    }

    /**
     * 显示传统方式 menu图标，即使是never
     * @param toolbar
     */
    @SuppressLint("RestrictedApi")
    @JvmStatic
    fun showMenuIcon(menu: Menu) {
        try {
            if (menu is MenuBuilder) {
                menu.setOptionalIconsVisible(true)
            }
        } catch (e: Exception) {
        }
    }

    /**
     * 上下文菜单
     */
    @JvmStatic
    fun showContextMenuIcon(menu: Menu) {
        if (menu.javaClass.simpleName == "ContextMenuBuilder") {
            try {
                //这是ContextMenu加载菜单的Builder，com.android.internal.view.menu.MenuBuilder
                val clazz = Class.forName("com.android.internal.view.menu.MenuBuilder")
                val m = clazz.getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE)
                m.isAccessible = true
                //MenuBuilder实现Menu接口，创建菜单时，传进来的menu其实就是MenuBuilder对象(java的多态特征)
                m.invoke(menu, true)
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * 使用startActionMode
     */
    @JvmStatic
    fun showMenuWrapICSIcon(menu: Menu) {
        if (menu.javaClass.simpleName == "MenuWrapperICS") {
            try {
                val declaredField1: Field = menu.javaClass.getDeclaredField("mWrappedObject")
                declaredField1.isAccessible = true
                val objMenuBuilder: Any = declaredField1.get(menu)
                //这是ContextMenu加载菜单的Builder，com.android.internal.view.menu.MenuBuilder
                val clazz = Class.forName("androidx.appcompat.view.menu.MenuBuilder")
                val m = clazz.getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE)
                m.isAccessible = true
                //MenuBuilder实现Menu接口，创建菜单时，传进来的menu其实就是MenuBuilder对象(java的多态特征)
                m.invoke(objMenuBuilder, true)
            } catch (ignored: Exception) {
                Log.e("",ignored.toString())
            }
        }
    }
}