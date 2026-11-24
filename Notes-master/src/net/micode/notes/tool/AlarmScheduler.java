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

package net.micode.notes.tool;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.ui.AlarmReceiver;

/**
 * 便签提醒的统一调度入口。
 *
 * 原版在 NoteEditActivity 与 AlarmInitReceiver 里各写了一遍
 * {@code PendingIntent.getBroadcast(context, 0, intent, 0)}。从 Android 12（API 31）起，
 * 这样创建 PendingIntent 会直接抛 IllegalArgumentException（必须显式指定
 * FLAG_IMMUTABLE 或 FLAG_MUTABLE），结果是"提醒根本设不上"。把两处收敛到这里，
 * 同时顺带解决闹钟精度与权限降级问题。
 */
public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";

    private AlarmScheduler() {
    }

    /**
     * 便签提醒的 PendingIntent。requestCode 固定为 0，靠 data 里的便签 id 区分不同
     * 闹钟——这是原版就有的约定，修改它会让已经设好的提醒失效。
     */
    public static PendingIntent buildPendingIntent(Context context, long noteId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    public static void cancel(Context context, long noteId) {
        AlarmManager alarmManager = (AlarmManager) context
                .getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(buildPendingIntent(context, noteId));
    }

    /**
     * 设置提醒。优先使用精确闹钟；没有精确闹钟权限（Android 12 起是"特殊应用权限"，
     * 用户可以随时撤销）时退化为普通闹钟，保证提醒仍然会响，只是可能延后几分钟。
     */
    public static void schedule(Context context, long noteId, long time) {
        AlarmManager alarmManager = (AlarmManager) context
                .getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent = buildPendingIntent(context, noteId);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // API 19 起 set() 不再精确，但 minSdk 21，这个分支只作兜底
            alarmManager.set(AlarmManager.RTC_WAKEUP, time, pendingIntent);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time,
                        pendingIntent);
                return;
            }
            Log.w(TAG, "无精确闹钟权限，改用非精确闹钟 noteId=" + noteId);
        } catch (SecurityException e) {
            Log.w(TAG, "设置精确闹钟被拒绝，改用非精确闹钟 noteId=" + noteId, e);
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent);
    }
}
