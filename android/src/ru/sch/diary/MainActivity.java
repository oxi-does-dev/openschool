package ru.sch.diary;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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

    // Палитра — поля, а не константы: бумага бывает и светлой, и вечерней.
    private int BG, SURFACE, SURFACE2, TEXT, MUTED;
    private int ACCENT, ACCENT2, ACCENT_SOFT;
    private int GREEN, AMBER, RED, DIVIDER, STROKE, RULE;
    private int CURRENT, MARK4, ERR_BG, ERR_STROKE;
    private int TEAL, VIOLET, CDZ_BG;   // CDZ_BG — фон окна задания
    private int ON_ACCENT;               // текст на акцентной плашке
    private int[] SUBJECT_COLORS;
    private boolean darkTheme;

    private static final int[] SUBJECT_COLORS_LIGHT = {
            0xFF34577D, 0xFF2F6B4F, 0xFF9A6A22, 0xFF6F4A75,
            0xFF2F6B6B, 0xFF9A4A5E, 0xFF9A5A32
    };

    private static final int[] SUBJECT_COLORS_DARK = {
            0xFF8FB0D4, 0xFF85B394, 0xFFD6A960, 0xFFB79BC4,
            0xFF7FB8B8, 0xFFD0919F, 0xFFC99A72
    };

    /**
     * Палитры тёмных тем. Названия совпадают со значениями в настройках.
     * Все они тёмные, светлая — только «бумага».
     */
    private void applyPalette(String theme) {
        darkTheme = !"light".equals(theme);
        // В окне ЦДЗ тёмная тема должна совпадать с темой приложения.
        CDZ_BG = 0xFF171613;
        if ("black".equals(theme)) {
            BG = 0xFF000000;
            SURFACE = 0xFF0A0A0A;
            SURFACE2 = 0xFF141414;
            TEXT = 0xFFE6E6E6;
            MUTED = 0xFF9A9A9A;
            ACCENT = 0xFF8AB4F8;
            ACCENT2 = 0xFFAECBFA;
            ACCENT_SOFT = 0x2A8AB4F8;
            GREEN = 0xFF7FC58F;
            AMBER = 0xFFE0B45C;
            RED = 0xFFE58C84;
            TEAL = 0xFF79C0C0;
            VIOLET = 0xFFBE9FD0;
            DIVIDER = 0xFF1F1F1F;
            STROKE = 0xFF2A2A2A;
            RULE = 0xFF333333;
            CURRENT = 0xFF101010;
            MARK4 = 0xFFA9D18E;
            ERR_BG = 0xFF1C0F0D;
            ERR_STROKE = 0xFF47231F;
            ON_ACCENT = 0xFF000000;
            SUBJECT_COLORS = new int[]{ACCENT, GREEN, AMBER, VIOLET,
                    TEAL, RED, 0xFFC99A72};
            CDZ_BG = BG;
        } else if ("nord".equals(theme)) {
            BG = 0xFF2E3440;
            SURFACE = 0xFF3B4252;
            SURFACE2 = 0xFF434C5E;
            TEXT = 0xFFECEFF4;
            MUTED = 0xFFAEB8C8;
            ACCENT = 0xFF88C0D0;
            ACCENT2 = 0xFF81A1C1;
            ACCENT_SOFT = 0x2A88C0D0;
            GREEN = 0xFFA3BE8C;
            AMBER = 0xFFEBCB8B;
            RED = 0xFFBF616A;
            TEAL = 0xFF8FBCBB;
            VIOLET = 0xFFB48EAD;
            DIVIDER = 0xFF434C5E;
            STROKE = 0xFF4C566A;
            RULE = 0xFF4C566A;
            CURRENT = 0xFF3B4252;
            MARK4 = 0xFFA3BE8C;
            ERR_BG = 0xFF3A2A2D;
            ERR_STROKE = 0xFF6E4A50;
            ON_ACCENT = 0xFF2E3440;
            SUBJECT_COLORS = new int[]{ACCENT, GREEN, AMBER, VIOLET,
                    TEAL, 0xFFD08770, 0xFFD9A38A};
            CDZ_BG = BG;
        } else if ("catppuccin".equals(theme)) {
            BG = 0xFF1E1E2E;
            SURFACE = 0xFF181825;
            SURFACE2 = 0xFF313244;
            TEXT = 0xFFCDD6F4;
            MUTED = 0xFFA6ADC8;
            ACCENT = 0xFF89B4FA;
            ACCENT2 = 0xFFB4BEFE;
            ACCENT_SOFT = 0x2A89B4FA;
            GREEN = 0xFFA6E3A1;
            AMBER = 0xFFF9E2AF;
            RED = 0xFFF38BA8;
            TEAL = 0xFF94E2D5;
            VIOLET = 0xFFCBA6F7;
            DIVIDER = 0xFF313244;
            STROKE = 0xFF45475A;
            RULE = 0xFF585B70;
            CURRENT = 0xFF313244;
            MARK4 = 0xFFA6E3A1;
            ERR_BG = 0xFF30222A;
            ERR_STROKE = 0xFF513245;
            ON_ACCENT = 0xFF11111B;
            SUBJECT_COLORS = new int[]{ACCENT, GREEN, AMBER, VIOLET,
                    TEAL, 0xFFF5C2E7, 0xFFFAB387};
            CDZ_BG = BG;
        } else if ("gruvbox".equals(theme)) {
            BG = 0xFF282828;
            SURFACE = 0xFF32302F;
            SURFACE2 = 0xFF3C3836;
            TEXT = 0xFFEBDBB2;
            MUTED = 0xFFA89984;
            ACCENT = 0xFF83A598;
            ACCENT2 = 0xFF8EC07C;
            ACCENT_SOFT = 0x2A83A598;
            GREEN = 0xFFB8BB26;
            AMBER = 0xFFFABD2F;
            RED = 0xFFFB4934;
            TEAL = 0xFF8EC07C;
            VIOLET = 0xFFD3869B;
            DIVIDER = 0xFF3C3836;
            STROKE = 0xFF504945;
            RULE = 0xFF665C54;
            CURRENT = 0xFF3C3836;
            MARK4 = 0xFFB8BB26;
            ERR_BG = 0xFF3C2020;
            ERR_STROKE = 0xFF5F2B26;
            ON_ACCENT = 0xFF282828;
            SUBJECT_COLORS = new int[]{ACCENT, GREEN, AMBER, VIOLET,
                    TEAL, 0xFFFB4934, 0xFFFE8019};
            CDZ_BG = BG;
        } else if (darkTheme) {
            BG = 0xFF171613;
            SURFACE = 0xFF201E1A;
            SURFACE2 = 0xFF2A2721;
            TEXT = 0xFFEAE3D5;
            MUTED = 0xFFA79D8B;
            ACCENT = 0xFF8FB0D4;
            ACCENT2 = 0xFF8FB0D4;
            ACCENT_SOFT = 0x2A8FB0D4;
            GREEN = 0xFF85B394;
            AMBER = 0xFFD6A960;
            RED = 0xFFD9827A;
            TEAL = 0xFF7FB8B8;
            VIOLET = 0xFFB79BC4;
            DIVIDER = 0xFF35312A;
            STROKE = 0xFF3E392F;
            RULE = 0xFF4A4438;
            CURRENT = 0xFF2C2A20;
            MARK4 = 0xFFA8C98A;
            ERR_BG = 0xFF33211D;
            ERR_STROKE = 0xFF5A3A31;
            ON_ACCENT = 0xFF171613;
            SUBJECT_COLORS = SUBJECT_COLORS_DARK;
            CDZ_BG = BG;
        } else {
            BG = 0xFFF5EFE2;
            SURFACE = 0xFFFFFCF4;
            SURFACE2 = 0xFFEDE5D3;
            TEXT = 0xFF2B2A26;
            MUTED = 0xFF7A7365;
            ACCENT = 0xFF34577D;
            ACCENT2 = 0xFF34577D;
            ACCENT_SOFT = 0x1A34577D;
            GREEN = 0xFF2F6B4F;
            AMBER = 0xFF9A6A22;
            RED = 0xFFB0403A;
            TEAL = 0xFF2F6B6B;
            VIOLET = 0xFF6F4A75;
            DIVIDER = 0xFFD9CEB8;
            STROKE = 0xFFD3C7AE;
            RULE = 0xFFC9BDA6;
            CURRENT = 0xFFFFF8E6;
            MARK4 = 0xFF4E7A3A;
            ERR_BG = 0xFFF7E7E2;
            ERR_STROKE = 0xFFD9B3A8;
            ON_ACCENT = 0xFFFFFFFF;
            SUBJECT_COLORS = SUBJECT_COLORS_LIGHT;
        }
    }

    /** Тема из настроек: auto следует за системой. */
    private String resolveTheme() {
        String mode = prefs.getString("theme", "auto");
        if (!"auto".equals(mode)) return mode;
        int night = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";
    }

    private static final String[] THEME_VALUES = {
            "auto", "light", "dark", "black", "nord", "catppuccin", "gruvbox"
    };
    private static final String[] THEME_LABELS = {
            "Как в системе", "Светлая бумага", "Тёмная бумага",
            "Full black", "Nord", "Catppuccin", "Gruvbox"
    };

    private String themeLabel() {
        String mode = prefs.getString("theme", "auto");
        for (int i = 0; i < THEME_VALUES.length; i++) {
            if (THEME_VALUES[i].equals(mode)) return THEME_LABELS[i];
        }
        return THEME_LABELS[0];
    }

    /** Палитра для окна ЦДЗ — те же ключи, что ждёт cdz-paper.js. */
    private String paletteJson() {
        StringBuilder b = new StringBuilder("{");
        colorField(b, "bg", BG);
        colorField(b, "surface", SURFACE);
        colorField(b, "surface2", SURFACE2);
        colorField(b, "ink", TEXT);
        colorField(b, "muted", MUTED);
        colorField(b, "accent", ACCENT);
        colorField(b, "accentBg", ACCENT);
        colorField(b, "onAccent", ON_ACCENT);
        colorField(b, "accentSoft", ACCENT_SOFT);
        colorField(b, "line", STROKE);
        colorField(b, "divider", DIVIDER);
        colorField(b, "rule", RULE);
        colorField(b, "current", CURRENT);
        colorField(b, "green", GREEN);
        colorField(b, "amber", AMBER);
        colorField(b, "red", RED);
        colorField(b, "teal", TEAL);
        colorField(b, "violet", VIOLET);
        colorField(b, "errBg", ERR_BG);
        colorField(b, "errStroke", ERR_STROKE);
        colorField(b, "mark", MARK4);
        b.append('}');
        return b.toString();
    }

    private static void colorField(StringBuilder b, String key, int argb) {
        b.append('"').append(key).append("\":\"").append(cssColor(argb))
                .append("\",");
    }

    private static String cssColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        if (a == 0xFF) return String.format(Locale.US, "#%02X%02X%02X", r, g, b);
        float al = Math.round(a / 255f * 1000f) / 1000f;
        return "rgba(" + r + "," + g + "," + b + "," + al + ")";
    }

    private static int dialogOverlay(String theme) {
        if ("black".equals(theme)) return R.style.OverlayBlack;
        if ("nord".equals(theme)) return R.style.OverlayNord;
        if ("catppuccin".equals(theme)) return R.style.OverlayCatppuccin;
        if ("gruvbox".equals(theme)) return R.style.OverlayGruvbox;
        return 0;
    }

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
    private static final int REQ_NOTIF = 46;

    // ------------------------------------------------------------- состояние

    private ExecutorService exec;
    private Handler ui;
    private SharedPreferences prefs;

    private FrameLayout host;
    private LinearLayout navBar;
    private TextView appTitle;
    private TextView appSub;
    private ImageView refreshBtn;

    private static final int TAB_DAY = 0, TAB_WEEK = 1, TAB_HW = 2,
            TAB_MARKS = 3, TAB_STATS = 4, TAB_MORE = 5;
    private int tab;

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
    private int marksView;                 // 0 оценки, 1 рейтинг, 2 итоговые

    private JSONArray weekData;
    private String weekStart;
    private String weekLoaded;
    private boolean weekLoading;
    private boolean weekAuth;
    private Exception weekError;
    private int weekGen;

    private JSONArray finalData;
    private boolean finalLoading;
    private boolean finalAuth;
    private Exception finalError;
    private int finalGen;

    private JSONArray hwData;
    private String hwFrom;
    private String hwTo;
    private boolean hwLoading;
    private boolean hwAuth;
    private Exception hwError;
    private int hwGen;
    private int hwDays = 14;
    private boolean hwPast;
    // Статистика: отдельный набор данных, чтобы период на «Итогах»
    // не зависел от периода на вкладке оценок.
    private JSONArray statsMarks;
    private JSONArray statsHw;
    private String statsMarksFrom;
    private String statsMarksTo;
    private String statsHwFrom;
    private String statsHwTo;
    private boolean statsLoading;
    private boolean statsAuth;
    private Exception statsMarksError;
    private Exception statsHwError;
    private int statsGen;
    private int statsDays = 30;
    private String statsSubject = "";
    private int statsTarget = 5;
    private int statsFuture = 3;
    private ScrollView statsScroll;
    private int statsScrollY;
    private View statsCalcAnchor;
    private boolean statsScrollToCalc;

    private int hwFilter;              // 0 всё, 1 не сделано, 2 сделано
    private ScrollView hwScroll;
    private int hwScrollY;

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
    private String selectedChildId = "";
    private String selectedChildPerson = "";
    private boolean parentAccount;
    private String parentName = "";
    private boolean cdzDark;

    private LessonDialog lessonDialog;
    private long lastForeground;
    private String openedDay = "";
    private ValueCallback<Uri[]> webFileCallback;
    private Typeface fontMedium;
    private Typeface fontBold;
    private Typeface fontSerif;
    private Typeface fontSerifBold;
    private Typeface fontHand;
    private String cdzThemeJs;
    private Uri pendingCameraUri;

    // ------------------------------------------------------------- lifecycle

    /**
     * «Размер текста» в настройках умножает системный масштаб шрифта.
     * Так можно сделать крупнее только дневник, не трогая весь телефон.
     */
    @Override
    protected void attachBaseContext(Context base) {
        Context ctx = base;
        try {
            float mult = base.getSharedPreferences("sch", MODE_PRIVATE)
                    .getFloat("font_mult", 1f);
            if (mult > 1.01f) {
                Configuration cfg = new Configuration(
                        base.getResources().getConfiguration());
                float scaled = cfg.fontScale * mult;
                cfg.fontScale = scaled > 2.0f ? 2.0f : scaled;
                ctx = base.createConfigurationContext(cfg);
            }
        } catch (Exception ignored) {
        }
        super.attachBaseContext(ctx);
    }

    @Override
    protected void onCreate(Bundle state) {
        prefs = getSharedPreferences("sch", MODE_PRIVATE);
        String theme = resolveTheme();
        boolean dark = !"light".equals(theme);
        String cdzMode = prefs.getString("cdz_theme", "auto");
        cdzDark = "dark".equals(cdzMode) || ("auto".equals(cdzMode) && dark);
        applyPalette(theme);
        setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        // Диалоги рисуются системным стилем, поэтому тему им задаём отдельно.
        int overlay = dialogOverlay(theme);
        if (overlay != 0) getTheme().applyStyle(overlay, true);
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 26) {
            int flags = darkTheme ? 0
                    : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }

        exec = Executors.newSingleThreadExecutor();
        ui = new Handler(Looper.getMainLooper());

        marksDays = prefs.getInt("marksDays", 14);
        hwDays = prefs.getInt("hwDays", 14);
        statsDays = prefs.getInt("statsDays", 30);
        statsTarget = prefs.getInt("statsTarget", 5);
        statsFuture = prefs.getInt("statsFuture", 3);
        statsSubject = prefs.getString("statsSubject", "");
        hwFilter = prefs.getInt("hwFilter", 0);
        selectedChildId = prefs.getString("child_id", "");
        selectedChildPerson = prefs.getString("child_person", "");
        Api.selectStudent(selectedChildId, selectedChildPerson);
        String saved = prefs.getString("date", "");
        currentDate = looksLikeDate(saved) ? saved : today();
        dayData = cacheGet("day3:" + currentDate);
        openedDay = today();
        lastForeground = System.currentTimeMillis();

        if (Notify.enabled(this)) Notify.schedule(this);

        loadFonts();
        buildShell();
        bootstrapCookies();
        // Возврат на ту же вкладку: смена темы пересоздаёт Activity.
        int savedTab = prefs.getInt("tab", TAB_DAY);
        selectTab(savedTab >= TAB_DAY && savedTab <= TAB_MORE ? savedTab : TAB_DAY);
        fetchProfile(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String t = today();
        // Приложение могли оставить открытым на ночь: «сегодня» уже другое.
        boolean rollover = !openedDay.isEmpty() && !t.equals(openedDay);
        if (rollover) {
            String previous = openedDay;
            openedDay = t;
            if (currentDate.equals(previous)) {
                currentDate = t;
                prefs.edit().putString("date", t).apply();
                dayData = cacheGet("day3:" + t);
                dayError = null;
                dayAuth = false;
            }
        }
        long now = System.currentTimeMillis();
        boolean stale = lastForeground > 0
                && now - lastForeground > 3 * 60 * 1000L;
        lastForeground = now;
        if (rollover || stale) refreshVisible(true);
    }

    /** Обновляет только открытую вкладку — остальные подтянутся при переходе. */
    private void refreshVisible(boolean force) {
        if (tab == TAB_DAY) scheduleDayLoad(force);
        else if (tab == TAB_WEEK) fetchWeek(force);
        else if (tab == TAB_HW) fetchHw(force);
        else if (tab == TAB_MARKS) {
            if (marksView == 0) fetchMarks(force);
            else if (marksView == 1) fetchRating(force);
            else fetchFinal(force);
        } else if (tab == TAB_STATS) {
            fetchStats(force);
        } else {
            fetchProfile(false);
        }
    }

    @Override
    public void onBackPressed() {
        // Назад — это «вернуться», а не «выбросить приложение».
        if (tab != TAB_DAY) {
            selectTab(TAB_DAY);
            return;
        }
        if (!currentDate.equals(today())) {
            selectDate(today());
            return;
        }
        super.onBackPressed();
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
        appTitle.setSingleLine(true);
        appTitle.setEllipsize(TextUtils.TruncateAt.END);
        appSub = tv("", 11, MUTED, false);
        appSub.setSingleLine(true);
        appSub.setEllipsize(TextUtils.TruncateAt.END);
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
                if (tab == TAB_DAY) scheduleDayLoad(true);
                else if (tab == TAB_WEEK) fetchWeek(true);
                else if (tab == TAB_HW) fetchHw(true);
                else if (tab == TAB_MARKS) fetchMarks(true);
                else if (tab == TAB_STATS) fetchStats(true);
                else fetchProfile(true);
            }
        });
        bar.addView(refreshBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(bar);

        View rule1 = new View(this);
        rule1.setBackgroundColor(RULE);
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
        navBar.addView(navItem(R.drawable.ic_calendar, "День", TAB_DAY));
        navBar.addView(navItem(R.drawable.ic_week, "Неделя", TAB_WEEK));
        navBar.addView(navItem(R.drawable.ic_homework, "ДЗ", TAB_HW));
        navBar.addView(navItem(R.drawable.ic_star, "Оценки", TAB_MARKS));
        navBar.addView(navItem(R.drawable.ic_stats, "Итоги", TAB_STATS));
        navBar.addView(navItem(R.drawable.ic_more, "Ещё", TAB_MORE));
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
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
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
        prefs.edit().putInt("tab", index).apply();
        buildNav();
        renderTab();
    }

    private void updateChrome() {
        if (tab == TAB_DAY) {
            appTitle.setText("Дневник");
            String sub = prettyTitle(currentDate);
            if (parentAccount && !studentName.isEmpty()) sub += "  ·  " + studentName;
            else if (!studentClass.isEmpty()) sub += "  ·  " + studentClass;
            appSub.setText(sub);
        } else if (tab == TAB_WEEK) {
            appTitle.setText("Неделя");
            String start = weekStart != null ? weekStart : mondayOf(today());
            appSub.setText(weekRangeLabel(start));
        } else if (tab == TAB_HW) {
            appTitle.setText("Домашнее задание");
            appSub.setText(hwPast
                    ? "прошедшие · " + hwDays + " дней"
                    : "с завтра на " + hwDays + " дней");
        } else if (tab == TAB_MARKS) {
            appTitle.setText("Оценки");
            appSub.setText("за последние " + marksDays + " дней");
        } else if (tab == TAB_STATS) {
            appTitle.setText("Итоги");
            appSub.setText(statsPeriodLabel() + " · оценки и домашнее задание");
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
        if (tab == TAB_DAY) {
            host.addView(buildDay());
            if (dayData == null && dayError == null && !dayAuth && !dayLoading) {
                scheduleDayLoad(false);
            }
        } else if (tab == TAB_WEEK) {
            host.addView(buildWeek());
            if (weekData == null && weekError == null && !weekAuth && !weekLoading) {
                fetchWeek(false);
            }
        } else if (tab == TAB_HW) {
            host.addView(buildHomework());
            if (hwData == null && hwError == null && !hwAuth && !hwLoading) {
                fetchHw(false);
            }
        } else if (tab == TAB_MARKS) {
            host.addView(buildMarks());
            if (marksData == null && marksError == null && !marksAuth && !marksLoading) {
                fetchMarks(false);
            }
        } else if (tab == TAB_STATS) {
            host.addView(buildStats());
            if (statsMarks == null && statsHw == null && statsMarksError == null
                    && statsHwError == null && !statsAuth && !statsLoading) {
                fetchStats(false);
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
            if (dayError != null) {
                content.addView(staleBanner("day3:" + currentDate, new Runnable() {
                    @Override public void run() { scheduleDayLoad(true); }
                }));
            }
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
        month.setSingleLine(true);
        month.setEllipsize(TextUtils.TruncateAt.END);
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
            if (tight()) {
                // Текстовая кнопка съедала место у названия месяца.
                ImageView cal = icon(R.drawable.ic_calendar, ACCENT, 18);
                cal.setPadding(dp(10), dp(10), dp(10), dp(10));
                cal.setBackground(rippleBg(ACCENT_SOFT, 20));
                cal.setContentDescription("Вернуться к сегодняшнему дню");
                cal.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { selectDate(today()); }
                });
                row.addView(cal, new LinearLayout.LayoutParams(dp(40), dp(40)));
            } else {
                TextView t = tv("Сегодня", 12, ACCENT, true);
                t.setSingleLine(true);
                t.setPadding(dp(12), dp(7), dp(12), dp(7));
                t.setBackground(roundBg(ACCENT_SOFT, 20, 0));
                t.setClickable(true);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { selectDate(today()); }
                });
                row.addView(t);
            }
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
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
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

    /** Полное ДЗ этого урока (с материалами и ЦДЗ), если оно есть в дне. */
    private JSONObject homeworkFor(JSONObject lesson) {
        JSONArray hws = dayData == null ? null : dayData.optJSONArray("homeworks");
        if (hws == null) return null;
        String subject = subjectOf(lesson).trim();
        String end = Api.s(lesson, "end");
        JSONObject fallback = null;
        for (int i = 0; i < hws.length(); i++) {
            JSONObject h = hws.optJSONObject(i);
            if (h == null) continue;
            String subj = Api.s(h, "subject").trim();
            if (subject.isEmpty() || !subj.equalsIgnoreCase(subject)) continue;
            String lt = Api.s(h, "lesson_time");
            if (!end.isEmpty() && end.equals(lt)) return h;
            if (fallback == null) fallback = h;
        }
        return fallback;
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
                ? roundBg(CURRENT, 2, ACCENT)
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
        begin.setSingleLine(true);
        String endTime = Api.s(l, "end");
        TextView end = tv(endTime.isEmpty() ? "" : "до " + endTime, 9, MUTED, false);
        end.setTypeface(Typeface.MONOSPACE);
        end.setSingleLine(true);
        timeCol.addView(begin);
        timeCol.addView(end);
        card.addView(timeCol, new LinearLayout.LayoutParams(
                Math.max(dp(64), textColWidth(begin, end)),
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

        java.util.List<View> chips = markChips(l.optJSONArray("marks"));
        if (tight() && !chips.isEmpty()) {
            body.addView(head);
            LinearLayout flow = chipFlow(chips);
            flow.setPadding(0, dp(4), 0, 0);
            body.addView(flow);
        } else {
            for (int i = 0; i < chips.size(); i++) head.addView(chips.get(i));
            body.addView(head);
        }

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
            body.addView(tag("отменён", RED, soft(RED)));
        } else if (Api.b(l, "replaced")) {
            body.addView(tag("замена", AMBER, soft(AMBER)));
        }

        final JSONObject fullHw = homeworkFor(l);
        String hw = Api.s(l, "homework").trim();
        if (!hw.isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, 0);
            row.addView(tag("ДЗ", AMBER, soft(AMBER)));
            TextView t = tv(hw, 13, AMBER, false);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(6), 0, 0, 0);
            row.addView(t, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (fullHw != null) {
                // В карточке дня ДЗ было только текстом: подробности
                // (файлы, материалы, ЦДЗ) открывались лишь на вкладке ДЗ.
                row.setClickable(true);
                row.setBackground(rippleBg(0x00000000, 2));
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        showHomeworkDetails(fullHw);
                    }
                });
                if (!tight()) {
                    row.addView(icon(R.drawable.ic_chevron, MUTED, 14));
                }
            }
            body.addView(row);
        }

        JSONObject digital = fullHw == null ? null : firstDigital(fullHw);
        if (digital != null) {
            final String entry = Api.s(fullHw, "entry");
            final String uuid = Api.s(digital, "uuid");
            final String title = Api.s(digital, "title");
            View go = gradientButton(
                    title.isEmpty() ? "Пройти ЦДЗ" : "Пройти  ·  " + title,
                    R.drawable.ic_play, new Runnable() {
                        @Override public void run() { openCdz(entry, uuid, title); }
                    });
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.topMargin = dp(7);
            body.addView(go, glp);
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
        begin.setSingleLine(true);
        String endTime = Api.s(l, "end");
        TextView end = tv(endTime.isEmpty() ? "" : "до " + endTime, 9, MUTED, false);
        end.setTypeface(Typeface.MONOSPACE);
        end.setSingleLine(true);
        timeCol.addView(begin);
        timeCol.addView(end);
        card.addView(timeCol, new LinearLayout.LayoutParams(
                Math.max(dp(64), textColWidth(begin, end)),
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

        head.addView(hwDoneBox(h, new Runnable() {
            @Override public void run() {
                if (tab == TAB_HW) {
                    if (hwScroll != null) hwScrollY = hwScroll.getScrollY();
                    renderTab();
                }
            }
        }), new LinearLayout.LayoutParams(dp(28), dp(28)));
        card.addView(head);

        String text = Api.s(h, "text").trim();
        if (text.isEmpty()) text = "(см. материалы)";
        TextView body = tv(text, 13, TEXT, false);
        body.setMaxLines(3);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(4), 0, 0);
        card.addView(body);

        java.util.List<View> metaItems = new java.util.ArrayList<>();

        String lt = Api.s(h, "lesson_time");
        String ls = Api.s(h, "lesson_start");
        if (!lt.isEmpty()) {
            String when = ls.isEmpty() ? "до " + lt : "урок " + ls + "–" + lt;
            metaItems.add(tag(when, MUTED, SURFACE2));
        }

        int files = 0;
        JSONArray fa = h.optJSONArray("files");
        if (fa != null) files = fa.length();
        int nMat = h.optInt("attachments", 0) + h.optInt("materials", 0);
        if (nMat > 0) metaItems.add(tag(nMat + " " + plural(nMat, "материал",
                "материала", "материалов"), MUTED, SURFACE2));
        if (files > 0) metaItems.add(tag(files + " " + plural(files, "файл",
                "файла", "файлов"), MUTED, SURFACE2));
        if (Api.b(h, "has_teacher_answer")) {
            metaItems.add(tag("ответ учителя", GREEN, soft(GREEN)));
        }
        if (!metaItems.isEmpty()) {
            if (tight()) {
                LinearLayout flow = chipFlow(metaItems);
                flow.setPadding(0, dp(7), 0, 0);
                card.addView(flow);
            } else {
                LinearLayout meta = new LinearLayout(this);
                meta.setOrientation(LinearLayout.HORIZONTAL);
                meta.setPadding(0, dp(7), 0, 0);
                for (int i = 0; i < metaItems.size(); i++) {
                    meta.addView(metaItems.get(i));
                }
                card.addView(meta);
            }
        }

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

        LinearLayout views = chipStrip(page);
        views.addView(hwViewChip("Актуальные", false));
        views.addView(hwViewChip("Прошедшие", true));

        LinearLayout periods = chipStrip(page);
        periods.addView(hwPeriodChip("7 дней", 7));
        periods.addView(hwPeriodChip("14 дней", 14));
        periods.addView(hwPeriodChip("30 дней", 30));

        LinearLayout filters = chipStrip(page);
        filters.addView(hwFilterChip("Все", 0));
        filters.addView(hwFilterChip("Не сделано", 1));
        filters.addView(hwFilterChip("Сделано", 2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        hwScroll = scroll;
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                hwScrollY = y;
            }
        });
        scroll.post(new Runnable() {
            @Override public void run() { scrollToSaved(hwScroll, hwScrollY); }
        });

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
            if (hwError != null && hwFrom != null && hwTo != null) {
                content.addView(staleBanner("hw3:" + hwFrom + ":" + hwTo,
                        new Runnable() {
                            @Override public void run() { fetchHw(true); }
                        }));
            }
            renderHomework(content, hwData);
        }

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View hwViewChip(String label, final boolean past) {
        boolean sel = hwPast == past;
        TextView t = tv(label, 13, sel ? ON_ACCENT : TEXT, sel);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setBackground(sel ? roundBg(ACCENT, 3, 0)
                : roundBg(SURFACE, 3, STROKE));
        tappableChip(t);
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (hwPast == past) return;
                hwPast = past;
                hwData = null;
                hwFrom = null;
                hwTo = null;
                hwError = null;
                hwAuth = false;
                hwScrollY = 0;
                renderTab();
                fetchHw(true);
            }
        });
        return t;
    }

    private View hwPeriodChip(String label, final int days) {
        boolean sel = hwDays == days;
        TextView t = tv(label, 12, sel ? ON_ACCENT : TEXT, sel);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        t.setBackground(sel ? roundBg(ACCENT, 3, 0)
                : roundBg(SURFACE, 3, STROKE));
        tappableChip(t);
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
                hwScrollY = 0;
                renderTab();
                fetchHw(true);
            }
        });
        return t;
    }

    private JSONArray reversed(JSONArray a) {
        JSONArray out = new JSONArray();
        for (int i = a.length() - 1; i >= 0; i--) {
            Object o = a.opt(i);
            if (o != null) out.put(o);
        }
        return out;
    }

    private void renderHomework(LinearLayout content, JSONArray items) {
        if (items.length() == 0) {
            content.addView(emptyCard(
                    hwPast ? "Прошедших заданий нет" : "Домашних заданий нет",
                    hwPast
                            ? "За последние " + hwDays + " дней ничего не было"
                            : "Начиная с завтрашнего дня ничего не задано"));
            return;
        }

        content.addView(hwProgressCard(items));

        JSONArray shown = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject h = items.optJSONObject(i);
            if (h != null && hwMatchesFilter(h)) shown.put(h);
        }
        if (shown.length() == 0) {
            if (hwFilter == 1) {
                content.addView(emptyCard("Всё сделано",
                        "Среди этих заданий невыполненных нет"));
            } else if (hwFilter == 2) {
                content.addView(emptyCard("Пока ничего не отмечено",
                        "Нажми на квадрат в карточке задания"));
            } else {
                content.addView(emptyCard("Пусто", "Заданий за этот период нет"));
            }
            return;
        }

        String lastDate = null;
        for (int i = 0; i < shown.length(); i++) {
            JSONObject h = shown.optJSONObject(i);
            if (h == null) continue;
            String date = Api.s(h, "date");
            if (!date.equals(lastDate)) {
                lastDate = date;
                int n = countOnDate(shown, date);
                content.addView(sectionHeader(
                        hwDateLabel(date).toUpperCase(Locale.ROOT)
                                + (n > 0 ? "  ·  " + n : ""), 0));
            }
            content.addView(homeworkCard(h));
        }
    }

    private boolean hwMatchesFilter(JSONObject h) {
        if (hwFilter == 1) return !hwDone(h);
        if (hwFilter == 2) return hwDone(h);
        return true;
    }

    private View hwFilterChip(String label, final int filter) {
        boolean sel = hwFilter == filter;
        TextView t = tv(label, 12, sel ? ON_ACCENT : TEXT, sel);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        t.setBackground(sel ? roundBg(ACCENT, 3, 0)
                : roundBg(SURFACE, 3, STROKE));
        tappableChip(t);
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (hwFilter == filter) return;
                hwFilter = filter;
                prefs.edit().putInt("hwFilter", filter).apply();
                hwScrollY = 0;
                renderTab();
            }
        });
        return t;
    }

    // ------------------------------------------------------------- отметки о ДЗ

    // В МЭШ есть is_done, но он обновляется не всегда и не для всех типов
    // заданий. Поэтому галочка — локальная: она хранится на телефоне и
    // перекрывает ответ школы.
    private String hwDoneKey(JSONObject h) {
        String id = Api.s(h, "entry");
        if (id.isEmpty()) id = Integer.toHexString(Api.s(h, "text").hashCode());
        return Api.s(h, "date") + "|" + Api.s(h, "subject") + "|"
                + Api.s(h, "lesson_time") + "|" + id;
    }

    private boolean hwDone(JSONObject h) {
        String key = "hwdone:" + hwDoneKey(h);
        if (prefs.contains(key)) return prefs.getBoolean(key, false);
        return Api.b(h, "done");
    }

    private boolean hwToggleDone(JSONObject h) {
        String key = "hwdone:" + hwDoneKey(h);
        boolean done = !hwDone(h);
        prefs.edit().putBoolean(key, done).apply();
        return done;
    }

    private View hwDoneBox(final JSONObject h, final Runnable after) {
        final FrameLayout box = new FrameLayout(this);
        box.setClickable(true);
        box.setFocusable(true);
        final ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_check);
        check.setScaleType(ImageView.ScaleType.FIT_CENTER);
        check.setPadding(dp(5), dp(5), dp(5), dp(5));
        box.addView(check, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable paint = new Runnable() {
            @Override public void run() {
                boolean done = hwDone(h);
                box.setBackground(roundBg(done ? soft(GREEN) : SURFACE2, 4,
                        done ? GREEN : STROKE));
                check.setColorFilter(done ? GREEN : 0x00000000);
                box.setContentDescription(done ? "Сделано" : "Не сделано");
            }
        };
        paint.run();

        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                hwToggleDone(h);
                paint.run();
                if (after != null) after.run();
            }
        });
        return box;
    }

    private View hwProgressCard(JSONArray items) {
        int total = 0, done = 0, overdue = 0, todayLeft = 0, tomorrowLeft = 0;
        String today = today();
        String tomorrow = shiftDate(today, 1);
        for (int i = 0; i < items.length(); i++) {
            JSONObject h = items.optJSONObject(i);
            if (h == null) continue;
            total++;
            String date = Api.s(h, "date");
            if (hwDone(h)) {
                done++;
            } else {
                if (date.compareTo(today) < 0) overdue++;
                else if (date.equals(today)) todayLeft++;
                else if (date.equals(tomorrow)) tomorrowLeft++;
            }
        }
        int left = total - done;
        int percent = total > 0 ? Math.round(done * 100f / total) : 0;
        int color = percent >= 80 ? GREEN : percent >= 50 ? AMBER : RED;

        LinearLayout card = card(SURFACE);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(tv("Сделано", 14, TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = tv(done + " / " + total, 18, color, true);
        top.addView(val);
        card.addView(top);

        card.addView(percentBar(percent, color));
        TextView pct = tv(percent + "%" + (left > 0
                ? "  ·  осталось " + left : "  ·  всё готово"), 11, MUTED, false);
        pct.setPadding(0, dp(2), 0, dp(6));
        card.addView(pct);

        java.util.List<View> tags = new java.util.ArrayList<>();
        if (overdue > 0) {
            tags.add(tag("просрочено " + overdue, RED, soft(RED)));
        }
        if (todayLeft > 0) {
            tags.add(tag("сегодня " + todayLeft, ACCENT, soft(ACCENT)));
        }
        if (tomorrowLeft > 0) {
            tags.add(tag("завтра " + tomorrowLeft, ACCENT, soft(ACCENT)));
        }
        if (left > 0) {
            tags.add(tag("не сделано " + left, AMBER, soft(AMBER)));
        } else {
            tags.add(tag("всё сделано", GREEN, soft(GREEN)));
        }
        card.addView(chipFlow(tags));
        return card;
    }

    private View percentBar(int percent, int color) {
        ProgressBar pb = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(Math.max(0, Math.min(100, percent)));
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(7));
        lp.topMargin = dp(8);
        pb.setLayoutParams(lp);
        return pb;
    }

    private void scrollToSaved(ScrollView sv, int y) {
        if (sv != null) sv.scrollTo(0, Math.max(0, y));
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
        final String from = hwPast
                ? shiftDate(today(), -hwDays) : shiftDate(today(), 1);
        final String to = hwPast
                ? today() : shiftDate(today(), hwDays);

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
                    if (hwPast) hwData = reversed(hwData);
                    hwFrom = from;
                    hwTo = to;
                    hwLoading = false;
                    if (tab == TAB_HW) renderTab();
                } catch (Exception ignored) {
                }
            }
        }

        if (tab == TAB_HW) renderTab();
        if (exec.isShutdown()) return;

        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    final JSONArray rawItems = Api.fromCookies().homeworksRange(from, to);
                    cachePut("hw3:" + from + ":" + to, rawItems.toString());
                    final JSONArray items = hwPast ? reversed(rawItems) : rawItems;
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != hwGen) return;
                            hwLoading = false;
                            hwData = items;
                            hwFrom = from;
                            hwTo = to;
                            if (tab == TAB_HW) renderTab();
                        }
                    });
                } catch (final Api.AuthException e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != hwGen) return;
                            hwLoading = false;
                            hwAuth = true;
                            if (tab == TAB_HW) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != hwGen) return;
                            hwLoading = false;
                            hwError = e;
                            if (tab == TAB_HW) renderTab();
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

        LinearLayout views = chipStrip(page);
        views.addView(viewChip("Оценки", 0));
        views.addView(viewChip("По предметам", 2));
        views.addView(viewChip("Рейтинг", 1));

        if (marksView == 0) {
            LinearLayout periods = chipStrip(page);
            periods.addView(periodChip("14 дней", 14));
            periods.addView(periodChip("30 дней", 30));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(2), dp(12), dp(28));

        if (marksView == 2) {
            if (finalAuth && finalData == null) {
                content.addView(authCard("Нужен вход в МЭШ",
                        "Чтобы показать итоговые оценки, войдите в школу заново."));
            } else if (finalData == null && finalError != null) {
                content.addView(errorCard(finalError, new Runnable() {
                    @Override public void run() { fetchFinal(true); }
                }));
            } else if (finalData == null) {
                content.addView(skeletonMarks());
                if (!finalLoading) fetchFinal(false);
            } else {
                if (finalAuth) content.addView(authBanner());
                if (finalError != null) {
                    content.addView(staleBanner(
                            "final2:" + selectedChildId + ":" + today(),
                            new Runnable() {
                                @Override public void run() { fetchFinal(true); }
                            }));
                }
                renderFinal(content, finalData);
            }
        } else if (marksView == 1) {
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
                if (ratingError != null) {
                    content.addView(staleBanner("rating:" + today(), new Runnable() {
                        @Override public void run() { fetchRating(true); }
                    }));
                }
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
            if (marksError != null && marksFrom != null && marksTo != null) {
                content.addView(staleBanner(
                        "marks2:" + marksFrom + ":" + marksTo,
                        new Runnable() {
                            @Override public void run() { fetchMarks(true); }
                        }));
            }
            renderMarks(content, marksData);
        }

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View viewChip(String label, final int view) {
        boolean sel = marksView == view;
        TextView t = tv(label, 13, sel ? ON_ACCENT : TEXT, sel);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setBackground(sel ? roundBg(ACCENT, 3, 0)
                : roundBg(SURFACE, 3, STROKE));
        tappableChip(t);
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
                if (view == 2 && finalData == null && !finalLoading) fetchFinal(false);
            }
        });
        return t;
    }

    private View periodChip(String label, final int days) {
        boolean sel = marksDays == days;
        TextView t = tv(label, 12, sel ? ON_ACCENT : TEXT, sel);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        t.setBackground(sel ? roundBg(ACCENT, 3, 0)
                : roundBg(SURFACE, 3, STROKE));
        tappableChip(t);
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


    // ------------------------------------------------------------- итоговые оценки

    private String academicStart(String iso) {
        int year;
        int month;
        try {
            year = Integer.parseInt(iso.substring(0, 4));
            month = Integer.parseInt(iso.substring(5, 7));
        } catch (Exception e) {
            return shiftDate(iso, -75);
        }
        return (month >= 8 ? year : year - 1) + "-09-01";
    }

    private String cleanSubjectName(String name) {
        if (name == null) return "";
        String s = name.trim();
        // «География 9-А 9б», «Алгебра 9А Иванова И.И.» -> предмет.
        s = s.replaceAll("\\s+\\d{1,2}\\s*-?\\s*[А-Яа-яA-Za-z]?\\s*\\d?[А-Яа-яA-Za-z]?\\s*$", "");
        s = s.replaceAll("\\s+\\d{1,2}\\s*[-–]?\\s*[А-Яа-яA-Za-z]\\s*$", "");
        return s.trim().isEmpty() ? name.trim() : s.trim();
    }

    private JSONArray cacheGetArray(String key) {
        String raw = prefs.getString(key, null);
        if (raw == null) return null;
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private void fetchFinal(boolean force) {
        if (finalLoading) return;
        finalLoading = true;
        finalAuth = false;
        finalError = null;

        final String key = "final2:" + selectedChildId + ":" + today();
        if (!force) {
            JSONArray cached = cacheGetArray(key);
            if (cached != null) {
                finalData = cached;
                finalLoading = false;
                if (tab == TAB_MARKS) renderTab();
                return;
            }
        }
        if (tab == TAB_MARKS && marksView == 2) renderTab();
        if (exec.isShutdown()) return;

        final int gen = ++finalGen;
        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    Api api = Api.fromCookies();
                    String from = academicStart(today());
                    final Map<String, String> subjects = new LinkedHashMap<>();

                    // Названия предметов берём из оценок за учебный год:
                    // там они уже человеческие, без «9-А 9б».
                    try {
                        JSONArray mm = api.marks(from, today());
                        for (int i = 0; i < mm.length(); i++) {
                            JSONObject m = mm.optJSONObject(i);
                            if (m == null) continue;
                            String id = Api.s(m, "subject_id");
                            String name = Api.s(m, "subject_name");
                            if (!id.isEmpty() && !name.isEmpty()) subjects.put(id, name);
                        }
                    } catch (Api.ApiException | Api.AuthException ignored) {
                    }

                    // Предметы без текущих оценок — из групп профиля.
                    JSONArray kids = profileData == null ? null
                            : profileData.optJSONArray("children");
                    JSONObject kid = selectedChild(kids);
                    JSONArray groups = kid == null ? null : kid.optJSONArray("groups");
                    if (groups != null) {
                        for (int i = 0; i < groups.length(); i++) {
                            JSONObject g = groups.optJSONObject(i);
                            if (g == null) continue;
                            String id = Api.s(g, "subject_id");
                            if (id.isEmpty() || subjects.containsKey(id)) continue;
                            String name = cleanSubjectName(Api.s(g, "name"));
                            if (!name.isEmpty()) subjects.put(id, name);
                        }
                    }

                    List<String> ids = new ArrayList<>(subjects.keySet());
                    Collections.sort(ids, new Comparator<String>() {
                        @Override public int compare(String a, String b) {
                            return subjects.get(a).compareToIgnoreCase(subjects.get(b));
                        }
                    });

                    JSONArray out = new JSONArray();
                    for (String id : ids) {
                        try {
                            JSONObject sm = api.subjectMarks(id);
                            JSONObject row = new JSONObject();
                            Api.put(row, "id", id);
                            Api.put(row, "name", subjects.get(id));
                            Api.put(row, "average", Api.s(sm, "average"));
                            Api.put(row, "dynamic", Api.s(sm, "dynamic"));
                            JSONArray periods = sm.optJSONArray("periods");
                            if (periods == null) periods = new JSONArray();
                            Api.put(row, "periods", periods);
                            JSONObject last = null;
                            for (int i = periods.length() - 1; i >= 0; i--) {
                                JSONObject p = periods.optJSONObject(i);
                                if (p != null) {
                                    last = p;
                                    if (!Api.s(p, "value").isEmpty()) break;
                                }
                            }
                            if (last != null) {
                                Api.put(row, "period_title", Api.s(last, "title"));
                                Api.put(row, "period_start", Api.s(last, "start"));
                                Api.put(row, "period_end", Api.s(last, "end"));
                                Api.put(row, "value", Api.s(last, "value"));
                            }
                            out.put(row);
                        } catch (Api.ApiException | Api.AuthException ignored) {
                            // предмет без итоговых — пропускаем
                        }
                    }

                    final JSONArray res = out;
                    cachePut(key, res.toString());
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != finalGen) return;
                            finalLoading = false;
                            finalError = null;
                            finalData = res;
                            if (tab == TAB_MARKS) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != finalGen) return;
                            finalLoading = false;
                            finalAuth = e instanceof Api.AuthException;
                            finalError = e;
                            if (tab == TAB_MARKS) renderTab();
                        }
                    });
                }
            }
        });
    }

    private JSONArray allSubjectMarks(JSONObject row) {
        JSONArray out = new JSONArray();
        JSONArray periods = row.optJSONArray("periods");
        if (periods == null) return out;
        for (int i = 0; i < periods.length(); i++) {
            JSONObject p = periods.optJSONObject(i);
            if (p == null) continue;
            JSONArray marks = p.optJSONArray("marks");
            if (marks == null) continue;
            for (int j = 0; j < marks.length(); j++) {
                JSONObject m = marks.optJSONObject(j);
                if (m != null) out.put(m);
            }
        }
        return out;
    }

    private int subjectMarkCount(JSONObject row) {
        return allSubjectMarks(row).length();
    }

    private String subjectAverage(JSONObject row) {
        String avg = Api.s(row, "average");
        if (!avg.isEmpty() && !"0.00".equals(avg) && !"0".equals(avg)) return avg;

        JSONArray all = allSubjectMarks(row);
        double sum = 0;
        double weight = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject m = all.optJSONObject(i);
            if (m == null) continue;
            try {
                double v = Double.parseDouble(Api.s(m, "value").replace(',', '.'));
                int w = m.optInt("weight", 1);
                if (w <= 0) w = 1;
                sum += v * w;
                weight += w;
            } catch (Exception ignored) {
            }
        }
        if (weight <= 0) return "";
        return String.format(Locale.US, "%.2f", sum / weight);
    }

    private void renderFinal(LinearLayout content, JSONArray items) {
        if (items.length() == 0) {
            content.addView(emptyCard("Оценок по предметам нет",
                    "За текущий учебный год оценок ещё не было"));
            return;
        }

        content.addView(sectionHeader("ПО ПРЕДМЕТАМ", items.length()));
        for (int i = 0; i < items.length(); i++) {
            final JSONObject row = items.optJSONObject(i);
            if (row == null) continue;

            LinearLayout card = card(SURFACE);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setClickable(true);

            LinearLayout body = column();
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = tv(Api.s(row, "name"), 15, TEXT, true);
            top.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            String avg = subjectAverage(row);
            TextView a = tvHand(avg.isEmpty() ? "—" : avg, 26,
                    avg.isEmpty() ? MUTED : markInk(avg));
            a.setGravity(Gravity.END);
            top.addView(a);
            body.addView(top);

            String title = Api.s(row, "period_title");
            int count = subjectMarkCount(row);
            StringBuilder sub = new StringBuilder();
            if (!title.isEmpty()) sub.append(title);
            if (count > 0) {
                if (sub.length() > 0) sub.append("  ·  ");
                sub.append(count).append(" оц.");
            }
            if (sub.length() > 0) {
                TextView t = tv(sub.toString(), 11, MUTED, false);
                t.setPadding(0, dp(3), 0, 0);
                body.addView(t);
            }

            JSONArray all = allSubjectMarks(row);
            FlowLayout marksRow = new FlowLayout(this, dp(4), dp(4));
            marksRow.setPadding(0, dp(7), 0, 0);
            for (int j = 0; j < all.length(); j++) {
                JSONObject m = all.optJSONObject(j);
                if (m == null) continue;
                String mv = Api.s(m, "value");
                if (mv.isEmpty()) continue;
                TextView chip = tvHand(mv, 18, markInk(mv));
                chip.setGravity(Gravity.CENTER);
                chip.setMinWidth(dp(26));
                chip.setPadding(dp(5), 0, dp(5), dp(1));
                chip.setBackground(roundBg(0, 2, STROKE));
                chip.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
                marksRow.addView(chip);
            }
            body.addView(marksRow);

            card.addView(body, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { showFinalDetails(row); }
            });
            content.addView(card);
        }
    }

    private void showFinalDetails(final JSONObject row) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(12), dp(8), dp(10));
        head.addView(tv(Api.s(row, "name"), 17, TEXT, true),
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

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        String value = Api.s(row, "value");
        TextView big = tvHand(value.isEmpty() ? "—" : value, 44,
                value.isEmpty() ? MUTED : markInk(value));
        top.addView(big);
        LinearLayout avgBox = column();
        avgBox.setPadding(dp(16), 0, 0, 0);
        String avg = subjectAverage(row);
        avgBox.addView(tv("средний балл", 11, MUTED, false));
        avgBox.addView(tv(avg.isEmpty() ? "—" : avg, 16, TEXT, true));
        top.addView(avgBox);
        box.addView(top);

        String dynamic = Api.s(row, "dynamic");
        if (!dynamic.isEmpty()) {
            TextView dtext = tv("динамика: " + trendLabel(dynamic), 12, MUTED, false);
            dtext.setPadding(0, dp(8), 0, 0);
            box.addView(dtext);
        }

        JSONArray periods = row.optJSONArray("periods");
        if (periods != null) {
            for (int i = 0; i < periods.length(); i++) {
                JSONObject p = periods.optJSONObject(i);
                if (p == null) continue;
                box.addView(rule(12, 8));
                String pv = Api.s(p, "value");
                LinearLayout phead = new LinearLayout(this);
                phead.setOrientation(LinearLayout.HORIZONTAL);
                phead.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout pl = column();
                pl.addView(tv(Api.s(p, "title"), 14, TEXT, true));
                String dates = Api.s(p, "start");
                if (!dates.isEmpty()) dates += "–" + Api.s(p, "end");
                if (!dates.isEmpty()) pl.addView(tv(dates, 11, MUTED, false));
                phead.addView(pl, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                phead.addView(tvHand(pv.isEmpty() ? "—" : pv, 26,
                        pv.isEmpty() ? MUTED : markInk(pv)));
                box.addView(phead);

                JSONArray marks = p.optJSONArray("marks");
                if (marks == null) continue;
                for (int j = 0; j < marks.length(); j++) {
                    JSONObject m = marks.optJSONObject(j);
                    if (m == null) continue;
                    LinearLayout mrow = new LinearLayout(this);
                    mrow.setOrientation(LinearLayout.HORIZONTAL);
                    mrow.setGravity(Gravity.CENTER_VERTICAL);
                    mrow.setPadding(0, dp(7), 0, 0);
                    String mv = Api.s(m, "value");
                    mrow.addView(tvHand(mv, 18, markInk(mv)),
                            new LinearLayout.LayoutParams(dp(34),
                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                    LinearLayout ml = column();
                    ml.addView(tv(Api.s(m, "control_form_name"), 12, TEXT, false));
                    String md = Api.s(m, "date");
                    int w = m.optInt("weight", 1);
                    if (!md.isEmpty()) {
                        ml.addView(tv(md + (w > 1 ? "  ·  вес " + w : ""),
                                10, MUTED, false));
                    }
                    mrow.addView(ml, new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    box.addView(mrow);
                }
            }
        }

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
        avgT.setSingleLine(true);
        TextView avgL = tv("средний балл", 10, MUTED, false);
        avgL.setSingleLine(true);
        avgBox.addView(avgT);
        avgBox.addView(avgL);
        card.addView(avgBox, new LinearLayout.LayoutParams(
                Math.max(dp(96), textColWidth(avgT, avgL)),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout counts = new LinearLayout(this);
        counts.setOrientation(LinearLayout.VERTICAL);

        java.util.List<View> tags = new java.util.ArrayList<>();
        if (count5 > 0) tags.add(countTag("5", count5, GREEN));
        if (count4 > 0) tags.add(countTag("4", count4, MARK4));
        if (count3 > 0) tags.add(countTag("3", count3, AMBER));
        if (count2 > 0) tags.add(countTag("2", count2, RED));
        if (other > 0) tags.add(countTag("·", other, MUTED));
        counts.addView(chipFlow(tags));

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
        if (Api.b(m, "is_exam")) flags.addView(tag("экзамен", RED, soft(RED)));
        if (Api.b(m, "is_point")) flags.addView(tag("балл", ACCENT, ACCENT_SOFT));
        if (Api.b(m, "comment_exists")) flags.addView(tag("комментарий", AMBER, soft(AMBER)));
        if (Api.b(m, "has_files")) flags.addView(tag("файлы", GREEN, soft(GREEN)));
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
        if (c == '4') return MARK4;
        if (c == '3') return AMBER;
        if (c == '2') return RED;
        return MUTED;
    }


    /** Поток: элементы переносятся на новую строку, а не обрезаются. */
    private static final class FlowLayout extends ViewGroup {
        private final int gapX;
        private final int gapY;

        FlowLayout(Context c, int gapX, int gapY) {
            super(c);
            this.gapX = gapX;
            this.gapY = gapY;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int avail = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            int x = 0;
            int y = 0;
            int lineH = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                ch.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = Math.min(ch.getMeasuredWidth(), avail);
                int chh = ch.getMeasuredHeight();
                if (x > 0 && x + gapX + cw > avail) {
                    y += lineH + gapY;
                    x = 0;
                    lineH = 0;
                }
                if (x > 0) x += gapX;
                x += cw;
                lineH = Math.max(lineH, chh);
            }

            int h = getPaddingTop() + getPaddingBottom() + y + lineH;
            setMeasuredDimension(width, h);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int avail = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
            int x = 0;
            int y = getPaddingTop();
            int lineH = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                int cw = Math.min(ch.getMeasuredWidth(), avail);
                int chh = ch.getMeasuredHeight();
                if (x > 0 && x + gapX + cw > avail) {
                    y += lineH + gapY;
                    x = 0;
                    lineH = 0;
                }
                if (x > 0) x += gapX;
                int left = getPaddingLeft() + x;
                ch.layout(left, y, left + cw, y + chh);
                x += cw;
                lineH = Math.max(lineH, chh);
            }
        }
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
        String t = trend == null ? "" : trend.toLowerCase(Locale.ROOT);
        if ("up".equals(t)) return "растёт";
        if ("down".equals(t)) return "падает";
        if ("stable".equals(t)) return "стабильно";
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

    private void toggleNotifications() {
        if (Notify.enabled(this)) {
            Notify.enable(this, false);
            toast("Уведомления выключены");
            renderTab();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIF);
            return;
        }
        enableNotifications();
    }

    private void enableNotifications() {
        Notify.enable(this, true);
        toast("Уведомления включены");
        renderTab();
    }

    private void themeDialog() {
        String mode = prefs.getString("theme", "auto");
        int sel = 0;
        for (int i = 0; i < THEME_VALUES.length; i++) {
            if (THEME_VALUES[i].equals(mode)) sel = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Тема")
                .setSingleChoiceItems(THEME_LABELS, sel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        prefs.edit().putString("theme", THEME_VALUES[which]).apply();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private String fontLabel() {
        float m = prefs.getFloat("font_mult", 1f);
        if (m > 1.25f) return "самый крупный";
        if (m > 1.05f) return "крупнее";
        return "как в системе";
    }

    private void fontDialog() {
        final String[] labels = {"Как в системе", "Крупнее", "Самый крупный"};
        final float[] values = {1f, 1.15f, 1.35f};
        float cur = prefs.getFloat("font_mult", 1f);
        int sel = 0;
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - cur) < 0.02f) sel = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Размер текста")
                .setMessage("Масштаб складывается с системным: если в телефоне "
                        + "уже крупный шрифт, приложение станет ещё крупнее.")
                .setSingleChoiceItems(labels, sel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        prefs.edit().putFloat("font_mult", values[which]).apply();
                        d.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private View buildMore() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(6), dp(12), dp(28));

        content.addView(profileCard());
        if (childrenCount() > 0 && (parentAccount || childrenCount() > 1)) {
            content.addView(childrenCard());
        }
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
        actions.addView(actionRow(R.drawable.ic_search, "Поиск по дневнику",
                "Задания, предметы и комментарии к оценкам", false, new Runnable() {
                    @Override public void run() { openSearch(); }
                }));
        actions.addView(actionDivider());
        actions.addView(actionRow(R.drawable.ic_bell, "Уведомления",
                Notify.enabled(this)
                        ? "Новые оценки и ДЗ · включены"
                        : "Новые оценки и ДЗ · выключены", false, new Runnable() {
                    @Override public void run() { toggleNotifications(); }
                }));
        actions.addView(actionDivider());
        actions.addView(actionRow(R.drawable.ic_theme, "Тема",
                themeLabel(), false, new Runnable() {
                    @Override public void run() { themeDialog(); }
                }));
        actions.addView(actionDivider());
        actions.addView(actionRow(R.drawable.ic_text, "Размер текста",
                "Сейчас: " + fontLabel(), false, new Runnable() {
                    @Override public void run() { fontDialog(); }
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

        TextView footer = tv("OpenSchool · 1.6\nданные: school.mos.ru",
                10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        content.addView(footer);

        scroll.addView(content);
        return scroll;
    }

    // ------------------------------------------------------------- поиск

    private JSONArray searchHw;
    private JSONArray searchMarks;
    private String searchQuery = "";
    private LinearLayout searchResults;
    private TextView searchStatus;
    private boolean searchAuth;
    private Exception searchError;

    private void openSearch() {
        searchQuery = "";
        searchHw = null;
        searchMarks = null;
        searchError = null;
        searchAuth = false;

        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(roundBg(BG, 4, STROKE));
        root.setPadding(dp(16), dp(14), dp(16), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(tvSerif("Поиск", 21, TEXT, true),
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView close = icon(R.drawable.ic_close, MUTED, 18);
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(rippleBg(0x00000000, 20));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        head.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        root.addView(head);

        EditText input = new EditText(this);
        input.setHint("Предмет, задание, комментарий");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setSingleLine(true);
        input.setBackground(roundBg(SURFACE2, 3, STROKE));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(10);
        root.addView(input, ilp);

        searchStatus = tv("Загружаю дневник…", 12, MUTED, false);
        searchStatus.setPadding(0, dp(8), 0, 0);
        root.addView(searchStatus);

        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchResults.setPadding(0, dp(4), 0, dp(12));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(searchResults);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
            }
            @Override public void afterTextChanged(Editable e) {
                searchQuery = e.toString().trim();
                renderSearchResults();
            }
        });

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
        input.requestFocus();
        loadSearchData(d);
    }

    private void loadSearchData(final Dialog d) {
        if (exec.isShutdown()) return;
        exec.execute(new Runnable() {
            @Override public void run() {
                JSONArray hw = null;
                JSONArray mk = null;
                Exception err = null;
                boolean auth = false;
                try {
                    Api api = Api.fromCookies();
                    hw = api.homeworksRange(shiftDate(today(), -30),
                            shiftDate(today(), 30));
                    mk = api.marks(shiftDate(today(), -90), today());
                } catch (Api.AuthException e) {
                    auth = true;
                } catch (Exception e) {
                    err = e;
                }
                final JSONArray fhw = hw;
                final JSONArray fmk = mk;
                final Exception ferr = err;
                final boolean fauth = auth;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!d.isShowing()) return;
                        searchAuth = fauth;
                        searchError = ferr;
                        searchHw = fhw;
                        searchMarks = fmk;
                        searchStatus.setText(fauth ? "Нужен вход в МЭШ"
                                : ferr != null ? "Не удалось загрузить дневник"
                                : "Введите запрос");
                        renderSearchResults();
                    }
                });
            }
        });
    }

    private void renderSearchResults() {
        if (searchResults == null) return;
        searchResults.removeAllViews();

        if (searchAuth) {
            searchResults.addView(authCard("Нужен вход в МЭШ",
                    "Поиск работает по данным дневника — войдите в школу заново."));
            return;
        }
        if (searchError != null) {
            searchResults.addView(errorCard(searchError, new Runnable() {
                @Override public void run() { searchStatus.setText("Повторите позже"); }
            }));
            return;
        }

        String q = searchQuery.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return;
        if (searchHw == null && searchMarks == null) return;

        List<JSONObject> hwHits = new ArrayList<>();
        if (searchHw != null) {
            for (int i = 0; i < searchHw.length() && hwHits.size() < 60; i++) {
                JSONObject h = searchHw.optJSONObject(i);
                if (h == null) continue;
                if (homeworkBlob(h).contains(q)) hwHits.add(h);
            }
        }

        List<JSONObject> mkHits = new ArrayList<>();
        if (searchMarks != null) {
            for (int i = 0; i < searchMarks.length() && mkHits.size() < 60; i++) {
                JSONObject m = searchMarks.optJSONObject(i);
                if (m == null) continue;
                if (markBlob(m).contains(q)) mkHits.add(m);
            }
        }

        if (hwHits.isEmpty() && mkHits.isEmpty()) {
            searchStatus.setText("Ничего не найдено");
            searchResults.addView(emptyCard("Ничего не найдено",
                    "Попробуйте другое слово или часть названия предмета"));
            return;
        }

        searchStatus.setText(hwHits.size() + " "
                + plural(hwHits.size(), "задание", "задания", "заданий")
                + " · " + mkHits.size() + " "
                + plural(mkHits.size(), "оценка", "оценки", "оценок"));

        if (!hwHits.isEmpty()) {
            searchResults.addView(sectionHeader("ДОМАШНИЕ ЗАДАНИЯ", hwHits.size()));
            for (JSONObject h : hwHits) searchResults.addView(homeworkCard(h));
        }
        if (!mkHits.isEmpty()) {
            searchResults.addView(sectionHeader("ОЦЕНКИ", mkHits.size()));
            for (JSONObject m : mkHits) searchResults.addView(marksRow(m));
        }
    }

    private static String homeworkBlob(JSONObject h) {
        StringBuilder sb = new StringBuilder();
        sb.append(' ').append(Api.s(h, "subject"));
        sb.append(' ').append(Api.s(h, "text"));
        sb.append(' ').append(Api.s(h, "date"));
        JSONArray mats = h.optJSONArray("materials_list");
        if (mats != null) {
            for (int i = 0; i < mats.length(); i++) {
                JSONObject m = mats.optJSONObject(i);
                if (m != null) sb.append(' ').append(Api.s(m, "title"));
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String markBlob(JSONObject m) {
        StringBuilder sb = new StringBuilder();
        sb.append(' ').append(Api.s(m, "subject_name"));
        sb.append(' ').append(Api.s(m, "comment"));
        sb.append(' ').append(Api.s(m, "control_form_name"));
        sb.append(' ').append(Api.s(m, "value"));
        sb.append(' ').append(Api.s(m, "date"));
        return sb.toString().toLowerCase(Locale.ROOT);
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

        TextView again = secondaryButton("Проверить снова", new Runnable() {
            @Override public void run() { checkNetwork(status); }
        });
        TextView close = primaryButton("Закрыть", new Runnable() {
            @Override public void run() { d.dismiss(); }
        });
        View buttons = buttonPair(again, close);
        buttons.setPadding(0, dp(14), 0, 0);
        root.addView(buttons);

        // На маленьком экране с крупным шрифтом диалог иначе не помещается
        // по высоте и кнопки уезжают за край.
        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setFillViewport(true);
        dialogScroll.addView(root);
        d.setContentView(dialogScroll);
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

    private int childrenCount() {
        if (profileData == null) return 0;
        JSONArray kids = profileData.optJSONArray("children");
        return kids == null ? 0 : kids.length();
    }

    private View childrenCard() {
        LinearLayout card = card(SURFACE);
        card.addView(tv("Дети", 13, TEXT, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        JSONArray kids = profileData.optJSONArray("children");
        for (int i = 0; i < kids.length(); i++) {
            final JSONObject kid = kids.optJSONObject(i);
            if (kid == null) continue;
            String id = Api.s(kid, "id");
            boolean sel = id.equals(selectedChildId)
                    || (selectedChildId.isEmpty() && i == 0);
            String name = joinNonEmpty(" ",
                    Api.s(kid, "first_name"), Api.s(kid, "last_name"));
            if (name.isEmpty()) name = "Ребёнок " + (i + 1);
            String cls = Api.s(kid, "class_name");
            TextView chip = tv(cls.isEmpty() ? name : name + "\n" + cls,
                    12, sel ? ON_ACCENT : TEXT, sel);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), dp(8), dp(12), dp(8));
            chip.setBackground(sel ? roundBg(ACCENT, 3, 0)
                    : roundBg(SURFACE2, 3, STROKE));
            chip.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchChild(kid); }
            });
            row.addView(chip);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        card.addView(hs);
        return card;
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

            if (parentAccount && !parentName.isEmpty()) {
                TextView pr = tv("родитель: " + parentName, 11, MUTED, false);
                pr.setPadding(0, dp(3), 0, 0);
                body.addView(pr);
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

        ImageView chevron = icon(R.drawable.ic_chevron, MUTED, 18);
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
                            if (tab == TAB_DAY && date.equals(currentDate)) renderTab();
                        }
                    });
                } catch (final Api.AuthException e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != dayGen) return;
                            dayLoading = false;
                            dayAuth = true;
                            if (tab == TAB_DAY && date.equals(currentDate)) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != dayGen) return;
                            dayLoading = false;
                            dayError = e;
                            if (tab == TAB_DAY && date.equals(currentDate)) renderTab();
                        }
                    });
                }
            }
        });
    }

    // ------------------------------------------------------------- неделя

    private View buildWeek() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(weekNavRow());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(2), dp(12), dp(28));

        if (weekAuth && weekData == null) {
            content.addView(authCard("Нужен вход в МЭШ",
                    "Чтобы показать неделю, войдите в школу заново."));
        } else if (weekData == null && weekError != null) {
            content.addView(errorCard(weekError, new Runnable() {
                @Override public void run() { fetchWeek(true); }
            }));
        } else if (weekData == null) {
            content.addView(skeletonMarks());
        } else {
            if (weekAuth) content.addView(authBanner());
            if (weekError != null) {
                final String cacheKey = "week3:" + (weekStart != null
                        ? weekStart : mondayOf(today()));
                content.addView(staleBanner(cacheKey, new Runnable() {
                    @Override public void run() { fetchWeek(true); }
                }));
            }
            renderWeek(content, weekData);
        }

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View weekNavRow() {
        final String start = weekStart != null ? weekStart : mondayOf(today());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(2), dp(10), dp(4));

        ImageView prev = icon(R.drawable.ic_chevron, MUTED, 18);
        prev.setRotation(180);
        prev.setPadding(dp(9), dp(9), dp(9), dp(9));
        prev.setBackground(rippleBg(0x00000000, 20));
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openWeek(shiftDate(start, -7));
            }
        });
        row.addView(prev, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView title = tvSerif(weekRangeLabel(start), 17, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(tight() ? 2 : 1);
        title.setSingleLine(!tight());
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView next = icon(R.drawable.ic_chevron, MUTED, 18);
        next.setPadding(dp(9), dp(9), dp(9), dp(9));
        next.setBackground(rippleBg(0x00000000, 20));
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openWeek(shiftDate(start, 7));
            }
        });
        row.addView(next, new LinearLayout.LayoutParams(dp(38), dp(38)));

        if (!start.equals(mondayOf(today()))) {
            TextView now = tv("эта неделя", 11, ACCENT, true);
            now.setPadding(dp(6), dp(8), dp(4), dp(8));
            now.setClickable(true);
            now.setBackground(rippleBg(0x00000000, 20));
            now.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    openWeek(mondayOf(today()));
                }
            });
            row.addView(now);
        }
        return row;
    }

    private String weekRangeLabel(String start) {
        String end = shiftDate(start, 6);
        int m1 = monthIndex(start);
        int m2 = monthIndex(end);
        String left = dayOfMonth(start) + (m1 != m2 ? " " + MONTHS[m1] : "");
        return left + " – " + dayOfMonth(end) + " " + MONTHS[m2];
    }

    private int monthIndex(String iso) {
        try {
            return Integer.parseInt(iso.substring(5, 7)) - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private void openWeek(String start) {
        weekStart = start;
        weekData = null;
        weekLoaded = null;
        weekError = null;
        weekAuth = false;
        String raw = prefs.getString("week3:" + start, null);
        if (raw != null) {
            try {
                weekData = new JSONArray(raw);
                weekLoaded = start;
            } catch (Exception ignored) {
            }
        }
        if (weekData == null) {
            fetchWeek(true);
        } else {
            renderTab();
            fetchWeek(true);
        }
    }

    private void fetchWeek(boolean force) {
        final String start = weekStart != null ? weekStart : mondayOf(today());
        weekStart = start;
        if (!force && weekData != null && start.equals(weekLoaded)) return;

        final int gen = ++weekGen;
        weekLoading = true;
        weekAuth = false;
        weekError = null;
        if (tab == TAB_WEEK) renderTab();
        if (exec.isShutdown()) return;

        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    final JSONArray days = Api.fromCookies().week(start, 7);
                    cachePut("week3:" + start, days.toString());
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != weekGen) return;
                            weekLoading = false;
                            weekAuth = false;
                            weekError = null;
                            weekData = days;
                            weekLoaded = start;
                            if (tab == TAB_WEEK) renderTab();
                        }
                    });
                } catch (final Api.AuthException e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != weekGen) return;
                            weekLoading = false;
                            weekAuth = true;
                            if (tab == TAB_WEEK) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != weekGen) return;
                            weekLoading = false;
                            weekError = e;
                            if (tab == TAB_WEEK) renderTab();
                        }
                    });
                }
            }
        });
    }

    /** Один день недели с уже посчитанными ДЗ и нагрузкой. */
    private static final class WeekDay {
        JSONObject raw;
        String date;
        final List<JSONObject> hw = new ArrayList<>();
        int lessons;
        int controls;
        int digital;
        int left;
    }

    private void renderWeek(LinearLayout content, JSONArray days) {
        String todayIso = today();
        List<WeekDay> list = new ArrayList<>();

        for (int i = 0; i < days.length(); i++) {
            JSONObject d = days.optJSONObject(i);
            if (d == null) continue;

            WeekDay w = new WeekDay();
            w.raw = d;
            w.date = Api.s(d, "date");

            JSONArray lessons = d.optJSONArray("lessons");
            if (lessons == null) lessons = new JSONArray();
            w.lessons = lessons.length();
            for (int j = 0; j < lessons.length(); j++) {
                if (isControlLesson(lessons.optJSONObject(j))) w.controls++;
            }

            JSONArray hws = d.optJSONArray("homeworks");
            if (hws == null) hws = new JSONArray();
            Set<String> seen = new HashSet<>();
            for (int j = 0; j < hws.length(); j++) {
                JSONObject h = hws.optJSONObject(j);
                if (h == null) continue;
                // один и тот же текст иногда приходит от нескольких уроков
                if (!seen.add(Api.s(h, "subject") + "|" + Api.s(h, "text"))) continue;
                w.hw.add(h);
                if (!Api.b(h, "done")) w.left++;
                if (hasDigitalTest(h)) w.digital++;
            }
            list.add(w);
        }

        int totalDone = 0;
        for (WeekDay w : list) {
            for (JSONObject h : w.hw) if (Api.b(h, "done")) totalDone++;
        }
        // is_done в API почти всегда false; счётчик показываем, только если
        // школа действительно отмечает выполненные задания.
        boolean trackDone = totalDone > 0;

        if (!list.isEmpty()) content.addView(weekSummary(list, trackDone));
        for (WeekDay w : list) renderWeekDay(content, w, todayIso, trackDone);
    }

    private void renderWeekDay(LinearLayout content, final WeekDay w,
                               String todayIso, boolean trackDone) {
        final String date = w.date;
        boolean isToday = date.equals(todayIso);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(9), dp(12), dp(10));
        card.setBackground(isToday ? roundBg(CURRENT, 3, ACCENT)
                : roundBg(SURFACE, 3, STROKE));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                selectTab(TAB_DAY);
                selectDate(date);
            }
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        String label = WEEKDAYS_SHORT[dow(date)] + " · "
                + dayOfMonth(date) + " " + MONTHS[monthIndex(date)];
        TextView dayName = tv(label, 13, isToday ? ACCENT : TEXT, true);
        head.addView(dayName, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        StringBuilder meta = new StringBuilder();
        if (w.lessons == 0) {
            meta.append("нет уроков");
        } else {
            meta.append(w.lessons).append(" ")
                    .append(plural(w.lessons, "урок", "урока", "уроков"));
        }
        if (!w.hw.isEmpty()) meta.append(" · ДЗ ").append(w.hw.size());
        head.addView(tv(meta.toString(), 10, MUTED, false));
        card.addView(head);

        String err = Api.s(w.raw, "error");
        if (!err.isEmpty()) {
            TextView e = tv(err, 11, MUTED, false);
            e.setPadding(0, dp(4), 0, 0);
            card.addView(e);
            content.addView(card);
            return;
        }

        LinearLayout flags = new LinearLayout(this);
        flags.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(6);
        flags.setLayoutParams(flp);
        if (w.controls > 0) {
            flags.addView(tag(w.controls > 1 ? "контрольных " + w.controls
                    : "контрольная", RED, soft(RED)));
        }
        if (w.digital > 0) {
            flags.addView(tag("ЦДЗ " + w.digital, ACCENT, soft(ACCENT)));
        }
        if (trackDone && w.left > 0) {
            flags.addView(tag("не сделано " + w.left, AMBER, soft(AMBER)));
        }
        if (flags.getChildCount() > 0) card.addView(flags);

        JSONArray lessons = w.raw.optJSONArray("lessons");
        if (lessons == null) lessons = new JSONArray();
        for (int j = 0; j < lessons.length(); j++) {
            JSONObject l = lessons.optJSONObject(j);
            if (l == null) continue;
            card.addView(weekLessonRow(l));
        }

        for (int j = 0; j < w.hw.size() && j < 3; j++) {
            card.addView(weekHwRow(w.hw.get(j), trackDone));
        }
        if (w.hw.size() > 3) {
            TextView more = tv("ещё " + (w.hw.size() - 3) + " — откройте день",
                    10, ACCENT, false);
            more.setPadding(0, dp(5), 0, 0);
            card.addView(more);
        }

        content.addView(card);
    }

    private View weekSummary(List<WeekDay> days, boolean trackDone) {
        int totalHw = 0, totalLeft = 0, totalControls = 0, totalDigital = 0;
        int totalLessons = 0, maxLessons = 0;
        for (WeekDay w : days) {
            totalHw += w.hw.size();
            totalLeft += w.left;
            totalControls += w.controls;
            totalDigital += w.digital;
            totalLessons += w.lessons;
            maxLessons = Math.max(maxLessons, w.lessons);
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(10));
        box.setBackground(roundBg(SURFACE, 3, STROKE));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = dp(10);
        box.setLayoutParams(blp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        top.addView(tvSerif(String.valueOf(totalHw), 30, TEXT, true));

        LinearLayout cap = new LinearLayout(this);
        cap.setOrientation(LinearLayout.VERTICAL);
        cap.setPadding(dp(10), 0, 0, 0);
        cap.addView(tv(plural(totalHw, "задание", "задания", "заданий")
                + " на неделю", 11, MUTED, false));
        if (totalHw == 0) {
            cap.addView(tv("пока пусто", 11, MUTED, false));
        } else if (trackDone && totalLeft == 0) {
            cap.addView(tv("всё сделано", 11, GREEN, true));
        } else if (trackDone) {
            cap.addView(tv(totalLeft + " не сделано", 11, AMBER, true));
        }
        top.addView(cap, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(top);

        LinearLayout tags = new LinearLayout(this);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(8);
        tags.setLayoutParams(tlp);
        if (totalControls > 0) {
            tags.addView(tag(totalControls > 1 ? "контрольных " + totalControls
                    : "контрольная", RED, soft(RED)));
        }
        if (totalDigital > 0) {
            tags.addView(tag("ЦДЗ " + totalDigital, ACCENT, soft(ACCENT)));
        }
        if (totalLessons > 0) {
            tags.addView(tag(totalLessons + " "
                    + plural(totalLessons, "урок", "урока", "уроков"), MUTED, soft(MUTED)));
        }
        if (tags.getChildCount() > 0) box.addView(tags);

        box.addView(weekLoadStrip(days, maxLessons));
        if (totalControls > 0) {
            TextView legend = tv("точка — контрольная или проверочная", 9, MUTED, false);
            legend.setPadding(dp(2), dp(4), 0, 0);
            box.addView(legend);
        }
        return box;
    }

    /** Столбики уроков по дням: видно, где неделя тяжёлая. */
    private View weekLoadStrip(List<WeekDay> days, int maxLessons) {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(12);
        strip.setLayoutParams(slp);

        String todayIso = today();
        for (WeekDay w : days) {
            boolean isToday = w.date.equals(todayIso);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout box = new FrameLayout(this);
            int h = maxLessons == 0 ? dp(3) : dp(3 + 23 * w.lessons / maxLessons);
            View bar = new View(this);
            bar.setBackground(roundBg(isToday ? ACCENT : RULE, 2, 0));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(12), h);
            blp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            box.addView(bar, blp);

            if (w.controls > 0) {
                View dot = new View(this);
                dot.setBackground(roundBg(RED, 2, 0));
                FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(4), dp(4));
                dlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                box.addView(dot, dlp);
            }
            col.addView(box, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

            String label = WEEKDAYS_SHORT[dow(w.date)];
            TextView day = tv(label, 9, isToday ? ACCENT : MUTED, isToday);
            day.setGravity(Gravity.CENTER);
            day.setPadding(0, dp(3), 0, 0);
            col.addView(day);

            strip.addView(col, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return strip;
    }

    /** Строка ДЗ: точка «сделано», текст и переход к подробностям. */
    private View weekHwRow(final JSONObject h, boolean trackDone) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, 0);
        row.setClickable(true);
        row.setBackground(rippleBg(0x00000000, 2));

        boolean done = Api.b(h, "done");
        if (trackDone) {
            View dot = new View(this);
            dot.setBackground(done ? roundBg(GREEN, 4, 0)
                    : roundBg(0x00000000, 4, MUTED));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
            dlp.topMargin = dp(4);
            dlp.rightMargin = dp(7);
            row.addView(dot, dlp);
        }

        String subject = Api.s(h, "subject");
        String text = Api.s(h, "text").replace("\n", " ").trim();
        String line = "ДЗ";
        if (!subject.isEmpty()) line += " · " + subject;
        if (!text.isEmpty()) line += " — " + text;

        TextView t = tv(line, 11, done ? MUTED : TEXT, false);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHomeworkDetails(h); }
        });
        return row;
    }

    /** Контрольная, проверочная, диктант или тест — по теме урока. */
    private static boolean isControlLesson(JSONObject l) {
        if (l == null) return false;
        String t = Api.s(l, "topic").toLowerCase(Locale.ROOT);
        return t.contains("контрольн") || t.contains("самостоятельн")
                || t.contains("проверочн") || t.contains("диктант")
                || t.contains("зачёт") || t.contains("зачет")
                || t.contains("тест");
    }

    /** В ДЗ есть цифровое задание (ЦДЗ). */
    private static boolean hasDigitalTest(JSONObject h) {
        JSONArray mats = h.optJSONArray("materials_list");
        if (mats == null) return false;
        for (int i = 0; i < mats.length(); i++) {
            JSONObject m = mats.optJSONObject(i);
            if (m != null && "test_spec_binding".equals(Api.s(m, "type"))) return true;
        }
        return false;
    }

    private View weekLessonRow(JSONObject l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);

        TextView time = tv(Api.s(l, "begin"), 11, MUTED, false);
        time.setTypeface(Typeface.MONOSPACE);
        time.setSingleLine(true);
        row.addView(time, new LinearLayout.LayoutParams(
                Math.max(dp(42), textColWidth(time)),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView subject = tv(subjectOf(l), 13, TEXT, false);
        subject.setMaxLines(1);
        subject.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(subject);

        String room = Api.s(l, "room");
        if (!room.isEmpty()) {
            TextView r = tv("каб. " + room, 10, MUTED, false);
            body.addView(r);
        }
        if (isControlLesson(l)) {
            TextView c = tv("контрольная", 9, RED, true);
            c.setPadding(0, dp(1), 0, 0);
            body.addView(c);
        }
        row.addView(body, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        java.util.List<View> chips = markChips(l.optJSONArray("marks"));
        if (!chips.isEmpty() && tight()) {
            LinearLayout flow = chipFlow(chips);
            flow.setPadding(0, dp(2), 0, 0);
            body.addView(flow);
        } else {
            for (int i = 0; i < chips.size(); i++) row.addView(chips.get(i));
        }
        return row;
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

        if (tab == TAB_MARKS) renderTab();

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
                            if (tab == TAB_MARKS) renderTab();
                        }
                    });
                } catch (final Api.AuthException e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != marksGen) return;
                            marksLoading = false;
                            marksAuth = true;
                            if (tab == TAB_MARKS) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != marksGen) return;
                            marksLoading = false;
                            marksError = e;
                            if (tab == TAB_MARKS) renderTab();
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
                    if (tab == TAB_MARKS && marksView == 1) renderTab();
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
        if (tab == TAB_MARKS && marksView == 1) renderTab();

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
                            if (tab == TAB_MARKS && marksView == 1) renderTab();
                        }
                    });
                } catch (final Api.AuthException e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != ratingGen) return;
                            ratingLoading = false;
                            ratingAuth = true;
                            if (tab == TAB_MARKS && marksView == 1) renderTab();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (gen != ratingGen) return;
                            ratingLoading = false;
                            ratingError = e;
                            if (tab == TAB_MARKS && marksView == 1) renderTab();
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

        TextView badge = tv(String.valueOf(place), 13, place == 1 ? ON_ACCENT : TEXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setMinWidth(dp(34));
        badge.setMinHeight(dp(34));
        badge.setPadding(dp(5), dp(4), dp(5), dp(4));
        badge.setBackground(roundBg(place == 1 ? GREEN : SURFACE2, 3,
                place == 1 ? 0 : STROKE));
        top.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

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

    // ------------------------------------------------------------- итоги

    private String statsPeriodLabel() {
        if (statsDays <= 0) return "учебный год";
        return statsDays + " дней";
    }

    private String statsMarksFromDate() {
        String to = today();
        return statsDays <= 0 ? academicStart(to) : shiftDate(to, -(statsDays - 1));
    }

    private View buildStats() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout periods = chipStrip(page);
        periods.addView(statsPeriodChip("30 дней", 30));
        periods.addView(statsPeriodChip("90 дней", 90));
        periods.addView(statsPeriodChip("Учебный год", 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        statsScroll = scroll;
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                statsScrollY = y;
            }
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(2), dp(12), dp(28));

        if (statsMarks == null && statsHw == null) {
            if (statsAuth) {
                content.addView(authCard("Нужен вход в МЭШ",
                        "Чтобы посчитать статистику, войдите в школу заново."));
            } else if (statsMarksError != null || statsHwError != null) {
                Exception e = statsMarksError != null ? statsMarksError : statsHwError;
                content.addView(errorCard(e, new Runnable() {
                    @Override public void run() { fetchStats(true); }
                }));
            } else {
                content.addView(skeletonMarks());
                if (!statsLoading) fetchStats(false);
            }
        } else {
            if (statsAuth) content.addView(authBanner());
            if (!statsLoading && (statsMarksError != null || statsHwError != null)) {
                TextView stale = tv("Не удалось обновить — показаны "
                        + "сохранённые данные", 11, MUTED, false);
                stale.setPadding(dp(4), 0, dp(4), dp(8));
                content.addView(stale);
            }

            if (statsMarks != null) {
                Map<String, SubjectStat> map = collectSubjectStats(statsMarks);
                SubjectStat all = map.get("Все предметы");
                if (all != null && all.scored() > 0) {
                    content.addView(sectionHeader("СВОДКА", 0));
                    content.addView(gradeSummaryCard(all, statsMarks, map.size() - 1));
                    content.addView(metricRow(
                            metricTile("Качество", all.quality() + "%",
                                    "оценок на 4 и 5", all.qualityColor()),
                            metricTile("Успеваемость", all.success() + "%",
                                    "оценок без двоек", all.successColor())));
                    View calc = statsCalculatorCard(map);
                    statsCalcAnchor = calc;
                    content.addView(calc);
                    content.addView(subjectsCard(map));
                } else {
                    content.addView(emptyCard("Оценок нет",
                            "За выбранный период оценок не было"));
                }
            } else if (statsMarksError != null) {
                content.addView(errorCard(statsMarksError, new Runnable() {
                    @Override public void run() { fetchStats(true); }
                }));
            }

            if (statsHw != null) {
                renderHwStats(content, statsHw);
            } else if (statsHwError != null) {
                content.addView(errorCard(statsHwError, new Runnable() {
                    @Override public void run() { fetchStats(true); }
                }));
            }

            TextView note = tv("Средний балл считается с учётом веса оценок. "
                    + "Итоговая — по школьному округлению: 4,5 → 5, 3,5 → 4. "
                    + "В калькуляторе будущие оценки считаются с весом 1. "
                    + "Отметки о домашнем задании хранятся только на этом телефоне "
                    + "и в МЭШ не отправляются.", 11, MUTED, false);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(10);
            content.addView(note, nlp);
        }

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final boolean toCalc = statsScrollToCalc;
        statsScrollToCalc = false;
        scroll.post(new Runnable() {
            @Override public void run() {
                if (toCalc && statsCalcAnchor != null) {
                    statsScrollY = statsCalcAnchor.getTop();
                    statsScroll.scrollTo(0, statsScrollY);
                } else {
                    scrollToSaved(statsScroll, statsScrollY);
                }
            }
        });
        return page;
    }

    private View statsPeriodChip(String label, final int days) {
        boolean sel = statsDays == days;
        TextView t = tv(label, 12, sel ? ON_ACCENT : TEXT, sel);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        t.setBackground(sel ? roundBg(ACCENT, 3, 0)
                : roundBg(SURFACE, 3, STROKE));
        tappableChip(t);
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (statsDays == days) return;
                statsDays = days;
                prefs.edit().putInt("statsDays", days).apply();
                statsMarks = null;
                statsHw = null;
                statsMarksError = null;
                statsHwError = null;
                statsAuth = false;
                statsLoading = false;   // старый запрос станет неактуальным
                statsScrollY = 0;
                renderTab();
            }
        });
        return t;
    }

    private void fetchStats(boolean force) {
        if (statsLoading && !force) return;
        final int gen = ++statsGen;
        statsLoading = true;
        statsAuth = false;
        statsMarksError = null;
        statsHwError = null;

        final String mFrom = statsMarksFromDate();
        final String mTo = today();
        final String hFrom = shiftDate(today(), -30);
        final String hTo = shiftDate(today(), 14);

        if (!force) {
            String raw = prefs.getString("statsm2:" + mFrom + ":" + mTo, null);
            if (raw != null) {
                try {
                    statsMarks = new JSONArray(raw);
                    statsMarksFrom = mFrom;
                    statsMarksTo = mTo;
                } catch (Exception ignored) {
                }
            }
            raw = prefs.getString("statshw2:" + hFrom + ":" + hTo, null);
            if (raw != null) {
                try {
                    statsHw = new JSONArray(raw);
                    statsHwFrom = hFrom;
                    statsHwTo = hTo;
                } catch (Exception ignored) {
                }
            }
        }

        if (tab == TAB_STATS) renderTab();
        if (exec.isShutdown()) return;

        exec.execute(new Runnable() {
            @Override public void run() {
                JSONArray marks = null;
                JSONArray hw = null;
                Exception me = null;
                Exception he = null;
                boolean auth = false;
                try {
                    Api api = Api.fromCookies();
                    try {
                        marks = api.marks(mFrom, mTo);
                        cachePut("statsm2:" + mFrom + ":" + mTo, marks.toString());
                    } catch (Api.AuthException e) {
                        auth = true;
                        me = e;
                    } catch (Exception e) {
                        me = e;
                    }
                    try {
                        hw = api.homeworksRange(hFrom, hTo);
                        cachePut("statshw2:" + hFrom + ":" + hTo, hw.toString());
                    } catch (Api.AuthException e) {
                        auth = true;
                        he = e;
                    } catch (Exception e) {
                        he = e;
                    }
                } catch (Api.AuthException e) {
                    auth = true;
                    me = e;
                    he = e;
                } catch (Exception e) {
                    me = e;
                    he = e;
                }

                final JSONArray fMarks = marks;
                final JSONArray fHw = hw;
                final Exception fMe = me;
                final Exception fHe = he;
                final boolean fAuth = auth;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (gen != statsGen) return;
                        statsLoading = false;
                        statsAuth = fAuth;
                        statsMarksError = fMe;
                        statsHwError = fHe;
                        if (fMarks != null) {
                            statsMarks = fMarks;
                            statsMarksFrom = mFrom;
                            statsMarksTo = mTo;
                        }
                        if (fHw != null) {
                            statsHw = fHw;
                            statsHwFrom = hFrom;
                            statsHwTo = hTo;
                        }
                        if (tab == TAB_STATS) renderTab();
                    }
                });
            }
        });
    }

    private final class SubjectStat {
        String name = "";
        int n5, n4, n3, n2, other, count;
        double sum, weight;

        double avg() {
            return weight > 0 ? sum / weight : 0;
        }

        int scored() {
            return count - other;
        }

        int quality() {
            int n = scored();
            return n > 0 ? Math.round((n4 + n5) * 100f / n) : 0;
        }

        int success() {
            int n = scored();
            return n > 0 ? Math.round((n - n2) * 100f / n) : 0;
        }

        int qualityColor() {
            int q = quality();
            return q >= 75 ? GREEN : q >= 50 ? AMBER : RED;
        }

        int successColor() {
            int s = success();
            return s >= 90 ? GREEN : s >= 70 ? AMBER : RED;
        }
    }

    private static double numericMark(String value) {
        if (value == null) return 0;
        String v = value.trim().replace(',', '.');
        try {
            double d = Double.parseDouble(v);
            if (d >= 1 && d <= 5) return d;
        } catch (Exception ignored) {
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c >= '2' && c <= '5') return c - '0';
        }
        return 0;
    }

    private void addMark(SubjectStat st, double v, int weight) {
        st.count++;
        if (v <= 0) {
            st.other++;
            return;
        }
        st.sum += v * weight;
        st.weight += weight;
        int g = (int) Math.round(v);
        if (g == 5) st.n5++;
        else if (g == 4) st.n4++;
        else if (g == 3) st.n3++;
        else st.n2++;
    }

    private Map<String, SubjectStat> collectSubjectStats(JSONArray items) {
        Map<String, SubjectStat> map = new LinkedHashMap<>();
        SubjectStat all = new SubjectStat();
        all.name = "Все предметы";
        map.put(all.name, all);

        for (int i = 0; i < items.length(); i++) {
            JSONObject m = items.optJSONObject(i);
            if (m == null) continue;
            String name = cleanSubjectName(Api.s(m, "subject_name"));
            if (name.isEmpty()) name = "Без предмета";
            SubjectStat st = map.get(name);
            if (st == null) {
                st = new SubjectStat();
                st.name = name;
                map.put(name, st);
            }
            int w = m.optInt("weight", 1);
            if (w <= 0) w = 1;
            double v = numericMark(Api.s(m, "value"));
            addMark(st, v, w);
            // Повторяем счётчики в общую сумму, не пересчитывая заново.
            all.count++;
            if (v <= 0) {
                all.other++;
            } else {
                all.sum += v * w;
                all.weight += w;
                int g = (int) Math.round(v);
                if (g == 5) all.n5++;
                else if (g == 4) all.n4++;
                else if (g == 3) all.n3++;
                else all.n2++;
            }
        }
        return map;
    }

    private int[] halfAverages(JSONArray items) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject m = items.optJSONObject(i);
            if (m != null && numericMark(Api.s(m, "value")) > 0) list.add(m);
        }
        if (list.size() < 4) return null;
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                return Api.s(a, "date").compareTo(Api.s(b, "date"));
            }
        });
        int mid = list.size() / 2;
        double s1 = 0, w1 = 0, s2 = 0, w2 = 0;
        for (int i = 0; i < list.size(); i++) {
            JSONObject m = list.get(i);
            int w = Math.max(1, m.optInt("weight", 1));
            double v = numericMark(Api.s(m, "value"));
            if (i < mid) {
                s1 += v * w;
                w1 += w;
            } else {
                s2 += v * w;
                w2 += w;
            }
        }
        if (w1 <= 0 || w2 <= 0) return null;
        return new int[]{Math.round((float) (s1 / w1 * 100)),
                Math.round((float) (s2 / w2 * 100))};
    }

    private View gradeSummaryCard(SubjectStat all, JSONArray items, int subjects) {
        double avg = all.avg();
        int color = avg >= 4.5 ? GREEN : avg >= 3.5 ? AMBER : RED;

        LinearLayout card = card(SURFACE);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView big = tv(String.format(Locale.US, "%.2f", avg), 32, color, true);
        big.setSingleLine(true);
        left.addView(big);
        TextView cap = tv("средний балл", 10, MUTED, false);
        left.addView(cap);
        top.addView(left, new LinearLayout.LayoutParams(
                Math.max(dp(96), textColWidth(big, cap)),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView grade = tv("выйдет " + Stats.gradeOf(avg), 15, color, true);
        grade.setGravity(Gravity.END);
        right.addView(grade);
        TextView meta = tv(all.count + " " + plural(all.count,
                "оценка", "оценки", "оценок") + " · " + subjects + " "
                + plural(subjects, "предмет", "предмета", "предметов"),
                10, MUTED, false);
        meta.setGravity(Gravity.END);
        right.addView(meta);
        if (all.other > 0) {
            TextView o = tv("ещё " + all.other + " без балла", 10, MUTED, false);
            o.setGravity(Gravity.END);
            right.addView(o);
        }
        top.addView(right, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);
        card.addView(markBar(avg, color));

        int[] halves = halfAverages(items);
        if (halves != null && halves[0] != halves[1]) {
            int delta = halves[1] - halves[0];
            String txt = (delta > 0 ? "↑ +" : "↓ ")
                    + String.format(Locale.US, "%.2f", Math.abs(delta / 100.0))
                    + " за период";
            TextView tr = tv(txt, 11, delta > 0 ? GREEN : RED, false);
            tr.setPadding(0, dp(5), 0, 0);
            card.addView(tr);
        }

        TextView dh = tv("Распределение", 10, MUTED, true);
        dh.setLetterSpacing(0.06f);
        dh.setPadding(0, dp(12), 0, dp(2));
        card.addView(dh);
        int scored = Math.max(0, all.scored());
        int total = Math.max(1, scored);
        card.addView(distRow("5", all.n5, scored, pct(all.n5, total)));
        card.addView(distRow("4", all.n4, scored, pct(all.n4, total)));
        card.addView(distRow("3", all.n3, scored, pct(all.n3, total)));
        card.addView(distRow("2", all.n2, scored, pct(all.n2, total)));
        return card;
    }

    private static int pct(int n, int total) {
        return total > 0 ? Math.round(n * 100f / total) : 0;
    }

    private View metricTile(String label, String value, String sub, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundBg(SURFACE, 3, STROKE));
        card.addView(tv(label, 10, MUTED, true));
        TextView v = tv(value, 22, color, true);
        v.setPadding(0, dp(2), 0, 0);
        card.addView(v);
        TextView s = tv(sub, 10, MUTED, false);
        s.setMaxLines(2);
        card.addView(s);
        return card;
    }

    private View metricRow(View a, View b) {
        LinearLayout row = new LinearLayout(this);
        boolean stack = tight();
        row.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        if (stack) {
            row.addView(a, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(8);
            row.addView(b, blp);
        } else {
            row.addView(a, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blp.leftMargin = dp(8);
            row.addView(b, blp);
        }
        return row;
    }

    private View subjectsCard(Map<String, SubjectStat> map) {
        List<SubjectStat> list = new ArrayList<>();
        for (SubjectStat st : map.values()) {
            if (!"Все предметы".equals(st.name) && st.scored() > 0) {
                list.add(st);
            }
        }
        Collections.sort(list, new Comparator<SubjectStat>() {
            @Override public int compare(SubjectStat a, SubjectStat b) {
                int c = Double.compare(b.avg(), a.avg());
                if (c != 0) return c;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        if (list.isEmpty()) return spacer(dp(0));

        LinearLayout card = card(SURFACE);
        card.setPadding(dp(16), dp(12), dp(16), dp(6));
        card.addView(tv("По предметам", 15, TEXT, true));
        card.addView(tv("средний балл и качество знаний", 11, MUTED, false));

        for (int i = 0; i < list.size(); i++) {
            if (i == 0) {
                View line = new View(this);
                line.setBackgroundColor(DIVIDER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                lp.topMargin = dp(10);
                card.addView(line, lp);
            }
            card.addView(subjectStatRow(list.get(i)));
        }
        return card;
    }

    private View subjectStatRow(final SubjectStat st) {
        double avg = st.avg();
        int color = avg >= 4.5 ? GREEN : avg >= 3.5 ? AMBER : RED;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(4), dp(9), dp(4), dp(9));
        row.setClickable(true);
        row.setBackground(rippleBg(0x00000000, 4));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = tv(st.name, 14, TEXT, true);
        name.setMaxLines(2);
        top.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView a = tv(String.format(Locale.US, "%.2f", avg), 16, color, true);
        a.setGravity(Gravity.END);
        top.addView(a);
        row.addView(top);
        row.addView(markBar(avg, color));

        java.util.List<View> tags = new java.util.ArrayList<>();
        tags.add(tag(st.count + " " + plural(st.count,
                "оценка", "оценки", "оценок"), MUTED, SURFACE2));
        tags.add(tag("качество " + st.quality() + "%",
                st.qualityColor(), soft(st.qualityColor())));
        if (st.n5 > 0) tags.add(tag("5 · " + st.n5, GREEN, soft(GREEN)));
        if (st.n4 > 0) tags.add(tag("4 · " + st.n4, MARK4, soft(MARK4)));
        if (st.n3 > 0) tags.add(tag("3 · " + st.n3, AMBER, soft(AMBER)));
        if (st.n2 > 0) tags.add(tag("2 · " + st.n2, RED, soft(RED)));
        LinearLayout flow = chipFlow(tags);
        flow.setPadding(0, dp(5), 0, 0);
        row.addView(flow);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { subjectStatsDialog(st); }
        });
        return row;
    }

    private void subjectStatsDialog(final SubjectStat st) {
        double avg = st.avg();
        int color = avg >= 4.5 ? GREEN : avg >= 3.5 ? AMBER : RED;
        String msg = "Средний балл: " + String.format(Locale.US, "%.2f", avg)
                + "\nВыйдет: " + Stats.gradeOf(avg)
                + "\nОценок: " + st.count + (st.other > 0
                ? " (+" + st.other + " без балла)" : "")
                + "\n\n5: " + st.n5 + "   4: " + st.n4
                + "   3: " + st.n3 + "   2: " + st.n2
                + "\n\nКачество: " + st.quality() + "%"
                + "\nУспеваемость: " + st.success() + "%";
        new AlertDialog.Builder(this)
                .setTitle(st.name)
                .setMessage(msg)
                .setPositiveButton("В калькулятор", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        statsSubject = st.name;
                        prefs.edit().putString("statsSubject", statsSubject).apply();
                        statsScrollToCalc = true;
                        renderTab();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    // ------------------------------------------------------------- калькулятор

    private View statsCalculatorCard(final Map<String, SubjectStat> map) {
        final List<String> names = new ArrayList<>(map.keySet());
        if (names.remove("Все предметы")) names.add(0, "Все предметы");
        if (names.size() > 2) {
            Collections.sort(names.subList(1, names.size()),
                    String.CASE_INSENSITIVE_ORDER);
        }
        final String[] subject = {map.containsKey(statsSubject)
                ? statsSubject : "Все предметы"};
        final int[] target = {statsTarget};
        final int[] future = {Math.max(1, Math.min(10, statsFuture))};

        LinearLayout card = card(SURFACE);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(tv("Калькулятор оценки", 15, TEXT, true));
        card.addView(tv("что нужно, чтобы вышла нужная итоговая", 11, MUTED, false));

        LinearLayout pick = new LinearLayout(this);
        pick.setOrientation(LinearLayout.HORIZONTAL);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        pick.setClickable(true);
        pick.setBackground(rippleBg(SURFACE2, 3));
        pick.setPadding(dp(10), dp(9), dp(10), dp(9));

        final TextView subjectLabel = tv(subject[0], 13, TEXT, true);
        subjectLabel.setSingleLine(true);
        subjectLabel.setEllipsize(TextUtils.TruncateAt.END);
        pick.addView(subjectLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pick.addView(tv("выбрать ▾", 11, ACCENT, true));
        card.addView(pick, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout targets = new LinearLayout(this);
        targets.setOrientation(LinearLayout.HORIZONTAL);
        targets.setPadding(0, dp(8), 0, 0);
        final Runnable[] refresh = new Runnable[1];
        final TextView[] tchips = new TextView[3];
        final int[] grades = {3, 4, 5};
        for (int i = 0; i < grades.length; i++) {
            final int g = grades[i];
            final TextView t = tv("на " + g, 12, TEXT, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(12), dp(9), dp(12), dp(9));
            t.setMinHeight(dp(38));
            t.setBackground(roundBg(SURFACE2, 3, STROKE));
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    target[0] = g;
                    statsTarget = g;
                    prefs.edit().putInt("statsTarget", g).apply();
                    styleCalcChip(tchips[0], target[0] == 3);
                    styleCalcChip(tchips[1], target[0] == 4);
                    styleCalcChip(tchips[2], target[0] == 5);
                    if (refresh[0] != null) refresh[0].run();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(6);
            targets.addView(t, lp);
            tchips[i] = t;
        }
        card.addView(targets);

        LinearLayout stepper = new LinearLayout(this);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setPadding(0, dp(10), 0, 0);
        stepper.addView(tv("Ещё оценок", 12, MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView minus = tv("−", 18, TEXT, true);
        minus.setGravity(Gravity.CENTER);
        minus.setBackground(roundBg(SURFACE2, 3, STROKE));
        minus.setClickable(true);
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(34), dp(34)));

        final TextView futureLabel = tv(String.valueOf(future[0]), 15, TEXT, true);
        futureLabel.setGravity(Gravity.CENTER);
        stepper.addView(futureLabel, new LinearLayout.LayoutParams(
                dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView plus = tv("+", 18, TEXT, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(roundBg(SURFACE2, 3, STROKE));
        plus.setClickable(true);
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(34), dp(34)));
        card.addView(stepper);

        final TextView big = tv("—", 28, ACCENT, true);
        big.setPadding(0, dp(10), 0, 0);
        final TextView explanation = tv("", 11, MUTED, false);
        final TextView current = tv("", 11, MUTED, false);
        current.setPadding(0, dp(2), 0, 0);
        final TextView needLine = tv("", 11, MUTED, false);
        card.addView(big);
        card.addView(explanation);
        card.addView(current);
        card.addView(needLine);

        TextView outLabel = tv("Если следующая оценка:", 10, MUTED, true);
        outLabel.setLetterSpacing(0.06f);
        outLabel.setPadding(0, dp(9), 0, 0);
        card.addView(outLabel);
        LinearLayout outRow = new LinearLayout(this);
        outRow.setOrientation(LinearLayout.HORIZONTAL);
        final int[] outMarks = {5, 4, 3};
        final int[] outColors = {GREEN, MARK4, AMBER};
        final TextView[] outs = new TextView[3];
        for (int i = 0; i < outs.length; i++) {
            TextView t = tv("", 11, outColors[i], false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(6), dp(6), dp(6), dp(6));
            t.setBackground(roundBg(SURFACE2, 3, STROKE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(6);
            outRow.addView(t, lp);
            outs[i] = t;
        }
        card.addView(outRow);

        final LinearLayout scenarios = new LinearLayout(this);
        scenarios.setOrientation(LinearLayout.VERTICAL);
        scenarios.setPadding(0, dp(8), 0, 0);
        card.addView(scenarios);
        final TextView[] scen = new TextView[5];
        for (int i = 0; i < 5; i++) {
            scen[i] = tv("", 11, MUTED, false);
            scen[i].setPadding(0, dp(1), 0, dp(1));
            scenarios.addView(scen[i]);
        }

        final Runnable update = new Runnable() {
            @Override public void run() {
                SubjectStat st = map.get(subject[0]);
                if (st == null) st = map.get("Все предметы");
                subjectLabel.setText(subject[0]);
                futureLabel.setText(String.valueOf(future[0]));
                styleCalcChip(tchips[0], target[0] == 3);
                styleCalcChip(tchips[1], target[0] == 4);
                styleCalcChip(tchips[2], target[0] == 5);

                double s = st.sum;
                double w = st.weight;
                double avg = st.avg();
                int grade = target[0];
                int proj = Stats.gradeOf(avg);
                double req = Stats.requiredAverage(s, w, grade, future[0]);
                int min = Stats.minMarks(s, w, grade);

                if (st.weight <= 0) {
                    big.setText("—");
                    big.setTextColor(MUTED);
                    explanation.setText("По этому предмету пока нет оценок с баллом");
                    current.setText("");
                } else if (min == 0) {
                    big.setText("уже есть");
                    big.setTextColor(GREEN);
                    boolean safe2 = Stats.projectedAfter(s, w, 2) >= Stats.thresholdFor(grade);
                    explanation.setText("Средний " + String.format(Locale.US, "%.2f", avg)
                            + " уже даёт " + grade + "."
                            + (safe2 ? " Даже двойка не помешает." : ""));
                    current.setText("Сейчас " + String.format(Locale.US, "%.2f", avg)
                            + " · выйдет " + proj + " · оценок: " + st.scored());
                } else if (req > 5.0001) {
                    big.setText("> 5");
                    big.setTextColor(RED);
                    explanation.setText("За " + future[0] + " "
                            + plural(future[0], "оценку", "оценки", "оценок")
                            + " не выйдет: нужно ещё " + min + " "
                            + plural(min, "пятёрка", "пятёрки", "пятёрок") + " подряд");
                    current.setText("Сейчас " + String.format(Locale.US, "%.2f", avg)
                            + " · выйдет " + proj + " · оценок: " + st.scored());
                } else {
                    big.setText(String.format(Locale.US, "%.2f", req));
                    big.setTextColor(req <= 4 ? GREEN : req <= 4.8 ? AMBER : RED);
                    explanation.setText("средний балл за " + future[0] + " "
                            + plural(future[0], "оценку", "оценки", "оценок")
                            + ", чтобы вышло " + grade);
                    current.setText("Сейчас " + String.format(Locale.US, "%.2f", avg)
                            + " · выйдет " + proj + " · оценок: " + st.scored());
                }

                if (w <= 0) {
                    needLine.setText("");
                } else if (min <= 0) {
                    needLine.setText("До цели уже хватает — пятёрки не обязательны");
                    needLine.setTextColor(GREEN);
                } else {
                    needLine.setText("Пятёрок подряд до цели: " + min);
                    needLine.setTextColor(MUTED);
                }
                for (int i = 0; i < outs.length; i++) {
                    double p = Stats.projectedAfter(s, w, outMarks[i]);
                    outs[i].setText(outMarks[i] + " → "
                            + (w > 0 ? String.format(Locale.US, "%.2f", p) : "—"));
                }

                for (int i = 0; i < 5; i++) {
                    int n = i + 1;
                    double r = Stats.requiredAverage(s, w, grade, n);
                    String txt;
                    if (w <= 0) {
                        txt = n + " → —";
                    } else if (r > 5.0001) {
                        txt = n + " → нужно больше 5";
                    } else if (r <= 2) {
                        txt = n + " → хватит любой";
                    } else {
                        txt = n + " → " + String.format(Locale.US, "%.2f", r);
                    }
                    scen[i].setText(txt);
                    scen[i].setTextColor(w <= 0 ? MUTED
                            : r > 5.0001 ? RED : r <= 2 ? GREEN : MUTED);
                }
            }
        };

        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int checked = names.indexOf(subject[0]);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Предмет")
                        .setSingleChoiceItems(names.toArray(new String[0]), checked,
                                new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                subject[0] = names.get(which);
                                statsSubject = subject[0];
                                prefs.edit().putString("statsSubject", statsSubject).apply();
                                d.dismiss();
                                update.run();
                            }
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
            }
        });

        minus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (future[0] <= 1) return;
                future[0]--;
                statsFuture = future[0];
                prefs.edit().putInt("statsFuture", statsFuture).apply();
                update.run();
            }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (future[0] >= 10) return;
                future[0]++;
                statsFuture = future[0];
                prefs.edit().putInt("statsFuture", statsFuture).apply();
                update.run();
            }
        });

        refresh[0] = update;
        update.run();
        return card;
    }

    private void styleCalcChip(TextView t, boolean sel) {
        t.setTextColor(sel ? ON_ACCENT : TEXT);
        t.setTypeface(sel ? fontSerifBold : fontMedium);
        t.setBackground(sel ? roundBg(ACCENT, 3, 0) : roundBg(SURFACE2, 3, STROKE));
    }

    // ------------------------------------------------------------- ДЗ в итогах

    private void renderHwStats(LinearLayout content, JSONArray items) {
        if (items.length() == 0) {
            content.addView(sectionHeader("ДОМАШНЕЕ ЗАДАНИЕ", 0));
            content.addView(emptyCard("Заданий нет",
                    "За последние 30 дней и на две недели вперёд ничего нет"));
            return;
        }

        content.addView(sectionHeader("ДОМАШНЕЕ ЗАДАНИЕ", 0));
        TextView range = tv("30 дней назад и 2 недели вперёд · "
                + "галочки ставятся на этом телефоне", 10, MUTED, false);
        range.setPadding(dp(4), 0, dp(4), dp(6));
        content.addView(range);
        content.addView(hwProgressCard(items));

        Map<String, int[]> bySubject = new LinkedHashMap<>();
        List<JSONObject> undone = new ArrayList<>();
        String today = today();
        for (int i = 0; i < items.length(); i++) {
            JSONObject h = items.optJSONObject(i);
            if (h == null) continue;
            String name = Api.s(h, "subject");
            if (name.isEmpty()) name = "Без предмета";
            int[] n = bySubject.get(name);
            if (n == null) {
                n = new int[]{0, 0};
                bySubject.put(name, n);
            }
            n[0]++;
            if (hwDone(h)) {
                n[1]++;
            } else {
                undone.add(h);
            }
        }

        List<String> subs = new ArrayList<>(bySubject.keySet());
        Collections.sort(subs, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int[] x = bySubject.get(a);
                int[] y = bySubject.get(b);
                int lx = x[0] - x[1];
                int ly = y[0] - y[1];
                if (lx != ly) return ly - lx;
                return a.compareToIgnoreCase(b);
            }
        });

        if (!subs.isEmpty()) {
            LinearLayout card = card(SURFACE);
            card.setPadding(dp(16), dp(12), dp(16), dp(8));
            card.addView(tv("По предметам", 15, TEXT, true));
            card.addView(tv("сколько сделано из заданного", 11, MUTED, false));
            int limit = Math.min(subs.size(), 8);
            for (int i = 0; i < limit; i++) {
                String name = subs.get(i);
                int[] n = bySubject.get(name);
                card.addView(hwSubjectStatRow(name, n[1], n[0]));
            }
            if (subs.size() > limit) {
                TextView more = tv("и ещё " + (subs.size() - limit) + " "
                        + plural(subs.size() - limit, "предмет", "предмета", "предметов"),
                        11, MUTED, false);
                more.setPadding(0, dp(6), 0, 0);
                card.addView(more);
            }
            content.addView(card);
        }

        Collections.sort(undone, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                int c = Api.s(a, "date").compareTo(Api.s(b, "date"));
                if (c != 0) return c;
                return Api.s(a, "subject").compareTo(Api.s(b, "subject"));
            }
        });

        content.addView(sectionHeader("НЕ СДЕЛАНО", undone.size()));
        if (undone.isEmpty()) {
            content.addView(emptyCard("Всё сделано",
                    "Среди заданий за 30 дней и на две недели вперёд"));
        } else {
            LinearLayout card = card(SURFACE);
            card.setPadding(dp(8), dp(4), dp(8), dp(4));
            int limit = Math.min(undone.size(), 12);
            for (int i = 0; i < limit; i++) {
                final JSONObject h = undone.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(6), dp(9), dp(6), dp(9));

                row.addView(hwDoneBox(h, new Runnable() {
                    @Override public void run() {
                        if (tab == TAB_STATS) {
                            if (statsScroll != null) statsScrollY = statsScroll.getScrollY();
                            renderTab();
                        }
                    }
                }), new LinearLayout.LayoutParams(dp(28), dp(28)));

                LinearLayout body = new LinearLayout(this);
                body.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                blp.leftMargin = dp(10);
                row.addView(body, blp);

                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                String subject = Api.s(h, "subject");
                TextView s = tv(subject.isEmpty() ? "Задание" : subject, 13, TEXT, true);
                s.setMaxLines(1);
                s.setEllipsize(TextUtils.TruncateAt.END);
                top.addView(s, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                String date = Api.s(h, "date");
                int dc = date.compareTo(today) < 0 ? RED : MUTED;
                top.addView(tv(hwDateLabel(date), 10, dc, false));
                body.addView(top);

                String text = Api.s(h, "text").trim();
                if (text.isEmpty()) text = "(см. материалы)";
                TextView t = tv(text, 11, MUTED, false);
                t.setMaxLines(2);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(0, dp(2), 0, 0);
                body.addView(t);

                card.addView(row);
            }
            content.addView(card);
            if (undone.size() > limit) {
                TextView more = tv("Показаны первые " + limit + " из " + undone.size()
                        + ". Остальные — на вкладке ДЗ.", 11, MUTED, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                content.addView(more, lp);
            }
        }
    }

    private View hwSubjectStatRow(String name, int done, int total) {
        int percent = total > 0 ? Math.round(done * 100f / total) : 0;
        int color = percent >= 80 ? GREEN : percent >= 50 ? AMBER : RED;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(4), dp(8), dp(4), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = tv(name, 13, TEXT, false);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(n, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(tv(done + " / " + total, 12, color, true));
        row.addView(top);

        ProgressBar pb = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        pb.setMax(Math.max(1, total));
        pb.setProgress(done);
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        lp.topMargin = dp(5);
        row.addView(pb, lp);
        return row;
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
                    if (tab == TAB_MORE) renderTab();
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
                            if (tab == TAB_MORE) renderTab();
                            else updateChrome();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            profileLoading = false;
                            profileError = e;
                            if (tab == TAB_MORE) renderTab();
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
        JSONObject kid = selectedChild(kids);

        if (prof != null) {
            parentName = joinNonEmpty(" ",
                    Api.s(prof, "last_name"), Api.s(prof, "first_name"),
                    Api.s(prof, "middle_name"));
        }

        // Родитель: в profile — взрослый, ребёнок отдельно в children.
        // Надёжнее всего роль из cookie, иначе — несколько детей в профиле.
        String profId = prof == null ? "" : Api.s(prof, "id");
        String kidId = kid == null ? "" : Api.s(kid, "id");
        if (Api.isParentRole()) parentAccount = true;
        else if (Api.isStudentRole()) parentAccount = false;
        else parentAccount = kids != null && kids.length() > 1;
        Api.setParentAccount(parentAccount);

        studentName = parentName;
        studentId = "";
        if (kid != null) {
            studentClass = Api.s(kid, "class_name");
            studentId = kidId;
            JSONObject school = kid.optJSONObject("school");
            studentSchool = school == null ? "" : Api.s(school, "short_name");
            if (parentAccount) {
                String kidName = joinNonEmpty(" ",
                        Api.s(kid, "last_name"), Api.s(kid, "first_name"),
                        Api.s(kid, "middle_name"));
                if (!kidName.isEmpty()) studentName = kidName;
            }
        }
        if (studentId.isEmpty()) studentId = profId;
    }

    private JSONObject selectedChild(JSONArray kids) {
        if (kids == null || kids.length() == 0) return null;
        if (!selectedChildId.isEmpty()) {
            for (int i = 0; i < kids.length(); i++) {
                JSONObject k = kids.optJSONObject(i);
                if (k != null && selectedChildId.equals(Api.s(k, "id"))) return k;
            }
        }
        return kids.optJSONObject(0);
    }

    private void switchChild(JSONObject kid) {
        String id = Api.s(kid, "id");
        if (id.isEmpty() || id.equals(selectedChildId)) return;
        selectedChildId = id;
        selectedChildPerson = Api.s(kid, "contingent_guid");
        prefs.edit()
                .putString("child_id", selectedChildId)
                .putString("child_person", selectedChildPerson)
                .apply();
        Api.selectStudent(selectedChildId, selectedChildPerson);
        resetStudentData();
        if (profileData != null) applyProfile(profileData);
        renderTab();
    }

    private void resetStudentData() {
        dayData = null;
        dayError = null;
        dayAuth = false;
        dayGen++;
        hwData = null;
        hwError = null;
        hwAuth = false;
        hwGen++;
        marksData = null;
        marksError = null;
        marksAuth = false;
        marksGen++;
        ratingData = null;
        ratingError = null;
        ratingAuth = false;
        ratingGen++;
        finalData = null;
        finalError = null;
        finalAuth = false;
        finalGen++;
        updateChrome();
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
        card.setBackground(roundBg(ERR_BG, 3, ERR_STROKE));
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

            long size = Api.numLong(f, "size");
            String by = Api.s(f, "uploaded_by").trim();
            boolean fromTeacher = by.equalsIgnoreCase("teacher");
            String desc = fromTeacher ? "от учителя" : "мой файл";
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

            if (window && !fromTeacher) {
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
            final String workIdNow = Api.workId(studentWork);
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
                            try {
                                JSONObject created = api.createStudentWork(lessonId, var);
                                workId = Api.workId(created);
                            } catch (Api.ApiException e) {
                                // 409 — работа уже создана (например, с другого
                                // устройства или при повторной отправке).
                                if (e.status == 409) {
                                    workId = api.existingWorkId(lessonId);
                                } else {
                                    throw e;
                                }
                            }
                            if (workId.isEmpty()) {
                                throw new Api.ApiException("МЭШ не вернул id работы "
                                        + "(работа уже есть, но id не пришёл — "
                                        + "переоткройте урок)");
                            }
                        }
                        if (var != null
                                && (oldVariant == null || !oldVariant.equals(var))) {
                            try {
                                api.updateVariant(workId, var);
                            } catch (Exception ignored) {
                            }
                        }

                        int order = nextOrder(studentWork);
                        JSONArray regs = new JSONArray();
                        int failed = 0;
                        String firstError = null;
                        for (PickedPhoto p : files) {
                            try {
                                JSONObject acc = api.storageAccess(workId);
                                String link = Api.s(acc, "file_link");
                                String tok = Api.s(acc, "token");
                                String uuid = Api.s(acc, "file_uuid");
                                if (link.isEmpty() || tok.isEmpty() || uuid.isEmpty()) {
                                    throw new Api.ApiException(
                                            "нет доступа к хранилищу: " + acc);
                                }
                                api.uploadFile(link, tok, p.name, "image/jpeg", p.data);
                                String ext = "jpg";
                                int dot = p.name.lastIndexOf('.');
                                if (dot > 0 && dot < p.name.length() - 1) {
                                    ext = p.name.substring(dot + 1).toLowerCase();
                                }
                                JSONObject f = new JSONObject();
                                Api.put(f, "file_uuid", uuid);
                                Api.put(f, "file_name", p.name);
                                Api.put(f, "order_number", order++);
                                Api.put(f, "size", p.data.length);
                                Api.put(f, "extension", ext);
                                regs.put(f);
                            } catch (Exception e) {
                                failed++;
                                if (firstError == null) firstError = e.getMessage();
                            }
                        }

                        if (regs.length() > 0) api.upsertFiles(workId, regs);

                        final int bad = failed;
                        final String why = firstError;
                        ui.post(new Runnable() {
                            @Override public void run() {
                                pd.dismiss();
                                uploading = false;
                                if (bad == files.size()) {
                                    if (why == null || why.isEmpty()) {
                                        toast("Не удалось отправить работу");
                                    } else {
                                        errorDialog(new Exception(why));
                                    }
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
                        if (f == null) continue;
                        int n = (int) Api.numLong(f, "ordinal");
                        if (n == 0) n = (int) Api.numLong(f, "order_number");
                        max = Math.max(max, n);
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
            final String workId = Api.workId(studentWork);
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
            ImageView ic = icon(iconRes, ON_ACCENT, 16);
            row.addView(ic, new LinearLayout.LayoutParams(dp(16), dp(16)));
        }

        TextView t = tvSans(label, 14, ON_ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        row.setMinimumHeight(dp(44));
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
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        row.setMinimumHeight(dp(44));
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
        } else if (req == REQ_NOTIF) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                enableNotifications();
            } else {
                toast("Без разрешения уведомления не придут");
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

    /**
     * Диагностика темы ЦДЗ: сайт огромный, и глазами по скриншоту не видно,
     * какой именно элемент остался светлым. Долгое нажатие на луну отдаёт
     * список таких элементов, его можно скопировать и прислать.
     */
    private void themeReport(final WebView web) {
        toast("Собираю отчёт…");
        web.evaluateJavascript(
                "(window.__schPaper&&window.__schPaper.report)"
                        + "?window.__schPaper.report():''",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String raw) {
                        String text = unquoteJson(raw);
                        if (text == null || text.trim().isEmpty()) {
                            toast("Отчёт не готов: страница ещё грузится");
                            return;
                        }
                        showThemeReport(text);
                    }
                });
    }

    private static String unquoteJson(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty() || "null".equals(s)) return "";
        if (s.charAt(0) == '"') {
            try {
                return new JSONArray("[" + s + "]").optString(0);
            } catch (Exception e) {
                return s;
            }
        }
        return s;
    }

    private void showThemeReport(final String text) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(roundBg(BG, 4, STROKE));
        root.setPadding(dp(16), dp(14), dp(16), dp(10));

        root.addView(tvSerif("Отчёт о теме", 20, TEXT, true));
        TextView hint = tv("Скопируй и пришли: по нему видно, какие элементы "
                + "страницы остались светлыми.", 12, MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(8));
        root.addView(hint);

        TextView body = tv(text, 10, TEXT, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        row.addView(secondaryButton("Скопировать", new Runnable() {
            @Override public void run() {
                ClipboardManager cm = (ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Отчёт о теме", text));
                    toast("Отчёт скопирован");
                }
            }
        }));
        row.addView(secondaryButton("Поделиться", new Runnable() {
            @Override public void run() {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Отчёт о теме"));
            }
        }));
        root.addView(row);

        d.setContentView(root);
        Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(0x00000000));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(win.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            win.setAttributes(lp);
        }
        d.show();
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

        final ImageView themeBtn = icon(R.drawable.ic_theme,
                cdzDark ? ACCENT : MUTED, 20);
        themeBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        themeBtn.setBackground(rippleBg(0x00000000, 20));
        themeBtn.setContentDescription(cdzDark ? "Светлая тема" : "Тёмная тема");
        head.addView(themeBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        final ImageView reloadBtn = icon(R.drawable.ic_refresh, MUTED, 20);
        reloadBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        reloadBtn.setBackground(rippleBg(0x00000000, 20));
        reloadBtn.setContentDescription("Обновить задание");
        head.addView(reloadBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(head);

        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        root.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        final ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setProgressTintList(
                android.content.res.ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(DIVIDER));
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

        web.setBackgroundColor(cdzDark ? CDZ_BG : BG);

        // Своя заставка вместо «внутренней» загрузки ЦДЗ: та появляется
        // белой полосой в верхней части экрана, пока сайт не докрасился.
        final LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setBackgroundColor(cdzDark ? CDZ_BG : BG);

        final ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams spinnerLp =
                new LinearLayout.LayoutParams(dp(34), dp(34));
        spinnerLp.bottomMargin = dp(16);
        loading.addView(spinner, spinnerLp);

        final TextView loadingText = tv("Открываю задание…", 14, MUTED, false);
        loadingText.setGravity(Gravity.CENTER);
        loading.addView(loadingText);

        final FrameLayout stage = new FrameLayout(this);
        stage.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        stage.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        reloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.animate().rotationBy(360f).setDuration(500).start();
                web.reload();
            }
        });

        themeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cdzDark = !cdzDark;
                prefs.edit().putString("cdz_theme", cdzDark ? "dark" : "light")
                        .apply();
                themeBtn.setColorFilter(cdzDark ? ACCENT : MUTED);
                themeBtn.setContentDescription(
                        cdzDark ? "Светлая тема" : "Тёмная тема");
                web.setBackgroundColor(cdzDark ? CDZ_BG : BG);
                loading.setBackgroundColor(cdzDark ? CDZ_BG : BG);
                web.evaluateJavascript("window.__schPaper&&window.__schPaper.set("
                        + cdzDark + ")", null);
            }
        });
        // Долгое нажатие на луну — отчёт о том, что осталось светлым.
        themeBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                themeReport(web);
                return true;
            }
        });

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
            public void onPageStarted(WebView view, String u, android.graphics.Bitmap icon) {
                super.onPageStarted(view, u, icon);
                // Прячем страницу до покраски: иначе видно, как светлая
                // версия переключается в тёмную, и мелькает загрузчик сайта.
                view.setAlpha(0f);
                spinner.setVisibility(View.VISIBLE);
                loadingText.setVisibility(View.VISIBLE);
                loadingText.setText("Открываю задание…");
                loading.animate().cancel();
                loading.setAlpha(1f);
                loading.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String u) {
                super.onPageFinished(view, u);
                String js = cdzTheme();
                if (!js.isEmpty()) {
                    view.evaluateJavascript(
                            "window.__schPalette=" + paletteJson() + ";"
                            + "window.__schDark=" + cdzDark + ";" + js, null);
                }
                view.postDelayed(new Runnable() {
                    @Override public void run() {
                        view.animate().alpha(1f).setDuration(200).start();
                        loading.animate().alpha(0f).setDuration(160)
                                .withEndAction(new Runnable() {
                                    @Override public void run() {
                                        loading.setVisibility(View.GONE);
                                    }
                                }).start();
                    }
                }, 180);
            }

            @Override
            public void onReceivedError(WebView view, int code, String desc,
                    String failingUrl) {
                super.onReceivedError(view, code, desc, failingUrl);
                spinner.setVisibility(View.GONE);
                loadingText.setText("Не удалось открыть задание");
                view.setAlpha(1f);
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

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(cdzDark ? CDZ_BG : BG));
            w.setDimAmount(0.6f);
            // Во весь экран: при высоте 97 % сверху оставалась полоска
            // предыдущего экрана, из-за которой окно выглядело кривым.
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
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


    private int uniqueFileCount(JSONObject h, JSONArray fa) {
        if (fa == null) return 0;
        Set<String> shown = new HashSet<>();
        JSONArray mats = h.optJSONArray("materials_list");
        if (mats != null) {
            for (int i = 0; i < mats.length(); i++) {
                JSONObject m = mats.optJSONObject(i);
                if (m != null) shown.add(Api.s(m, "title"));
            }
        }
        int n = 0;
        for (int i = 0; i < fa.length(); i++) {
            JSONObject f = fa.optJSONObject(i);
            if (f == null) continue;
            String title = Api.s(f, "title");
            if (title.isEmpty()) title = Api.s(f, "kind");
            if (title.isEmpty()) title = "файл";
            if (!shown.contains(title)) n++;
        }
        return n;
    }

    private void showHomeworkDetails(final JSONObject h) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(12), dp(8), dp(10));
        head.addView(tv("Домашнее задание", 17, TEXT, true),
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

        String subject = Api.s(h, "subject");
        box.addView(tvSerif(subject.isEmpty() ? "Задание" : subject, 25, TEXT, true));

        String date = Api.s(h, "date");
        String lt = Api.s(h, "lesson_time");
        String ls = Api.s(h, "lesson_start");
        StringBuilder when = new StringBuilder();
        if (!date.isEmpty()) when.append(hwDateLabel(date));
        if (!lt.isEmpty()) {
            if (when.length() > 0) when.append("  ·  ");
            when.append("урок ");
            if (!ls.isEmpty()) when.append(ls).append("–");
            else when.append("до ");
            when.append(lt);
        }
        if (when.length() > 0) {
            TextView w = tv(when.toString(), 12, MUTED, false);
            w.setPadding(0, dp(5), 0, 0);
            box.addView(w);
        }

        java.util.List<View> tagItems = new java.util.ArrayList<>();
        if (Api.b(h, "done")) {
            tagItems.add(tag("выполнено", GREEN, soft(GREEN)));
        } else {
            tagItems.add(tag("не выполнено", AMBER, 0x1F9A6A22));
        }
        final JSONObject cdz = firstDigital(h);
        if (cdz != null) tagItems.add(tag("ЦДЗ", ACCENT, ACCENT_SOFT));
        if (Api.b(h, "has_teacher_answer")) {
            tagItems.add(tag("ответ учителя", GREEN, soft(GREEN)));
        }
        JSONArray fa = h.optJSONArray("files");
        int files = uniqueFileCount(h, fa);
        if (files > 0) {
            tagItems.add(tag(files + " " + plural(files, "файл",
                    "файла", "файлов"), MUTED, SURFACE2));
        }
        if (tight()) {
            LinearLayout flow = chipFlow(tagItems);
            flow.setPadding(0, dp(10), 0, dp(2));
            box.addView(flow);
        } else {
            LinearLayout tags = new LinearLayout(this);
            tags.setOrientation(LinearLayout.HORIZONTAL);
            tags.setPadding(0, dp(10), 0, dp(2));
            for (int i = 0; i < tagItems.size(); i++) tags.addView(tagItems.get(i));
            box.addView(tags);
        }

        String text = Api.s(h, "text").trim();
        if (!text.isEmpty()) {
            box.addView(detailLabel("Задание"));
            TextView task = tvSerif(text, 16, TEXT, false);
            task.setLineSpacing(dp(4), 1f);
            task.setPadding(dp(14), dp(12), dp(14), dp(12));
            task.setBackground(roundBg(SURFACE, 3, STROKE));
            box.addView(task);
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
                View b;
                if (isDigital(m)) {
                    b = gradientButton("Пройти  ·  " + title, R.drawable.ic_play,
                            new Runnable() {
                                @Override public void run() {
                                    d.dismiss();
                                    openCdz(entry, uuid, title);
                                }
                            });
                } else if (!url.isEmpty()) {
                    b = smallButton("Открыть  ·  " + title, R.drawable.ic_import,
                            new Runnable() {
                                @Override public void run() { previewFile(url); }
                            });
                } else {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setPadding(dp(14), dp(11), dp(14), dp(11));
                    row.setBackground(roundBg(SURFACE, 3, STROKE));
                    row.addView(tv(title, 13, TEXT, false));
                    if (!action.isEmpty()) {
                        TextView a = tv(action, 11, MUTED, false);
                        a.setPadding(0, dp(2), 0, 0);
                        row.addView(a);
                    }
                    b = row;
                }
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = dp(6);
                box.addView(b, blp);
            }
        }

        if (fa != null && fa.length() > 0) {
            Set<String> shown = new HashSet<>();
            JSONArray mats0 = h.optJSONArray("materials_list");
            if (mats0 != null) {
                for (int i = 0; i < mats0.length(); i++) {
                    JSONObject m0 = mats0.optJSONObject(i);
                    if (m0 != null) shown.add(Api.s(m0, "title"));
                }
            }
            int shownFiles = 0;
            LinearLayout filesBox = column();
            for (int i = 0; i < fa.length(); i++) {
                JSONObject f = fa.optJSONObject(i);
                if (f == null) continue;
                String title = Api.s(f, "title");
                if (title.isEmpty()) title = Api.s(f, "kind");
                if (title.isEmpty()) title = "файл";
                if (shown.contains(title)) continue;
                final String furl = Api.s(f, "url");
                TextView ft = tv(title, 13, furl.isEmpty() ? TEXT : ACCENT, false);
                ft.setPadding(dp(14), dp(11), dp(14), dp(11));
                ft.setBackground(roundBg(SURFACE, 3, STROKE));
                if (!furl.isEmpty()) {
                    ft.setClickable(true);
                    ft.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { previewFile(furl); }
                    });
                }
                LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                flp.topMargin = dp(6);
                filesBox.addView(ft, flp);
                shownFiles++;
            }
            if (shownFiles > 0) {
                box.addView(detailLabel("Файлы"));
                box.addView(filesBox);
            }
        }

        if (Api.b(h, "has_teacher_answer")) {
            box.addView(detailLabel("Ответ учителя"));
            LinearLayout ans = new LinearLayout(this);
            ans.setOrientation(LinearLayout.VERTICAL);
            ans.setPadding(dp(14), dp(12), dp(14), dp(12));
            ans.setBackground(roundBg(soft(GREEN), 3, 0));
            ans.addView(tv("Учитель ответил на работу", 14, GREEN, true));
            TextView note = tv("Текст ответа и отметка о проверке — в МЭШ, "
                    + "на карточке задания.", 12, MUTED, false);
            note.setPadding(0, dp(3), 0, 0);
            ans.addView(note);
            box.addView(ans);
        }

        final String shareText = homeworkShareText(h);
        if (!shareText.isEmpty()) {
            TextView copy = secondaryButton("Копировать", new Runnable() {
                @Override public void run() {
                    ClipboardManager cm = (ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText(
                                "Домашнее задание", shareText));
                        toast("Задание скопировано");
                    }
                }
            });
            TextView share = secondaryButton("Поделиться", new Runnable() {
                @Override public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, shareText);
                    startActivity(Intent.createChooser(i, "Поделиться заданием"));
                }
            });
            View actions = buttonPair(copy, share);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = dp(14);
            box.addView(actions, alp);
        }

        LinearLayout dump = new LinearLayout(this);
        dump.setOrientation(LinearLayout.VERTICAL);
        String json;
        try {
            json = h.toString(2);
        } catch (Exception e) {
            json = String.valueOf(h);
        }
        TextView jt = tv(json, 11, MUTED, false);
        jt.setTypeface(Typeface.MONOSPACE);
        jt.setLineSpacing(dp(2), 1f);
        jt.setTextIsSelectable(true);
        dump.addView(jt);
        box.addView(section("Служебные данные", "все поля", false, dump));

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
    }

    /** ДЗ простым текстом — чтобы отправить родителю или в заметки. */
    private String homeworkShareText(JSONObject h) {
        StringBuilder sb = new StringBuilder();
        String subject = Api.s(h, "subject");
        if (!subject.isEmpty()) sb.append(subject);
        String date = Api.s(h, "date");
        if (!date.isEmpty()) {
            String label = hwDateLabel(date);
            if (sb.length() > 0) sb.append(" · ");
            sb.append(label);
        }
        String lt = Api.s(h, "lesson_time");
        String ls = Api.s(h, "lesson_start");
        if (!lt.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("урок ");
            if (!ls.isEmpty()) sb.append(ls).append("–");
            else sb.append("до ");
            sb.append(lt);
        }
        String text = Api.s(h, "text").trim();
        if (!text.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(text);
        }
        JSONArray mats = h.optJSONArray("materials_list");
        if (mats != null && mats.length() > 0) {
            sb.append("\n\nМатериалы:");
            for (int i = 0; i < mats.length(); i++) {
                JSONObject m = mats.optJSONObject(i);
                if (m == null) continue;
                String title = Api.s(m, "title");
                if (title.isEmpty()) continue;
                sb.append("\n• ").append(title);
            }
        }
        return sb.toString().trim();
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

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void about() {
        File dir = getExternalFilesDir(null);
        new AlertDialog.Builder(this)
                .setTitle("OpenSchool")
                .setMessage("OpenSchool. Дневник МЭШ. Версия " + versionName() + "\n\n"
                        + "Данные берутся с school.mos.ru по вашей сессии. "
                        + "Cookies хранятся только внутри приложения.\n\n"
                        + "Вход: «Ещё → Войти в МЭШ» или кнопка на экране входа.\n"
                        + "Импорт: ~/.cache/sch/cookies.json с ПК.\n"
                        + "Папка для файла:\n"
                        + (dir == null ? "Android/data/ru.sch.diary/files"
                                       : dir.getAbsolutePath()) + "/cookies.json\n\n"
                        + "2.4: размер текста в настройках, копирование и отправка "
                        + "домашнего задания, плашка «нет связи» с временем данных.\n\n"
                        + "2.5: тёмная тема ЦДЗ переведена на токены дизайн-системы "
                        + "(карточки, рамки ответов, экран результата). Долгое нажатие "
                        + "на луну в окне задания — отчёт о том, что осталось светлым.\n\n"
                        + "2.6: темы оформления — Full black, Nord, Catppuccin и Gruvbox. "
                        + "Тема применяется и к приложению, и к окну задания.\n\n"
                        + "2.7: вкладка «Итоги» — средний балл с учётом веса, качество "
                        + "и успеваемость, распределение оценок, динамика, статистика "
                        + "по предметам, калькулятор «что нужно до 3/4/5» и чек-лист "
                        + "домашнего задания.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void clearCache() {
        SharedPreferences.Editor ed = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("day:") || key.startsWith("day2:") || key.startsWith("day3:")
                    || key.startsWith("marks:") || key.startsWith("marks2:")
                    || key.startsWith("hw3:") || key.startsWith("week3:")
                    || key.startsWith("final2:") || key.startsWith("rating:")
                    || key.startsWith("rankclass:") || key.equals("profile")
                    || key.endsWith("#at")) {
                ed.remove(key);
            }
        }
        ed.apply();
        dayData = null;
        marksData = null;
        marksFrom = null;
        marksTo = null;
        weekData = null;
        weekLoaded = null;
        finalData = null;
        ratingData = null;
        hwData = null;
        hwFrom = null;
        hwTo = null;
        toast("Кэш очищен");
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
        prefs.edit().putString(key, raw)
                .putLong(key + "#at", System.currentTimeMillis()).apply();
    }

    /** Когда эти данные реально пришли с сервера. */
    private String cacheAtLabel(String key) {
        long at = prefs.getLong(key + "#at", 0L);
        if (at <= 0) return "";
        Date d = new Date(at);
        String day = isoFormat().format(d);
        String hm = new SimpleDateFormat("HH:mm", Locale.US).format(d);
        if (day.equals(today())) return hm;
        return new SimpleDateFormat("dd.MM", Locale.US).format(d) + ", " + hm;
    }

    /**
     * Когда связи нет, приложение показывает кэш. Без этой плашки было
     * непонятно, что данные старые.
     */
    private View staleBanner(String key, final Runnable retry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(8), dp(8), dp(8));
        card.setBackground(roundBg(SURFACE2, 3, STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        String at = cacheAtLabel(key);
        TextView t = tv(at.isEmpty()
                ? "Нет связи — показаны сохранённые данные"
                : "Нет связи — данные от " + at,
                11, MUTED, false);
        t.setMaxLines(2);
        card.addView(t, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView b = tv("Обновить", 12, ACCENT, true);
        b.setSingleLine(true);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setBackground(roundBg(ACCENT_SOFT, 14, 0));
        b.setClickable(true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (retry != null) retry.run();
            }
        });
        card.addView(b);
        return card;
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

    // Экран узкий или шрифт увеличен — горизонтальные пары кнопок
    // в таких условиях превращаются в кашу из переносов.
    private boolean tight() {
        Configuration c = getResources().getConfiguration();
        return c.fontScale >= 1.3f || (c.screenWidthDp > 0 && c.screenWidthDp < 340);
    }

    private float textWidth(View v) {
        if (!(v instanceof TextView)) return 0f;
        TextView t = (TextView) v;
        CharSequence cs = t.getText();
        if (cs == null) return 0f;
        return t.getPaint().measureText(cs.toString())
                + t.getPaddingLeft() + t.getPaddingRight();
    }

    // Помещаются ли две кнопки в одну строку с учётом реальной ширины
    // текста и поля экрана, а не «на глаз».
    private boolean fitsSideBySide(View a, View b) {
        if (tight()) return false;
        int avail = getResources().getDisplayMetrics().widthPixels
                - dp(40) - dp(8);                 // поля диалога + зазор
        return textWidth(a) + textWidth(b) + dp(28) <= avail;
    }

    private View buttonPair(View a, View b) {
        LinearLayout row = new LinearLayout(this);
        boolean stack = !fitsSideBySide(a, b);
        row.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        if (stack) {
            row.addView(a, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            row.addView(b, lp);
        } else {
            row.addView(a, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dp(8);
            row.addView(b, lp);
        }
        return row;
    }

    // Ширина колонки под текст: фиксированные dp обрезают «08:30», когда
    // шрифт в системе увеличен.
    private int textColWidth(TextView... views) {
        float max = 0f;
        for (TextView v : views) max = Math.max(max, textWidth(v));
        return Math.max(dp(42), (int) Math.ceil(max) + dp(8));
    }

    // Раскладка чипов «сколько влезет в строку»: ряд из пяти оценок на
    // крупном шрифте иначе вылезал за экран и обрезался справа.
    private LinearLayout chipFlow(final java.util.List<View> items) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        if (items.isEmpty()) return col;
        int avail = getResources().getDisplayMetrics().widthPixels - dp(40);
        LinearLayout row = null;
        int used = 0;
        for (int i = 0; i < items.size(); i++) {
            View v = items.get(i);
            v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int extra = 0;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) lp;
                extra = mp.leftMargin + mp.rightMargin;
            }
            int w = v.getMeasuredWidth() + extra;
            if (row == null || (used > 0 && used + w > avail)) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (col.getChildCount() > 0) rlp.topMargin = dp(4);
                col.addView(row, rlp);
                used = 0;
            }
            row.addView(v);
            used += w;
        }
        return col;
    }

    // Собирает оценки урока в чипы; на узком экране они уходят под тему,
    // а не выжимают её в ноль.
    private java.util.List<View> markChips(JSONArray marks) {
        java.util.List<View> out = new java.util.ArrayList<>();
        if (marks == null) return out;
        for (int i = 0; i < marks.length(); i++) {
            String v = Api.str(marks.opt(i));
            if (!v.isEmpty()) out.add(markChip(v));
        }
        return out;
    }

    // Полоса чипов: на узком экране она прокручивается, а не обрезается.
    private LinearLayout chipStrip(LinearLayout page) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(4), dp(14), dp(8));
        hs.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView tappableChip(TextView t) {
        t.setSingleLine(true);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMinHeight(dp(40));
        t.setGravity(Gravity.CENTER);
        return t;
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

    /** Полупрозрачная подложка того же тона, что и текст метки. */
    private static int soft(int color) {
        return (color & 0x00FFFFFF) | 0x22000000;
    }

    private TextView tag(String text, int color, int bg) {
        TextView t = tv(text, 10, color, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
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
        t.setSingleLine(true);
        t.setMinWidth(dp(30));
        t.setMinHeight(dp(32));
        t.setPadding(dp(5), dp(3), dp(5), dp(3));
        t.setBackground(roundBg(0x00000000, 2, STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
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
        TextView t = tv(label, 14, ON_ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(44));
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
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
        t.setMinHeight(dp(44));
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
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
