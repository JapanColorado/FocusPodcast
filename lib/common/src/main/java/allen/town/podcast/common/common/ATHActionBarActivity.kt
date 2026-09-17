package allen.town.podcast.common.common

import androidx.appcompat.widget.Toolbar

import allen.town.podcast.theme.util.ToolbarContentTintHelper

class ATHActionBarActivity : ATHToolbarActivity() {

    override fun getATHToolbar(): Toolbar? {
        return ToolbarContentTintHelper.getSupportActionBarView(supportActionBar)
    }
}
