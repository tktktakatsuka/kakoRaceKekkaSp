package com.tktkcompany.kakoRaceKeiba.ui.welcome;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseViews;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

/**
 * 初めて開いたときだけ出す、楽しさを伝える3枚。推し馬の登録はここではしない。
 * 閉じたら（はじめる・スキップ・戻る）MainActivity に戻り、そこで通知の許可を聞く。
 */
public class WelcomeActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private MaterialButton next;
    private TextView skip;
    private LinearLayout dots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        View root = findViewById(R.id.welcome_root);
        int pad = HorseViews.dp(this, 24);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(pad, bars.top + HorseViews.dp(this, 12), pad, bars.bottom + HorseViews.dp(this, 20));
            return insets;
        });

        pager = findViewById(R.id.welcome_pager);
        next = findViewById(R.id.welcome_next);
        skip = findViewById(R.id.welcome_skip);
        dots = findViewById(R.id.welcome_dots);
        pager.setAdapter(new WelcomePagerAdapter());
        skip.setText(Lang.t("スキップ", "Skip"));
        skip.setOnClickListener(v -> finishWelcome());
        next.setOnClickListener(v -> {
            int page = pager.getCurrentItem();
            if (page < WelcomePagerAdapter.PAGES - 1) pager.setCurrentItem(page + 1);
            else finishWelcome();
        });
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showPage(position);
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWelcome();
            }
        });
        showPage(0);
    }

    private void showPage(int position) {
        boolean last = position == WelcomePagerAdapter.PAGES - 1;
        next.setText(last ? Lang.t("はじめる", "Get started") : Lang.t("つぎへ", "Next"));
        // 最後の1枚はスキップを出さない（場所は残して、ボタンが動かないようにする）
        skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        dots.removeAllViews();
        for (int i = 0; i < WelcomePagerAdapter.PAGES; i++) {
            View dot = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(HorseViews.dp(this, 4));
            bg.setColor(i == position ? 0xFF4E7A27 : 0xFFC5E1A5);
            dot.setBackground(bg);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(HorseViews.dp(this, i == position ? 20 : 8), HorseViews.dp(this, 8));
            p.setMargins(HorseViews.dp(this, 4), 0, HorseViews.dp(this, 4), 0);
            dots.addView(dot, p);
        }
    }

    private void finishWelcome() {
        Welcome.markDone(this);
        setResult(RESULT_OK);
        finish();
    }
}
