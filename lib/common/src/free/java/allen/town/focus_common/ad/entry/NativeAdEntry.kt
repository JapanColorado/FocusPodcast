package allen.town.focus_common.ad.entry

import com.zh.pocket.ads.banner.BannerAD


class NativeAdEntry(val bannerAD: BannerAD) {
    fun destroy() {
        bannerAD.destroy()
    }
}