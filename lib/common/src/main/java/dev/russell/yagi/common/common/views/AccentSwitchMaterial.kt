package dev.russell.yagi.common.common.views

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import dev.russell.yagi.theme.ATH
import dev.russell.yagi.theme.ThemeStore
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * @author Aidan Follestad (afollestad)
 */
class AccentSwitchMaterial : SwitchMaterial {

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    private fun init(context: Context) {
        ATH.setTint(this, ThemeStore.accentColor(context))
    }

    override fun isShown(): Boolean {
        return parent != null && isVisible
    }
}