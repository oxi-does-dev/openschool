package ru.sch.diary;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.provider.MediaStore;
import android.os.Build;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.text.TextUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Дневник МЭШ: день, оценки и профиль в одном приложении. */
public final class MainActivity extends Activity {

    // ------------------------------------------------------------- палитра

    private static final int BG         = 0xFFF5EFE2;
    private static final int SURFACE    = 0xFFFFFCF4;
    private static final int SURFACE2   = 0xFFEDE5D3;
    private static final int TEXT       = 0xFF2B2A26;
    private static final int MUTED      = 0xFF7A7365;
    private static final int ACCENT     = 0xFF34577D;
    private static final int ACCENT2    = 0xFF34577D;
    private static final int ACCENT_SOFT= 0x1A34577D;
    private static final int GREEN      = 0xFF2F6B4F;
    private static final int AMBER      = 0xFF9A6A22;
    private static final int RED        = 0xFFB0403A;
    private static final int DIVIDER    = 0xFFD9CEB8;
    private static final int STROKE     = 0xFFD3C7AE;
    private static final int WHITE      = 0xFFFFFFFF;

    private static final int[] SUBJECT_COLORS = {
            0xFF34577D, 0xFF2F6B4F, 0xFF9A6A22, 0xFF6F4A75,
            0xFF2F6B6B, 0xFF9A4A5E, 0xFF9A5A32
    };

    private static final String[] MONTHS = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };
    private static final String[] MONTHS_NOM = {
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    };
    private static final String[] WEEKDAYS_FULL = {
            "воскресенье", "понедельник", "вторник", "среда",
            "четверг", "пятница", "суббота"
    };
    private static final String[] WEEKDAYS_SHORT = {
            "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"
    };

    private static final int REQ_IMPORT = 41;
    private static final int REQ_PICK_PHOTOS = 42;
    private static final int REQ_CAMERA = 43;
    private static final int REQ_CAMERA_PERM = 44;
    private static final int REQ_WEB_FILE = 45;

    // ------------------------------------------------------------- состояние

    private ExecutorService exec;
    private Handler ui;
    private SharedPreferences prefs;

    private FrameLayout host;
    private LinearLayout navBar;
    private TextView appTitle;
    private TextView appSub;
    private ImageView refreshBtn;

    private int tab;                       // 0 день, 1 оценки, 2 ещё

    private String currentDate;
    private JSONObject dayData;
    private boolean dayLoading;
    private boolean dayAuth;
    private Exception dayError;
    private int dayGen;
    private Runnable dayJob;

    private JSONArray marksData;
    private String marksFrom;
    private String marksTo;
    private boolean marksLoading;
    private boolean marksAuth;
    private Exception marksError;
    private int marksGen;
    private int marksDays = 14;
    private int marksView;                 // 0 оценки, 1 рейтинг

    private JSONArray hwData;
    private String hwFrom;
    private String hwTo;
    private boolean hwLoading;
    private boolean hwAuth;
    private Exception hwError;
    private int hwGen;
    private int hwDays = 14;
    private JSONArray ratingData;
    private JSONObject overallRank;
    private boolean overallTried;
    private boolean ratingLoading;
    private boolean ratingAuth;
    private Exception ratingError;
    private int ratingGen;

    private JSONObject profileData;
    private boolean profileLoading;
    private Exception profileError;

    private String studentName = "";
    private String studentClass = "";
    private String studentSchool = "";
    private String studentId = "";

    private LessonDialog lessonDialog;
    private ValueCallback<Uri[]> webFileCallback;
    private Typeface fontMedium;
    private Typeface fontBold;
    private Typeface fontSerif;
    private Typeface fontSerifBold;
    private Typeface fontHand;
    private String cdzThemeJs;
    private Uri pendingCameraUri;

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        exec = Executors.newSingleThreadExecutor();
        ui = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("sch", MODE_PRIVATE);

        marksDays = prefs.getInt("marksDays", 14);
        hwDays = prefs.getInt("hwDays", 14);
        String saved = prefs.getString("date", "");
        currentDate = looksLikeDate(saved) ? saved : today();
        dayData = cacheGet("day3:" + currentDate);

        loadFonts();
        buildShell();
        bootstrapCookies();
        selectTab(0);
        fetchProfile(false);
    }

    @Override
    protected void onDestroy() {
        if (dayJob != null) ui.removeCallbacks(dayJob);
        ui.removeCallbacksAndMessages(null);
        exec.shutdownNow();
        super.onDestroy();
    }

    private void loadFonts() {
        Typeface base = Typeface.DEFAULT;
        try {
            base = new Typeface.Builder(getAssets(), "fonts/GolosText.ttf")
                    .setWeight(500).build();
        } catch (Exception ignored) {
        }
        fontMedium = base;
        try {
            fontBold = new Typeface.Builder(getAssets(), "fonts/GolosText.ttf")
                    .setWeight(700).build();
        } catch (Exception e) {
            fontBold = Typeface.DEFAULT_BOLD;
        }
        try {
            fontSerif = Typeface.createFromAsset(getAssets(),
                    "fonts/PTSerif-Regular.ttf");
            fontSerifBold = Typeface.createFromAsset(getAssets(),
                    "fonts/PTSerif-Bold.ttf");
        } catch (Exception e) {
            fontSerif = base;
            fontSerifBold = fontBold;
        }
        try {
            fontHand = new Typeface.Builder(getAssets(), "fonts/Caveat.ttf")
                    .setWeight(600).build();
        } catch (Exception e) {
            fontHand = fontSerif;
        }
    }

    // ------------------------------------------------------------- каркас

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        // верхняя панель
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(10), dp(10), dp(8));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        appTitle = tvSerif("Дневник", 22, TEXT, true);
        appSub = tv("", 11, MUTED, false);
        titles.addView(appTitle);
        titles.addView(appSub);
        bar.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        refreshBtn = icon(R.drawable.ic_refresh, MUTED, 22);
        refreshBtn.setPadding(dp(9), dp(9), dp(9), dp(9));
        refreshBtn.setBackground(rippleBg(0x00000000, 22));
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.animate().rotationBy(360f).setDuration(500).start();
                if (tab == 0) scheduleDayLoad(true);
                else if (tab == 1) fetchHw(true);
                else if (tab == 2) fetchMarks(true);
                else fetchProfile(true);
            }
        });
        bar.addView(refreshBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(bar);

        View rule1 = new View(this);
        rule1.setBackgroundColor(0xFFC9BDA6);
        root.addView(rule1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        View ruleGap = new View(this);
        root.addView(ruleGap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        View rule2 = new View(this);
        rule2.setBackgroundColor(DIVIDER);
        root.addView(rule2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        host = new FrameLayout(this);
        root.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        root.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackgroundColor(BG);
        navBar.setPadding(dp(14), dp(2), dp(14), dp(8));
        root.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void buildNav() {
        navBar.removeAllViews();
        navBar.addView(navItem(R.drawable.ic_calendar, "День", 0));
        navBar.addView(navItem(R.drawable.ic_homework, "ДЗ", 1));
        navBar.addView(navItem(R.drawable.ic_star, "Оценки", 2));
        navBar.addView(navItem(R.drawable.ic_more, "Ещё", 3));
    }

    private View navItem(int iconRes, String label, final int index) {
        boolean sel = index == tab;
        int color = sel ? TEXT : MUTED;

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(7), 0, dp(3));
        item.setClickable(true);
        item.setBackground(rippleBg(0x00000000, 8));

        ImageView ic = icon(iconRes, color, 20);
        item.addView(ic);
        TextView t = tvSans(label, 10, color, sel);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(2), 0, 0);
        item.addView(t);

        View underline = new View(this);
        underline.setBackgroundColor(sel ? TEXT : 0x00000000);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(20), dp(2));
        ulp.topMargin = dp(3);
        item.addView(underline, ulp);

        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectTab(index); }
        });
        item.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return item;
    }

    private void selectTab(int index) {
        tab = index;
        buildNav();
        renderTab();
    }

    private void updateChrome() {
        if (tab == 0) {
            appTitle.setText("Дневник");
            String sub = prettyTitle(currentDate);
            if (!studentClass.isEmpty()) sub += "  ·  " + studentClass;
            appSub.setText(sub);
        } else if (tab == 1) {
            appTitle.setText("Домашнее задание");
            appSub.setText("с сегодня на " + hwDays + " дней");
        } else if (tab == 2) {
            appTitle.setText("Оценки");
            appSub.setText("за последние " + marksDays + " дней");
        } else {
            appTitle.setText("Ещё");
            String sub = studentClass.isEmpty() ? "МЭШ" : studentClass;
            if (!studentSchool.isEmpty()) sub += "  ·  " + studentSchool;
            appSub.setText(sub);
        }
    }

    private void renderTab() {
        updateChrome();
        host.removeAllViews();
        if (tab == 0) {
            host.addView(buildDay());
            if (dayData == null && dayError == null && !dayAuth && !dayLoading) {
                scheduleDayLoad(false);
            }
        } else if (tab == 1) {
            host.addView(buildHomework());
            if (hwData == null && hwError == null && !hwAuth && !hwLoading) {
                fetchHw(false);
            }
        } else if (tab == 2) {
            host.addView(buildMarks());
            if (marksData == null && marksError == null && !marksAuth && !marksLoading) {
                fetchMarks(false);
            }
        } else {
            host.addView(buildMore());
            if (profileData == null && profileError == null && !profileLoading) {
                fetchProfile(false);
            }
        }
    }

    // ------------------------------------------------------------- день

    private View buildDay() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        page.addView(monthRow());
        page.addView(weekStrip());

        View status = statusBar();
        if (status != null) page.addView(status);

        DayScrollView scroll = new DayScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(2), dp(12), dp(28));

        if (dayAuth && dayData == null) {
            content.addView(authCard("Нужен вход в МЭШ",
                    "Сессия истекла. Войдите в школу — cookies останутся внутри приложения."));
        } else if (dayData == null && dayError != null) {
            content.addView(errorCard(dayError, new Runnable() {
                @Override public void run() { scheduleDayLoad(true); }
            }));
        } else if (dayData == null) {
            content.addView(skeletonDay());
        } else {
            if (dayAuth) content.addView(authBanner());
            renderDay(content, dayData);
        }

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return page;
    }

    /** Скролл, который забирает горизонтальные свайпы у карточек. */
    private final class DayScrollView extends ScrollView {
        private float downX;
        private float downY;
        private boolean swiping;

        DayScrollView(Context c) {
            super(c);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    swiping = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(ev.getX() - downX);
                    float dy = Math.abs(ev.getY() - downY);
                    if (!swiping && dx > dp(28) && dx > dy * 1.5f) {
                        swiping = true;
                        return true;
                    }
                    break;
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (swiping) {
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_UP) {
                    float dx = ev.getX() - downX;
                    swiping = false;
                    if (Math.abs(dx) > dp(56)) {
                        selectDate(shiftDate(currentDate, dx < 0 ? 1 : -1));
                    }
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    swiping = false;
                }
                return true;
            }
            return super.onTouchEvent(ev);
        }
    }

    private View monthRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(2), dp(8), 0);

        ImageView prev = icon(R.drawable.ic_chevron, MUTED, 20);
        prev.setRotation(180f);
        prev.setPadding(dp(10), dp(10), dp(10), dp(10));
        prev.setBackground(rippleBg(0x00000000, 20));
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectDate(shiftDate(currentDate, -7)); }
        });
        row.addView(prev, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView month = tv(monthLabel(), 15, TEXT, true);
        month.setGravity(Gravity.CENTER);
        month.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickDate(); }
        });
        row.addView(month, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView next = icon(R.drawable.ic_chevron, MUTED, 20);
        next.setPadding(dp(10), dp(10), dp(10), dp(10));
        next.setBackground(rippleBg(0x00000000, 20));
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectDate(shiftDate(currentDate, 7)); }
        });
        row.addView(next, new LinearLayout.LayoutParams(dp(40), dp(40)));

        if (!currentDate.equals(today())) {
            TextView t = tv("Сегодня", 12, ACCENT, true);
            t.setPadding(dp(12), dp(7), dp(12), dp(7));
            t.setBackground(roundBg(ACCENT_SOFT, 20, 0));
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectDate(today()); }
            });
            row.addView(t);
        }
        return row;
    }

    private View weekStrip() {
        String monday = mondayOf(currentDate);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(8));

        for (int i = 0; i < 7; i++) {
            final String d = shiftDate(monday, i);
            boolean selected = d.equals(currentDate);
            boolean isToday = d.equals(today());

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(2), dp(7), dp(2), dp(5));
            cell.setClickable(true);
            cell.setBackground(selected
                    ? roundBg(ACCENT_SOFT, 4, ACCENT)
                    : rippleBg(0x00000000, 4));

            TextView wd = tvSans(WEEKDAYS_SHORT[i], 9,
                    selected ? ACCENT : MUTED, false);
            wd.setGravity(Gravity.CENTER);
            wd.setSingleLine(true);
            TextView num = tvSerif(String.valueOf(dayOfMonth(d)), 16,
                    selected ? ACCENT : TEXT, true);
            num.setGravity(Gravity.CENTER);
            num.setSingleLine(true);
            num.setPadding(0, dp(1), 0, 0);
            cell.addView(wd);
            cell.addView(num);

            if (isToday) {
                View dot = new View(this);
                dot.setBackground(roundBg(selected ? ACCENT : RED, 4, 0));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(4), dp(4));
                dlp.topMargin = dp(3);
                cell.addView(dot, dlp);
            }

            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectDate(d); }
            });
            row.addView(cell, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private View statusBar() {
        if (dayData == null) return null;

        String text;
        int dotColor;

        if (currentDate.equals(today())) {
            String now = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
            JSONArray lessons = dayData.optJSONArray("lessons");
            JSONObject current = null;
            JSONObject next = null;
            JSONObject prev = null;
            if (lessons != null) {
                for (int i = 0; i < lessons.length(); i++) {
                    JSONObject l = lessons.optJSONObject(i);
                    if (l == null) continue;
                    String b = Api.s(l, "begin");
                    String e = Api.s(l, "end");
                    if (b.compareTo(now) <= 0 && now.compareTo(e) < 0) current = l;
                    if (b.compareTo(now) > 0 && (next == null
                            || b.compareTo(Api.s(next, "begin")) < 0)) next = l;
                    if (e.compareTo(now) <= 0) prev = l;
                }
            }

            if (current != null) {
                text = "Сейчас идёт " + subjectOf(current)
                        + " · до " + Api.s(current, "end");
                dotColor = GREEN;
            } else if (next != null) {
                String begin = Api.s(next, "begin");
                if (prev != null) {
                    text = "Перемена · следующий урок в " + begin;
                } else {
                    text = "Следующий урок в " + begin
                            + " · через " + minutesUntil(now, begin) + " мин";
                }
                dotColor = ACCENT;
            } else {
                text = "Уроки на сегодня закончились";
                dotColor = MUTED;
            }
        } else {
            int n = countOf(dayData, "lessons");
            int ex = countOf(dayData, "extra");
            int hw = countOf(dayData, "homeworks");
            text = n + " " + plural(n, "урок", "урока", "уроков");
            if (ex > 0) text += " · " + ex + " доп.";
            if (hw > 0) text += " · ДЗ " + hw;
            dotColor = MUTED;
        }

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setBackground(roundBg(SURFACE2, 3, STROKE));

        View dot = new View(this);
        dot.setBackground(roundBg(dotColor, 4, 0));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dlp.rightMargin = dp(8);
        pill.addView(dot, dlp);

        TextView t = tv(text, 12, TEXT, false);
        pill.addView(t);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setPadding(dp(14), 0, dp(14), dp(6));
        wrap.addView(pill);
        return wrap;
    }

    private void renderDay(LinearLayout content, JSONObject day) {
        JSONArray lessons = day.optJSONArray("lessons");
        if (lessons == null) lessons = new JSONArray();

        if (lessons.length() == 0) {
            content.addView(emptyCard("Уроков нет", "На этот день занятий не задано"));
        } else {
            for (int i = 0; i < lessons.length(); i++) {
                JSONObject l = lessons.optJSONObject(i);
                if (l != null) content.addView(lessonCard(l));
            }
        }

        JSONArray extra = day.optJSONArray("extra");
        if (extra != null && extra.length() > 0) {
            content.addView(sectionHeader("ДОП. ЗАНЯТИЯ", extra.length()));
            for (int i = 0; i < extra.length(); i++) {
                JSONObject l = extra.optJSONObject(i);
                if (l != null) content.addView(extraCard(l));
            }
        }

    }

    private View lessonCard(final JSONObject l) {
        boolean current = false;
        if (currentDate.equals(today())) {
            String now = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
            current = Api.s(l, "begin").compareTo(now) <= 0
                    && now.compareTo(Api.s(l, "end")) < 0;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        card.setBackground(current
                ? roundBg(0xFFFFF8E6, 2, ACCENT)
                : roundBg(SURFACE, 2, STROKE));
        card.setClickable(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLessonDetails(l); }
        });

        // время
        LinearLayout timeCol = new LinearLayout(this);
        timeCol.setOrientation(LinearLayout.VERTICAL);
        timeCol.setPadding(0, dp(1), dp(6), 0);
        TextView begin = tv(Api.s(l, "begin"), 13, current ? ACCENT : TEXT, true);
        begin.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        String endTime = Api.s(l, "end");
        TextView end = tv(endTime.isEmpty() ? "" : "до " + endTime, 9, MUTED, false);
        end.setTypeface(Typeface.MONOSPACE);
        timeCol.addView(begin);
        timeCol.addView(end);
        card.addView(timeCol, new LinearLayout.LayoutParams(dp(64),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        // предмет + оценки
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView subject = tv(subjectOf(l), 16, TEXT, true);
        head.addView(subject, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        JSONArray marks = l.optJSONArray("marks");
        if (marks != null) {
            for (int i = 0; i < marks.length(); i++) {
                String v = Api.str(marks.opt(i));
                if (!v.isEmpty()) head.addView(markChip(v));
            }
        }
        body.addView(head);

        String topic = Api.s(l, "topic").trim();
        if (!topic.isEmpty() && !topic.equalsIgnoreCase(subjectOf(l))) {
            TextView t = tv(topic, 13, MUTED, false);
            t.setMaxLines(3);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(0, dp(2), 0, 0);
            body.addView(t);
        }

        String teacher = Api.s(l, "teacher");
        String room = Api.s(l, "room");
        StringBuilder meta = new StringBuilder();
        if (!room.isEmpty()) meta.append("каб. ").append(room);
        if (!teacher.isEmpty()) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(teacher);
        }
        if (meta.length() > 0) {
            TextView mt = tv(meta.toString(), 12, MUTED, false);
            mt.setPadding(0, dp(3), 0, 0);
            body.addView(mt);
        }

        if (Api.b(l, "cancelled")) {
            body.addView(tag("отменён", RED, 0x1AB0403A));
        } else if (Api.b(l, "replaced")) {
            body.addView(tag("замена", AMBER, 0x1A9A6A22));
        }

        String hw = Api.s(l, "homework").trim();
        if (!hw.isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(7), 0, 0);
            row.addView(tag("ДЗ", AMBER, 0x1A9A6A22));
            TextView t = tv(hw, 13, AMBER, false);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(6), 0, 0, 0);
            row.addView(t, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            body.addView(row);
        }

        card.addView(body, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private View extraCard(final JSONObject l) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(roundBg(SURFACE, 3, STROKE));
        card.setClickable(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLessonDetails(l); }
        });

        LinearLayout timeCol = new LinearLayout(this);
        timeCol.setOrientation(LinearLayout.VERTICAL);
        TextView begin = tv(Api.s(l, "begin"), 12, TEXT, true);
        begin.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        String endTime = Api.s(l, "end");
        TextView end = tv(endTime.isEmpty() ? "" : "до " + endTime, 9, MUTED, false);
        end.setTypeface(Typeface.MONOSPACE);
        timeCol.addView(begin);
        timeCol.addView(end);
        card.addView(timeCol, new LinearLayout.LayoutParams(dp(64),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        String title = subjectOf(l);
        if (title.length() > 110) title = title.substring(0, 109).trim() + "…";
        TextView t = tv(title, 14, TEXT, true);
        t.setMaxLines(2);
        body.addView(t);

        String teacher = Api.s(l, "teacher");
        String room = Api.s(l, "room");
        StringBuilder meta = new StringBuilder();
        if (!room.isEmpty()) meta.append("каб. ").append(room);
        if (!teacher.isEmpty()) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(teacher);
        }
        if (meta.length() > 0) {
            TextView mt = tv(meta.toString(), 11, MUTED, false);
            mt.setPadding(0, dp(2), 0, 0);
            body.addView(mt);
        }

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.leftMargin = dp(10);
        card.addView(body, blp);
        return card;
    }

    private View homeworkCard(final JSONObject h) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundBg(SURFACE, 2, STROKE));
        card.setClickable(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(6);
        card.setLayoutParams(clp);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHomeworkDetails(h); }
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackground(roundBg(subjectColor(Api.s(h, "subject")), 4, 0));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.rightMargin = dp(8);
        head.addView(dot, dlp);

        TextView s = tv(Api.s(h, "subject").isEmpty()
                ? "Задание" : Api.s(h, "subject"), 15, TEXT, true);
        head.addView(s, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (Api.b(h, "done")) {
            ImageView check = icon(R.drawable.ic_check, GREEN, 16);
            head.addView(check);
        }
        card.addView(head);

        String text = Api.s(h, "text").trim();
        if (text.isEmpty()) text = "(см. материалы)";
        TextView body = tv(text, 13, TEXT, false);
        body.setMaxLines(3);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(4), 0, 0);
        card.addView(body);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setPadding(0, dp(7), 0, 0);

        String lt = Api.s(h, "lesson_time");
        String ls = Api.s(h, "lesson_start");
        if (!lt.isEmpty()) {
            String when = ls.isEmpty() ? "до " + lt : "урок " + ls + "–" + lt;
            meta.addView(tag(when, MUTED, SURFACE2));
        }

        int files = 0;
        JSONArray fa = h.optJSONArray("files");
        if (fa != null) files = fa.length();
        int nMat = h.optInt("attachments", 0) + h.optInt("materials", 0);
        if (nMat > 0) meta.addView(tag(nMat + " " + plural(nMat, "материал",
                "материала", "материалов"), MUTED, SURFACE2));
        if (files > 0) meta.addView(tag(files + " " + plural(files, "файл",
                "файла", "файлов"), MUTED, SURFACE2));
        if (Api.b(h, "has_teacher_answer")) {
            meta.addView(tag("ответ учителя", GREEN, 0x1A2F6B4F));
        }
        if (meta.getChildCount() > 0) card.addView(meta);

        final JSONObject cdz = firstDigital(h);
        if (cdz != null) {
            View go = gradientButton("Пройти ЦДЗ", R.drawable.ic_play, new Runnable() {
                @Override public void run() {
                    openCdz(Api.s(h, "entry"), Api.s(cdz, "uuid"),
                            Api.s(cdz, "title"));
                }
            });
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.topMargin = dp(8);
            card.addView(go, glp);
        }
        return card;
    }

    private static boolean isDigital(JSONObject m) {
        if (m == null) return false;
        String uuid = Api.s(m, "uuid");
        if (uuid.isEmpty()) return false;
        String type = Api.s(m, "type");
        String action = Api.s(m, "action").toLowerCase(Locale.ROOT);
        return action.contains("пройти")
                || "test_spec_binding".equals(type)
                || "basic_material".equals(type);
    }

    private static JSONObject firstDigital(JSONObject h) {
        JSONArray mats = h.optJSONArray("materials_list");
        if (mats == null) return null;
        for (int i = 0; i < mats.length(); i++) {
            JSONObject m = mats.optJSONObject(i);
            if (isDigital(m)) return m;
        }
        return null;
    }

    // ------------------------------------------------------------- оценки

    // ------------------------------------------------------------- домашнее задание

    private View buildHomework() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout periods = new LinearLayout(this);
        periods.setOrientation(LinearLayout.HORIZONTAL);
        periods.setPadding(dp(14), dp(4), dp(14), dp(8));
        periods.addView(hwPeriodChip("7 дней", 7));
        periods.addView(hwPeriodChip("14 дней", 14));
        periods.addView(hwPeriodChip("30 дней", 30));
        page.addView(periods);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(2), dp(12), dp(28));

        if (hwAuth && hwData == null) {
            content.addView(authCard("Нужен вход в МЭШ",
                    "Чтобы показать домашнее задание, войдите в школу заново."));
        } else if (hwData == null && hwError != null) {
            content.addView(errorCard(hwError, new Runnable() {
                @Override public void run() { fetchHw(true); }
            }));
        } else if (hwData == null) {
            content.addView(skeletonMarks());
        } else {
            if (hwAuth) content.addView(authBanner());
            renderHomework(content, hwData);
        }

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View hwPeriodChip(String label, final int days) {
        boolean sel = hwDays == days;
        TextView t = tv(label, 12, sel ? WHITE : TEXT, sel);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        t.setBackground(sel ? roundBg(ACCENT, 3, 0)
                : roundBg(SURFACE, 3, STROKE));
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (hwDays == days) return;
                hwDays = days;
                prefs.edit().putInt("hwDays", days).apply();
                hwData = null;
                hwError = null;
                hwAuth = false;
                renderTab();
                fetchHw(true);
            }
        });
        return t;
    }

    private void renderHomework(LinearLayout content, JSONArray items) {
        if (items.length() == 0) {
            content.addView(emptyCard("Домашних заданий нет",
                    "На ближайшие " + hwDays + " дней ничего не задано"));
            return;
        }

        String lastDate = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject h = items.optJSONObject(i);
            if (h == null) continue;
            String date = Api.s(h, "date");
            if (!date.equals(lastDate)) {
                lastDate = date;
                int n = countOnDate(items, date);
                content.addView(sectionHeader(
                        hwDateLabel(date).toUpperCase(Locale.ROOT)
                                + (n > 0 ? "  ·  " + n : ""), 0));
            }
            content.addView(homeworkCard(h));
        }
    }

    private int countOnDate(JSONArray items, String date) {
        int n = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject h = items.optJSONObject(i);
            if (h != null && date.equals(Api.s(h, "date"))) n++;
        }
        return n;
    }

    private String hwDateLabel(String iso) {
        String t = prettyTitle(iso);
        if (t.startsWith("Сегодня") || t.startsWith("Завтра")
                || t.startsWith("Вчера")) {
            return t;
        }
        return prettyDay(iso);
    }

    private void fetchHw(boolean force) {
        final String from = today();
        final String to = shiftDate(today(), hwDays - 1);

        if (!force && hwData != null && from.equals(hwFrom) && to.equals(hwTo)) {
            return;
        }

        final int gen = ++hwGen;
        hwLoading = true;
        hwAuth = false;
        hwError = null;

        if (!force) {
            String raw = prefs.getString("hw3:" + from + ":" + to, null);
            if (raw != null) {
                try {
                    hwData = new JSONArray(raw);
                    hwFrom = from;
                    hwTo = to;
                    hwLoading = false;
                    if (tab == 1) renderTab();
                } catch (Exception ignored) {
                }
            }
        }

        if (tab == 1) renderTab();
        if (exec.isShutdown()) return;

        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    final JSONArray items = Api.fromCookies().homeworksRange(from, to);
                    cachePut("hw3:" + from + ":" + to, items.toString());
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != hwGen) return;
                            hwLoading = false;
                            hwData = items;
                            hwFrom = from;
                            hwTo = to;
                            if (tab == 1) renderTab();
                        }
                    });
                } catch (final Api.AuthException e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != hwGen) return;
                            hwLoading = false;
                            hwAuth = true;
                            if (tab == 1) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != hwGen) return;
                            hwLoading = false;
                            hwError = e;
                            if (tab == 1) renderTab();
                        }
                    });
                }
            }
        });
    }

    private View buildMarks() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        if (ratingData == null && !ratingLoading && ratingError == null) {
            fetchRating(false);
        }

        LinearLayout views = new LinearLayout(this);
        views.setOrientation(LinearLayout.HORIZONTAL);
        views.setPadding(dp(14), dp(4), dp(14), dp(6));
        views.addView(viewChip("Оценки", 0));
        views.addView(viewChip("Рейтинг", 1));
        page.addView(views);

        if (marksView == 0) {
            LinearLayout periods = new LinearLayout(this);
            periods.setOrientation(LinearLayout.HORIZONTAL);
            periods.setPadding(dp(14), dp(0), dp(14), dp(8));
            periods.addView(periodChip("14 дней", 14));
            periods.addView(periodChip("30 дней", 30));
            page.addView(periods);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(2), dp(12), dp(28));

        if (marksView == 1) {
            if (ratingAuth && ratingData == null) {
                content.addView(authCard("Нужен вход в МЭШ",
                        "Чтобы показать рейтинг, войдите в школу заново."));
            } else if (ratingData == null && ratingError != null) {
                content.addView(errorCard(ratingError, new Runnable() {
                    @Override public void run() { fetchRating(true); }
                }));
            } else if (ratingData == null) {
                content.addView(skeletonMarks());
                if (!ratingLoading) fetchRating(false);
            } else {
                renderRating(content, ratingData);
            }
        } else if (marksAuth && marksData == null) {
            content.addView(authCard("Нужен вход в МЭШ",
                    "Чтобы показать оценки, войдите в школу заново."));
        } else if (marksData == null && marksError != null) {
            content.addView(errorCard(marksError, new Runnable() {
                @Override public void run() { fetchMarks(true); }
            }));
        } else if (marksData == null) {
            content.addView(skeletonMarks());
        } else {
            if (marksAuth) content.addView(authBanner());
            renderMarks(content, marksData);
        }

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View viewChip(String label, final int view) {
        boolean sel = marksView == view;
        TextView t = tv(label, 13, sel ? WHITE : TEXT, sel);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setBackground(sel ? roundBg(ACCENT, 3, 0)
                : roundBg(SURFACE, 3, STROKE));
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (marksView == view) return;
                marksView = view;
                renderTab();
                if (view == 1 && ratingData == null && !ratingLoading) fetchRating(false);
            }
        });
        return t;
    }

    private View periodChip(String label, final int days) {
        boolean sel = marksDays == days;
        TextView t = tv(label, 12, sel ? WHITE : TEXT, sel);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        t.setBackground(sel ? roundBg(ACCENT, 3, 0)
                : roundBg(SURFACE, 3, STROKE));
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (marksDays == days) return;
                marksDays = days;
                prefs.edit().putInt("marksDays", days).apply();
                marksData = null;
                marksError = null;
                marksAuth = false;
                renderTab();
                fetchMarks(true);
            }
        });
        return t;
    }

    private void renderMarks(LinearLayout content, JSONArray items) {
        if (items.length() == 0) {
            content.addView(emptyCard("Оценок нет",
                    "За последние " + marksDays + " дней оценок не было"));
            return;
        }

        content.addView(marksSummary(items));

        Map<String, List<JSONObject>> byDate = new LinkedHashMap<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject m = items.optJSONObject(i);
            if (m == null) continue;
            String d = Api.s(m, "date");
            if (!byDate.containsKey(d)) byDate.put(d, new ArrayList<JSONObject>());
            byDate.get(d).add(m);
        }

        List<String> dates = new ArrayList<>(byDate.keySet());
        Collections.sort(dates, new Comparator<String>() {
            @Override public int compare(String a, String b) { return b.compareTo(a); }
        });

        for (String d : dates) {
            content.addView(marksDateHeader(d));
            for (JSONObject m : byDate.get(d)) content.addView(marksRow(m));
        }
    }

    private View marksSummary(JSONArray items) {
        int count5 = 0, count4 = 0, count3 = 0, count2 = 0, other = 0;
        double sum = 0;
        double weight = 0;

        for (int i = 0; i < items.length(); i++) {
            JSONObject m = items.optJSONObject(i);
            if (m == null) continue;
            String v = Api.s(m, "value");
            int w = m.optInt("weight", 1);
            if (w <= 0) w = 1;
            if (v.startsWith("5")) count5++;
            else if (v.startsWith("4")) count4++;
            else if (v.startsWith("3")) count3++;
            else if (v.startsWith("2")) count2++;
            else other++;

            try {
                double x = Double.parseDouble(v.replace(',', '.'));
                sum += x * w;
                weight += w;
            } catch (Exception ignored) {
            }
        }

        double avg = weight > 0 ? sum / weight : 0;
        int avgColor = avg >= 4.5 ? GREEN : avg >= 3.5 ? AMBER : RED;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundBg(SURFACE, 3, STROKE));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout avgBox = new LinearLayout(this);
        avgBox.setOrientation(LinearLayout.VERTICAL);
        TextView avgT = tv(String.format(Locale.US, "%.1f", avg), 30, avgColor, true);
        TextView avgL = tv("средний балл", 10, MUTED, false);
        avgBox.addView(avgT);
        avgBox.addView(avgL);
        card.addView(avgBox, new LinearLayout.LayoutParams(dp(96),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout counts = new LinearLayout(this);
        counts.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        if (count5 > 0) row1.addView(countTag("5", count5, GREEN));
        if (count4 > 0) row1.addView(countTag("4", count4, 0xFF8BC34A));
        if (count3 > 0) row1.addView(countTag("3", count3, AMBER));
        if (count2 > 0) row1.addView(countTag("2", count2, RED));
        if (other > 0) row1.addView(countTag("·", other, MUTED));
        counts.addView(row1);

        TextView total = tv(items.length() + " " + plural(items.length(),
                "оценка", "оценки", "оценок") + " за " + marksDays + " дней",
                11, MUTED, false);
        total.setPadding(0, dp(6), 0, 0);
        counts.addView(total);

        card.addView(counts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private View marksDateHeader(String iso) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(dp(4), dp(12), dp(4), dp(6));

        TextView t = tv(prettyDay(iso), 12, MUTED, true);
        t.setLetterSpacing(0.04f);
        wrap.addView(t);

        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, dp(1), 1f);
        llp.leftMargin = dp(10);
        wrap.addView(line, llp);
        return wrap;
    }

    private View marksRow(final JSONObject m) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackground(roundBg(SURFACE, 2, STROKE));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(6);
        card.setLayoutParams(clp);

        card.addView(markChip(Api.s(m, "value")));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.leftMargin = dp(10);
        card.addView(body, blp);

        String subject = Api.s(m, "subject_name");
        if (subject.isEmpty()) subject = "?";
        TextView s = tv(subject, 14, TEXT, true);
        body.addView(s);

        String form = Api.s(m, "control_form_name");
        String comment = Api.s(m, "comment");
        StringBuilder extra = new StringBuilder();
        if (!form.isEmpty()) extra.append(form);
        if (!comment.isEmpty()) {
            if (extra.length() > 0) extra.append("  ·  ");
            extra.append(comment);
        }
        if (extra.length() > 0) {
            TextView e = tv(extra.toString(), 11, MUTED, false);
            e.setMaxLines(2);
            body.addView(e);
        }

        int w = m.optInt("weight", 1);
        if (w > 1) {
            TextView wt = tv("×" + w, 11, MUTED, false);
            wt.setPadding(dp(8), 0, 0, 0);
            card.addView(wt);
        }

        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMarkDetails(m); }
        });
        return card;
    }

    // ------------------------------------------------------------- карточка оценки

    private void showMarkDetails(final JSONObject m) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(12), dp(8), dp(10));
        head.addView(tv("Оценка", 17, TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView close = icon(R.drawable.ic_close, MUTED, 20);
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        close.setBackground(rippleBg(0x00000000, 20));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(head);

        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        root.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(14), dp(20), dp(24));
        scroll.addView(box);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(BG));
            w.setDimAmount(0.55f);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.TOP);
        }
        d.show();

        box.addView(tv("Загружаю…", 12, MUTED, false));

        final String id = Api.s(m, "id");
        if (id.isEmpty() || exec.isShutdown()) {
            box.removeAllViews();
            renderMarkDetails(box, m);
            return;
        }
        exec.execute(new Runnable() {
            @Override public void run() {
                JSONObject full = null;
                Exception err = null;
                try {
                    full = Api.fromCookies().mark(id);
                } catch (Exception e) {
                    err = e;
                }
                final JSONObject ffull = full;
                final Exception ferr = err;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!d.isShowing()) return;
                        box.removeAllViews();
                        renderMarkDetails(box, ffull == null ? m : ffull);
                        if (ffull == null) {
                            TextView e = tv("Не удалось загрузить подробности"
                                    + (ferr == null ? "" : ": " + ferr.getMessage()),
                                    11, MUTED, false);
                            e.setPadding(0, dp(10), 0, 0);
                            box.addView(e);
                        }
                    }
                });
            }
        });
    }

    private void renderMarkDetails(LinearLayout box, JSONObject m) {
        String value = Api.s(m, "value");

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView big = tvHand(value.isEmpty() ? "?" : value, 44, markInk(value));
        big.setGravity(Gravity.CENTER);
        big.setBackground(roundBg(0x00000000, 2, STROKE));
        top.addView(big, new LinearLayout.LayoutParams(dp(78), dp(78)));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hlp.leftMargin = dp(14);
        top.addView(head, hlp);

        String subject = Api.s(m, "subject_name");
        head.addView(tvSerif(subject.isEmpty() ? "?" : subject, 21, TEXT, true));

        String form = Api.s(m, "control_form_name");
        StringBuilder sub = new StringBuilder(prettyDay(Api.s(m, "date")));
        if (!form.isEmpty()) sub.append("  ·  ").append(form);
        int weight = m.optInt("weight", 1);
        if (weight > 1) sub.append("  ·  вес ×").append(weight);
        TextView subT = tvSans(sub.toString(), 12, MUTED, false);
        subT.setPadding(0, dp(3), 0, 0);
        head.addView(subT);

        LinearLayout flags = new LinearLayout(this);
        flags.setOrientation(LinearLayout.HORIZONTAL);
        flags.setPadding(0, dp(6), 0, 0);
        if (Api.b(m, "is_exam")) flags.addView(tag("экзамен", RED, 0x1AB0403A));
        if (Api.b(m, "is_point")) flags.addView(tag("балл", ACCENT, ACCENT_SOFT));
        if (Api.b(m, "comment_exists")) flags.addView(tag("комментарий", AMBER, 0x1A9A6A22));
        if (Api.b(m, "has_files")) flags.addView(tag("файлы", GREEN, 0x1A2F6B4F));
        if (flags.getChildCount() > 0) head.addView(flags);

        box.addView(top);
        box.addView(rule(12, 6));

        // ---- урок
        JSONObject act = m.optJSONObject("activity");
        String topic = act == null ? "" : Api.s(act, "lesson_topic");
        JSONObject lesson = findLesson(act == null ? "" : Api.s(act, "schedule_item_id"));
        if (lesson != null && topic.isEmpty()) topic = Api.s(lesson, "topic");

        LinearLayout lessonBody = column();
        addKv(lessonBody, "Тема", topic);
        if (lesson != null) {
            String begin = Api.s(lesson, "begin");
            String end = Api.s(lesson, "end");
            String time = begin;
            if (!end.isEmpty()) time = begin.isEmpty() ? end : begin + "–" + end;
            addKv(lessonBody, "Время", time);
            String room = Api.s(lesson, "room");
            String building = Api.s(lesson, "building");
            String place = room;
            if (!building.isEmpty()) place = place.isEmpty() ? building : place + " · " + building;
            addKv(lessonBody, "Кабинет", place);
            addKv(lessonBody, "Тип урока", lessonTypeLabel(Api.s(lesson, "lesson_type")));
            addKv(lessonBody, "Формат", eduLabel(Api.s(lesson, "edu")));
            Object group = lesson.opt("group");
            if (group != null && group != JSONObject.NULL) {
                addKv(lessonBody, "Группа", String.valueOf(group));
            }
        }
        if (lessonBody.getChildCount() > 0) {
            box.addView(section("Урок", "", true, lessonBody));
        }

        // ---- учитель
        JSONObject teacher = m.optJSONObject("teacher");
        if (teacher != null) {
            String name = (Api.s(teacher, "last_name") + " "
                    + Api.s(teacher, "first_name") + " "
                    + Api.s(teacher, "middle_name")).trim();
            if (!name.isEmpty()) {
                LinearLayout tb = column();
                addKv(tb, "ФИО", name);
                box.addView(section("Учитель", "", true, tb));
            }
        }

        // ---- комментарий
        String comment = Api.s(m, "comment");
        if (!comment.isEmpty()) {
            LinearLayout cb = column();
            addKv(cb, "Текст", comment);
            box.addView(section("Комментарий", "", true, cb));
        }

        // ---- критерии
        JSONArray criteria = m.optJSONArray("criteria");
        if (criteria != null && criteria.length() > 0) {
            LinearLayout cb = column();
            for (int i = 0; i < criteria.length(); i++) {
                JSONObject c = criteria.optJSONObject(i);
                if (c == null) continue;
                String n = Api.s(c, "name");
                if (n.isEmpty()) n = Api.s(c, "criteria_name");
                String v = Api.s(c, "value");
                if (v.isEmpty()) v = Api.s(c, "mark_value");
                addKv(cb, n.isEmpty() ? "Критерий " + (i + 1) : n,
                        v.isEmpty() ? "—" : v);
            }
            box.addView(section("Критерии", String.valueOf(criteria.length()), true, cb));
        }

        // ---- как оценили класс
        JSONObject cr = m.optJSONObject("class_results");
        if (cr != null) {
            JSONArray dist = cr.optJSONArray("marks_distributions");
            int total = cr.optInt("total_students", 0);
            if (dist != null && dist.length() > 0) {
                LinearLayout db = column();
                addKv(db, "Всего в классе", String.valueOf(total));

                double sum = 0;
                int cnt = 0;
                for (int i = 0; i < dist.length(); i++) {
                    JSONObject o = dist.optJSONObject(i);
                    if (o == null) continue;
                    double v = gradeOf(o.optJSONObject("mark_value"));
                    int n = o.optInt("number_of_students", 0);
                    if (v > 0 && n > 0) { sum += v * n; cnt += n; }
                }
                if (cnt > 0) {
                    addKv(db, "Средний балл класса",
                            String.format(Locale.US, "%.2f", sum / cnt));
                }

                for (int mark = 5; mark >= 1; mark--) {
                    for (int i = 0; i < dist.length(); i++) {
                        JSONObject o = dist.optJSONObject(i);
                        if (o == null) continue;
                        double v = gradeOf(o.optJSONObject("mark_value"));
                        if ((int) Math.round(v) != mark) continue;
                        int n = o.optInt("number_of_students", 0);
                        int pct = o.optInt("percentage_of_students", 0);
                        db.addView(distRow(String.valueOf(mark), n, total, pct));
                    }
                }
                box.addView(section("Как оценили класс", total + " чел.", true, db));
            }
        }

        // ---- рейтинг по предмету
        JSONObject rating = ratingForSubject(Api.s(m, "subject_id"));
        if (rating != null) {
            int place = rating.optInt("rankPlace", 0);
            double avg = rating.optDouble("averageMarkFive", 0);
            String trend = Api.s(rating, "trend");
            if (place > 0) {
                LinearLayout rb = column();
                addKv(rb, "Место в классе", place + " "
                        + plural(place, "место", "места", "мест"));
                if (avg > 0) {
                    addKv(rb, "Средний балл по предмету",
                            String.format(Locale.US, "%.2f", avg));
                }
                if (!trend.isEmpty()) {
                    addKv(rb, "Динамика", trendLabel(trend));
                }
                box.addView(section("Рейтинг по предмету", "", true, rb));
            }
        }

        // ---- системы оценивания
        JSONArray values = m.optJSONArray("values");
        if (values != null && values.length() > 0) {
            LinearLayout vb = column();
            for (int i = 0; i < values.length(); i++) {
                JSONObject v = values.optJSONObject(i);
                if (v == null) continue;
                String name = Api.s(v, "name");
                if (name.isEmpty()) name = Api.s(v, "grade_system_type");
                StringBuilder line = new StringBuilder();
                JSONObject g = v.optJSONObject("grade");
                if (g != null) {
                    appendGrade(line, "5-балльная", g.opt("five"));
                    appendGrade(line, "10-балльная", g.opt("ten"));
                    appendGrade(line, "100-балльная", g.opt("hundred"));
                    appendGrade(line, "исходная", g.opt("origin"));
                }
                if (line.length() == 0) line.append(Api.s(v, "grade_system_type"));
                addKv(vb, name.isEmpty() ? "Система " + (i + 1) : name,
                        line.toString());
            }
            String original = Api.s(m, "original_grade_system_type");
            if (!original.isEmpty()) {
                addKv(vb, "Исходная система", systemLabel(original));
            }
            box.addView(section("Системы оценивания",
                    String.valueOf(values.length()), true, vb));
        }

        // ---- цифровая тема
        JSONObject theme = m.optJSONObject("theme_mastery");
        if (theme != null) {
            int correct = theme.optInt("correct_percent", 0);
            int suggested = theme.optInt("suggested", 0);
            final String url = !Api.s(theme, "stats_url").isEmpty()
                    ? Api.s(theme, "stats_url") : Api.s(theme, "url");
            if (correct > 0 || suggested > 0 || !url.isEmpty()) {
                LinearLayout thb = column();
                if (correct > 0) addKv(thb, "Верных ответов", correct + "%");
                if (suggested > 0) addKv(thb, "Рекомендовано заданий",
                        String.valueOf(suggested));
                if (!url.isEmpty()) {
                    View link = smallButton("Открыть статистику", 0, new Runnable() {
                        @Override public void run() { openExternal(url); }
                    });
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    llp.topMargin = dp(10);
                    thb.addView(link, llp);
                }
                box.addView(section("Цифровая тема", "", true, thb));
            }
        }

        // ---- файлы
        JSONArray files1 = m.optJSONArray("result_files");
        JSONArray files2 = m.optJSONArray("student_work_result_files");
        LinearLayout fb = column();
        int nFiles = addFiles(fb, files1) + addFiles(fb, files2);
        if (nFiles > 0) {
            box.addView(section("Файлы", String.valueOf(nFiles), true, fb));
        }

        // ---- история
        JSONArray history = m.optJSONArray("history");
        if (history != null && history.length() > 0) {
            StringBuilder sb = new StringBuilder();
            appendJson(sb, history, 0);
            LinearLayout hb = column();
            hb.addView(mono(sb.toString(), 11));
            box.addView(section("История", String.valueOf(history.length()),
                    false, hb));
        }

        // ---- служебные данные
        LinearLayout sb = column();
        addKv(sb, "id", Api.s(m, "id"));
        addKv(sb, "group_id", Api.s(m, "group_id"));
        addKv(sb, "subject_id", Api.s(m, "subject_id"));
        addKv(sb, "schedule_item_id", act == null ? "" : Api.s(act, "schedule_item_id"));
        addKv(sb, "theme_id", theme == null ? "" : Api.s(theme, "theme_id"));
        addKv(sb, "point_date", Api.s(m, "point_date"));
        addKv(sb, "created_at", Api.s(m, "created_at"));
        addKv(sb, "updated_at", Api.s(m, "updated_at"));
        addKv(sb, "is_exam", String.valueOf(Api.b(m, "is_exam")));
        addKv(sb, "is_point", String.valueOf(Api.b(m, "is_point")));
        addKv(sb, "comment_exists", String.valueOf(Api.b(m, "comment_exists")));
        addKv(sb, "has_files", String.valueOf(Api.b(m, "has_files")));
        box.addView(section("Служебные данные", "", false, sb));

        // ---- вообще все поля ответа
        StringBuilder all = new StringBuilder();
        appendJson(all, m, 0);
        LinearLayout ab = column();
        ab.addView(mono(all.toString(), 10));
        box.addView(section("Все поля ответа", "", false, ab));
    }

    private int markInk(String value) {
        char c = value == null || value.isEmpty() ? '?' : value.charAt(0);
        if (c == '5') return GREEN;
        if (c == '4') return 0xFF4E7A3A;
        if (c == '3') return AMBER;
        if (c == '2') return RED;
        return MUTED;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void addKv(LinearLayout body, String label, String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) return;
        body.addView(detailLabel(label));
        body.addView(detailText(value));
    }

    private View section(String title, String note, boolean open, final View body) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(10), 0, dp(8));
        head.setClickable(true);
        head.setBackground(rippleBg(0x00000000, 3));

        final TextView arrow = tvSans(open ? "▾" : "▸", 13, MUTED, false);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(dp(18),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        head.addView(arrow);

        TextView t = tvSans(title.toUpperCase(Locale.ROOT), 10, MUTED, true);
        t.setLetterSpacing(0.08f);
        head.addView(t, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (note != null && !note.isEmpty()) {
            head.addView(tvSans(note, 10, MUTED, false));
        }
        sec.addView(head);
        sec.addView(rule(0, 0));

        body.setVisibility(open ? View.VISIBLE : View.GONE);
        body.setPadding(0, dp(2), 0, dp(8));
        sec.addView(body);

        head.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean show = body.getVisibility() != View.VISIBLE;
                body.setVisibility(show ? View.VISIBLE : View.GONE);
                arrow.setText(show ? "▾" : "▸");
            }
        });
        return sec;
    }

    private View rule(int topDp, int bottomDp) {
        View v = new View(this);
        v.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(topDp);
        lp.bottomMargin = dp(bottomDp);
        v.setLayoutParams(lp);
        return v;
    }

    private View distRow(String mark, int n, int total, int pct) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView m = tvHand(mark, 22, markInk(mark));
        m.setGravity(Gravity.CENTER);
        m.setMinWidth(dp(28));
        top.addView(m);

        TextView t = tvSans(n + " из " + total, 12, TEXT, false);
        t.setPadding(dp(10), 0, 0, 0);
        top.addView(t, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        top.addView(tvSans(pct + "%", 11, MUTED, false));
        row.addView(top);

        ProgressBar pb = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(Math.max(0, Math.min(100, pct)));
        pb.setProgressTintList(ColorStateList.valueOf(markInk(mark)));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE2));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        plp.topMargin = dp(5);
        row.addView(pb, plp);
        return row;
    }

    private JSONObject findLesson(String id) {
        if (id == null || id.isEmpty() || dayData == null) return null;
        String[] keys = {"lessons", "extra"};
        for (String key : keys) {
            JSONArray a = dayData.optJSONArray(key);
            if (a == null) continue;
            for (int i = 0; i < a.length(); i++) {
                JSONObject l = a.optJSONObject(i);
                if (l != null && id.equals(Api.s(l, "id"))) return l;
            }
        }
        return null;
    }

    private double gradeOf(JSONObject g) {
        if (g == null) return 0;
        double five = g.optDouble("five", 0);
        if (five > 0) return five;
        try {
            return Double.parseDouble(Api.s(g, "origin"));
        } catch (Exception e) {
            return 0;
        }
    }

    private void appendGrade(StringBuilder sb, String label, Object v) {
        if (v == null || v == JSONObject.NULL) return;
        String s = String.valueOf(v);
        if (s.isEmpty() || "0".equals(s) || "0.0".equals(s)) return;
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        if (sb.length() > 0) sb.append("  ·  ");
        sb.append(label).append(": ").append(s);
    }

    private String lessonTypeLabel(String v) {
        if ("NORMAL".equals(v)) return "обычный (NORMAL)";
        if ("REPLACEMENT".equals(v)) return "замена (REPLACEMENT)";
        if ("CANCELLED".equals(v)) return "отменён (CANCELLED)";
        return v;
    }

    private String eduLabel(String v) {
        if ("OO".equals(v)) return "основное общее (OO)";
        if ("SOO".equals(v)) return "среднее общее (SOO)";
        if ("NOO".equals(v)) return "начальное общее (NOO)";
        if ("DO".equals(v)) return "дополнительное (DO)";
        if ("AE".equals(v)) return "дополнительное занятие (AE)";
        if ("EC".equals(v)) return "электив (EC)";
        return v;
    }

    private String systemLabel(String v) {
        if ("five".equals(v)) return "5-балльная";
        if ("ten".equals(v)) return "10-балльная";
        if ("hundred".equals(v)) return "100-балльная";
        return v;
    }

    private String trendLabel(String trend) {
        if ("up".equals(trend)) return "растёт";
        if ("down".equals(trend)) return "падает";
        if ("stable".equals(trend)) return "стабильно";
        return trend;
    }

    private int addFiles(LinearLayout body, JSONArray files) {
        if (files == null) return 0;
        int n = 0;
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f == null) continue;
            String url = Api.s(f, "file_url");
            if (url.startsWith("/")) url = Api.BASE + url;
            String name = Api.s(f, "file_name");
            if (name.isEmpty()) name = "файл";
            long size = f.optLong("file_size", 0);
            final String furl = url;
            TextView t = tvSans(name + (size > 0 ? "  ·  " + fileSize(size) : ""),
                    13, ACCENT, true);
            t.setPadding(0, dp(8), 0, 0);
            if (!furl.isEmpty()) {
                t.setClickable(true);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { previewFile(furl); }
                });
            }
            body.addView(t);
            n++;
        }
        return n;
    }

    private TextView mono(String text, float sp) {
        TextView t = tv(text, sp, TEXT, false);
        t.setTypeface(Typeface.MONOSPACE);
        t.setLineSpacing(dp(1), 1f);
        t.setTextIsSelectable(true);
        return t;
    }

    private void appendJson(StringBuilder sb, Object o, int depth) {
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < depth; i++) pad.append("  ");
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            java.util.Iterator<String> it = j.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = j.opt(k);
                if (v instanceof JSONObject || v instanceof JSONArray) {
                    sb.append(pad).append(k).append(":\n");
                    appendJson(sb, v, depth + 1);
                } else {
                    sb.append(pad).append(k).append(": ")
                            .append(v == null || v == JSONObject.NULL
                                    ? "—" : String.valueOf(v)).append('\n');
                }
            }
        } else if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) {
                Object v = a.opt(i);
                if (v instanceof JSONObject || v instanceof JSONArray) {
                    sb.append(pad).append("[").append(i).append("]\n");
                    appendJson(sb, v, depth + 1);
                } else {
                    sb.append(pad).append("[").append(i).append("] ")
                            .append(v == null || v == JSONObject.NULL
                                    ? "—" : String.valueOf(v)).append('\n');
                }
            }
        } else {
            sb.append(pad).append(o == null || o == JSONObject.NULL
                    ? "—" : String.valueOf(o)).append('\n');
        }
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("Не удалось открыть ссылку");
        }
    }

    private JSONObject ratingForSubject(String subjectId) {
        if (ratingData == null || subjectId.isEmpty()) return null;
        for (int i = 0; i < ratingData.length(); i++) {
            JSONObject o = ratingData.optJSONObject(i);
            if (o == null) continue;
            if (subjectId.equals(Api.s(o, "subjectId"))) return o.optJSONObject("rank");
        }
        return null;
    }

    // ------------------------------------------------------------- ещё

    private View buildMore() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(6), dp(12), dp(28));

        content.addView(profileCard());
        content.addView(sessionCard());

        content.addView(sectionHeader("ДЕЙСТВИЯ", 0));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setBackground(roundBg(SURFACE, 3, STROKE));

        actions.addView(actionRow(R.drawable.ic_login, "Войти в МЭШ",
                "Открыть страницу входа в приложении", false, new Runnable() {
                    @Override public void run() { openLogin(); }
                }));
        actions.addView(actionDivider());
        actions.addView(actionRow(R.drawable.ic_import, "Импорт cookies",
                "Файл cookies.json с компьютера", false, new Runnable() {
                    @Override public void run() { importCookies(); }
                }));
        actions.addView(actionDivider());
        actions.addView(actionRow(R.drawable.ic_refresh, "Обновить профиль",
                "Перечитать данные ученика", false, new Runnable() {
                    @Override public void run() { fetchProfile(true); }
                }));
        actions.addView(actionDivider());
        actions.addView(actionRow(R.drawable.ic_info, "Сеть и VPN",
                "Проверить IP, страну выхода и доступ к МЭШ", false, new Runnable() {
                    @Override public void run() { networkCheck(); }
                }));
        actions.addView(actionDivider());
        actions.addView(actionRow(R.drawable.ic_trash, "Очистить кэш дней",
                "Оставить сессию и профиль", false, new Runnable() {
                    @Override public void run() { clearCache(); }
                }));
        actions.addView(actionDivider());
        actions.addView(actionRow(R.drawable.ic_info, "О приложении",
                "Версия, формат cookies, адрес API", false, new Runnable() {
                    @Override public void run() { about(); }
                }));

        if (Api.usable(Cookies.token())) {
            actions.addView(actionDivider());
            actions.addView(actionRow(R.drawable.ic_logout, "Выйти из аккаунта",
                    "Удалить cookies и кэш из приложения", true, new Runnable() {
                        @Override public void run() { confirmLogout(); }
                    }));
        }
        content.addView(actions);

        TextView footer = tv("OpenSchool · 1.5\nданные: school.mos.ru",
                10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        content.addView(footer);

        scroll.addView(content);
        return scroll;
    }

    // ------------------------------------------------------------- сеть и VPN

    private boolean vpnActive() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void networkCheck() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));

        root.addView(tv("Сеть и VPN", 18, TEXT, true));

        final TextView status = tv("Проверяю…", 13, TEXT, false);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status);

        root.addView(tv("МЭШ пускает только с российских IP. Если VPN включён, "
                + "добавьте приложение в его список (split tunneling) и выберите "
                + "российский сервер.", 11, MUTED, false));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(14), 0, 0);
        buttons.addView(secondaryButton("Проверить снова", new Runnable() {
            @Override public void run() { checkNetwork(status); }
        }), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(8);
        buttons.addView(primaryButton("Закрыть", new Runnable() {
            @Override public void run() { d.dismiss(); }
        }), lp);
        root.addView(buttons);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(BG));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        checkNetwork(status);
    }

    private void checkNetwork(final TextView status) {
        status.setText("Проверяю…");
        if (exec.isShutdown()) return;
        exec.execute(new Runnable() {
            @Override public void run() {
                final boolean vpn = vpnActive();
                JSONObject ip = null;
                String ipErr = null;
                try {
                    ip = Api.ipInfo();
                } catch (Exception e) {
                    ipErr = e.getMessage();
                }
                final JSONObject fip = ip;
                final String fipErr = ipErr;
                final String mesErr = Api.probe();
                ui.post(new Runnable() {
                    @Override public void run() {
                        StringBuilder b = new StringBuilder();
                        b.append("VPN: ").append(vpn ? "включён" : "выключен").append('\n');
                        if (fip != null && fip.optBoolean("success", true)) {
                            String ipAddr = Api.s(fip, "ip");
                            String country = Api.s(fip, "country");
                            String cc = Api.s(fip, "country_code");
                            b.append("Внешний IP: ").append(ipAddr);
                            if (!country.isEmpty()) b.append(" · ").append(country);
                            b.append('\n');
                            b.append("Страна выхода: ")
                                    .append("RU".equalsIgnoreCase(cc)
                                            ? "Россия — можно"
                                            : "не Россия — МЭШ заблокирует")
                                    .append('\n');
                        } else {
                            b.append("Внешний IP: не определён");
                            if (fipErr != null) b.append(" (").append(fipErr).append(')');
                            b.append('\n');
                        }
                        b.append("МЭШ: ").append(mesErr == null ? "доступен" : mesErr);
                        status.setText(b.toString());
                    }
                });
            }
        });
    }

    private View profileCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundBg(SURFACE, 3, STROKE));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);

        TextView avatar = tv(initials(), 20, ACCENT, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(ovalBg(ACCENT, 1));
        card.addView(avatar, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.leftMargin = dp(14);
        card.addView(body, blp);

        if (profileData != null) {
            TextView name = tv(studentName.isEmpty() ? "Без имени" : studentName,
                    18, TEXT, true);
            body.addView(name);

            StringBuilder line = new StringBuilder();
            if (!studentClass.isEmpty()) line.append("класс ").append(studentClass);
            if (!studentSchool.isEmpty()) {
                if (line.length() > 0) line.append("  ·  ");
                line.append(studentSchool);
            }
            if (line.length() > 0) {
                TextView l = tv(line.toString(), 12, MUTED, false);
                l.setPadding(0, dp(3), 0, 0);
                body.addView(l);
            }

            String birth = "";
            JSONObject prof = profileData.optJSONObject("profile");
            if (prof != null) birth = Api.s(prof, "birth_date");
            if (!birth.isEmpty()) {
                TextView b = tv("дата рождения: " + birth, 11, MUTED, false);
                b.setPadding(0, dp(3), 0, 0);
                body.addView(b);
            }
        } else if (profileError != null) {
            TextView name = tv("Профиль недоступен", 16, TEXT, true);
            body.addView(name);
            TextView l = tv(String.valueOf(profileError.getMessage()), 11, MUTED, false);
            l.setPadding(0, dp(3), 0, 0);
            body.addView(l);
        } else {
            body.addView(bar(140, 16, SURFACE2));
            View sp = new View(this);
            sp.setLayoutParams(new LinearLayout.LayoutParams(1, dp(8)));
            body.addView(sp);
            body.addView(bar(180, 11, SURFACE2));
        }
        return card;
    }

    private View sessionCard() {
        boolean ok = profileData != null && Api.usable(Cookies.token());
        int dot = ok ? GREEN : (profileError != null ? RED : MUTED);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(roundBg(SURFACE, 3, STROKE));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View d = new View(this);
        d.setBackground(roundBg(dot, 4, 0));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.rightMargin = dp(8);
        row.addView(d, dlp);

        TextView title = tv(ok ? "Сессия активна"
                : (profileError != null ? "Нужен вход в МЭШ" : "Проверка сессии…"),
                13, TEXT, true);
        row.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView id = tv(studentId.isEmpty() ? "" : "id " + studentId, 11, MUTED, false);
        row.addView(id);

        card.addView(row);

        if (!ok) {
            TextView hint = tv("Войдите в школу или импортируйте cookies с компьютера.",
                    11, MUTED, false);
            hint.setPadding(0, dp(6), 0, 0);
            card.addView(hint);
        }
        return card;
    }

    private View actionRow(int iconRes, String title, String sub,
                           boolean danger, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(14), dp(12));
        row.setClickable(true);
        row.setBackground(rippleBg(0x00000000, 0));

        ImageView ic = icon(iconRes, danger ? RED : MUTED, 20);
        row.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.leftMargin = dp(14);
        row.addView(body, blp);

        TextView t = tv(title, 14, danger ? RED : TEXT, false);
        body.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = tv(sub, 11, MUTED, false);
            s.setPadding(0, dp(2), 0, 0);
            body.addView(s);
        }

        ImageView chevron = icon(R.drawable.ic_chevron, 0xFF3F4652, 18);
        row.addView(chevron);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return row;
    }

    private View actionDivider() {
        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.leftMargin = dp(54);
        line.setLayoutParams(lp);
        return line;
    }

    // ------------------------------------------------------------- загрузка дня

    private void selectDate(String d) {
        if (!looksLikeDate(d) || d.equals(currentDate)) return;
        currentDate = d;
        prefs.edit().putString("date", d).apply();
        dayData = cacheGet("day3:" + d);
        dayError = null;
        dayAuth = false;
        renderTab();
        scheduleDayLoad(true);
    }

    private void scheduleDayLoad(final boolean force) {
        final String date = currentDate;
        if (!force && dayData != null && date.equals(Api.s(dayData, "date"))) return;
        if (dayJob != null) ui.removeCallbacks(dayJob);
        dayJob = new Runnable() {
            @Override public void run() { fetchDay(date); }
        };
        ui.postDelayed(dayJob, 120);
    }

    private void fetchDay(final String date) {
        final int gen = ++dayGen;
        dayLoading = true;

        if (exec.isShutdown()) return;
        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    Api api = Api.fromCookies();
                    final JSONObject day = api.day(date);
                    cachePut("day3:" + date, day.toString());
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != dayGen) return;
                            dayLoading = false;
                            dayAuth = false;
                            dayError = null;
                            dayData = day;
                            if (tab == 0 && date.equals(currentDate)) renderTab();
                        }
                    });
                } catch (final Api.AuthException e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != dayGen) return;
                            dayLoading = false;
                            dayAuth = true;
                            if (tab == 0 && date.equals(currentDate)) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != dayGen) return;
                            dayLoading = false;
                            dayError = e;
                            if (tab == 0 && date.equals(currentDate)) renderTab();
                        }
                    });
                }
            }
        });
    }

    // ------------------------------------------------------------- загрузка оценок

    private void fetchMarks(boolean force) {
        final String to = today();
        final String from = shiftDate(to, -(marksDays - 1));

        if (!force && marksData != null && from.equals(marksFrom) && to.equals(marksTo)) {
            return;
        }

        final int gen = ++marksGen;
        marksLoading = true;
        marksAuth = false;
        marksError = null;

        if (!force) {
            String raw = prefs.getString("marks2:" + from + ":" + to, null);
            if (raw != null) {
                try {
                    marksData = new JSONArray(raw);
                    marksFrom = from;
                    marksTo = to;
                    marksLoading = false;
                    renderTab();
                } catch (Exception ignored) {
                }
            }
        }

        if (tab == 2) renderTab();

        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    Api api = Api.fromCookies();
                    final JSONArray items = api.marks(from, to);
                    cachePut("marks2:" + from + ":" + to, items.toString());
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != marksGen) return;
                            marksLoading = false;
                            marksData = items;
                            marksFrom = from;
                            marksTo = to;
                            if (tab == 2) renderTab();
                        }
                    });
                } catch (final Api.AuthException e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != marksGen) return;
                            marksLoading = false;
                            marksAuth = true;
                            if (tab == 2) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != marksGen) return;
                            marksLoading = false;
                            marksError = e;
                            if (tab == 2) renderTab();
                        }
                    });
                }
            }
        });
    }

    // ------------------------------------------------------------- рейтинг

    private void fetchRating(boolean force) {
        if (ratingLoading) return;
        if (!force && ratingData != null
                && (overallRank != null || overallTried)) return;
        final int gen = ++ratingGen;
        ratingLoading = true;
        ratingAuth = false;
        ratingError = null;

        if (!force) {
            String raw = prefs.getString("rating:" + today(), null);
            if (raw != null) {
                try {
                    ratingData = new JSONArray(raw);
                    ratingLoading = false;
                    if (tab == 2 && marksView == 1) renderTab();
                } catch (Exception ignored) {
                }
            }
            String or = prefs.getString("rankclass:" + today(), null);
            if (or != null) {
                try {
                    overallRank = new JSONObject(or);
                } catch (Exception ignored) {
                }
            }
        }
        if (tab == 2 && marksView == 1) renderTab();

        if (exec.isShutdown()) return;
        overallTried = true;
        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    final Api api = Api.fromCookies();
                    final JSONArray items = api.subjectRatings(today());
                    cachePut("rating:" + today(), items.toString());
                    JSONObject overall = null;
                    try {
                        overall = api.classRank(today());
                        cachePut("rankclass:" + today(), overall.toString());
                    } catch (Exception ignored) {
                        // общее место не критично — покажем рейтинг по предметам
                    }
                    final JSONObject fOverall = overall;
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != ratingGen) return;
                            ratingLoading = false;
                            ratingData = items;
                            if (fOverall != null) overallRank = fOverall;
                            if (tab == 2 && marksView == 1) renderTab();
                        }
                    });
                } catch (final Api.AuthException e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != ratingGen) return;
                            ratingLoading = false;
                            ratingAuth = true;
                            if (tab == 2 && marksView == 1) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != ratingGen) return;
                            ratingLoading = false;
                            ratingError = e;
                            if (tab == 2 && marksView == 1) renderTab();
                        }
                    });
                }
            }
        });
    }

    private void renderRating(LinearLayout content, JSONArray items) {
        if (overallRank != null && overallRank.optInt("place", 0) > 0) {
            content.addView(overallRatingCard(overallRank));
        }

        List<JSONObject> rows = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            JSONObject r = o.optJSONObject("rank");
            if (r == null) continue;
            if (r.optInt("rankPlace", 0) <= 0) continue;
            rows.add(o);
        }
        if (rows.isEmpty()) {
            content.addView(emptyCard("Рейтинга пока нет",
                    "МЭШ ещё не посчитал места по предметам"));
            return;
        }

        Collections.sort(rows, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                JSONObject ra = a.optJSONObject("rank");
                JSONObject rb = b.optJSONObject("rank");
                int pa = ra == null ? 999 : ra.optInt("rankPlace", 999);
                int pb = rb == null ? 999 : rb.optInt("rankPlace", 999);
                if (pa != pb) return pa - pb;
                return Api.s(a, "subjectName").compareTo(Api.s(b, "subjectName"));
            }
        });

        LinearLayout card = card(SURFACE);
        card.setPadding(dp(16), dp(14), dp(16), dp(8));

        card.addView(tv("Рейтинг в классе", 15, TEXT, true));
        card.addView(tv("по среднему баллу за текущий период", 11, MUTED, false));

        for (int i = 0; i < rows.size(); i++) {
            if (i == 0) {
                View line = new View(this);
                line.setBackgroundColor(DIVIDER);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                llp.topMargin = dp(12);
                card.addView(line, llp);
            }
            card.addView(ratingRow(rows.get(i)));
        }
        content.addView(card);

        TextView note = tv("Место считается по среднему баллу среди учеников класса. "
                + "Данные обновляет МЭШ.", 11, MUTED, false);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(10);
        content.addView(note, flp);
    }

    private View overallRatingCard(JSONObject r) {
        int place = r.optInt("place", 0);
        int total = r.optInt("total", 0);
        double avg = r.optDouble("average", 0);
        int place30 = r.optInt("place30", 0);
        double avg30 = r.optDouble("average30", 0);
        String trend = Api.s(r, "trend");

        LinearLayout card = card(SURFACE);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        card.addView(tv("Общее место в классе", 15, TEXT, true));
        card.addView(tv("по среднему баллу среди всех предметов", 11, MUTED, false));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        TextView big = tv(String.valueOf(place), 34, ACCENT, true);
        row.addView(big);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, 0, 0);
        col.addView(tv("из " + total, 13, TEXT, false));
        if (avg > 0) {
            col.addView(tv("средний балл " + String.format(Locale.US, "%.2f", avg),
                    12, MUTED, false));
        }
        row.addView(col, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!trend.isEmpty() && !"stable".equals(trend)) {
            boolean up = "up".equals(trend);
            TextView tr = tv(up ? "↑ растёт" : "↓ падает", 11,
                    up ? GREEN : RED, false);
            row.addView(tr);
        }
        card.addView(row);
        if (avg > 0) card.addView(markBar(avg, ACCENT));

        if (place30 > 0) {
            TextView d = tv("За 30 дней: " + place30 + " из " + total
                    + (avg30 > 0
                    ? "  ·  " + String.format(Locale.US, "%.2f", avg30) : ""),
                    11, MUTED, false);
            d.setPadding(0, dp(8), 0, 0);
            card.addView(d);
        }
        return card;
    }

    private View ratingRow(JSONObject o) {
        JSONObject r = o.optJSONObject("rank");
        int place = r == null ? 0 : r.optInt("rankPlace", 0);
        double avg = r == null ? 0 : r.optDouble("averageMarkFive", 0);
        String trend = r == null ? "" : Api.s(r, "trend");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = tv(String.valueOf(place), 13, place == 1 ? WHITE : TEXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundBg(place == 1 ? GREEN : SURFACE2, 3,
                place == 1 ? 0 : STROKE));
        top.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.leftMargin = dp(12);
        top.addView(body, blp);

        String name = Api.s(o, "subjectName");
        TextView n = tv(name.isEmpty() ? "?" : name, 14, TEXT, false);
        n.setMaxLines(2);
        body.addView(n);

        TextView sub = tv(place + " " + plural(place, "место", "места", "мест")
                + " в классе", 11, MUTED, false);
        body.addView(sub);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        top.addView(right);

        if (avg > 0) {
            TextView a = tv(String.format(Locale.US, "%.1f", avg), 15,
                    avg >= 4.5 ? GREEN : TEXT, true);
            a.setGravity(Gravity.END);
            right.addView(a);
        }
        if (!trend.isEmpty() && !"stable".equals(trend)) {
            boolean up = "up".equals(trend);
            TextView tr = tv(up ? "↑ растёт" : "↓ падает", 10,
                    up ? GREEN : RED, false);
            tr.setGravity(Gravity.END);
            right.addView(tr);
        }
        row.addView(top);

        if (avg > 0) {
            row.addView(markBar(avg, avg >= 4.5 ? GREEN : ACCENT));
        }
        return row;
    }

    private View markBar(double avg, int color) {
        ProgressBar pb = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        pb.setMax(500);
        pb.setProgress((int) Math.round(avg * 100));
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        lp.topMargin = dp(7);
        pb.setLayoutParams(lp);
        return pb;
    }

    // ------------------------------------------------------------- профиль

    private void fetchProfile(boolean force) {
        if (profileLoading) return;
        profileLoading = true;

        if (!force) {
            String raw = prefs.getString("profile", null);
            if (raw != null) {
                try {
                    applyProfile(new JSONObject(raw));
                    profileLoading = false;
                    if (tab == 3) renderTab();
                    else updateChrome();
                    return;
                } catch (Exception ignored) {
                }
            }
        }

        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    Api api = Api.fromCookies();
                    final JSONObject p = api.profile();
                    cachePut("profile", p.toString());
                    ui.post(new Runnable() {
                        @Override public void run() {
                            profileLoading = false;
                            profileError = null;
                            applyProfile(p);
                            if (tab == 3) renderTab();
                            else updateChrome();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            profileLoading = false;
                            profileError = e;
                            if (tab == 3) renderTab();
                        }
                    });
                }
            }
        });
    }

    private void applyProfile(JSONObject p) {
        profileData = p;
        JSONObject prof = p.optJSONObject("profile");
        JSONArray kids = p.optJSONArray("children");
        JSONObject kid = kids != null && kids.length() > 0 ? kids.optJSONObject(0) : null;

        if (prof != null) {
            studentName = joinNonEmpty(" ",
                    Api.s(prof, "last_name"), Api.s(prof, "first_name"),
                    Api.s(prof, "middle_name"));
        }
        studentId = "";
        if (kid != null) {
            studentClass = Api.s(kid, "class_name");
            studentId = Api.s(kid, "id");
            JSONObject school = kid.optJSONObject("school");
            studentSchool = school == null ? "" : Api.s(school, "short_name");
        }
        if (studentId.isEmpty() && prof != null) studentId = Api.s(prof, "id");
    }

    // ------------------------------------------------------------- карточки состояний

    private View authCard(String title, String text) {
        LinearLayout card = card(SURFACE);
        card.setPadding(dp(18), dp(18), dp(18), dp(16));

        TextView h = tv(title, 18, TEXT, true);
        card.addView(h);

        TextView m = tv(text, 13, MUTED, false);
        m.setPadding(0, dp(6), 0, dp(16));
        card.addView(m);

        card.addView(primaryButton("Войти в МЭШ", new Runnable() {
            @Override public void run() { openLogin(); }
        }));
        card.addView(spacer(8));
        card.addView(secondaryButton("Импорт cookies", new Runnable() {
            @Override public void run() { importCookies(); }
        }));

        File dir = getExternalFilesDir(null);
        TextView hint = tv("Можно положить cookies.json в папку приложения:\n"
                + (dir == null ? "Android/data/ru.sch.diary/files"
                               : dir.getAbsolutePath()), 10, MUTED, false);
        hint.setPadding(0, dp(14), 0, 0);
        card.addView(hint);
        return card;
    }

    private View authBanner() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackground(roundBg(0xFFF7E7E2, 3, 0xFFD9B3A8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        TextView t = tv("Сессия истекла — показаны сохранённые данные",
                12, TEXT, false);
        card.addView(t, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView b = tv("Войти", 12, ACCENT, true);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setBackground(roundBg(ACCENT_SOFT, 14, 0));
        b.setClickable(true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openLogin(); }
        });
        card.addView(b);
        return card;
    }

    private View errorCard(Exception e, final Runnable retry) {
        LinearLayout card = card(SURFACE);
        card.setPadding(dp(18), dp(18), dp(18), dp(16));

        TextView h = tv("Не удалось загрузить", 17, TEXT, true);
        card.addView(h);

        TextView m = tv(String.valueOf(e == null ? "неизвестная ошибка" : e.getMessage()),
                13, MUTED, false);
        m.setPadding(0, dp(6), 0, dp(16));
        card.addView(m);

        card.addView(primaryButton("Повторить", retry));
        TextView net = secondaryButton("Проверить сеть и VPN", new Runnable() {
            @Override public void run() { networkCheck(); }
        });
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(8);
        card.addView(net, nlp);
        return card;
    }

    private View emptyCard(String title, String sub) {
        LinearLayout card = card(SURFACE);
        card.setPadding(dp(18), dp(24), dp(18), dp(24));

        TextView h = tv(title, 15, TEXT, true);
        h.setGravity(Gravity.CENTER);
        card.addView(h);

        TextView m = tv(sub, 12, MUTED, false);
        m.setGravity(Gravity.CENTER);
        m.setPadding(0, dp(4), 0, 0);
        card.addView(m);
        return card;
    }

    private View skeletonDay() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 4; i++) {
            LinearLayout c = card(SURFACE);
            c.setPadding(dp(14), dp(14), dp(14), dp(14));
            c.addView(bar(60, 10, SURFACE2));
            View s1 = bar(180, 15, SURFACE2);
            s1.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(180), dp(15));
            lp.topMargin = dp(10);
            c.addView(s1, lp);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    dp(120), dp(10));
            lp2.topMargin = dp(8);
            c.addView(bar(120, 10, SURFACE2), lp2);
            box.addView(c);
        }
        return box;
    }

    private View skeletonMarks() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout summary = card(SURFACE);
        summary.setPadding(dp(16), dp(16), dp(16), dp(16));
        summary.addView(bar(80, 28, SURFACE2));
        box.addView(summary);

        for (int i = 0; i < 5; i++) {
            LinearLayout c = card(SURFACE);
            c.setPadding(dp(14), dp(12), dp(14), dp(12));
            c.addView(bar(24, 24, SURFACE2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(160), dp(12));
            lp.leftMargin = dp(10);
            c.addView(bar(160, 12, SURFACE2), lp);
            box.addView(c);
        }
        return box;
    }

    // ------------------------------------------------------------- детали

    private void showLessonDetails(final JSONObject l) {
        if (lessonDialog != null) lessonDialog.dismiss();
        lessonDialog = new LessonDialog(l);
        lessonDialog.show();
    }

    // ------------------------------------------------------------- урок + прикрепление работы

    private final class PickedPhoto {
        final String name;
        final byte[] data;

        PickedPhoto(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    /** Карточка урока с возможностью прикрепить фото самостоятельной работы. */
    private final class LessonDialog extends Dialog {

        private final JSONObject lesson;
        private final String lessonId;
        private JSONObject details;
        private JSONObject studentWork;
        private boolean loading = true;
        private Exception error;
        private Integer variant;
        private boolean uploading;
        private final List<PickedPhoto> pending = new ArrayList<>();
        private LinearLayout attachBox;

        LessonDialog(JSONObject lesson) {
            super(MainActivity.this);
            this.lesson = lesson;
            this.lessonId = Api.s(lesson, "id");
        }

        @Override
        protected void onCreate(Bundle b) {
            super.onCreate(b);
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(BG);

            LinearLayout head = new LinearLayout(MainActivity.this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(20), dp(12), dp(8), dp(10));

            TextView title = tv("Урок", 17, TEXT, true);
            head.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView close = icon(R.drawable.ic_close, MUTED, 20);
            close.setPadding(dp(10), dp(10), dp(10), dp(10));
            close.setBackground(rippleBg(0x00000000, 20));
            close.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dismiss(); }
            });
            head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
            root.addView(head);

            View line = new View(MainActivity.this);
            line.setBackgroundColor(DIVIDER);
            root.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

            ScrollView scroll = new ScrollView(MainActivity.this);
            scroll.setFillViewport(true);

            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(20), dp(14), dp(20), dp(24));

            box.addView(tv(subjectOf(lesson), 20, TEXT, true));

            String time = Api.s(lesson, "begin") + "–" + Api.s(lesson, "end");
            String room = Api.s(lesson, "room");
            String teacher = Api.s(lesson, "teacher");
            StringBuilder sub = new StringBuilder(time);
            if (!room.isEmpty()) sub.append("  ·  каб. ").append(room);
            if (!teacher.isEmpty()) sub.append("  ·  ").append(teacher);
            TextView st = tv(sub.toString(), 12, MUTED, false);
            st.setPadding(0, dp(4), 0, dp(10));
            box.addView(st);

            JSONArray marks = lesson.optJSONArray("marks");
            if (marks != null && marks.length() > 0) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 0, 0, dp(10));
                for (int i = 0; i < marks.length(); i++) {
                    String v = Api.str(marks.opt(i));
                    if (!v.isEmpty()) row.addView(markChip(v));
                }
                box.addView(row);
            }

            String topic = Api.s(lesson, "topic").trim();
            if (!topic.isEmpty()) {
                box.addView(detailLabel("Тема"));
                box.addView(detailText(topic));
            }

            String hw = Api.s(lesson, "homework").trim();
            if (!hw.isEmpty()) {
                box.addView(detailLabel("Домашнее задание"));
                box.addView(detailText(hw));
            }

            String group = Api.s(lesson, "group");
            if (!group.isEmpty() && !group.equalsIgnoreCase(subjectOf(lesson))
                    && !group.startsWith(subjectOf(lesson))) {
                box.addView(detailLabel("Группа"));
                box.addView(detailText(group));
            }

            attachBox = new LinearLayout(MainActivity.this);
            attachBox.setOrientation(LinearLayout.VERTICAL);
            box.addView(attachBox);

            scroll.addView(box);
            root.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            setContentView(root);

            Window w = getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(BG));
                w.setDimAmount(0.55f);
                int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.92f);
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h);
                w.setGravity(Gravity.BOTTOM);
            }

            renderAttachment();
            loadDetails();
        }

        @Override
        public void dismiss() {
            if (lessonDialog == this) lessonDialog = null;
            super.dismiss();
        }

        private void loadDetails() {
            loading = true;
            error = null;
            renderAttachment();
            if (lessonId.isEmpty()) {
                loading = false;
                error = new Exception("в расписании нет id урока");
                renderAttachment();
                return;
            }
            if (exec.isShutdown()) return;
            exec.execute(new Runnable() {
                @Override public void run() {
                    try {
                        final JSONObject d = Api.fromCookies().lesson(lessonId);
                        ui.post(new Runnable() {
                            @Override public void run() {
                                if (!isShowing()) return;
                                details = d;
                                studentWork = d.optJSONObject("student_work");
                                variant = workVariant(studentWork);
                                loading = false;
                                error = null;
                                renderAttachment();
                            }
                        });
                    } catch (final Exception e) {
                        ui.post(new Runnable() {
                            @Override public void run() {
                                if (!isShowing()) return;
                                loading = false;
                                error = e;
                                renderAttachment();
                            }
                        });
                    }
                }
            });
        }

        private Integer workVariant(JSONObject work) {
            if (work == null) return null;
            Object v = work.opt("variant");
            if (v == null || v == JSONObject.NULL) return null;
            int n = work.optInt("variant", 0);
            return n > 0 ? n : null;
        }

        private void renderAttachment() {
            if (attachBox == null) return;
            attachBox.removeAllViews();

            // Пока грузится — молчим: прикрепление работы не должно быть
            // главным на экране урока.
            if (loading) return;

            boolean eduOk = "OO".equals(Api.s(details, "lesson_education_type"))
                    || "OO".equals(Api.s(lesson, "edu"));
            if (!eduOk) return;

            if (error != null) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(10), 0, 0);
                row.addView(tv("Прикрепление работы недоступно", 11, MUTED, false),
                        new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView again = tv("Повторить", 11, ACCENT, true);
                again.setPadding(dp(8), dp(4), 0, dp(4));
                again.setClickable(true);
                again.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { loadDetails(); }
                });
                row.addView(again);
                attachBox.addView(row);
                return;
            }

            boolean window = isWorkWindowOpen();
            JSONArray files = studentWork == null
                    ? null : studentWork.optJSONArray("files");
            boolean hasFiles = files != null && files.length() > 0;

            if (!window && !hasFiles) return;

            if (hasFiles) {
                TextView lbl = tv("Работа на проверке", 11, MUTED, true);
                lbl.setLetterSpacing(0.04f);
                lbl.setPadding(0, dp(14), 0, dp(2));
                attachBox.addView(lbl);
                for (int i = 0; i < files.length(); i++) {
                    JSONObject f = files.optJSONObject(i);
                    if (f != null) attachBox.addView(workFileRow(f, window));
                }
            }

            for (int i = 0; i < pending.size(); i++) {
                attachBox.addView(pendingRow(i));
            }

            if (!window) return;

            if (!pending.isEmpty()) {
                attachBox.addView(variantRow());
                TextView send = primaryButton("Отправить на проверку", new Runnable() {
                    @Override public void run() { sendWork(); }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(10);
                attachBox.addView(send, lp);
            }

            View add = smallButton(pending.isEmpty() && !hasFiles
                            ? "Прикрепить фото работы"
                            : "Добавить фото",
                    R.drawable.ic_camera, new Runnable() {
                        @Override public void run() { pickPhotoSource(); }
                    });
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = dp(10);
            attachBox.addView(add, alp);
        }

        private void pickPhotoSource() {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Прикрепить фото")
                    .setItems(new String[]{"Снять фото", "Выбрать из галереи"},
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    if (w == 0) takePhoto(); else pickPhotos();
                                }
                            })
                    .show();
        }

        private View workFileRow(final JSONObject f, boolean window) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(3));

            ImageView ic = icon(R.drawable.ic_photo, ACCENT, 18);
            row.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));

            LinearLayout body = new LinearLayout(MainActivity.this);
            body.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blp.leftMargin = dp(10);
            row.addView(body, blp);

            String name = Api.s(f, "file_name");
            if (name.isEmpty()) name = "файл";
            TextView n = tv(name, 13, TEXT, false);
            n.setMaxLines(1);
            n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            body.addView(n);

            long size = f.optLong("size", 0);
            String by = Api.s(f, "uploaded_by");
            String desc = "TEACHER".equals(by) ? "от учителя" : "мой файл";
            if (size > 0) desc += "  ·  " + fileSize(size);
            TextView s = tv(desc, 10, MUTED, false);
            body.addView(s);

            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String url = Api.s(f, "file_url");
                    if (url.startsWith("/")) url = Api.BASE + url;
                    if (!url.isEmpty()) previewFile(url);
                }
            });

            if (window && !"TEACHER".equals(by)) {
                ImageView del = icon(R.drawable.ic_close, MUTED, 16);
                del.setPadding(dp(8), dp(8), dp(8), dp(8));
                del.setBackground(rippleBg(0x00000000, 20));
                del.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        confirmDeleteFile(Api.s(f, "id"), Api.s(f, "file_name"));
                    }
                });
                row.addView(del, new LinearLayout.LayoutParams(dp(36), dp(36)));
            }
            return row;
        }

        private View pendingRow(final int index) {
            final PickedPhoto p = pending.get(index);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(3));

            ImageView ic = icon(R.drawable.ic_photo, AMBER, 18);
            row.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));

            LinearLayout body = new LinearLayout(MainActivity.this);
            body.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blp.leftMargin = dp(10);
            row.addView(body, blp);

            TextView n = tv(p.name, 13, TEXT, false);
            n.setMaxLines(1);
            n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            body.addView(n);
            body.addView(tv(fileSize(p.data.length) + "  ·  будет отправлено", 10, AMBER, false));

            ImageView del = icon(R.drawable.ic_close, MUTED, 16);
            del.setPadding(dp(8), dp(8), dp(8), dp(8));
            del.setBackground(rippleBg(0x00000000, 20));
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pending.remove(index);
                    renderAttachment();
                }
            });
            row.addView(del, new LinearLayout.LayoutParams(dp(36), dp(36)));
            return row;
        }

        private View variantRow() {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, 0);

            TextView label = tv("Вариант", 12, MUTED, false);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView chip = tv(variant == null ? "не выбран" : "Вариант " + variant,
                    12, variant == null ? MUTED : ACCENT, true);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setBackground(roundBg(variant == null ? SURFACE2 : ACCENT_SOFT, 3,
                    variant == null ? STROKE : ACCENT));
            chip.setClickable(true);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { pickVariant(); }
            });
            row.addView(chip);
            return row;
        }

        private void pickVariant() {
            final String[] items = new String[11];
            items[0] = "Не выбран";
            for (int i = 1; i <= 10; i++) items[i] = "Вариант " + i;
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Вариант работы")
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            variant = w == 0 ? null : w;
                            renderAttachment();
                        }
                    })
                    .show();
        }

        void addPhotos(final List<Uri> uris) {
            if (uploading || uris == null || uris.isEmpty()) return;
            int existing = 0;
            if (studentWork != null) {
                JSONArray f = studentWork.optJSONArray("files");
                if (f != null) existing = f.length();
            }
            int room = 10 - existing - pending.size();
            if (room <= 0) {
                toast("К работе можно прикрепить не больше 10 файлов");
                return;
            }
            final List<Uri> take = new ArrayList<>(
                    uris.subList(0, Math.min(room, uris.size())));
            toast("Готовлю фото…");
            if (exec.isShutdown()) return;
            exec.execute(new Runnable() {
                @Override public void run() {
                    final List<PickedPhoto> out = new ArrayList<>();
                    int failed = 0;
                    for (Uri u : take) {
                        try {
                            out.add(readPhoto(u));
                        } catch (Exception e) {
                            failed++;
                        }
                    }
                    final int bad = failed;
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (!isShowing()) return;
                            pending.addAll(out);
                            if (bad > 0) toast("Не удалось прочитать фото: " + bad);
                            renderAttachment();
                        }
                    });
                }
            });
        }

        private void sendWork() {
            if (uploading || pending.isEmpty()) return;
            int n = pending.size();
            String what = n == 1
                    ? "Отправить это фото учителю на проверку?"
                    : "Отправить " + n + " фото учителю на проверку?";
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Отправка работы")
                    .setMessage(what + "\n\nЭто настоящее действие в МЭШ: работа появится "
                            + "у учителя. Отменить отправку нельзя.")
                    .setPositiveButton("Отправить", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            sendWorkNow();
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }

        private void sendWorkNow() {
            if (uploading || pending.isEmpty()) return;
            uploading = true;

            final ProgressDialog pd = new ProgressDialog(MainActivity.this);
            pd.setMessage("Отправляю работу…");
            pd.setCancelable(false);
            pd.show();

            final List<PickedPhoto> files = new ArrayList<>(pending);
            final Integer var = variant;
            final String workIdNow = studentWork == null
                    ? "" : Api.s(studentWork, "student_work_id");
            final Integer oldVariant = workVariant(studentWork);

            if (exec.isShutdown()) {
                pd.dismiss();
                uploading = false;
                return;
            }
            exec.execute(new Runnable() {
                @Override public void run() {
                    try {
                        Api api = Api.fromCookies();
                        String workId = workIdNow;
                        if (workId.isEmpty()) {
                            JSONObject created = api.createStudentWork(lessonId, var);
                            workId = Api.s(created, "id");
                            if (workId.isEmpty()) {
                                throw new Api.ApiException("МЭШ не вернул id работы");
                            }
                        } else if (var != null
                                && (oldVariant == null || !oldVariant.equals(var))) {
                            try {
                                api.updateVariant(workId, var);
                            } catch (Exception ignored) {
                            }
                        }

                        int order = nextOrder(studentWork);
                        JSONArray regs = new JSONArray();
                        int failed = 0;
                        for (PickedPhoto p : files) {
                            try {
                                JSONObject acc = api.storageAccess(workId);
                                String link = Api.s(acc, "file_link");
                                String tok = Api.s(acc, "token");
                                String uuid = Api.s(acc, "file_uuid");
                                if (link.isEmpty() || tok.isEmpty() || uuid.isEmpty()) {
                                    throw new Api.ApiException("нет доступа к хранилищу");
                                }
                                api.uploadFile(link, tok, p.name, "image/jpeg", p.data);
                                JSONObject f = new JSONObject();
                                Api.put(f, "file_uuid", uuid);
                                Api.put(f, "file_name", p.name);
                                Api.put(f, "order_number", order++);
                                Api.put(f, "size", p.data.length);
                                Api.put(f, "extension", "jpg");
                                regs.put(f);
                            } catch (Exception e) {
                                failed++;
                            }
                        }

                        if (regs.length() > 0) api.upsertFiles(workId, regs);

                        final int bad = failed;
                        ui.post(new Runnable() {
                            @Override public void run() {
                                pd.dismiss();
                                uploading = false;
                                if (bad == files.size()) {
                                    toast("Не удалось отправить работу");
                                } else {
                                    pending.clear();
                                    toast(bad == 0
                                            ? "Работа отправлена на проверку"
                                            : "Отправлено, но " + bad + " фото не загрузились");
                                }
                                loadDetails();
                                dayData = null;
                                scheduleDayLoad(true);
                            }
                        });
                    } catch (final Exception e) {
                        ui.post(new Runnable() {
                            @Override public void run() {
                                pd.dismiss();
                                uploading = false;
                                errorDialog(e);
                            }
                        });
                    }
                }
            });
        }

        private int nextOrder(JSONObject work) {
            int max = 0;
            if (work != null) {
                JSONArray files = work.optJSONArray("files");
                if (files != null) {
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject f = files.optJSONObject(i);
                        if (f != null) max = Math.max(max, f.optInt("ordinal", 0));
                    }
                }
            }
            return max + 1;
        }

        private void confirmDeleteFile(final String fileId, String name) {
            if (fileId.isEmpty()) return;
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Удалить файл?")
                    .setMessage(name == null || name.isEmpty() ? "Файл" : name)
                    .setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            deleteFile(fileId);
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }

        private void deleteFile(final String fileId) {
            final String workId = studentWork == null
                    ? "" : Api.s(studentWork, "student_work_id");
            if (workId.isEmpty()) return;
            final ProgressDialog pd = new ProgressDialog(MainActivity.this);
            pd.setMessage("Удаляю…");
            pd.setCancelable(false);
            pd.show();
            if (exec.isShutdown()) {
                pd.dismiss();
                return;
            }
            exec.execute(new Runnable() {
                @Override public void run() {
                    try {
                        JSONArray files = new JSONArray();
                        JSONObject f = new JSONObject();
                        Api.put(f, "file_uuid", fileId);
                        Api.put(f, "deleted", true);
                        files.put(f);
                        Api.fromCookies().upsertFiles(workId, files);
                        ui.post(new Runnable() {
                            @Override public void run() {
                                pd.dismiss();
                                toast("Файл удалён");
                                loadDetails();
                            }
                        });
                    } catch (final Exception e) {
                        ui.post(new Runnable() {
                            @Override public void run() {
                                pd.dismiss();
                                errorDialog(e);
                            }
                        });
                    }
                }
            });
        }

        private boolean isWorkWindowOpen() {
            String date = Api.s(details, "date");
            if (!date.isEmpty() && !date.equals(today())) return false;
            String begin = Api.s(details, "begin_time");
            String end = Api.s(details, "end_time");
            if (begin.isEmpty()) begin = Api.s(lesson, "begin");
            if (end.isEmpty()) end = Api.s(lesson, "end");
            if (begin.isEmpty() || end.isEmpty()) return false;
            int now = nowMinutes();
            return now >= toMinutes(begin) && now <= toMinutes(end) + 5;
        }
    }

    private int nowMinutes() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private int toMinutes(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private String fileSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f КБ", bytes / 1024.0);
        return String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    private View gradientButton(String label, int iconRes, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(rippleBg(ACCENT, 3));
        row.setClickable(true);

        if (iconRes != 0) {
            ImageView ic = icon(iconRes, WHITE, 16);
            row.addView(ic, new LinearLayout.LayoutParams(dp(16), dp(16)));
        }

        TextView t = tvSans(label, 14, WHITE, true);
        t.setGravity(Gravity.CENTER);
        if (iconRes != 0) t.setPadding(dp(8), 0, 0, 0);
        row.addView(t);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return row;
    }

    private View smallButton(String label, int iconRes, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1F2B2A26),
                roundBg(SURFACE2, 3, STROKE), null));
        row.setClickable(true);

        if (iconRes != 0) {
            ImageView ic = icon(iconRes, TEXT, 16);
            row.addView(ic, new LinearLayout.LayoutParams(dp(18), dp(18)));
        }

        TextView t = tvSans(label, 13, TEXT, true);
        t.setGravity(Gravity.CENTER);
        if (iconRes != 0) t.setPadding(dp(7), 0, 0, 0);
        row.addView(t);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return row;
    }

    private PickedPhoto readPhoto(Uri uri) throws Exception {
        String name = null;
        try {
            android.database.Cursor c = getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (c != null) {
                if (c.moveToFirst()) name = c.getString(0);
                c.close();
            }
        } catch (Exception ignored) {
        }

        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("не открыл файл");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        byte[] raw = bos.toByteArray();
        if (raw.length == 0) throw new Exception("пустой файл");

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        int sample = 1;
        int maxSide = Math.max(o.outWidth, o.outHeight);
        while (maxSide / sample > 2400) sample *= 2;

        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length, d);
        if (bm == null) throw new Exception("это не картинка");

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(raw));
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) {
        }

        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.postScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.postRotate(90); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.postRotate(270); m.postScale(-1, 1); break;
        }
        if (!m.isIdentity()) {
            Bitmap rotated = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(),
                    bm.getHeight(), m, true);
            if (rotated != bm) bm.recycle();
            bm = rotated;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 86, out);
        bm.recycle();
        byte[] data = out.toByteArray();

        String base = name == null ? "" : name;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        if (base.isEmpty()) base = "photo";
        base = base.replaceAll("[^A-Za-z0-9_-]", "_");
        String outName = base + "_" + System.currentTimeMillis() + ".jpg";
        return new PickedPhoto(outName, data);
    }

    private void pickPhotos() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_PICK_PHOTOS);
        } catch (Exception e) {
            toast("Нет приложения для выбора фото");
        }
    }

    private void takePhoto() {
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_CAMERA_PERM);
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "openschool_" + System.currentTimeMillis() + ".jpg");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenSchool");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                toast("Не могу создать файл для фото");
                return;
            }
            pendingCameraUri = uri;
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            toast("Камера недоступна");
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_CAMERA_PERM) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            } else {
                toast("Без разрешения камера не сможет сохранить фото");
            }
        }
    }

    private void downloadAndOpen(final String url) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Скачиваю файл…");
        pd.setCancelable(false);
        pd.show();
        if (exec.isShutdown()) {
            pd.dismiss();
            return;
        }
        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    byte[] data = Api.fromCookies().download(url);
                    String name = url.substring(url.lastIndexOf('/') + 1);
                    int q = name.indexOf('?');
                    if (q >= 0) name = name.substring(0, q);
                    try {
                        name = java.net.URLDecoder.decode(name, "UTF-8");
                    } catch (Exception ignored) {
                    }
                    if (name.isEmpty()) name = "file.pdf";
                    name = name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
                    final File f = new File(getCacheDir(), name);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    out.write(data);
                    out.close();
                    ui.post(new Runnable() {
                        @Override public void run() {
                            pd.dismiss();
                            openDownloaded(f);
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            pd.dismiss();
                            errorDialog(e);
                        }
                    });
                }
            }
        });
    }

    private void openDownloaded(File f) {
        if (f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            showPdf(f);
            return;
        }
        Uri uri = new Uri.Builder().scheme("content")
                .authority(SchFilesProvider.AUTHORITY)
                .appendPath(f.getName()).build();
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mimeOf(f.getName()));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (getPackageManager().queryIntentActivities(i, 0).isEmpty()) {
            toast("Не нашлось приложения для этого файла");
            return;
        }
        try {
            startActivity(Intent.createChooser(i, "Открыть файл"));
        } catch (Exception e) {
            toast("Не нашлось приложения для этого файла");
        }
    }

    private void showPdf(final File file) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(8), dp(10));
        TextView title = tv(file.getName(), 15, TEXT, true);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView close = icon(R.drawable.ic_close, MUTED, 20);
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(head);

        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        root.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        final LinearLayout pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.VERTICAL);
        pages.setPadding(dp(12), dp(12), dp(12), dp(20));
        scroll.addView(pages);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(BG));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        d.show();

        final int maxPages = 10;
        final int targetW = getResources().getDisplayMetrics().widthPixels - dp(24);
        if (exec.isShutdown()) return;
        exec.execute(new Runnable() {
            @Override public void run() {
                ParcelFileDescriptor pfd = null;
                PdfRenderer renderer = null;
                final List<Bitmap> bitmaps = new ArrayList<>();
                int total = 0;
                Exception err = null;
                try {
                    pfd = ParcelFileDescriptor.open(file,
                            ParcelFileDescriptor.MODE_READ_ONLY);
                    renderer = new PdfRenderer(pfd);
                    total = renderer.getPageCount();
                    int n = Math.min(total, maxPages);
                    for (int i = 0; i < n; i++) {
                        PdfRenderer.Page page = renderer.openPage(i);
                        float scale = targetW / (float) page.getWidth();
                        int pw = targetW;
                        int ph = Math.max(1, Math.round(page.getHeight() * scale));
                        Bitmap bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(0xFFFFFCF4);
                        page.render(bmp, null, null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        page.close();
                        bitmaps.add(bmp);
                    }
                } catch (Exception e) {
                    err = e;
                } finally {
                    try { if (renderer != null) renderer.close(); } catch (Exception ignored) { }
                    try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
                }
                final Exception ferr = err;
                final int ftotal = total;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!d.isShowing()) return;
                        if (ferr != null || bitmaps.isEmpty()) {
                            TextView t = tv("Не удалось открыть PDF"
                                    + (ferr == null ? "" : ": " + ferr.getMessage()),
                                    13, MUTED, false);
                            t.setPadding(dp(8), dp(10), dp(8), dp(10));
                            pages.addView(t);
                            return;
                        }
                        for (int i = 0; i < bitmaps.size(); i++) {
                            ImageView iv = new ImageView(MainActivity.this);
                            iv.setImageBitmap(bitmaps.get(i));
                            iv.setAdjustViewBounds(true);
                            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            iv.setBackground(roundBg(SURFACE, 2, STROKE));
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                            lp.bottomMargin = dp(10);
                            pages.addView(iv, lp);
                        }
                        if (ftotal > bitmaps.size()) {
                            TextView t = tv("Показаны первые " + bitmaps.size()
                                    + " из " + ftotal + " страниц", 11, MUTED, false);
                            t.setGravity(Gravity.CENTER);
                            pages.addView(t);
                        }
                    }
                });
            }
        });
    }

    private String mimeOf(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (n.endsWith(".doc")) return "application/msword";
        if (n.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (n.endsWith(".xls")) return "application/vnd.ms-excel";
        if (n.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (n.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        return "*/*";
    }

    private void previewFile(final String url) {
        String low = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (low.contains(".pdf") || low.contains(".doc") || low.contains(".xls")
                || low.contains(".ppt")) {
            downloadAndOpen(url);
            return;
        }
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(8), dp(10));
        head.addView(tv("Файл работы", 16, TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView close = icon(R.drawable.ic_close, MUTED, 20);
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(head);

        final WebView web = new WebView(this);
        CookieManager.getInstance().setAcceptCookie(true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl(url);
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(BG));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.92f));
        }
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface di) {
                try {
                    web.stopLoading();
                    web.destroy();
                } catch (Exception ignored) {
                }
            }
        });
        d.show();
    }

    // ------------------------------------------------------------- ЦДЗ

    /** Переносит cookies сессии в CookieManager перед открытием задания. */
    private void syncWebViewCookies() {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        for (Map.Entry<String, String> e : Cookies.map().entrySet()) {
            String v = e.getValue();
            if (v == null || v.isEmpty()) continue;
            cm.setCookie("https://school.mos.ru/",
                    e.getKey() + "=" + v + "; Path=/; Secure");
        }
        cm.flush();
    }

    private void openCdz(final String entry, final String uuid, final String title) {
        if (entry == null || entry.isEmpty() || uuid == null || uuid.isEmpty()) {
            toast("У задания нет ссылки для запуска");
            return;
        }
        syncWebViewCookies();

        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Открываю задание…");
        pd.setCancelable(false);
        pd.show();

        if (exec.isShutdown()) {
            pd.dismiss();
            return;
        }
        exec.execute(new Runnable() {
            @Override public void run() {
                String url = null;
                Exception err = null;
                try {
                    url = Api.fromCookies().homeworkLaunchUrl(entry, uuid);
                } catch (Exception e) {
                    err = e;
                }
                final String furl = url;
                final Exception ferr = err;
                ui.post(new Runnable() {
                    @Override public void run() {
                        try { pd.dismiss(); } catch (Exception ignored) {
                        }
                        if (furl == null) {
                            toast("Не удалось открыть задание: "
                                    + (ferr == null ? "?" : ferr.getMessage()));
                            return;
                        }
                        showCdzWeb(furl, title);
                    }
                });
            }
        });
    }

    private String cdzTheme() {
        if (cdzThemeJs != null) return cdzThemeJs;
        try (InputStream in = getAssets().open("cdz-paper.js")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bos.write(b, 0, n);
            cdzThemeJs = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            cdzThemeJs = "";
        }
        return cdzThemeJs;
    }

    private void showCdzWeb(String url, String title) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(8), dp(10));
        TextView t = tv(title == null || title.isEmpty() ? "Задание" : title,
                15, TEXT, true);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(t, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView close = icon(R.drawable.ic_close, MUTED, 20);
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        close.setBackground(rippleBg(0x00000000, 20));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(head);

        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        root.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        final ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        final WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setUserAgentString(Api.UA);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> cb,
                    FileChooserParams params) {
                if (webFileCallback != null) webFileCallback.onReceiveValue(null);
                webFileCallback = cb;
                String type = "*/*";
                if (params != null) {
                    String[] accept = params.getAcceptTypes();
                    if (accept != null && accept.length > 0
                            && accept[0] != null && accept[0].contains("/")) {
                        type = accept[0];
                    }
                }
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(type);
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(
                            Intent.createChooser(i, "Выбрать файл"), REQ_WEB_FILE);
                    return true;
                } catch (Exception e) {
                    webFileCallback = null;
                    return false;
                }
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String u) {
                super.onPageFinished(view, u);
                String js = cdzTheme();
                if (!js.isEmpty()) view.evaluateJavascript(js, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String u) {
                if (u == null) return false;
                if (u.startsWith("http://") || u.startsWith("https://")) {
                    return false;   // всё внутри WebView
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        d.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface di, int code, KeyEvent event) {
                if (code == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    if (web.canGoBack()) {
                        web.goBack();
                        return true;
                    }
                }
                return false;
            }
        });

        web.loadUrl(url);
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(BG));
            w.setDimAmount(0.6f);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.97f));
            w.setGravity(Gravity.BOTTOM);
        }
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface di) {
                try {
                    web.stopLoading();
                    web.loadUrl("about:blank");
                    web.destroy();
                } catch (Exception ignored) {
                }
            }
        });
        d.show();
    }

    private void showHomeworkDetails(final JSONObject h) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(6));

        String subject = Api.s(h, "subject");
        box.addView(tv(subject.isEmpty() ? "Задание" : subject, 20, TEXT, true));

        StringBuilder sub = new StringBuilder();
        String lt = Api.s(h, "lesson_time");
        String ls = Api.s(h, "lesson_start");
        if (!lt.isEmpty()) {
            sub.append("урок ");
            if (!ls.isEmpty()) sub.append(ls).append("–");
            else sub.append("до ");
            sub.append(lt);
        }
        if (Api.b(h, "done")) {
            if (sub.length() > 0) sub.append("  ·  ");
            sub.append("выполнено");
        }
        if (sub.length() > 0) {
            TextView st = tv(sub.toString(), 12, MUTED, false);
            st.setPadding(0, dp(4), 0, dp(10));
            box.addView(st);
        }

        String text = Api.s(h, "text").trim();
        box.addView(detailText(text.isEmpty() ? "(см. материалы)" : text));

        JSONArray files = h.optJSONArray("files");
        if (files != null && files.length() > 0) {
            box.addView(detailLabel("Файлы"));
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i);
                if (f == null) continue;
                String title = Api.s(f, "title");
                if (title.isEmpty()) title = Api.s(f, "kind");
                if (title.isEmpty()) title = "файл";
                final String furl = Api.s(f, "url");
                TextView ft = tv("•  " + title, 13, ACCENT, false);
                ft.setPadding(0, dp(2), 0, dp(2));
                if (!furl.isEmpty()) {
                    ft.setClickable(true);
                    ft.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { previewFile(furl); }
                    });
                }
                box.addView(ft);
            }
        }

        final String entry = Api.s(h, "entry");
        JSONArray mats = h.optJSONArray("materials_list");
        if (mats != null && mats.length() > 0) {
            box.addView(detailLabel("Материалы"));
            for (int i = 0; i < mats.length(); i++) {
                final JSONObject m = mats.optJSONObject(i);
                if (m == null) continue;
                final String title = Api.s(m, "title").isEmpty()
                        ? "материал" : Api.s(m, "title");
                String action = Api.s(m, "action");
                final String url = Api.s(m, "url");
                final String uuid = Api.s(m, "uuid");
                if (isDigital(m)) {
                    View b = gradientButton("Пройти  ·  " + title, R.drawable.ic_play,
                            new Runnable() {
                                @Override public void run() {
                                    openCdz(entry, uuid, title);
                                }
                            });
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    blp.topMargin = dp(6);
                    box.addView(b, blp);
                } else if (!url.isEmpty()) {
                    View b = smallButton("Открыть  ·  " + title, 0, new Runnable() {
                        @Override public void run() { previewFile(url); }
                    });
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    blp.topMargin = dp(6);
                    box.addView(b, blp);
                } else {
                    TextView t = tv("•  " + title
                            + (action.isEmpty() ? "" : "  ·  " + action), 14, TEXT, false);
                    t.setPadding(0, dp(5), 0, dp(5));
                    box.addView(t);
                }
            }
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this)
                .setView(sv)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private TextView detailLabel(String s) {
        TextView t = tv(s.toUpperCase(Locale.ROOT), 10, MUTED, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private View detailText(String s) {
        TextView t = tv(s, 14, TEXT, false);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    // ------------------------------------------------------------- меню/диалоги

    private void about() {
        File dir = getExternalFilesDir(null);
        new AlertDialog.Builder(this)
                .setTitle("OpenSchool")
                .setMessage("OpenSchool. Дневник МЭШ. Версия 1.5\n\n"
                        + "Данные берутся с school.mos.ru по вашей сессии. "
                        + "Cookies хранятся только внутри приложения.\n\n"
                        + "Вход: «Ещё → Войти в МЭШ» или кнопка на экране входа.\n"
                        + "Импорт: ~/.cache/sch/cookies.json с ПК.\n"
                        + "Папка для файла:\n"
                        + (dir == null ? "Android/data/ru.sch.diary/files"
                                       : dir.getAbsolutePath()) + "/cookies.json")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void clearCache() {
        SharedPreferences.Editor ed = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("day:") || key.startsWith("day2:") || key.startsWith("day3:")
                    || key.startsWith("marks:") || key.startsWith("marks2:")
                    || key.startsWith("hw3:") || key.startsWith("rating:")
                    || key.startsWith("rankclass:")) {
                ed.remove(key);
            }
        }
        ed.apply();
        dayData = null;
        marksData = null;
        marksFrom = null;
        marksTo = null;
        toast("Кэш дней очищен");
        renderTab();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Выйти из аккаунта?")
                .setMessage("Cookies и кэш будут удалены из приложения. "
                        + "Для доступа придётся войти заново.")
                .setPositiveButton("Выйти", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        logout();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void logout() {
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();
        prefs.edit().clear().apply();
        marksDays = 14;
        hwDays = 14;
        currentDate = today();
        dayData = null;
        dayError = null;
        dayAuth = false;
        marksData = null;
        marksError = null;
        marksAuth = false;
        hwData = null;
        hwError = null;
        hwAuth = false;
        ratingData = null;
        overallRank = null;
        overallTried = false;
        profileData = null;
        profileError = null;
        studentName = "";
        studentClass = "";
        studentSchool = "";
        studentId = "";
        toast("Вы вышли из аккаунта");
        selectTab(0);
        scheduleDayLoad(true);
    }

    // ------------------------------------------------------------- вход

    private void openLogin() {
        LoginDialog d = new LoginDialog();
        d.show();
    }

    private final class LoginDialog extends Dialog {
        private WebView web;
        private boolean verifying;

        LoginDialog() {
            super(MainActivity.this);
        }

        @Override
        protected void onCreate(Bundle b) {
            super.onCreate(b);
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(BG);
            root.setFitsSystemWindows(true);

            LinearLayout head = new LinearLayout(MainActivity.this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(16), dp(10), dp(8), dp(10));

            TextView title = tv("Вход в МЭШ", 17, TEXT, true);
            head.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView check = tv("Проверить", 13, ACCENT, true);
            check.setPadding(dp(12), dp(8), dp(12), dp(8));
            check.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { verify(); }
            });
            head.addView(check);

            ImageView close = icon(R.drawable.ic_close, MUTED, 20);
            close.setPadding(dp(10), dp(10), dp(10), dp(10));
            close.setBackground(rippleBg(0x00000000, 20));
            close.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dismiss(); }
            });
            head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
            root.addView(head);

            View line = new View(MainActivity.this);
            line.setBackgroundColor(DIVIDER);
            root.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

            web = new WebView(MainActivity.this);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setUserAgentString(Api.UA);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(web, true);

            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (verifying) return;
                    if (Api.usable(Cookies.token())) {
                        view.postDelayed(new Runnable() {
                            @Override public void run() { verify(); }
                        }, 1200);
                    }
                }
            });
            web.setWebChromeClient(new WebChromeClient());
            web.loadUrl("https://school.mos.ru/");
            root.addView(web, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            setContentView(root);
            Window w = getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(BG));
                w.setDimAmount(0f);
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }

        private void verify() {
            if (verifying) return;
            verifying = true;
            toast("Проверяю вход…");
            if (exec.isShutdown()) return;
            exec.execute(new Runnable() {
                @Override public void run() {
                    try {
                        Api api = Api.fromCookies();
                        api.profile();
                        CookieManager.getInstance().flush();
                        ui.post(new Runnable() {
                            @Override public void run() {
                                verifying = false;
                                dismiss();
                                toast("Вход выполнен");
                                dayData = null;
                                dayAuth = false;
                                marksData = null;
                                profileData = null;
                                profileError = null;
                                selectTab(0);
                                scheduleDayLoad(true);
                                fetchProfile(true);
                            }
                        });
                    } catch (final Exception e) {
                        ui.post(new Runnable() {
                            @Override public void run() {
                                verifying = false;
                                toast(e instanceof Api.AuthException
                                        ? "Пока не вошли — завершите вход"
                                        : "Не получилось проверить: " + e.getMessage());
                            }
                        });
                    }
                }
            });
        }

        @Override
        public void dismiss() {
            WebView w = web;
            web = null;
            super.dismiss();
            if (w != null) {
                try {
                    w.stopLoading();
                    w.loadUrl("about:blank");
                    w.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------- cookies

    private void bootstrapCookies() {
        if (Api.usable(Cookies.token())) return;
        File dir = getExternalFilesDir(null);
        if (dir == null) return;
        File f = new File(dir, "cookies.json");
        if (!f.isFile()) return;
        try {
            int n = Cookies.importJson(readText(f));
            if (n > 0) toast("Импортировано cookies: " + n);
        } catch (Exception e) {
            toast("cookies.json не прочитан: " + e.getMessage());
        }
    }

    private void importCookies() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast("Нет файлового менеджера");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (req == REQ_PICK_PHOTOS) {
            if (res == RESULT_OK && data != null && lessonDialog != null) {
                List<Uri> uris = new ArrayList<>();
                ClipData clip = data.getClipData();
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        Uri u = clip.getItemAt(i).getUri();
                        if (u != null) uris.add(u);
                    }
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }
                if (!uris.isEmpty()) lessonDialog.addPhotos(uris);
            }
            return;
        }

        if (req == REQ_CAMERA) {
            Uri uri = pendingCameraUri;
            pendingCameraUri = null;
            if (uri == null) return;
            if (res == RESULT_OK) {
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, v, null, null);
                    } catch (Exception ignored) {
                    }
                }
                if (lessonDialog != null) lessonDialog.addPhotos(Collections.singletonList(uri));
            } else {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            return;
        }

        if (req == REQ_WEB_FILE) {
            if (webFileCallback == null) return;
            Uri[] out = null;
            if (res == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null) {
                    out = new Uri[clip.getItemCount()];
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        out[i] = clip.getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    out = new Uri[]{data.getData()};
                }
            }
            webFileCallback.onReceiveValue(out);
            webFileCallback = null;
            return;
        }

        if (req == REQ_IMPORT) {
            if (res != RESULT_OK || data == null) return;
            importCookiesFrom(data.getData());
        }
    }

    private void importCookiesFrom(Uri uri) {
        if (uri == null) return;
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (in != null && (n = in.read(buf)) > 0) bos.write(buf, 0, n);
            if (in != null) in.close();
            int count = Cookies.importJson(
                    new String(bos.toByteArray(), StandardCharsets.UTF_8));
            if (count == 0) {
                toast("В файле не нашлось cookies");
                return;
            }
            toast("Импортировано cookies: " + count);
            dayData = null;
            dayAuth = false;
            marksData = null;
            profileData = null;
            profileError = null;
            selectTab(0);
            scheduleDayLoad(true);
            fetchProfile(true);
        } catch (final Exception e) {
            errorDialog(e);
        }
    }

    // ------------------------------------------------------------- кэш

    private JSONObject cacheGet(String key) {
        String raw = prefs.getString(key, null);
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private void cachePut(String key, String raw) {
        prefs.edit().putString(key, raw).apply();
    }

    // ------------------------------------------------------------- даты

    private SimpleDateFormat isoFormat() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        return f;
    }

    private String today() {
        return isoFormat().format(new Date());
    }

    private boolean looksLikeDate(String s) {
        if (s == null || s.length() != 10) return false;
        try {
            isoFormat().parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String shiftDate(String iso, int days) {
        try {
            Date d = isoFormat().parse(iso);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            c.add(Calendar.DAY_OF_MONTH, days);
            return isoFormat().format(c.getTime());
        } catch (Exception e) {
            return today();
        }
    }

    private int dayOfMonth(String iso) {
        try {
            return Integer.parseInt(iso.substring(8, 10));
        } catch (Exception e) {
            return 1;
        }
    }

    private int dow(String iso) {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(isoFormat().parse(iso));
            return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Пн=0 … Вс=6
        } catch (Exception e) {
            return 0;
        }
    }

    private String mondayOf(String iso) {
        return shiftDate(iso, -dow(iso));
    }

    private String monthLabel() {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(isoFormat().parse(currentDate));
            return MONTHS_NOM[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
        } catch (Exception e) {
            return "";
        }
    }

    private String prettyTitle(String iso) {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(isoFormat().parse(iso));
            String base = c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)];
            String today = today();
            if (iso.equals(today)) return "Сегодня, " + base;
            if (iso.equals(shiftDate(today, 1))) return "Завтра, " + base;
            if (iso.equals(shiftDate(today, -1))) return "Вчера, " + base;
            return base;
        } catch (Exception e) {
            return iso;
        }
    }

    private String prettyDay(String iso) {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(isoFormat().parse(iso));
            return c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)]
                    + " · " + WEEKDAYS_FULL[c.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) {
            return iso;
        }
    }

    private void pickDate() {
        final Calendar c = Calendar.getInstance();
        try {
            Date d = isoFormat().parse(currentDate);
            if (d != null) c.setTime(d);
        } catch (Exception ignored) {
        }
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override public void onDateSet(DatePicker view, int y, int m, int d) {
                selectDate(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d));
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private int minutesUntil(String now, String begin) {
        try {
            String[] a = now.split(":");
            String[] b = begin.split(":");
            int n = Integer.parseInt(a[0]) * 60 + Integer.parseInt(a[1]);
            int t = Integer.parseInt(b[0]) * 60 + Integer.parseInt(b[1]);
            return Math.max(1, t - n);
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------- утилиты

    private String subjectOf(JSONObject l) {
        String s = Api.s(l, "subject");
        if (s.isEmpty()) s = Api.s(l, "group");
        if (s.isEmpty()) s = "Занятие";
        return s;
    }

    private int subjectColor(String subject) {
        int h = subject == null ? 0 : (subject.hashCode() & 0x7fffffff);
        return SUBJECT_COLORS[h % SUBJECT_COLORS.length];
    }

    private int countOf(JSONObject day, String key) {
        JSONArray a = day.optJSONArray(key);
        return a == null ? 0 : a.length();
    }

    private String initials() {
        String name = studentName;
        if (name == null || name.trim().isEmpty()) return "Д";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) sb.append(parts[i].charAt(0));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    private static String joinNonEmpty(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    private static String plural(int n, String one, String few, String many) {
        int m10 = n % 10;
        int m100 = n % 100;
        if (m10 == 1 && m100 != 11) return one;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
        return many;
    }

    private static String readText(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void errorDialog(Exception e) {
        new AlertDialog.Builder(this)
                .setTitle("Ошибка")
                .setMessage(String.valueOf(e == null ? "?" : e.getMessage()))
                .setPositiveButton("Ок", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------- виджеты

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s == null ? "" : s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bold ? fontSerifBold : fontMedium);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    private TextView tvSerif(String s, float sp, int color, boolean bold) {
        TextView t = tv(s, sp, color, bold);
        t.setTypeface(bold ? fontSerifBold : fontSerif);
        return t;
    }

    private TextView tvHand(String s, float sp, int color) {
        TextView t = tv(s, sp, color, false);
        t.setTypeface(fontHand);
        return t;
    }

    private TextView tvSans(String s, float sp, int color, boolean bold) {
        TextView t = tv(s, sp, color, bold);
        t.setTypeface(bold ? fontBold : fontMedium);
        return t;
    }

    private ImageView icon(int res, int color, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(color);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        iv.setLayoutParams(lp);
        return iv;
    }

    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(roundBg(color, 2, STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    private View sectionHeader(String title, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(14), dp(4), dp(8));

        TextView t = tv(title, 10, MUTED, true);
        t.setLetterSpacing(0.1f);
        row.addView(t);

        if (count > 0) {
            TextView c = tv(String.valueOf(count), 10, MUTED, false);
            c.setPadding(dp(6), 0, 0, 0);
            row.addView(c);
        }
        return row;
    }

    private TextView tag(String text, int color, int bg) {
        TextView t = tv(text, 10, color, true);
        t.setPadding(dp(7), dp(3), dp(7), dp(3));
        t.setBackground(roundBg(bg, 3, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(5);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView markChip(String value) {
        int fg = markInk(value);

        TextView t = tvHand(value, 22, fg);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(30));
        t.setPadding(dp(5), 0, dp(5), dp(1));
        t.setBackground(roundBg(0x00000000, 2, STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        lp.leftMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private View countTag(String value, int n, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(3), dp(8), dp(3));
        row.setBackground(roundBg(SURFACE2, 3, STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        row.setLayoutParams(lp);

        TextView v = tv(value, 12, color, true);
        row.addView(v);
        TextView c = tv(String.valueOf(n), 11, MUTED, false);
        c.setPadding(dp(4), 0, 0, 0);
        row.addView(c);
        return row;
    }

    private TextView primaryButton(String label, final Runnable action) {
        TextView t = tv(label, 14, WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        t.setBackground(roundBg(ACCENT, 3, 0));
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return t;
    }

    private TextView secondaryButton(String label, final Runnable action) {
        TextView t = tv(label, 14, ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(11), dp(16), dp(11));
        t.setBackground(roundBg(0x00000000, 3, ACCENT));
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return t;
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(h)));
        return v;
    }

    private View bar(int widthDp, int heightDp, int color) {
        View v = new View(this);
        v.setBackground(roundBg(color, 6, 0));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        return v;
    }

    private GradientDrawable gradientBg(int c1, int c2, int radiusDp, int strokeColor) {
        return roundBg(c1, radiusDp, strokeColor);
    }

    private GradientDrawable ovalBg(int color, int widthDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x00000000);
        g.setStroke(dp(widthDp), color);
        return g;
    }

    private GradientDrawable roundBg(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private Drawable rippleBg(int color, int radiusDp) {
        GradientDrawable content = roundBg(color, radiusDp, 0);
        return new RippleDrawable(ColorStateList.valueOf(0x26FFFFFF), content, null);
    }
}
