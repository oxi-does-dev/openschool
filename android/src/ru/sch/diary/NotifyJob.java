package ru.sch.diary;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Одна проверка дневника: свежие оценки за три дня и ДЗ на неделю вперёд.
 * Что уже видели, помним в настройках; первый запуск только запоминает.
 */
public class NotifyJob extends JobService {

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    check(NotifyJob.this);
                } catch (Exception ignored) {
                    // тишина: фоновая проверка не должна падать с диалогом
                } finally {
                    jobFinished(params, false);
                }
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void check(Context c) throws Exception {
        if (!Notify.enabled(c)) return;

        String to = iso(new Date());
        Api api = Api.fromCookies();

        Map<String, String> marks = new LinkedHashMap<>();
        JSONArray markItems = api.marks(shift(to, -3), to);
        for (int i = 0; i < markItems.length(); i++) {
            JSONObject m = markItems.optJSONObject(i);
            if (m == null) continue;
            String key = Api.s(m, "id");
            if (key.isEmpty()) {
                key = Api.s(m, "subject_name") + "|" + Api.s(m, "date")
                        + "|" + Api.s(m, "value");
            }
            String subject = Api.s(m, "subject_name");
            String value = Api.s(m, "value");
            marks.put(key, (subject.isEmpty() ? "Оценка" : subject)
                    + (value.isEmpty() ? "" : " — " + value));
        }

        Map<String, String> hw = new LinkedHashMap<>();
        JSONArray hwItems = api.homeworksRange(to, shift(to, 7));
        for (int i = 0; i < hwItems.length(); i++) {
            JSONObject h = hwItems.optJSONObject(i);
            if (h == null) continue;
            String key = Api.s(h, "entry");
            if (key.isEmpty()) {
                key = Api.s(h, "date") + "|" + Api.s(h, "subject")
                        + "|" + Api.s(h, "text");
            }
            String subject = Api.s(h, "subject");
            String date = Api.s(h, "date");
            String label = subject.isEmpty() ? "Задание" : subject;
            if (date.length() == 10) {
                label += " (" + date.substring(8, 10) + "."
                        + date.substring(5, 7) + ")";
            }
            hw.put(key, label);
        }

        SharedPreferences prefs = c.getSharedPreferences("sch", Context.MODE_PRIVATE);
        Set<String> seenMarks = prefs.getStringSet(Notify.K_MARKS, null);
        Set<String> seenHw = prefs.getStringSet(Notify.K_HW, null);
        boolean seeded = prefs.getBoolean(Notify.K_SEEDED, false);

        if (seeded) {
            List<String> freshMarks = fresh(marks, seenMarks);
            List<String> freshHw = fresh(hw, seenHw);
            if (!freshMarks.isEmpty()) {
                Notify.post(c, 1, freshMarks.size() == 1
                                ? "Новая оценка" : "Новые оценки",
                        join(freshMarks));
            }
            if (!freshHw.isEmpty()) {
                Notify.post(c, 2, freshHw.size() == 1
                                ? "Новое домашнее задание" : "Новые домашние задания",
                        join(freshHw));
            }
        }

        prefs.edit()
                .putStringSet(Notify.K_MARKS, new LinkedHashSet<>(marks.keySet()))
                .putStringSet(Notify.K_HW, new LinkedHashSet<>(hw.keySet()))
                .putBoolean(Notify.K_SEEDED, true)
                .apply();
    }

    private static List<String> fresh(Map<String, String> now, Set<String> seen) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : now.entrySet()) {
            if (seen == null || !seen.contains(e.getKey())) out.add(e.getValue());
        }
        return out;
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(items.size(), 4);
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
        if (items.size() > shown) sb.append(" и ещё ").append(items.size() - shown);
        return sb.toString();
    }

    private static String iso(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        return f.format(d);
    }

    private static String shift(String iso, int days) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setLenient(false);
            Calendar c = Calendar.getInstance();
            c.setTime(f.parse(iso));
            c.add(Calendar.DAY_OF_MONTH, days);
            return f.format(c.getTime());
        } catch (Exception e) {
            return iso;
        }
    }
}
