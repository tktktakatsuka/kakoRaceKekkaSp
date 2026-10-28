package com.tktkcompany.kakoRaceKeiba.ui.home;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.db.SupabaseManager;
import com.tktkcompany.kakoRaceKeiba.share.WinShare;
import com.tktkcompany.kakoRaceKeiba.ui.entries.EntryForm;
import com.tktkcompany.kakoRaceKeiba.ui.favorite.CardStyles;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseFragment;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseViews;
import com.tktkcompany.kakoRaceKeiba.util.Lang;
import com.tktkcompany.kakoRaceKeiba.util.WeekendDays;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ホームの主役のカード。Spotlight が選んだ推し馬1頭を、横並びのカードより大きく出す。
 * 推し馬がまだいなければ、推し馬を見つける案内を出す。
 */
final class SpotlightCard {

    private static final Pattern START = Pattern.compile("(\\d{1,2}):(\\d{2})発走");
    private static final int GREEN = 0xFF2E7D32;
    private static final int ORANGE = 0xFFE65100;
    private static final int RED = 0xFFC62828;
    private static final int GRAY = 0xFF757575;
    private static final int INK = 0xFF1B1B1B;
    /** 読み込み中のカードと、読み込んだあとのカードの高さをそろえる（ガタつかないように） */
    private static final int MIN_HEIGHT_DP = 168;

    private SpotlightCard() {
    }

    static View create(Context context, Spotlight.Pick pick, Runnable onStyleChanged) {
        if (pick.kind == Spotlight.Kind.ONBOARDING) return onboarding(context);
        FavoritesStrip.Item item = pick.item;
        MaterialCardView card = baseCard(context);
        CardStyles.applyBackground(card, item.name, pick.kind == Spotlight.Kind.UPCOMING ? 0xFFFFF8E1 : 0xFFFFFFFF);
        LinearLayout inner = (LinearLayout) card.getChildAt(0);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        View avatar = CardStyles.avatar(context, item.name, 56);
        inner.addView(avatar);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(HorseViews.dp(context, 14), 0, 0, 0);
        inner.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        switch (pick.kind) {
            case UPCOMING:
                body.addView(text(context, Lang.t("▶ 次に走る推し馬", "▶ Next up"), 12, GREEN, true));
                body.addView(name(context, item.name));
                body.addView(text(context, entryLine(item.next), 15, ORANGE, true));
                String course = courseLine(item.next);
                if (!course.isEmpty()) body.addView(text(context, course, 14, ORANGE, false));
                if (item.last != null) body.addView(text(context, resultLine(Lang.t("前走 ", "Last "), item.last), 13, GRAY, false));
                break;
            case FINISHED:
                body.addView(text(context, Lang.t("🏁 今日の結果", "🏁 Today's result"), 12, GREEN, true));
                body.addView(name(context, item.name));
                addFinish(context, body, item.name, pick.result);
                break;
            case RECENT:
                body.addView(text(context, Lang.t("前回の結果", "Last run"), 12, GREEN, true));
                body.addView(name(context, item.name));
                body.addView(text(context, resultLine("", pick.result), 14, INK, false));
                body.addView(text(context, Lang.t("次の出走が決まったらお知らせします", "We'll tell you when the next entry is set"), 12, GRAY, false));
                break;
            default:
                body.addView(text(context, Lang.t("★ 推し馬", "★ Your horse"), 12, GREEN, true));
                body.addView(name(context, item.name));
                body.addView(text(context, Lang.t("出走予定を待っています", "Waiting for an entry"), 13, GRAY, false));
                break;
        }
        card.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.navigation_horse, HorseFragment.args(item.name)));
        // 長押しで、アイコンと背景を選ぶ（横並びのカード・推し馬一覧と同じ）
        card.setOnLongClickListener(v -> {
            CardStyles.showEditor(context, item.name, item.name, onStyleChanged);
            return true;
        });
        return card;
    }

    /** 読み込み中。読み込んだあとのカードと同じくらいの高さで場所を取っておく */
    static View loading(Context context) {
        MaterialCardView card = baseCard(context);
        card.setCardBackgroundColor(0xFFFFFFFF);
        LinearLayout inner = (LinearLayout) card.getChildAt(0);
        inner.setGravity(Gravity.CENTER);
        inner.addView(text(context, Lang.t("推し馬の予定を読み込み中…", "Loading your horses…"), 13, GRAY, false));
        return card;
    }

    /** 着順を大きく。1着なら赤で「1着！」と、勝利のシェア */
    private static void addFinish(Context context, LinearLayout body, String horseName, @Nullable SupabaseManager.RaceResult r) {
        if (r == null) return;
        boolean win = WinShare.isWin(r.getRank());
        String rank = Lang.rank(r.getRank() != null ? r.getRank() : "-");
        body.addView(text(context, win ? Lang.t(rank + "！", "Won!") : rank, 26, win ? RED : INK, true));
        StringBuilder line = new StringBuilder(WeekendDays.formatCompact(r.getKaisaibi()))
                .append(' ').append(Lang.race(r.getKaisaijo(), r.getRaceNo()));
        String title = plainTitle(r.getRaceTitle());
        if (!title.isEmpty()) line.append(' ').append(title);
        if (r.getPopular() != null && !r.getPopular().isEmpty()) {
            line.append(Lang.t("（" + r.getPopular() + "人気）", " (" + Lang.popularity(r.getPopular()) + ")"));
        }
        body.addView(text(context, line.toString(), 13, GRAY, false));
        if (win) {
            TextView share = text(context, Lang.t("🎉 勝利をシェア", "🎉 Share the win"), 13, RED, true);
            share.setBackgroundResource(R.drawable.bg_share_win);
            int ph = HorseViews.dp(context, 12), pv = HorseViews.dp(context, 6);
            share.setPadding(ph, pv, ph, pv);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = HorseViews.dp(context, 8);
            share.setLayoutParams(params);
            share.setOnClickListener(v -> WinShare.open(v.getContext(),
                    WinShare.text(horseName, r.getKaisaibi(), r.getKaisaijo(), r.getRaceNo(), r.getRaceTitle())));
            body.addView(share);
        }
    }

    /** 推し馬がまだいないとき。今週の重賞に出る馬から選べる、馬を探す画面へ */
    private static View onboarding(Context context) {
        MaterialCardView card = baseCard(context);
        card.setCardBackgroundColor(0xFFE8F5E9);
        LinearLayout inner = (LinearLayout) card.getChildAt(0);
        inner.addView(text(context, Lang.t("推し馬を見つけよう", "Find a horse to follow"), 18, GREEN, true));
        TextView body = text(context, Lang.t("推し馬（応援したいお気に入りの馬）を登録すると、出走が決まったとき・発走30分前・レース結果をお知らせします",
                "Follow a horse to get notified when it's entered, 30 minutes before it runs, and when results are in"), 13, 0xFF333333, false);
        body.setPadding(0, HorseViews.dp(context, 6), 0, HorseViews.dp(context, 10));
        inner.addView(body);
        MaterialButton button = new MaterialButton(context);
        button.setText(Lang.t("今週の重賞に出る馬から選ぶ", "Pick from this week's graded races"));
        // テーマの文字色だと緑の上で読みにくいので、白にそろえる
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
        button.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.navigation_horseSearch));
        inner.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    /** 「▶ 11/1(日) 東京8R 15:00」 */
    private static String entryLine(SupabaseManager.RaceEntry e) {
        StringBuilder sb = new StringBuilder("▶ ").append(WeekendDays.formatCompact(e.getKaisaibi()))
                .append(' ').append(Lang.race(e.getKaisaijo(), e.getRaceNo()));
        Matcher m = START.matcher(e.getRaceInfo() != null ? e.getRaceInfo() : "");
        if (m.find()) sb.append(' ').append(Integer.parseInt(m.group(1))).append(':').append(m.group(2));
        return sb.toString();
    }

    /** 「本栖湖特別（芝2400m）」 */
    private static String courseLine(SupabaseManager.RaceEntry e) {
        String title = plainTitle(e.getRaceTitle());
        String course = EntryForm.courseText(EntryForm.courseLabel(e.getRaceInfo()));
        if (!course.isEmpty()) title += Lang.t("（" + course + "m）", " (" + course + "m)");
        return title.trim();
    }

    /** 「前走 10/11(日) 東京8R 1着（2人気）」。1着は赤、2・3着は緑 */
    private static CharSequence resultLine(String prefix, @Nullable SupabaseManager.RaceResult r) {
        SpannableStringBuilder sb = new SpannableStringBuilder(prefix);
        if (r == null) return sb;
        sb.append(WeekendDays.formatCompact(r.getKaisaibi())).append(' ')
                .append(Lang.race(r.getKaisaijo(), r.getRaceNo())).append(' ');
        String rank = r.getRank() != null ? r.getRank() : "-";
        int from = sb.length();
        sb.append(Lang.rank(rank));
        if (rank.matches("[123]")) {
            sb.setSpan(new ForegroundColorSpan("1".equals(rank) ? RED : GREEN), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (r.getPopular() != null && !r.getPopular().isEmpty()) {
            sb.append(Lang.t("（" + r.getPopular() + "人気）", " (" + Lang.popularity(r.getPopular()) + ")"));
        }
        return sb;
    }

    private static String plainTitle(@Nullable String title) {
        return title != null ? title.trim().replaceFirst("^サラ系", "").trim() : "";
    }

    private static TextView name(Context context, String horseName) {
        TextView name = text(context, horseName, 22, INK, true);
        // 長い馬名でも2行までにして、カードの高さが暴れないようにする
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, HorseViews.dp(context, 2), 0, HorseViews.dp(context, 4));
        return name;
    }

    private static MaterialCardView baseCard(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = HorseViews.dp(context, 8);
        params.setMargins(m, m, m, m);
        card.setLayoutParams(params);
        card.setRadius(HorseViews.dp(context, 18));
        card.setCardElevation(HorseViews.dp(context, 3));
        card.setStrokeWidth(0);
        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        int pad = HorseViews.dp(context, 16);
        inner.setPadding(pad, pad, pad, pad);
        inner.setMinimumHeight(HorseViews.dp(context, MIN_HEIGHT_DP));
        card.addView(inner, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static TextView text(Context context, CharSequence s, float sp, int color, boolean bold) {
        TextView tv = new TextView(context);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }
}
