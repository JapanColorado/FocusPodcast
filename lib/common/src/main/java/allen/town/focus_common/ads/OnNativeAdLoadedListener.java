package allen.town.focus_common.ads;

import android.view.View;

import allen.town.focus_common.ad.entry.NativeAdEntry;

public interface OnNativeAdLoadedListener {
    void onNativeAdLoaded(NativeAdEntry nativeAdEntry, float position);
}