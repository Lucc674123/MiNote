/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

/**
 * 提醒到点后的入口。
 *
 * 原版直接 startActivity 拉起 {@link AlarmAlertActivity}。从 Android 10（API 29）起，
 * 后台应用启动 Activity 会被系统拦截，所以这里改成"高优先级通知 + 全屏 Intent"：
 * 锁屏/息屏时会直接拉起提醒界面，解锁状态下先弹出横幅通知，点一下同样进入提醒界面。
 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "note_alarm";

    public static final int NOTIFICATION_ID = 0x0A1A;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }

        long noteId;
        try {
            noteId = Long.valueOf(intent.getData().getPathSegments().get(1));
        } catch (Exception e) {
            return;
        }
        // 便签可能已经被删除，这种情况直接忽略，不打扰用户
        if (!DataUtils.visibleInNoteDatabase(context.getContentResolver(), noteId,
                Notes.TYPE_NOTE)) {
            return;
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        Intent alertIntent = new Intent(context, AlarmAlertActivity.class);
        alertIntent.setData(intent.getData());
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent alertPendingIntent = PendingIntent.getActivity(context, 0, alertIntent,
                flags);

        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        ensureChannel(context, notificationManager);

        String snippet = DataUtils.getSnippetById(context.getContentResolver(), noteId);
        if (snippet == null || snippet.trim().length() == 0) {
            snippet = context.getString(R.string.alert_notification_title);
        }
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
            builder.setPriority(Notification.PRIORITY_HIGH);
            builder.setDefaults(Notification.DEFAULT_VIBRATE);
        }
        builder.setSmallIcon(R.drawable.ic_notify_alarm)
                .setContentTitle(context.getString(R.string.alert_notification_title))
                .setContentText(snippet)
                .setStyle(new Notification.BigTextStyle().bigText(snippet))
                .setContentIntent(alertPendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                // 锁屏/息屏时直接拉起提醒界面；无法弹出时退化为横幅通知
                .setFullScreenIntent(alertPendingIntent, true);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * 通知渠道只在 Android 8.0 以上需要。渠道本身静音，铃声统一由
     * {@link AlarmAlertActivity} 循环播放，避免通知和界面同时响。
     */
    private void ensureChannel(Context context, NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.alert_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.alert_notification_channel_desc));
        channel.setSound(null, null);
        channel.enableVibration(true);
        notificationManager.createNotificationChannel(channel);
    }
}
