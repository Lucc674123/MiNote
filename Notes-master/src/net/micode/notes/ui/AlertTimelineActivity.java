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

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时提醒时间轴。
 *
 * 只查询 NOTES 表里 alert_date &gt; 0 的便签，按提醒时间升序排列，分成「已过期」和
 * 「即将提醒」两个分区展示；点击某一条会跳到该便签的编辑页，用户可以在那里修改或
 * 取消提醒。
 *
 * 这里不新建任何闹钟：提醒的调度仍然由 {@link NoteEditActivity#onClockAlertChanged}
 * 负责（AlarmManager → AlarmReceiver → AlarmAlertActivity），本页面只做查看入口，
 * 避免出现两套并行的闹钟调度逻辑。
 */
public class AlertTimelineActivity extends Activity implements AdapterView.OnItemClickListener {

    private static final String TAG = "AlertTimelineActivity";

    private static final String[] PROJECTION = new String[] {
            NoteColumns.ID,
            NoteColumns.SNIPPET,
            NoteColumns.ALERTED_DATE
    };

    private static final int COLUMN_ID = 0;

    private static final int COLUMN_SNIPPET = 1;

    private static final int COLUMN_ALERTED_DATE = 2;

    private ListView mListView;

    private TextView mEmptyView;

    private AlertTimelineAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alert_timeline);

        mListView = (ListView) findViewById(R.id.timeline_list);
        mEmptyView = (TextView) findViewById(R.id.tv_timeline_empty);
        mAdapter = new AlertTimelineAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从编辑页返回后提醒可能已被修改，每次回到前台都重新查询
        reloadTimeline();
    }

    private void reloadTimeline() {
        long now = System.currentTimeMillis();
        List<AlertTimelineAdapter.Row> expired = new ArrayList<AlertTimelineAdapter.Row>();
        List<AlertTimelineAdapter.Row> upcoming = new ArrayList<AlertTimelineAdapter.Row>();

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(Notes.CONTENT_NOTE_URI, PROJECTION,
                    NoteColumns.ALERTED_DATE + ">0 AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                    null, NoteColumns.ALERTED_DATE + " ASC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long noteId = cursor.getLong(COLUMN_ID);
                    long alertDate = cursor.getLong(COLUMN_ALERTED_DATE);
                    String snippet = cursor.getString(COLUMN_SNIPPET);
                    if (TextUtils.isEmpty(snippet)) {
                        snippet = getString(R.string.alert_timeline_no_content);
                    }
                    if (alertDate <= now) {
                        // 已过期的按「最近过期在前」展示
                        expired.add(0, AlertTimelineAdapter.Row.ofNote(noteId, snippet, alertDate,
                                true));
                    } else {
                        upcoming.add(AlertTimelineAdapter.Row.ofNote(noteId, snippet, alertDate,
                                false));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Query notes with reminder failed", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        List<AlertTimelineAdapter.Row> rows = new ArrayList<AlertTimelineAdapter.Row>();
        if (!expired.isEmpty()) {
            rows.add(AlertTimelineAdapter.Row.ofSection(
                    getString(R.string.alert_timeline_section_expired)));
            rows.addAll(expired);
        }
        if (!upcoming.isEmpty()) {
            rows.add(AlertTimelineAdapter.Row.ofSection(
                    getString(R.string.alert_timeline_section_upcoming)));
            rows.addAll(upcoming);
        }

        mAdapter.setRows(rows);
        boolean empty = rows.isEmpty();
        mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        AlertTimelineAdapter.Row row = mAdapter.getItem(position);
        if (row == null || row.section) {
            return;
        }
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, row.noteId);
        startActivity(intent);
    }
}
