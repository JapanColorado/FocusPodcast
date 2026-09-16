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
     * Show the toolbar option menu icons, even for items with showAsAction="never".
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
     * Show the classic menu icons, even for items with showAsAction="never".
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
     * Context menu.
     */
    @JvmStatic
    fun showContextMenuIcon(menu: Menu) {
        if (menu.javaClass.simpleName == "ContextMenuBuilder") {
            try {
                // The builder a ContextMenu inflates into: com.android.internal.view.menu.MenuBuilder
                val clazz = Class.forName("com.android.internal.view.menu.MenuBuilder")
                val m = clazz.getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE)
                m.isAccessible = true
                // MenuBuilder implements Menu, so the menu handed to us on creation really is a MenuBuilder
                m.invoke(menu, true)
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * For menus created through startActionMode.
     */
    @JvmStatic
    fun showMenuWrapICSIcon(menu: Menu) {
        if (menu.javaClass.simpleName == "MenuWrapperICS") {
            try {
                val declaredField1: Field = menu.javaClass.getDeclaredField("mWrappedObject")
                declaredField1.isAccessible = true
                val objMenuBuilder: Any = declaredField1.get(menu)
                // The builder a ContextMenu inflates into: com.android.internal.view.menu.MenuBuilder
                val clazz = Class.forName("androidx.appcompat.view.menu.MenuBuilder")
                val m = clazz.getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE)
                m.isAccessible = true
                // MenuBuilder implements Menu, so the menu handed to us on creation really is a MenuBuilder
                m.invoke(objMenuBuilder, true)
            } catch (ignored: Exception) {
                Log.e("",ignored.toString())
            }
        }
    }
}