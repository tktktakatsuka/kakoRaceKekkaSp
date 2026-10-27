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
import androidx.navigation.NavController;
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
import com.tktkcompany.kakoRaceKeiba.ui.horsememo.HorseMemos;
import com.tktkcompany.kakoRaceKeiba.dto.SharedViewModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;
import android.widget.Toast;

import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;


public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private SharedViewModel sharedViewModel;
    private FirebaseAuth mAuth; // mAuthをメンバー変数として追加
    private boolean exeSetRaceResult;
    // 「今日の開催」カード（開催日だけ表示）
    private TodayCard todayCard;
    // 「今週の開催」に出馬表の開催日・競馬場を出したか（出していれば開催中の競馬場の一覧で上書きしない）
    private boolean entriesSummaryShown;
    // yyyyMMdd形式用
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        View root = binding.getRoot();


        // バナー（画面の一番上に固定）
        BannerAd.show(this, binding.adContainer, AdUnits.BANNER_HOME);

        // お気に入り一覧ボタンの設定
        binding.btnFavoriteHorses.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(v);
            navController.navigate(R.id.action_navigation_home_to_navigation_favorites);
        });

        binding.btnPredictions.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.navigation_predictions));
        binding.btnOnboardingFind.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.navigation_horseSearch));
        binding.cardEntries.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_navigation_home_to_navigation_entries));
        // 下の「お気に入り」タブに切り替える（タブの選択も合わせるため、画面遷移ではなくタブを選ぶ）
        binding.textMyHorsesAll.setOnClickListener(v -> {
            com.google.android.material.bottomnavigation.BottomNavigationView nav = requireActivity().findViewById(R.id.nav_view);
            if (nav != null) nav.setSelectedItemId(R.id.navigation_favorites);
        });
        todayCard = new TodayCard(this, binding.cardToday, binding.todayContainer);
        loadUpcomingEntries();

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
            updateUpcomingCard(joNames);
            if (joNames == null || joNames.isEmpty()) return;

            loadMainRaces(joNames);
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (todayCard != null) todayCard.stop();
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

        updateUpcomingHint(user);

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
        // お気に入りは下のタブと「あなたのお気に入り馬」から、予想はメモ・予想のタブから開くので、ボタンは出さない
        binding.layoutQuickButtons.setVisibility(View.GONE);
        placeAuthCard(user != null);
        refreshFavorites(user);
    }

    /**
     * お気に入りの馬がいれば一番上に「あなたのお気に入り馬」を並べ、
     * 1頭もいなければ「好きな馬を登録しよう」のカードを出す
     */
    private void refreshFavorites(@androidx.annotation.Nullable FirebaseUser user) {
        if (user == null) {
            binding.cardOnboarding.setVisibility(View.GONE);
            binding.layoutMyHorses.setVisibility(View.GONE);
            return;
        }
        FavoriteHorseNames.load(favorites -> {
            if (binding == null) return;
            binding.cardOnboarding.setVisibility(favorites.isEmpty() ? View.VISIBLE : View.GONE);
            binding.layoutMyHorses.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
            if (!favorites.isEmpty()) {
                if (favoritesStrip == null) favoritesStrip = new FavoritesStrip(binding.myHorsesContainer);
                favoritesStrip.show(favorites);
            }
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


    // 「今週の開催」カードに開催中の競馬場と次の開催日を表示する
    private void updateUpcomingCard(List<String> joNames) {
        if (binding == null) return;
        List<LocalDate> days = WeekendDays.getUpcomingRaceDays();
        if (joNames == null || joNames.isEmpty() || days.isEmpty()) {
            binding.cardUpcoming.setVisibility(View.GONE);
            return;
        }
        List<String> dayLabels = new ArrayList<>();
        for (LocalDate d : days) {
            dayLabels.add(WeekendDays.formatShort(d.format(formatter)));
        }
        // 出馬表が読めたら、そちらの開催日・競馬場で書き直す（新潟などの開催も入るため）
        if (!entriesSummaryShown) {
            binding.textUpcomingBody.setText(TextUtils.join("・", dayLabels) + "　" + TextUtils.join("・", courseNames(joNames)));
        }
        binding.cardUpcoming.setVisibility(View.VISIBLE);
        updateUpcomingHint(mAuth != null ? mAuth.getCurrentUser() : null);
    }

    // 出馬表が登録されていれば「今週の出走馬」カードを出す
    private void loadUpcomingEntries() {
        String today = LocalDate.now().format(formatter);
        SupabaseManager.fetchUpcomingEntries(today, new SupabaseManager.SupabaseCallback<List<SupabaseManager.RaceEntry>>() {
            @Override
            public void onSuccess(List<SupabaseManager.RaceEntry> entries) {
                if (!isAdded() || binding == null) return;
                binding.progressHome.setVisibility(View.GONE);
                if (entries.isEmpty()) {
                    // 出馬表がまだ無い週は開催日ではないので、前の開催の結果を出す
                    binding.layoutLastResults.setVisibility(View.VISIBLE);
                    return;
                }
                Set<String> days = new LinkedHashSet<>();
                Set<String> courses = new LinkedHashSet<>();
                Set<String> races = new LinkedHashSet<>();
                for (SupabaseManager.RaceEntry e : entries) {
                    days.add(WeekendDays.formatShort(e.getKaisaibi()));
                    courses.add(e.getKaisaijo());
                    races.add(e.getKaisaibi() + e.getKaisaijo() + e.getRaceNo());
                }
                binding.textUpcomingBody.setText(TextUtils.join("・", days) + "　" + TextUtils.join("・", courseNames(courses)));
                entriesSummaryShown = true;
                String body = Lang.t(races.size() + "レースの出走馬と近走成績", races.size() + " races: runners and recent form");
                binding.textEntriesBody.setText(body);
                binding.cardEntries.setVisibility(View.VISIBLE);

                // お気に入り馬・メモした馬が出走予定なら、その馬名も出す
                FavoriteHorseNames.load(favorites -> HorseMemos.load(memos -> {
                    if (!isAdded() || binding == null) return;
                    Set<String> favoriteRunners = new LinkedHashSet<>();
                    Set<String> memoRunners = new LinkedHashSet<>();
                    for (SupabaseManager.RaceEntry e : entries) {
                        if (FavoriteHorseNames.contains(favorites, e.getHorseName())) favoriteRunners.add(e.getHorseName());
                        if (HorseMemos.find(memos, e.getHorseName()) != null) memoRunners.add(e.getHorseName());
                    }
                    // 馬名を全部並べると長くなるので、頭数と最初の2頭だけ出す
                    StringBuilder text = new StringBuilder(body);
                    if (!favoriteRunners.isEmpty()) text.append(Lang.t("\n★ 推し馬 ", "\n★ Favorites: ")).append(runnersLabel(favoriteRunners));
                    if (!memoRunners.isEmpty()) text.append(Lang.t("\n📝 メモした馬 ", "\n📝 Noted: ")).append(runnersLabel(memoRunners));
                    binding.textEntriesBody.setText(text);
                    todayCard.show(entries, favorites);
                    // 開催日は今日の開催カードで足りるので、前の開催のメインレース結果は出さない
                    // （最初は隠しておき、開催日でないと分かってから出す。先に出すと開催日に一瞬見えるため）
                    binding.layoutLastResults.setVisibility(
                            binding.cardToday.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }));
            }

            @Override
            public void onFailure(@NonNull String message) {
                Log.w("HomeFragment", "Entries load failed: " + message);
                if (isAdded() && binding != null) {
                    binding.progressHome.setVisibility(View.GONE);
                    binding.layoutLastResults.setVisibility(View.VISIBLE);
                }
            }
        });
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

    /** 競馬場名の一覧を表示の言語にする */
    private static List<String> courseNames(java.util.Collection<String> courses) {
        List<String> names = new ArrayList<>();
        for (String c : courses) names.add(Lang.course(c));
        return names;
    }

    /** 1頭なら馬名、何頭もいれば「7頭（バルチックアンバー ほか）」 */
    static String runnersLabel(Set<String> names) {
        List<String> list = new ArrayList<>(names);
        if (list.size() == 1) return list.get(0);
        return Lang.t(list.size() + "頭（" + list.get(0) + " ほか）", list.size() + " (" + list.get(0) + " and more)");
    }

    private void updateUpcomingHint(FirebaseUser user) {
        if (binding == null) return;
        binding.textUpcomingHint.setText(Lang.t("🔔 通知の設定 ›", "🔔 Notification settings ›"));
        binding.textUpcomingHint.setOnClickListener(v -> showNotificationSettings(user != null));
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

