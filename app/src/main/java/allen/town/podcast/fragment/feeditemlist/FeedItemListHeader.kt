package allen.town.podcast.fragment.feeditemlist

import allen.town.podcast.common.util.ImageUtils.getColoredDrawable
import allen.town.podcast.common.util.Timber
import allen.town.podcast.common.util.Util.dp2Px
import allen.town.podcast.MyApp.Companion.runOnUiThread
import allen.town.podcast.R
import allen.town.podcast.core.glide.ApGlideSettings
import allen.town.podcast.core.glide.FastBlurTransformation
import allen.town.podcast.core.storage.DBWriter
import allen.town.podcast.dialog.RemoveFeedDialog.OnFeedRemovedListener
import allen.town.podcast.dialog.RemoveFeedDialog.show
import allen.town.podcast.model.feed.Feed
import allen.town.podcast.view.SeriesDetailInfoView
import allen.town.podcast.view.SubscribeButton
import android.animation.Animator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.LightingColorFilter
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import allen.town.podcast.theme.ThemeStore.Companion.accentColor
import allen.town.podcast.theme.util.ATHUtil.resolveColor
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.joanzapata.iconify.Iconify

/**
 * Owns the collapsing header of [allen.town.podcast.fragment.FeedItemlistFragment]: the podcast
 * title/author labels, the blurred background and cover images, the "update failed" and "updates
 * disabled" notices, the filter icon state, the subscribe/unsubscribe button (including the remove
 * confirmation dialog) and the expandable [SeriesDetailInfoView] together with its open/close
 * animation. It never reads the fragment's state directly: the feed it renders is supplied through
 * the `currentFeed` provider, and the only thing it hands back is the navigation that has to happen
 * after a feed was removed.
 */
internal class FeedItemListHeader(
    private val fragment: Fragment,
    root: View,
    private val filterIcon: ImageView,
    private val currentFeed: () -> Feed?,
    private val onFeedRemoved: () -> Unit
) {
    private val txtvTitle: TextView = root.findViewById(R.id.txtvTitle)
    private val txtvAuthor: TextView = root.findViewById(R.id.txtvAuthor)
    private val imgvBackground: ImageView = root.findViewById(R.id.imgvBackground)
    private val imgvCover: ImageView = root.findViewById(R.id.imgvCover)
    private val txtvFailure: TextView = root.findViewById(R.id.txtvFailure)
    private val txtvUpdatesDisabled: TextView = root.findViewById(R.id.txtvUpdatesDisabled)

    /** The header container; the fragment still owns its status bar / orientation padding. */
    val headerView: View = root.findViewById(R.id.headerContainer)

    /** The blurred artwork behind the header; sized together with [headerView]. */
    val backgroundView: View get() = imgvBackground

    private val detailInfoView: SeriesDetailInfoView = root.findViewById(R.id.detailInfoView)
    private val infoViewToggleButton: View = root.findViewById(R.id.info_view_toggle_button)
    private val detailInfoViewContainer: FrameLayout =
        root.findViewById(R.id.series_info_view_container)
    private val subscribeButton: SubscribeButton = root.findViewById(R.id.subscribe_button)

    private var headerCreated = false

    init {
        imgvBackground.setOnClickListener { openAbout() }
        subscribeButton.setTickSize(dp2Px(fragment.requireContext(), 18.0f))
        subscribeButton.setOnClickListener {
            val feed = currentFeed() ?: return@setOnClickListener
            if (subscribeButton.isSubscribed()) {
                show(fragment.requireContext(), feed, object : OnFeedRemovedListener {
                    override fun onFeedRemoved() {
                        this@FeedItemListHeader.onFeedRemoved()
                    }
                })
            } else {
                DBWriter.subscribeFeed(feed, fragment.requireContext())
            }
        }
    }

    /** Keeps the header's side padding in sync with the current orientation. */
    fun setHorizontalSpacing(horizontalSpacing: Int) {
        headerView.setPadding(
            horizontalSpacing,
            headerView.paddingTop,
            horizontalSpacing,
            headerView.paddingBottom
        )
    }

    fun setEpisodesLoaded(loaded: Boolean) {
        detailInfoView.setEpisodesLoaded(loaded)
    }

    fun showSubscribeButton() {
        subscribeButton.setVisibility(View.VISIBLE)
    }

    fun showFeedUrlFailure() {
        txtvFailure.setText(R.string.null_value_podcast_error)
    }

    /**
     * Refresh the header: feed title, image, etc.
     */
    fun refresh() {
        setupHeaderView()
        val feed = currentFeed()
        if (feed == null) {
            Log.e(TAG, "Unable to refresh header view")
            return
        }
        loadFeedImage(feed)
        if (feed.hasLastUpdateFailed()) {
            txtvFailure.visibility = View.VISIBLE
        } else {
            txtvFailure.visibility = View.INVISIBLE
        }
        if (feed.preferences != null && !feed.preferences.keepUpdated) {
            txtvUpdatesDisabled.text =
                "{md-pause-circle-outline} " + fragment.getString(R.string.updates_disabled_label)
            Iconify.addIcons(txtvUpdatesDisabled)
            txtvUpdatesDisabled.visibility = View.VISIBLE
        } else {
            txtvUpdatesDisabled.visibility = View.GONE
        }
        txtvTitle.text = feed.title
        txtvAuthor.text = feed.author
        val itemFilter = feed.itemFilter
        if (itemFilter != null && itemFilter.values.isNotEmpty()) {
            filterIcon.setImageResource(R.drawable.ic_filter_disable)
        } else {
            filterIcon.setImageResource(R.drawable.ic_filter)
        }
    }

    private fun setupHeaderView() {
        if (currentFeed() == null || headerCreated) {
            return
        }

        // https://github.com/bumptech/glide/issues/529
        imgvBackground.colorFilter = LightingColorFilter(-0x99999a, 0x000000)
        imgvCover.setOnClickListener { openAbout() }
        headerCreated = true
    }

    private fun loadFeedImage(feed: Feed) {
        Glide.with(fragment)
            .load(feed.imageUrl)
            .apply(
                RequestOptions()
                    .placeholder(R.color.image_readability_tint)
                    .error(R.color.image_readability_tint)
                    .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                    .transform(FastBlurTransformation())
                    .dontAnimate()
            )
            .into(imgvBackground)
        Glide.with(fragment)
            .load(feed.imageUrl)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.ic_podcast_background_round)
                    .error(R.drawable.ic_podcast_background_round)
                    .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                    .centerCrop()
                    .dontAnimate()
            )
            .into(imgvCover)
    }

    fun initDetailView() {
        runOnUiThread({
            val ctx = fragment.getContext()
            if (ctx == null) {
                //entered from a home screen shortcut: the item gets loaded twice (one recent, one current), which leaves this null and makes neither RxJava branch run afterwards (reason unknown)
                Timber.e(" loadItems break getContext() == null")
                return@runOnUiThread
            }
            val feed = currentFeed() ?: return@runOnUiThread
            //must run on the main thread; called before downloading, since the feed may have to be fetched from the network if it is not in the local database
            if (!feed.isLocalFeed()) {
                infoViewToggleButton.setVisibility(View.VISIBLE)
                infoViewToggleButton.setOnClickListener { openAbout() }
                infoViewToggleButton.setBackground(
                    getColoredDrawable(
                        ctx, R.drawable.shape_circle, resolveColor(
                            ctx, android.R.attr.windowBackground
                        )
                    )
                )
                if (!feed.isSubscribed()) {
                    detailInfoView.expandCollapseContent(false, false)
                } else {
                    detailInfoView.setVisibility(View.GONE)
                }
            } else {
                detailInfoView.setVisibility(View.GONE)
            }

            //subscribe button state
            subscribeButton.setCircleRingColor(
                resolveColor(
                    ctx,
                    android.R.attr.windowBackground
                )
            )
            subscribeButton.setSubscribedIconColor(accentColor(ctx))
            subscribeButton.setSubscribed(feed.isSubscribed())
            var circleFillColor: Int = 0
            if (feed.isSubscribed()) {
                circleFillColor = subscribeButton.getCircleRingColor()
            }
            subscribeButton.setCircleFillColor(circleFillColor)
            detailInfoView.setData(feed, fragment.getActivity())
        })
    }

    fun openAbout() {
        val feed = currentFeed() ?: return
        if (!feed.isLocalFeed && detailInfoView.isAllDataLoaded) {
            if (!feed.isSubscribed) {
                detailInfoView.toggleCollapseMode()
                return
            }
            if (detailInfoView.isCollapsed) {
                detailInfoView.expandCollapseContent(true, false)
            }
            animateSeriesInfoView(null)
        }
    }

    /* synthetic */ private fun `lambda$animateSeriesInfoView$2`(valueAnimator: ValueAnimator) {
        infoViewToggleButton.rotation = (valueAnimator.animatedValue as Float).toFloat()
    }

    private fun animateSeriesInfoView(animatorListener: Animator.AnimatorListener?) {
        val feed = currentFeed() ?: return
        if (!feed.isLocalFeed) {
            val z = detailInfoView.visibility == View.VISIBLE
            detailInfoView.clearDescriptionSelection()
            val fArr = FloatArray(2)
            var f = 180.0f
            fArr[0] = if (z) 180.0f else 0.0f
            if (z) {
                f = 0.0f
            }
            fArr[1] = f
            val ofFloat = ValueAnimator.ofFloat(*fArr)
            ofFloat.duration = 150L
            ofFloat.addUpdateListener(AnimatorUpdateListener { valueAnimator ->

                // from class: fm.player.ui.fragments.i
                // android.animation.ValueAnimator.AnimatorUpdateListener
                Timber.e("rote " + valueAnimator.animatedValue)
                `lambda$animateSeriesInfoView$2`(valueAnimator)
            })
            ofFloat.start()
            if (z) {
                val viewHeight = detailInfoView.getViewHeight(false)
                if (viewHeight > 0) {
                    detailInfoViewContainer.layoutParams.height = viewHeight
                    detailInfoViewContainer.pivotY = 0.0f
                    detailInfoViewContainer.pivotX = 0.0f
                    val ofInt = ValueAnimator.ofInt(viewHeight, 0)
                    ofInt.addUpdateListener(object : AnimatorUpdateListener {
                        // from class: fm.player.ui.fragments.FeedItemlistFragment.10
                        // android.animation.ValueAnimator.AnimatorUpdateListener
                        override fun onAnimationUpdate(valueAnimator: ValueAnimator) {
                            detailInfoViewContainer.scaleY =
                                (valueAnimator.animatedValue as Int).toFloat() / viewHeight
                            detailInfoView.layoutParams.height =
                                (valueAnimator.animatedValue as Int)
                            detailInfoView.requestLayout()
                        }
                    })
                    ofInt.addListener(object : Animator.AnimatorListener {
                        // from class: fm.player.ui.fragments.FeedItemlistFragment.11
                        // android.animation.Animator.AnimatorListener
                        override fun onAnimationCancel(animator: Animator) {
                            val animatorListener2 = animatorListener
                            animatorListener2?.onAnimationCancel(animator)
                        }

                        // android.animation.Animator.AnimatorListener
                        override fun onAnimationEnd(animator: Animator) {
                            detailInfoView.visibility = View.GONE
                            val animatorListener2 = animatorListener
                            animatorListener2?.onAnimationEnd(animator)
                        }

                        // android.animation.Animator.AnimatorListener
                        override fun onAnimationRepeat(animator: Animator) {
                            val animatorListener2 = animatorListener
                            animatorListener2?.onAnimationRepeat(animator)
                        }

                        // android.animation.Animator.AnimatorListener
                        override fun onAnimationStart(animator: Animator) {
                            val animatorListener2 = animatorListener
                            animatorListener2?.onAnimationStart(animator)
                        }
                    })
                    ofInt.duration = 250L
                    ofInt.interpolator = AccelerateDecelerateInterpolator()
                    ofInt.start()
                    detailInfoView.animateFadeOverlayExpandButton(false, 250)
                    return
                }
                return
            }
            detailInfoView.measure(0, 0)
            val viewHeight2 = detailInfoView.getViewHeight(true)
            if (viewHeight2 > 0) {
                detailInfoViewContainer.layoutParams.height = viewHeight2
                detailInfoViewContainer.pivotY = 0.0f
                detailInfoViewContainer.pivotX = 0.0f
                detailInfoView.visibility = View.VISIBLE
                val ofInt2 = ValueAnimator.ofInt(0, viewHeight2)
                ofInt2.addUpdateListener(object : AnimatorUpdateListener {
                    // from class: fm.player.ui.fragments.FeedItemlistFragment.12
                    // android.animation.ValueAnimator.AnimatorUpdateListener
                    override fun onAnimationUpdate(valueAnimator: ValueAnimator) {
                        detailInfoViewContainer.scaleY =
                            (valueAnimator.animatedValue as Int).toFloat() / viewHeight2
                        detailInfoView.layoutParams.height =
                            (valueAnimator.animatedValue as Int)
                        detailInfoView.requestLayout()
                    }
                })
                ofInt2.addListener(object : Animator.AnimatorListener {
                    // from class: fm.player.ui.fragments.FeedItemlistFragment.13
                    // android.animation.Animator.AnimatorListener
                    override fun onAnimationCancel(animator: Animator) {}

                    // android.animation.Animator.AnimatorListener
                    override fun onAnimationEnd(animator: Animator) {
                        detailInfoView.layoutParams.height =
                            if (detailInfoView.isCollapsed) detailInfoView.collapsedHeight else -2
                        detailInfoViewContainer.layoutParams.height = -2
                        detailInfoViewContainer.requestLayout()
                        detailInfoView.requestLayout()
                    }

                    // android.animation.Animator.AnimatorListener
                    override fun onAnimationRepeat(animator: Animator) {}

                    // android.animation.Animator.AnimatorListener
                    override fun onAnimationStart(animator: Animator) {}
                })
                ofInt2.duration = 250L
                ofInt2.interpolator = AccelerateDecelerateInterpolator()
                ofInt2.start()
                detailInfoView.animateFadeOverlayExpandButton(true, 250)
            }
        }
    }

    private companion object {
        private const val TAG = "ItemlistFragment"
    }
}
