package allen.town.podcast.common.util

import android.annotation.SuppressLint
import android.view.Menu
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import java.lang.Boolean
import java.lang.reflect.Field
import kotlin.Any
import kotlin.Exception

// detekt: every method here reflects into @RestrictTo / hidden platform menu internals,
// which can fail in a different way on every OEM and AppCompat version. Menu icons are
// cosmetic, so each call site logs and carries on.
@Suppress("TooGenericExceptionCaught")
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
            // setOptionalIconsVisible is @RestrictTo, so it can disappear with an AppCompat
            // upgrade. Losing it only means overflow items show without their icons.
            Timber.w(e, "could not show the toolbar menu icons")
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
            // Same @RestrictTo caveat as above; icons are cosmetic.
            Timber.w(e, "could not show the menu icons")
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
            } catch (e: Exception) {
                // com.android.internal.view.menu.MenuBuilder is a hidden platform class; without
                // it the context menu simply shows no icons.
                Timber.w(e, "could not show the context menu icons")
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
            } catch (e: Exception) {
                // Reflection into MenuWrapperICS/MenuBuilder internals; without it the action
                // mode menu simply shows no icons.
                Timber.w(e, "could not show the action mode menu icons")
            }
        }
    }
}