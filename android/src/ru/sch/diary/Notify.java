package ru.sch.diary;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Фоновые уведомления о новых оценках и заданиях. Без AndroidX:
 * JobScheduler проверяет дневник раз в несколько часов, результат
 * сравнивается с прошлым запуском.
 */
final class Notify {

    static final int JOB_ID = 4711;
    static final int JOB_SEED = 4712;
    static final String CHANNEL = "school";

    static final String K_ON = "notify_on";
    static final String K_SEEDED = "notify_seeded";
    static final String K_MARKS = "notify_marks";
    static final String K_HW = "notify_hw";

    private Notify() {
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(K_ON, false);
    }

    static void enable(Context c, boolean on) {
        if (on) {
            // Первый запуск после включения только запоминает текущее
            // состояние: иначе придут уведомления обо всём дневнике.
            prefs(c).edit()
                    .putBoolean(K_ON, true)
                    .putBoolean(K_SEEDED, false)
                    .remove(K_MARKS)
                    .remove(K_HW)
                    .apply();
            channel(c);
            schedule(c);
        } else {
            prefs(c).edit().putBoolean(K_ON, false).apply();
            cancel(c);
        }
    }

    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        ComponentName cn = new ComponentName(c, NotifyJob.class);
        JobInfo periodic = new JobInfo.Builder(JOB_ID, cn)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6 * 60 * 60 * 1000L)
                .build();
        js.schedule(periodic);
        // Первый запуск почти сразу: он только запоминает текущее состояние,
        // чтобы не присылать уведомления обо всём дневнике.
        JobInfo seed = new JobInfo.Builder(JOB_SEED, cn)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(15 * 1000L)
                .build();
        js.schedule(seed);
    }

    static void cancel(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.cancel(JOB_ID);
        js.cancel(JOB_SEED);
    }

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Дневник",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Новые оценки и домашние задания");
        nm.createNotificationChannel(ch);
    }

    static void post(Context c, int id, String title, String text) {
        channel(c);
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(tap)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        b.setPublicVersion(new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle("OpenSchool")
                .setContentText("Новые данные в дневнике")
                .build());
        nm.notify(id, b.build());
    }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sch", Context.MODE_PRIVATE);
    }
}
