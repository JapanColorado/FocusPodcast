package allen.town.focus_common.ad;

import android.app.Application;
import android.view.ViewGroup;

import allen.town.focus_common.ads.OnOpenAdFinishListener;

public class AppOpenAdManager {

    /**
     * Constructor
     */
    public AppOpenAdManager(Application myApplication) {
    }


    public void fetchAd(ViewGroup adContainer, OnOpenAdFinishListener onOpenAdFinishListener) {
        onOpenAdFinishListener.onNext();

    }

}
