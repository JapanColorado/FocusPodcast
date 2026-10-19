package dev.russell.focuspodcast.common.common

import androidx.appcompat.widget.Toolbar

import dev.russell.focuspodcast.theme.util.ToolbarContentTintHelper

class ATHActionBarActivity : ATHToolbarActivity() {

    override fun getATHToolbar(): Toolbar? {
        return ToolbarContentTintHelper.getSupportActionBarView(supportActionBar)
    }
}
