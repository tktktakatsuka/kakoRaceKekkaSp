package com.tktkcompany.kakoRaceKeiba.notification;

import com.tktkcompany.kakoRaceKeiba.util.Lang;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.tktkcompany.kakoRaceKeiba.MainActivity;
import com.tktkcompany.kakoRaceKeiba.R;

/**
 * 通知チャンネルの作成と通知の表示をまとめたクラス。
 * チャンネルIDはここで一元管理する（AndroidManifestのFCM既定チャンネルとも一致させる）。
 */
public class NotificationHelper {

    public static final String DEFAULT_CHANNEL_ID = "default_channel";
    public static final String FAVORITE_CHANNEL_ID = "favorite_horse_channel";
    public static final String ENTRY_CHANNEL_ID = "horse_entry_channel";
    public static final String RACE_START_CHANNEL_ID = "race_start_channel";
    public static final String LIVE_RESULT_CHANNEL_ID = "live_result_channel";
    public static final String GRADED_RACE_CHANNEL_ID = "graded_race_channel";

    public static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel defaultChannel = new NotificationChannel(
                DEFAULT_CHANNEL_ID, Lang.t("お知らせ", "Announcements"), NotificationManager.IMPORTANCE_DEFAULT);
        defaultChannel.setDescription(Lang.t("アプリからのお知らせ", "News from the app"));
        manager.createNotificationChannel(defaultChannel);

        NotificationChannel favoriteChannel = new NotificationChannel(
                FAVORITE_CHANNEL_ID, Lang.t("推し馬の結果", "Favorite horses' results"), NotificationManager.IMPORTANCE_DEFAULT);
        favoriteChannel.setDescription(Lang.t("応援している推し馬のレース結果", "Race results of your favorite horses"));
        manager.createNotificationChannel(favoriteChannel);

        NotificationChannel entryChannel = new NotificationChannel(
                ENTRY_CHANNEL_ID, Lang.t("推し馬・メモした馬の出走予定", "Entries of your favorite and noted horses"), NotificationManager.IMPORTANCE_DEFAULT);
        entryChannel.setDescription(Lang.t("出馬表に載ったときにお知らせします", "When they appear on a race card"));
        manager.createNotificationChannel(entryChannel);

        NotificationChannel liveResultChannel = new NotificationChannel(
                LIVE_RESULT_CHANNEL_ID, Lang.t("推し馬の結果（速報）", "Favorite horses' results (live)"), NotificationManager.IMPORTANCE_HIGH);
        liveResultChannel.setDescription(Lang.t("推し馬のレースの結果が確定したらすぐにお知らせします", "As soon as their race result is official"));
        manager.createNotificationChannel(liveResultChannel);

        NotificationChannel gradedChannel = new NotificationChannel(
                GRADED_RACE_CHANNEL_ID, Lang.t("重賞の発走前", "Before graded races"), NotificationManager.IMPORTANCE_DEFAULT);
        gradedChannel.setDescription(Lang.t("重賞レースの発走30分前にお知らせします", "30 minutes before graded races"));
        manager.createNotificationChannel(gradedChannel);

        // 発走直前に気づけるよう、ほかの通知より目立たせる
        NotificationChannel raceStartChannel = new NotificationChannel(
                RACE_START_CHANNEL_ID, Lang.t("推し馬の出走前", "Before favorites run"), NotificationManager.IMPORTANCE_HIGH);
        raceStartChannel.setDescription(Lang.t("推し馬が出るレースの発走30分前にお知らせします", "30 minutes before your favorites run"));
        manager.createNotificationChannel(raceStartChannel);
    }

    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * 通知を表示する。notificationIdを変えれば複数の通知が上書きされずに並ぶ。
     */
    public static void show(Context context, String channelId, int notificationId, String title, String message) {
        show(context, channelId, notificationId, title, message, null);
    }

    /**
     * タップしたときに開く画面を extras で指定できる版（{@link MainActivity#EXTRA_OPEN_RACE_DATE} など）。
     */
    public static void show(Context context, String channelId, int notificationId, String title, String message,
                            @Nullable Bundle extras) {
        if (!canPostNotifications(context)) return;
        createChannels(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (extras != null) intent.putExtras(extras);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.drawable.uma)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // 通知権限が直前に取り消された場合
        }
    }
}
