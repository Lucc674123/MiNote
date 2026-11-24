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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import net.micode.notes.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 定时提醒时间轴的列表适配器。
 *
 * 数据源是「分区标题 + 提醒条目」混合的一维列表，因此需要两种视图类型：
 * {@link #VIEW_TYPE_SECTION} 与 {@link #VIEW_TYPE_NOTE}。用 BaseAdapter 而不是
 * RecyclerView，是为了不往这个 2012 年的老工程里引入 androidx 依赖。
 */
public class AlertTimelineAdapter extends BaseAdapter {

    private static final int VIEW_TYPE_SECTION = 0;

    private static final int VIEW_TYPE_NOTE = 1;

    // 列表背景是灰色皮革纹理，原先的深棕色文字几乎融进背景，统一改为白色；
    // 「已过期」用略低透明度的白，保留视觉层级但不影响可读性。
    private static final int COLOR_TIME_NORMAL = 0xFFFFFFFF;

    private static final int COLOR_TIME_EXPIRED = 0xB3FFFFFF;

    private static final int COLOR_TEXT_NORMAL = 0xFFFFFFFF;

    private static final int COLOR_TEXT_EXPIRED = 0xCCFFFFFF;

    /**
     * 时间轴上的一行。{@link #section} 为 true 时表示分区标题，其余字段无意义。
     */
    public static class Row {
        final boolean section;

        final String text;

        final long noteId;

        final long alertDate;

        final boolean expired;

        private Row(boolean section, String text, long noteId, long alertDate, boolean expired) {
            this.section = section;
            this.text = text;
            this.noteId = noteId;
            this.alertDate = alertDate;
            this.expired = expired;
        }

        static Row ofSection(String title) {
            return new Row(true, title, -1, 0, false);
        }

        static Row ofNote(long noteId, String snippet, long alertDate, boolean expired) {
            return new Row(false, snippet, noteId, alertDate, expired);
        }
    }

    private final LayoutInflater mInflater;

    private final List<Row> mRows = new ArrayList<Row>();

    private final SimpleDateFormat mSameYearFormat = new SimpleDateFormat("MM-dd HH:mm",
            Locale.getDefault());

    private final SimpleDateFormat mFullYearFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm",
            Locale.getDefault());

    public AlertTimelineAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
    }

    public void setRows(List<Row> rows) {
        mRows.clear();
        if (rows != null) {
            mRows.addAll(rows);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mRows.size();
    }

    @Override
    public Row getItem(int position) {
        return mRows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mRows.get(position).noteId;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return mRows.get(position).section ? VIEW_TYPE_SECTION : VIEW_TYPE_NOTE;
    }

    @Override
    public boolean isEnabled(int position) {
        // 分区标题不可点击
        return !mRows.get(position).section;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = mRows.get(position);

        if (row.section) {
            TextView title;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.alert_timeline_section, parent, false);
            }
            title = (TextView) convertView;
            title.setText(row.text);
            return convertView;
        }

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.alert_timeline_item, parent, false);
        }
        TextView time = (TextView) convertView.findViewById(R.id.tv_alert_time);
        TextView snippet = (TextView) convertView.findViewById(R.id.tv_alert_snippet);

        time.setText(formatDate(row.alertDate));
        time.setTextColor(row.expired ? COLOR_TIME_EXPIRED : COLOR_TIME_NORMAL);
        snippet.setText(row.text);
        snippet.setTextColor(row.expired ? COLOR_TEXT_EXPIRED : COLOR_TEXT_NORMAL);
        return convertView;
    }

    /**
     * 当年的提醒只显示「月-日 时:分」，跨年的补上年份，避免同一条时间轴里出现歧义。
     */
    private String formatDate(long time) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(time);
        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR)) {
            return mSameYearFormat.format(new Date(time));
        }
        return mFullYearFormat.format(new Date(time));
    }
}
