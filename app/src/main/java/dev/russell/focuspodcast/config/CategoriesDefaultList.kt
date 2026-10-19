package dev.russell.focuspodcast.config

import dev.russell.focuspodcast.common.model.CategoryInfo
import dev.russell.focuspodcast.fragment.*

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