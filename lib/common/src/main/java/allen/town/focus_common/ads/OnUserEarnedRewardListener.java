package allen.town.focus_common.ads;

public interface OnUserEarnedRewardListener {
    void onUserEarnedReward();
    void onClosed(boolean isEarned);
}