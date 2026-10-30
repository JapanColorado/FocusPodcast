package dev.russell.yagi.config

import dev.russell.yagi.common.model.CategoryInfo
import dev.russell.yagi.discovery.FyydPodcastSearcher
import dev.russell.yagi.discovery.ItunesPodcastSearcher
import dev.russell.yagi.discovery.PodcastIndexPodcastSearcher

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