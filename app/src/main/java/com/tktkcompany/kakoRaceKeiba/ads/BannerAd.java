package com.tktkcompany.kakoRaceKeiba.ads;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 画面ごとのバナー。
 * Navigation では画面に戻るたびに View が作り直されるが、AdView はユニットごとにとっておいて付け直す。
 * こうすると読み込み（リクエスト）は画面ごとに1回で済み、あとの入れ替えは AdMob の自動更新に任せられる。
 * 画面が見えている間だけ resume し、隠れたら pause する。
 */
public final class BannerAd {

    private static final String TAG = "BannerAd";

    /** ユニット ID ごとの、作ったバナー */
    private static final Map<String, Entry> CACHE = new HashMap<>();

    private static final class Entry {
        final AdView view;
        final AdSize size;
        // 読み込みに失敗したら、次に画面を開いたときにもう一度読み込む
        boolean failed;

        Entry(AdView view, AdSize size) {
            this.view = view;
            this.size = size;
        }
    }

    private BannerAd() {
    }

    /** container にバナーを出す。onCreateView か onViewCreated で呼ぶ。広告なしのアカウントなら container を隠す */
    public static void show(@NonNull Fragment fragment, @NonNull FrameLayout container, @NonNull String unitId) {
        if (AdFree.isOn(container.getContext())) {
            container.setVisibility(View.GONE);
            return;
        }
        Activity activity = fragment.requireActivity();
        AdSize size = adaptiveSize(activity);
        Entry entry = CACHE.get(unitId);
        if (entry != null && (entry.view.getContext() != activity || !entry.size.equals(size))) {
            // 画面の作り直し（回転など）で Activity や幅が変わったら作り直す
            destroy(unitId);
            entry = null;
        }
        if (entry == null) {
            entry = create(activity, unitId, size);
            watchActivity(activity);
        } else if (entry.failed) {
            entry.failed = false;
            entry.view.loadAd(new AdRequest.Builder().build());
        }

        AdView view = entry.view;
        ViewGroup oldParent = (ViewGroup) view.getParent();
        if (oldParent != null) oldParent.removeView(view);
        view.setVisibility(View.VISIBLE);
        container.removeAllViews();
        container.addView(view);
        container.setVisibility(View.VISIBLE);

        fragment.getViewLifecycleOwner().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onResume(@NonNull LifecycleOwner owner) {
                view.resume();
            }

            @Override
            public void onPause(@NonNull LifecycleOwner owner) {
                view.pause();
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                // 画面の View は捨てられるが、バナーはとっておく（次に開いたとき付け直す）
                if (view.getParent() == container) container.removeView(view);
            }
        });
    }

    private static Entry create(Activity activity, String unitId, AdSize size) {
        AdView view = new AdView(activity);
        view.setAdUnitId(unitId);
        view.setAdSize(size);
        Entry entry = new Entry(view, size);
        view.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                entry.failed = true;
                Log.w(TAG, "バナーの読み込みに失敗: " + error.getMessage());
            }
        });
        view.loadAd(new AdRequest.Builder().build());
        CACHE.put(unitId, entry);
        return entry;
    }

    /** 画面の幅いっぱいのアダプティブバナー（端末ごとにちょうどよい高さを Google が決める） */
    private static AdSize adaptiveSize(Activity activity) {
        int widthDp = activity.getResources().getConfiguration().screenWidthDp;
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp);
    }

    /** Activity が終わるとき、その Activity で作ったバナーを片付ける */
    private static void watchActivity(Activity activity) {
        if (!(activity instanceof LifecycleOwner)) return;
        ((LifecycleOwner) activity).getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                owner.getLifecycle().removeObserver(this);
                Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
                while (it.hasNext()) {
                    Entry entry = it.next().getValue();
                    if (entry.view.getContext() == activity) {
                        entry.view.destroy();
                        it.remove();
                    }
                }
            }
        });
    }

    private static void destroy(String unitId) {
        Entry entry = CACHE.remove(unitId);
        if (entry == null) return;
        ViewGroup parent = (ViewGroup) entry.view.getParent();
        if (parent != null) parent.removeView(entry.view);
        entry.view.destroy();
    }
}
