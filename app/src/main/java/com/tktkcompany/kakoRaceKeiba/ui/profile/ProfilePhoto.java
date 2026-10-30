package com.tktkcompany.kakoRaceKeiba.ui.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;

/**
 * プロフ帳のアイコンの写真。端末の中（アプリの内部の保存場所）だけに置き、Firebase には上げない。
 * 機種変更では引き継がれない。
 */
public final class ProfilePhoto {

    private static final String TAG = "ProfilePhoto";
    private static final String FILE = "profile_photo.jpg";
    private static final int SIZE = 512;

    private ProfilePhoto() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    @Nullable
    public static Bitmap load(Context c) {
        File f = file(c);
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    /** 選んだ写真の真ん中を正方形に切り抜き、512px にして保存する。失敗したら false */
    public static boolean save(Context c, Uri uri) {
        try {
            Bitmap src;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(), uri),
                        (decoder, info, s) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
            } else {
                src = MediaStore.Images.Media.getBitmap(c.getContentResolver(), uri);
            }
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            Bitmap small = Bitmap.createScaledBitmap(square, SIZE, SIZE, true);
            try (FileOutputStream out = new FileOutputStream(file(c))) {
                small.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "save failed", e);
            return false;
        }
    }

    public static void delete(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }
}
