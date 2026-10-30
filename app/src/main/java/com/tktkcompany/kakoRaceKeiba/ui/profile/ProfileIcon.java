package com.tktkcompany.kakoRaceKeiba.ui.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.tktkcompany.kakoRaceKeiba.R;
import com.tktkcompany.kakoRaceKeiba.ui.horse.HorseViews;

/**
 * ホームの上の帯の右上に出す、自分のアイコン（プロフ帳と同じもの）。
 * 写真を選んでいれば丸い写真、絵文字を選んでいればその絵文字、どちらもなければ人の形（ic_profile）。
 */
public final class ProfileIcon {

    private static final int SIZE_DP = 32;

    private ProfileIcon() {
    }

    /** null なら、決まったアイコン（ic_profile）のままにする */
    @Nullable
    public static Drawable of(Context c, Profile p) {
        int size = HorseViews.dp(c, SIZE_DP);
        if (p.usePhoto) {
            Bitmap photo = ProfilePhoto.load(c);
            if (photo != null) {
                RoundedBitmapDrawable round = RoundedBitmapDrawableFactory.create(c.getResources(),
                        Bitmap.createScaledBitmap(photo, size, size, true));
                round.setCircular(true);
                return round;
            }
        }
        if (p.emoji == null) return null;
        // 淡い黄緑の丸に絵文字
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        circle.setColor(ContextCompat.getColor(c, R.color.oshi_nav_active));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circle);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(size * 0.6f);
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics m = text.getFontMetrics();
        canvas.drawText(p.emoji, size / 2f, size / 2f - (m.ascent + m.descent) / 2f, text);
        return new BitmapDrawable(c.getResources(), bitmap);
    }
}
