package allen.town.podcast.config

import allen.town.podcast.common.model.CategoryInfo
import allen.town.podcast.fragment.*

object CategoriesDefaultList {
    val defaultList: List<CategoryInfo>
        get() = listOf(
            CategoryInfo(SubFeedsFragment.TAG,true,true),
            CategoryInfo(EpisodesFragment.TAG,true,true),
            CategoryInfo(PlaylistFragment.TAG,true,true),
            CategoryInfo(FavoriteEpisodesFragment.TAG,true,true),
            CategoryInfo(DownloadPagerFragment.TAG,true,true)
        )
}