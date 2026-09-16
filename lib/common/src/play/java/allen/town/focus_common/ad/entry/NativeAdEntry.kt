package allen.town.focus_common.ad.entry

import com.google.android.gms.ads.nativead.NativeAd

class NativeAdEntry(val nativeAd: NativeAd) {
    fun destroy() {
        nativeAd.destroy()
    }
}