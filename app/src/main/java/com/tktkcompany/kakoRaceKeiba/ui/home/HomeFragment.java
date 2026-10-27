package com.tktkcompany.kakoRaceKeiba.ui.home;

import com.tktkcompany.kakoRaceKeiba.ads.AdUnits;
import com.tktkcompany.kakoRaceKeiba.ads.BannerAd;
import com.tktkcompany.kakoRaceKeiba.auth.GuestAccount;
import com.tktkcompany.kakoRaceKeiba.util.Lang;


import android.os.Bundle;

import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tktkcompany.kakoRaceKeiba.MainActivity;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.databinding.FragmentHomeBinding;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.notification.FavoriteResultWorker;
import com.tktkcompany.kakoRaceKeiba.notification.GradedRaceWorker;
import com.tktkcompany.kakoRaceKeiba.ui.entries.FavoriteHorseNames;
import com.tktkcompany.kakoRaceKeiba.dto.SharedViewModel;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;
import android.widget.Toast;

import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;



public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private SharedViewModel sharedViewModel;
    private FirebaseAuth mAuth; // mAuthをメンバー変数として追加
    private boolean exeSetRaceResult;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        View root = binding.getRoot();


        // バナー（画面の一番上に固定）
        BannerAd.show(this, binding.adContainer, AdUnits.BANNER_HOME);

        // 下の「推し馬」タブに切り替える（タブの選択も合わせるため、画面遷移ではなくタブを選ぶ）
        binding.textMyHorsesAll.setOnClickListener(v -> {
            com.google.android.material.bottomnavigation.BottomNavigationView nav = requireActivity().findViewById(R.id.nav_view);
            if (nav != null) nav.setSelectedItemId(R.id.navigation_favorites);
        });

        // ログイン・ログアウトボタンの設定
        binding.btnLogin.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).signInWithGoogle();
            }
        });

        binding.btnLogout.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).signOut();
            }
        });

        // Supabaseから開催中の競馬場リストを取得
        SupabaseManager.fetchActiveCourses(new SupabaseManager.SupabaseCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> joNames) {
                if (!isAdded()) return;
                sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
                sharedViewModel.setJoNames(joNames);
            }

            @Override
            public void onFailure(@NonNull String message) {
                Toast.makeText(getContext(), Lang.t("データの読み込みに失敗しました", "Couldn't load data"), Toast.LENGTH_SHORT).show();
            }
        });


        // 🔑 setJoNames 完了後にここが呼ばれる
        sharedViewModel.getJoNames().observe(getViewLifecycleOwner(), joNames -> {
            if (joNames == null || joNames.isEmpty()) {
                binding.progressHome.setVisibility(View.GONE);
                return;
            }
            loadMainRaces(joNames);
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 古い画面の部品を持ち続けないよう、次に画面を作ったときに作り直す
        favoritesStrip = null;
    }


    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // ★★★ Firebase Authを初期化 ★★★
        mAuth = FirebaseAuth.getInstance();

    }

    @Override
    public void onStart() {
        super.onStart();
        // ★★★ ここからが重要な修正 ★★★
        // Fragmentが表示されるたびに、現在のログイン状態をチェックしてUIを更新する
        FirebaseUser currentUser = mAuth.getCurrentUser();
        updateUI(currentUser);
        // ★★★ ここまで ★★★
    }

    // ★★★ 自分の画面のUI更新は自分で行う ★★★
// (MainActivityから呼び出されるようにpublicにする)
    public void updateUI(FirebaseUser user) {
        if (binding == null) return; // 画面が破棄された後は何もしない
        // ログインに失敗したときなどは null で呼ばれるので、今のアカウント（匿名かもしれない）で表示する
        if (user == null) user = FirebaseAuth.getInstance().getCurrentUser();

        bindNotificationSettings(user);

        if (GuestAccount.isGoogle(user)) {
            // Google でログイン中
            binding.layoutUserInfo.setVisibility(View.VISIBLE);
            String name = GuestAccount.displayName(user);
            binding.textUserName.setText(Lang.t(name + " さんとしてログイン中", "Signed in as " + name));
            binding.layoutLogin.setVisibility(View.GONE);
        } else {
            // ログインしていない（匿名で使っている）。お気に入りなどは使えるので、引き継ぎのためのログインを勧める
            binding.layoutUserInfo.setVisibility(View.GONE);
            binding.layoutLogin.setVisibility(View.VISIBLE);
        }
        placeAuthCard(user != null);
        refreshFavorites(user);
    }

    /**
     * お気に入りの馬がいれば一番上に「あなたのお気に入り馬」を並べ、
     * 1頭もいなければ「好きな馬を登録しよう」のカードを出す
     */
    private void refreshFavorites(@androidx.annotation.Nullable FirebaseUser user) {
        if (user == null) {
            binding.spotlightContainer.setVisibility(View.GONE);
            binding.layoutMyHorses.setVisibility(View.GONE);
            return;
        }
        binding.spotlightContainer.setVisibility(View.VISIBLE);
        FavoriteHorseNames.load(favorites -> {
            if (binding == null) return;
            if (favoritesStrip == null) {
                favoritesStrip = new FavoritesStrip(binding.spotlightContainer, binding.layoutMyHorses, binding.myHorsesContainer);
            }
            // 空でも呼ぶ（主役のカードに推し馬を見つける案内を出し、横並びの欄を隠す）
            favoritesStrip.show(favorites);
        });
    }

    private FavoritesStrip favoritesStrip;

    /**
     * お気に入りなどが使えるとき（Google か匿名で入っている）は、ログインのカードを一番下に回し、
     * 今日の開催を一番上に出す。どちらでも入れていないときは、ログインを促すため一番上に置く
     */
    private void placeAuthCard(boolean signedIn) {
        ViewGroup parent = (ViewGroup) binding.cardUserAuth.getParent();
        if (parent == null) return;
        parent.removeView(binding.cardUserAuth);
        if (signedIn) {
            parent.addView(binding.cardUserAuth);
        } else {
            parent.addView(binding.cardUserAuth, 0);
        }
    }


    /** 開催していた競馬場すべてのメインレース(11R)の結果を集め、G1 から順にカードで出す */
    private void loadMainRaces(List<String> joNames) {
        List<List<SupabaseManager.RaceResult>> collected = new ArrayList<>();
        for (int i = 0; i < joNames.size(); i++) collected.add(new ArrayList<>());
        int[] remaining = {joNames.size()};
        for (int i = 0; i < joNames.size(); i++) {
            final int index = i;
            loadMainRace(joNames.get(i), results -> {
                collected.set(index, results);
                if (--remaining[0] == 0 && isAdded() && binding != null) showMainRaces(collected);
            });
        }
    }

    private interface MainRaceListener {
        void onLoaded(List<SupabaseManager.RaceResult> results);
    }

    /** 1場分の、いちばん新しいメインレースの結果（開催日は PC のバッチが発走の数分後に入れる） */
    private void loadMainRace(String joName, MainRaceListener listener) {
        SupabaseManager.fetchLatestMainRace(joName, new SupabaseManager.SupabaseCallback<List<SupabaseManager.RaceResult>>() {
            @Override
            public void onSuccess(List<SupabaseManager.RaceResult> results) {
                listener.onLoaded(results);
            }

            @Override
            public void onFailure(@NonNull String message) {
                Log.e("HomeFragment", "Supabase load failed: " + message);
                listener.onLoaded(new ArrayList<>());
            }
        });
    }

    private void showMainRaces(List<List<SupabaseManager.RaceResult>> collected) {
        binding.progressHome.setVisibility(View.GONE);
        LinearLayout container = binding.lastResultsContainer;
        container.removeAllViews();
        List<List<SupabaseManager.RaceResult>> races = MainRaces.sort(collected);
        if (races.isEmpty()) {
            binding.textLastResultsHeader.setVisibility(View.GONE);
            return;
        }
        binding.textLastResultsHeader.setVisibility(View.VISIBLE);
        binding.textLastResultsHeader.setText(Lang.t(
                WeekendDays.formatShort(races.get(0).get(0).getKaisaibi()) + "のメインレース",
                "Main races · " + WeekendDays.formatShort(races.get(0).get(0).getKaisaibi())));
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (List<SupabaseManager.RaceResult> race : races) {
            container.addView(MainRaceCard.create(inflater, container, race));
        }
    }

    /** 「ほかの推し馬」の見出しの右の「🔔 通知の設定 ›」 */
    private void bindNotificationSettings(FirebaseUser user) {
        if (binding == null) return;
        binding.textNotificationSettings.setOnClickListener(v -> showNotificationSettings(user != null));
    }

    /** 通知の説明と設定（今は重賞の発走前の通知のオン・オフだけ） */
    private void showNotificationSettings(boolean signedIn) {
        boolean[] checked = {GradedRaceWorker.isEnabled(requireContext())};
        float density = getResources().getDisplayMetrics().density;
        TextView title = new TextView(requireContext());
        int pad = (int) (20 * density);
        title.setPadding(pad, pad, pad, (int) (8 * density));
        SpannableStringBuilder text = new SpannableStringBuilder(Lang.t("通知の設定", "Notification settings"));
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(1.3f), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append("\n\n").append(signedIn
                ? Lang.t("推し馬・メモした馬の出走予定、推し馬の発走30分前と結果（確定後すぐ）をお知らせします。", "We'll notify you when your favorite or noted horses are entered, 30 minutes before your favorites run, and as soon as their results are official.")
                : Lang.t("ログインして推し馬を登録すると、出走予定・発走前・結果もお知らせします。", "Sign in and add favorites to get entry, pre-race and result notifications."));
        title.setText(text);
        title.setTextColor(0xFF333333);
        new AlertDialog.Builder(requireContext())
                .setCustomTitle(title)
                .setMultiChoiceItems(new String[]{Lang.t("重賞の発走30分前に上位人気とオッズを知らせる", "Notify me 30 minutes before graded races with the favorites and odds")}, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(Lang.t("保存", "Save"), (dialog, which) -> {
                    GradedRaceWorker.setEnabled(requireContext(), checked[0]);
                    // オンにしたら、次の定期チェックを待たずに予約する
                    if (checked[0]) FavoriteResultWorker.runOnce(requireContext());
                })
                .setNegativeButton(Lang.t("キャンセル", "Cancel"), null)
                .show();
    }




}

