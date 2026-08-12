package com.peoplelab.pdfjpgconverter;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public final class RewardedAdGate {
    private static final String TEST_REWARDED_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/5224354917";

    private RewardedAd rewardedAd;
    private boolean isLoading;
    private Runnable pendingAction;
    private Activity pendingActivity;

    public void initialize(Context context) {
        MobileAds.initialize(context, initializationStatus -> load(context));
    }

    public void load(Context context) {
        if (isLoading || rewardedAd != null) return;
        isLoading = true;

        RewardedAd.load(
                context,
                TEST_REWARDED_AD_UNIT_ID,
                new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        isLoading = false;
                        rewardedAd = ad;
                        if (pendingAction != null && pendingActivity != null) {
                            Activity activity = pendingActivity;
                            Runnable action = pendingAction;
                            pendingActivity = null;
                            pendingAction = null;
                            showLoadedAd(activity, action);
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        isLoading = false;
                        rewardedAd = null;
                        if (pendingActivity != null) {
                            Toast.makeText(
                                    pendingActivity,
                                    "광고를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            if (BuildConfig.DEBUG && pendingAction != null) {
                                Runnable action = pendingAction;
                                pendingActivity = null;
                                pendingAction = null;
                                action.run();
                            } else {
                                pendingActivity = null;
                                pendingAction = null;
                            }
                        }
                    }
                }
        );
    }

    public void showOrLoadThen(Activity activity, Runnable onReward) {
        if (rewardedAd != null) {
            showLoadedAd(activity, onReward);
            return;
        }

        pendingActivity = activity;
        pendingAction = onReward;
        Toast.makeText(activity, "광고를 준비하고 있습니다…", Toast.LENGTH_SHORT).show();
        load(activity);
    }

    private void showLoadedAd(Activity activity, Runnable onReward) {
        RewardedAd ad = rewardedAd;
        if (ad == null) {
            showOrLoadThen(activity, onReward);
            return;
        }

        final boolean[] rewardEarned = {false};
        rewardedAd = null;

        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                load(activity.getApplicationContext());
                if (!rewardEarned[0]) {
                    Toast.makeText(
                            activity,
                            "광고 시청을 완료하면 변환이 시작됩니다.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                load(activity.getApplicationContext());
                Toast.makeText(
                        activity,
                        "광고 표시 중 오류가 발생했습니다.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        ad.show(activity, rewardItem -> {
            if (!rewardEarned[0]) {
                rewardEarned[0] = true;
                onReward.run();
            }
        });
    }
}
