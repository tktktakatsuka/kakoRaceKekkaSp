package com.tktkcompany.kakoRaceKeiba.ui.profile;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import androidx.core.content.FileProvider;

import com.tktkcompany.kakoRaceKeiba.share.WinShare;
import com.tktkcompany.kakoRaceKeiba.util.Lang;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * プロフ帳を1枚の画像にして、共有する・端末の写真に保存する。
 * 画面の外で幅 360dp に並べて描き、横 1080px にそろえる。
 */
final class ProfileImage {

    private static final String TAG = "ProfileImage";
    private static final int WIDTH_PX = 1080;

    private ProfileImage() {
    }

    static Bitmap render(Context c, View sheet) {
        float density = c.getResources().getDisplayMetrics().density;
        int width = Math.round(360 * density);
        sheet.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        sheet.layout(0, 0, sheet.getMeasuredWidth(), sheet.getMeasuredHeight());
        float scale = WIDTH_PX / (float) width;
        Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, Math.round(sheet.getMeasuredHeight() * scale), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale);
        sheet.draw(canvas);
        return bitmap;
    }

    /** X・LINE などに共有する。失敗したら false */
    static boolean share(Context c, Bitmap bitmap) {
        try {
            File dir = new File(c.getCacheDir(), "images");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File file = new File(dir, "profile.png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".provider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_TEXT, Lang.t("推し馬ノートでプロフ帳をつくったよ #推し馬ノート\n", "I made my profile with Oshiuma Note #推し馬ノート\n") + WinShare.STORE_URL);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, Lang.t("プロフ帳をシェア", "Share your profile"));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(chooser);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "share failed", e);
            return false;
        }
    }

    /** 端末の写真（Pictures/推し馬ノート）に保存できるのは Android 10 以上（9 では保存の権限が要るため出さない） */
    static boolean canSave() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    static boolean save(Context c, Bitmap bitmap) {
        if (!canSave()) return false;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "oshiuma_profile_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/推し馬ノート");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;
        try (OutputStream out = c.getContentResolver().openOutputStream(uri)) {
            if (out == null) return false;
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            Log.w(TAG, "save failed", e);
            c.getContentResolver().delete(uri, null, null);
            return false;
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        c.getContentResolver().update(uri, values, null, null);
        return true;
    }
}
