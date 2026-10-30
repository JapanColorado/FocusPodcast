package dev.russell.yagi.common.common

import androidx.appcompat.widget.Toolbar

import dev.russell.yagi.theme.util.ToolbarContentTintHelper

class ATHActionBarActivity : ATHToolbarActivity() {

    override fun getATHToolbar(): Toolbar? {
        return ToolbarContentTintHelper.getSupportActionBarView(supportActionBar)
    }
}
