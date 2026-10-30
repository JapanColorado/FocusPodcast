package dev.russell.yagi.config

import dev.russell.yagi.common.model.CategoryInfo
import dev.russell.yagi.fragment.*

object CategoriesDefaultList {
    val defaultList: List<CategoryInfo>
        get() = listOf(
            CategoryInfo(SubFeedsFragment.TAG,true,true),
            CategoryInfo(EpisodesFragment.TAG,true,true),
            CategoryInfo(PlaylistFragment.TAG,true,true),
            CategoryInfo(FavoriteEpisodesFragment.TAG,true,true),
            CategoryInfo(BookmarksFragment.TAG,true,true),
            CategoryInfo(DownloadPagerFragment.TAG,true,true)
        )
}