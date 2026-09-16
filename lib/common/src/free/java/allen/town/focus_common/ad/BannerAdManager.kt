package allen.town.focus_common.ad

import allen.town.core.service.AdService
import allen.town.core.service.PayService
import allen.town.focus_common.ad.entry.NativeAdEntry
import allen.town.focus_common.ads.OnNativeAdLoadedListener
import allen.town.focus_common.util.Timber
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.wyjson.router.GoRouter
import com.zh.pocket.ads.banner.BannerAD
import com.zh.pocket.ads.banner.BannerADListener
import com.zh.pocket.error.ADError

object BannerAdManager {
    var mBannerAD: BannerAD? = null
    @JvmStatic
    fun showBannerAd(
        context: Activity,
        bottomAdView: ViewGroup,
        position: Float = 0f,
        onNativeAdLoadedListener: OnNativeAdLoadedListener? = null
    ) {
        if (!GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
            Timber.d("show banner ads")
            //穿山甲创建广告后的尺寸不能修改，所以高度固定了，默认隐藏
            mBannerAD = BannerAD(context, GoRouter.getInstance().getService(AdService::class.java)!!.getBannerAdUnitId())
            mBannerAD!!.setBannerADListener(object : BannerADListener {
                override fun onSuccess() {
                    if (GoRouter.getInstance().getService(PayService::class.java)!!.isAdBlocker()) {
                        //虽然隐藏了广告view，但是banner广告会自动刷新，依旧会调用onAdLoaded
                        Timber.i("destroy bottom ads view ")
                        bottomAdView.removeAllViews()
                        //destroy是空的实现
                        mBannerAD?.destroy()
                    } else {
                        bottomAdView.visibility = View.VISIBLE
                        Timber.i("show banner ads success")
                        onNativeAdLoadedListener?.onNativeAdLoaded(NativeAdEntry(mBannerAD!!),position)
                    }
                }

                override fun onFailed(leError: ADError) {
                    Timber.e("show banner ads failed $leError")
                }

                override fun onADExposure() {}
                override fun onADClicked() {}
                override fun onADClosed() {}
            })
            if (bottomAdView.childCount > 0) {
                Timber.i("banner ads will refresh it self,do not need request again")
            } else {
                mBannerAD!!.loadAD(bottomAdView)
            }
        } else {
            bottomAdView.visibility = View.GONE
            Timber.d("hide banner ads button")
        }
    }
}