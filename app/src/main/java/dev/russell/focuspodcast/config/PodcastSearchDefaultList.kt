package dev.russell.focuspodcast.config

import dev.russell.focuspodcast.common.model.CategoryInfo
import dev.russell.focuspodcast.discovery.FyydPodcastSearcher
import dev.russell.focuspodcast.discovery.ItunesPodcastSearcher
import dev.russell.focuspodcast.discovery.PodcastIndexPodcastSearcher

object PodcastSearchDefaultList {
    val defaultList: List<CategoryInfo>
        get() = listOf(
            CategoryInfo(
                ItunesPodcastSearcher.SEARCH_ENGINE_TAG,
                true,
                true
            ),
            CategoryInfo(
                PodcastIndexPodcastSearcher.SEARCH_ENGINE_TAG,
                false,
                true
            ),
            CategoryInfo(
                FyydPodcastSearcher.SEARCH_ENGINE_TAG,
                false,
                true
            )

        )
}