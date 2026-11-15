//! OpenSchool — дневник МЭШ на рабочем столе (Linux, macOS).

mod api;
mod theme;
mod views;

use std::sync::mpsc::{channel, Receiver, TryRecvError};

use api::{
    AttendanceDay, ClassRank, Client, CookieFile, Day, Homework, Mark, MarkDetails, Profile,
    SubjectMarks, SubjectRank,
};
use chrono::{Local, NaiveDate};
use eframe::egui;
use egui::{Context, RichText};

const VERSION: &str = "0.1.0";

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Tab {
    Day,
    Week,
    Hw,
    Marks,
    Attendance,
    Search,
    More,
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum MarksView {
    Dates,
    Subjects,
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum ThemeMode {
    Auto,
    Light,
    Dark,
}

impl ThemeMode {
    pub fn key(self) -> &'static str {
        match self {
            ThemeMode::Auto => "auto",
            ThemeMode::Light => "light",
            ThemeMode::Dark => "dark",
        }
    }

    fn parse(s: &str) -> ThemeMode {
        match s {
            "light" => ThemeMode::Light,
            "dark" => ThemeMode::Dark,
            _ => ThemeMode::Auto,
        }
    }
}

#[derive(Clone)]
pub enum Load<T> {
    Idle,
    Loading,
    Ready(T),
    Failed(String),
}

impl<T> Default for Load<T> {
    fn default() -> Self {
        Load::Idle
    }
}

pub struct Slot<T> {
    pub state: Load<T>,
    rx: Option<Receiver<api::Result<T>>>,
}

impl<T> Default for Slot<T> {
    fn default() -> Self {
        Slot {
            state: Load::Idle,
            rx: None,
        }
    }
}

impl<T: Send + 'static> Slot<T> {
    fn spawn<F>(&mut self, ctx: &Context, job: F)
    where
        F: FnOnce() -> api::Result<T> + Send + 'static,
    {
        let (tx, rx) = channel();
        self.state = Load::Loading;
        self.rx = Some(rx);
        let ctx = ctx.clone();
        std::thread::spawn(move || {
            let _ = tx.send(job());
            ctx.request_repaint();
        });
    }

    fn poll(&mut self) {
        let Some(rx) = self.rx.as_ref() else {
            return;
        };
        match rx.try_recv() {
            Ok(res) => {
                self.state = match res {
                    Ok(v) => Load::Ready(v),
                    Err(e) => Load::Failed(e.text().to_string()),
                };
                self.rx = None;
            }
            Err(TryRecvError::Disconnected) => {
                self.state = Load::Failed("запрос оборвался".into());
                self.rx = None;
            }
            Err(TryRecvError::Empty) => {}
        }
    }

    fn reset(&mut self) {
        self.state = Load::Idle;
        self.rx = None;
    }

    fn ready(&self) -> Option<&T> {
        match &self.state {
            Load::Ready(v) => Some(v),
            _ => None,
        }
    }
}

pub struct App {
    client: Option<Client>,
    cookies: Option<CookieFile>,
    session_error: Option<String>,

    profile: Slot<Profile>,
    day: Slot<Day>,
    week: Slot<Vec<Day>>,
    hw: Slot<Vec<Homework>>,
    marks: Slot<Vec<Mark>>,
    search_data: Slot<(Vec<Homework>, Vec<Mark>)>,
    ranks: Slot<(Vec<SubjectRank>, ClassRank)>,
    attendance: Slot<Vec<AttendanceDay>>,
    subject_marks: Slot<SubjectMarks>,
    mark_extra: Slot<MarkDetails>,

    tab: Tab,
    day_date: NaiveDate,
    week_start: NaiveDate,
    hw_past: bool,
    hw_days: i64,
    marks_days: i64,
    marks_view: MarksView,
    attendance_days: i64,
    subject: Option<(i64, String)>,
    search_query: String,

    theme_mode: ThemeMode,
    dark: bool,
    last_dark: Option<bool>,

    detail: Option<Detail>,
    notice: Option<String>,
    /// Запрос ссылки на ЦДЗ: заголовок задания и канал с ответом.
    cdz: Option<(String, Receiver<api::Result<String>>)>,
    /// Для какой оценки уже запрошены подробности.
    mark_extra_for: Option<String>,
}

#[derive(Clone)]
pub enum Detail {
    Hw(Homework),
    Mark(Mark),
}

fn settings_path() -> Option<std::path::PathBuf> {
    let home = std::env::var("HOME").ok()?;
    Some(std::path::PathBuf::from(home).join(".config/openschool/settings.json"))
}

fn load_theme() -> ThemeMode {
    let Some(p) = settings_path() else {
        return ThemeMode::Auto;
    };
    let Ok(text) = std::fs::read_to_string(p) else {
        return ThemeMode::Auto;
    };
    let v: serde_json::Value = serde_json::from_str(&text).unwrap_or_default();
    ThemeMode::parse(v.get("theme").and_then(|x| x.as_str()).unwrap_or("auto"))
}

fn save_theme(mode: ThemeMode) {
    let Some(p) = settings_path() else {
        return;
    };
    if let Some(dir) = p.parent() {
        let _ = std::fs::create_dir_all(dir);
    }
    let body = serde_json::json!({ "theme": mode.key() });
    let _ = std::fs::write(p, body.to_string());
}

impl App {
    fn new(cc: &eframe::CreationContext<'_>) -> App {
        theme::install_fonts(&cc.egui_ctx);
        let mut app = App {
            client: None,
            cookies: None,
            session_error: None,
            profile: Slot::default(),
            day: Slot::default(),
            week: Slot::default(),
            hw: Slot::default(),
            marks: Slot::default(),
            search_data: Slot::default(),
            ranks: Slot::default(),
            attendance: Slot::default(),
            subject_marks: Slot::default(),
            mark_extra: Slot::default(),
            tab: Tab::Day,
            day_date: api::today(),
            week_start: api::week_start(api::today()),
            hw_past: false,
            hw_days: 14,
            marks_days: 14,
            marks_view: MarksView::Dates,
            attendance_days: 90,
            subject: None,
            search_query: String::new(),
            theme_mode: load_theme(),
            dark: false,
            last_dark: None,
            detail: None,
            notice: None,
            cdz: None,
            mark_extra_for: None,
        };
        // Отладочные флаги: удобно открыть нужный экран для проверки.
        if let Ok(t) = std::env::var("SCH_TAB") {
            app.tab = match t.as_str() {
                "week" => Tab::Week,
                "hw" => Tab::Hw,
                "marks" => Tab::Marks,
                "attendance" => Tab::Attendance,
                "search" => Tab::Search,
                "more" => Tab::More,
                _ => Tab::Day,
            };
        }
        if let Ok(d) = std::env::var("SCH_DATE") {
            if let Some(x) = api::parse_iso(&d) {
                app.day_date = x;
                app.week_start = api::week_start(x);
            }
        }
        if let Ok(m) = std::env::var("SCH_THEME") {
            app.theme_mode = ThemeMode::parse(&m);
        }
        app.reload_session(&cc.egui_ctx, true);
        app
    }

    fn reload_session(&mut self, ctx: &Context, first: bool) {
        self.session_error = None;
        match CookieFile::load() {
            Ok(cf) => match Client::new(&cf) {
                Ok(c) => {
                    self.client = Some(c);
                    self.cookies = Some(cf);
                    self.profile.reset();
                    self.day.reset();
                    self.week.reset();
                    self.hw.reset();
                    self.marks.reset();
                    self.search_data.reset();
                    self.ranks.reset();
                    self.attendance.reset();
                    self.subject_marks.reset();
                    self.mark_extra.reset();
                    self.subject = None;
                    self.load_profile(ctx);
                    self.load_day(ctx);
                    if !first {
                        self.notice = Some("Сессия перечитана".into());
                    }
                }
                Err(e) => {
                    self.client = None;
                    self.cookies = Some(cf);
                    self.session_error = Some(e.text().to_string());
                }
            },
            Err(e) => {
                self.client = None;
                self.cookies = None;
                self.session_error = Some(e.text().to_string());
            }
        }
    }

    fn load_profile(&mut self, ctx: &Context) {
        let Some(c) = self.client.clone() else {
            return;
        };
        self.profile.spawn(ctx, move || c.profile());
    }

    fn load_day(&mut self, ctx: &Context) {
        let Some(c) = self.client.clone() else {
            return;
        };
        let date = api::iso(self.day_date);
        self.day.spawn(ctx, move || c.day(&date));
    }

    fn load_week(&mut self, ctx: &Context) {
        let Some(c) = self.client.clone() else {
            return;
        };
        let start = self.week_start;
        self.week.spawn(ctx, move || Ok(c.week(start, 7)));
    }

    fn load_hw(&mut self, ctx: &Context) {
        let Some(c) = self.client.clone() else {
            return;
        };
        let today = api::today();
        let days = self.hw_days;
        let past = self.hw_past;
        let (from, to) = if past {
            (api::iso(api::shift(today, -days + 1)), api::iso(today))
        } else {
            (api::iso(today), api::iso(api::shift(today, days)))
        };
        self.hw.spawn(ctx, move || {
            let payload = c.homeworks(&from, &to)?;
            Ok(api::normalize_homeworks(&c, &payload, &from, &to))
        });
    }

    fn load_marks(&mut self, ctx: &Context) {
        let Some(c) = self.client.clone() else {
            return;
        };
        let today = api::today();
        let days = self.marks_days;
        let from = api::iso(api::shift(today, -days + 1));
        let to = api::iso(today);
        self.marks.spawn(ctx, move || {
            let payload = c.marks(&from, &to)?;
            Ok(api::normalize_marks(&payload))
        });
    }

    fn load_ranks(&mut self, ctx: &Context) {
        let Some(c) = self.client.clone() else {
            return;
        };
        let date = api::iso(api::today());
        self.ranks.spawn(ctx, move || c.ranks(&date));
    }

    fn load_attendance(&mut self, ctx: &Context) {
        let Some(c) = self.client.clone() else {
            return;
        };
        let today = api::today();
        let from = api::iso(api::shift(today, -self.attendance_days));
        let to = api::iso(today);
        self.attendance
            .spawn(ctx, move || c.attendance(&from, &to));
    }

    fn load_subject(&mut self, ctx: &Context, id: i64) {
        let Some(c) = self.client.clone() else {
            return;
        };
        self.subject_marks.spawn(ctx, move || c.subject_marks(id));
    }

    fn load_mark_extra(&mut self, ctx: &Context, id: String) {
        let Some(c) = self.client.clone() else {
            return;
        };
        self.mark_extra.spawn(ctx, move || c.mark_details(&id));
    }

    fn load_search(&mut self, ctx: &Context) {
        let Some(c) = self.client.clone() else {
            return;
        };
        let today = api::today();
        let hw_from = api::iso(api::shift(today, -30));
        let hw_to = api::iso(api::shift(today, 30));
        let m_from = api::iso(api::shift(today, -90));
        self.search_data.spawn(ctx, move || {
            let hw = c
                .homeworks(&hw_from, &hw_to)
                .map(|p| api::normalize_homeworks(&c, &p, &hw_from, &hw_to))
                .unwrap_or_default();
            let marks = c
                .marks(&m_from, &hw_to)
                .map(|p| api::normalize_marks(&p))
                .unwrap_or_default();
            Ok((hw, marks))
        });
    }

    fn ensure_tab(&mut self, ctx: &Context) {
        match self.tab {
            Tab::Day => {
                if matches!(self.day.state, Load::Idle) {
                    self.load_day(ctx);
                }
            }
            Tab::Week => {
                if matches!(self.week.state, Load::Idle) {
                    self.load_week(ctx);
                }
            }
            Tab::Hw => {
                if matches!(self.hw.state, Load::Idle) {
                    self.load_hw(ctx);
                }
            }
            Tab::Marks => {
                if matches!(self.marks.state, Load::Idle) {
                    self.load_marks(ctx);
                }
            }
            Tab::Attendance => {
                if matches!(self.attendance.state, Load::Idle) {
                    self.load_attendance(ctx);
                }
            }
            Tab::Search => {
                if matches!(self.search_data.state, Load::Idle) {
                    self.load_search(ctx);
                }
            }
            Tab::More => {}
        }
    }

    fn open_day(&mut self, ctx: &Context, date: NaiveDate) {
        self.day_date = date;
        self.tab = Tab::Day;
        self.day.reset();
        self.load_day(ctx);
    }

    fn poll_cdz(&mut self) {
        let Some((title, rx)) = self.cdz.as_ref() else {
            return;
        };
        let outcome = match rx.try_recv() {
            Ok(res) => Some(res),
            Err(TryRecvError::Disconnected) => Some(Err(api::Error::Api(
                "запрос ссылки оборвался".into(),
            ))),
            Err(TryRecvError::Empty) => None,
        };
        let Some(res) = outcome else {
            return;
        };
        let title = title.clone();
        self.cdz = None;
        match res {
            Ok(url) => {
                if api::allowed_url(&url) {
                    match webbrowser::open(&url) {
                        Ok(_) => self.notice = Some(format!("Открыл «{}» в браузере", title)),
                        Err(_) => {
                            self.notice =
                                Some("не удалось открыть браузер для цифрового задания".into())
                        }
                    }
                } else {
                    self.notice = Some("МЭШ предложил ссылку вне mos.ru — не открываю".into());
                }
            }
            Err(e) => self.notice = Some(e.text().to_string()),
        }
    }

    fn poll_all(&mut self) {
        self.poll_cdz();
        self.profile.poll();
        self.day.poll();
        self.week.poll();
        self.hw.poll();
        self.marks.poll();
        self.search_data.poll();
        self.ranks.poll();
        self.attendance.poll();
        self.subject_marks.poll();
        self.mark_extra.poll();
    }

    /// Открыть цифровое задание: сначала берём ссылку у МЭШ, потом отдаём браузеру.
    pub fn start_cdz(&mut self, ctx: &Context, title: String, entry: String, uuid: String) {
        let Some(c) = self.client.clone() else {
            self.notice = Some("Нет сессии МЭШ — перечитайте cookies".into());
            return;
        };
        if self.cdz.is_some() {
            return;
        }
        let (tx, rx) = channel();
        self.cdz = Some((title, rx));
        let ctx = ctx.clone();
        std::thread::spawn(move || {
            let _ = tx.send(c.homework_launch_url(&entry, &uuid));
            ctx.request_repaint();
        });
    }

    fn resolved_dark(&self, ctx: &Context) -> bool {
        match self.theme_mode {
            ThemeMode::Light => false,
            ThemeMode::Dark => true,
            ThemeMode::Auto => ctx
                .system_theme()
                .map(|t| t == egui::Theme::Dark)
                .unwrap_or(false),
        }
    }

    fn set_theme(&mut self, mode: ThemeMode) {
        self.theme_mode = mode;
        save_theme(mode);
    }
}

impl eframe::App for App {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();
        let ctx = &ctx;
        self.poll_all();
        self.dark = self.resolved_dark(ctx);
        if self.last_dark != Some(self.dark) {
            theme::apply_style(ctx, theme::palette(self.dark));
            self.last_dark = Some(self.dark);
        }
        self.ensure_tab(ctx);
        let p = theme::palette(self.dark);

        egui::Panel::left("nav")
            .exact_size(214.0)
            .frame(
                egui::Frame::NONE
                    .fill(p.surface)
                    .inner_margin(egui::Margin::symmetric(16, 18)),
            )
            .show(ui, |ui| {
                views::sidebar(self, ui, &p, ctx);
            });

        egui::CentralPanel::default()
            .frame(egui::Frame::NONE.fill(p.bg).inner_margin(egui::Margin {
                left: 26,
                right: 26,
                top: 18,
                bottom: 14,
            }))
            .show(ui, |ui| {
                egui::ScrollArea::vertical()
                    .auto_shrink([false, false])
                    .show(ui, |ui| {
                        let full = ui.available_width();
                        let content = full.min(980.0);
                        let pad = ((full - content) / 2.0).max(0.0);
                        ui.horizontal(|ui| {
                            ui.add_space(pad);
                            ui.vertical(|ui| {
                                ui.set_width(content);
                                match self.tab {
                                    Tab::Day => views::day(self, ui, &p, ctx),
                                    Tab::Week => views::week(self, ui, &p, ctx),
                                    Tab::Hw => views::homework(self, ui, &p, ctx),
                                    Tab::Marks => views::marks(self, ui, &p, ctx),
                                    Tab::Attendance => views::attendance(self, ui, &p, ctx),
                                    Tab::Search => views::search(self, ui, &p),
                                    Tab::More => views::more(self, ui, &p, ctx),
                                }
                            });
                        });
                    });
            });

        let want_mark = match &self.detail {
            Some(Detail::Mark(m)) => Some(m.id.clone()),
            _ => None,
        };
        if want_mark != self.mark_extra_for {
            self.mark_extra_for = want_mark.clone();
            self.mark_extra.reset();
            if let Some(id) = want_mark {
                self.load_mark_extra(ctx, id);
            }
        }

        if self.subject.is_some() {
            let mut open = true;
            let name = self
                .subject
                .as_ref()
                .map(|(_, n)| n.clone())
                .unwrap_or_default();
            egui::Window::new(if name.is_empty() {
                "Предмет".to_string()
            } else {
                name
            })
            .open(&mut open)
            .collapsible(false)
            .resizable(true)
            .default_width(620.0)
            .max_width(760.0)
            .anchor(egui::Align2::CENTER_TOP, egui::vec2(0.0, 56.0))
            .frame(
                egui::Frame::NONE
                    .fill(p.surface)
                    .stroke(egui::Stroke::new(1.0, p.stroke))
                    .corner_radius(egui::CornerRadius::same(4))
                    .inner_margin(egui::Margin::same(16)),
            )
            .show(ctx, |ui| views::subject_window(self, ui, &p, ctx));
            if !open {
                self.subject = None;
            }
        }

        if let Some(d) = self.detail.clone() {
            let mut open = true;
            let title = match &d {
                Detail::Hw(_) => "Домашнее задание",
                Detail::Mark(_) => "Оценка",
            };
            egui::Window::new(title)
                .open(&mut open)
                .collapsible(false)
                .resizable(true)
                .default_width(560.0)
                .max_width(720.0)
                .anchor(egui::Align2::CENTER_TOP, egui::vec2(0.0, 56.0))
                .frame(
                    egui::Frame::NONE
                        .fill(p.surface)
                        .stroke(egui::Stroke::new(1.0, p.stroke))
                        .corner_radius(egui::CornerRadius::same(4))
                        .inner_margin(egui::Margin::same(16)),
                )
                .show(ctx, |ui| match &d {
                    Detail::Hw(h) => views::hw_details(self, ui, &p, h, ctx),
                    Detail::Mark(m) => views::mark_details(self, ui, &p, m),
                });
            if !open {
                self.detail = None;
            }
        }

        if let Some(text) = self.notice.clone() {
            let mut open = true;
            egui::Window::new("OpenSchool")
                .open(&mut open)
                .collapsible(false)
                .resizable(false)
                .anchor(egui::Align2::CENTER_BOTTOM, egui::vec2(0.0, -24.0))
                .show(ctx, |ui| {
                    ui.label(RichText::new(text).size(13.0).color(p.text));
                });
            if !open {
                self.notice = None;
            }
        }
    }
}

fn main() {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1120.0, 820.0])
            .with_min_inner_size([880.0, 560.0])
            .with_title("OpenSchool"),
        ..Default::default()
    };
    let _ = eframe::run_native(
        "OpenSchool",
        options,
        Box::new(|cc| Ok(Box::new(App::new(cc)))),
    );
}

// --------------------------------------------------------------- доступ к полям

impl App {
    pub fn client(&self) -> Option<&Client> {
        self.client.as_ref()
    }

    pub fn tab(&self) -> Tab {
        self.tab
    }

    pub fn set_tab(&mut self, tab: Tab) {
        self.tab = tab;
    }

    pub fn day_date(&self) -> NaiveDate {
        self.day_date
    }

    pub fn set_day_date(&mut self, d: NaiveDate, ctx: &Context) {
        self.day_date = d;
        self.day.reset();
        self.load_day(ctx);
    }

    pub fn day_slot(&self) -> &Slot<Day> {
        &self.day
    }

    pub fn retry_day(&mut self, ctx: &Context) {
        self.day.reset();
        self.load_day(ctx);
    }

    pub fn week_start(&self) -> NaiveDate {
        self.week_start
    }

    pub fn shift_week(&mut self, days: i64, ctx: &Context) {
        self.week_start = api::shift(self.week_start, days);
        self.week.reset();
        self.load_week(ctx);
    }

    pub fn week_slot(&self) -> &Slot<Vec<Day>> {
        &self.week
    }

    pub fn retry_week(&mut self, ctx: &Context) {
        self.week.reset();
        self.load_week(ctx);
    }

    pub fn hw_past(&self) -> bool {
        self.hw_past
    }

    pub fn hw_days(&self) -> i64 {
        self.hw_days
    }

    pub fn set_hw_filter(&mut self, past: bool, days: i64, ctx: &Context) {
        if self.hw_past == past && self.hw_days == days {
            return;
        }
        self.hw_past = past;
        self.hw_days = days;
        self.hw.reset();
        self.load_hw(ctx);
    }

    pub fn hw_slot(&self) -> &Slot<Vec<Homework>> {
        &self.hw
    }

    pub fn retry_hw(&mut self, ctx: &Context) {
        self.hw.reset();
        self.load_hw(ctx);
    }

    pub fn marks_days(&self) -> i64 {
        self.marks_days
    }

    pub fn set_marks_days(&mut self, days: i64, ctx: &Context) {
        if self.marks_days == days {
            return;
        }
        self.marks_days = days;
        self.marks.reset();
        self.load_marks(ctx);
    }

    pub fn marks_slot(&self) -> &Slot<Vec<Mark>> {
        &self.marks
    }

    pub fn retry_marks(&mut self, ctx: &Context) {
        self.marks.reset();
        self.load_marks(ctx);
    }

    pub fn search_query(&mut self) -> &mut String {
        &mut self.search_query
    }

    pub fn search_slot(&self) -> &Slot<(Vec<Homework>, Vec<Mark>)> {
        &self.search_data
    }

    pub fn retry_search(&mut self, ctx: &Context) {
        self.search_data.reset();
        self.load_search(ctx);
    }

    pub fn open_detail(&mut self, d: Detail) {
        self.detail = Some(d);
    }

    pub fn profile(&self) -> Option<&Profile> {
        self.profile.ready()
    }

    pub fn profile_loading(&self) -> bool {
        matches!(self.profile.state, Load::Loading)
    }

    pub fn profile_error(&self) -> Option<&str> {
        match &self.profile.state {
            Load::Failed(e) => Some(e.as_str()),
            _ => None,
        }
    }

    pub fn session_error(&self) -> Option<&str> {
        self.session_error.as_deref()
    }

    pub fn cookies(&self) -> Option<&CookieFile> {
        self.cookies.as_ref()
    }

    pub fn theme_mode(&self) -> ThemeMode {
        self.theme_mode
    }

    pub fn set_theme_mode(&mut self, m: ThemeMode) {
        self.set_theme(m);
    }

    pub fn reload_session_ui(&mut self, ctx: &Context) {
        self.reload_session(ctx, false);
    }

    pub fn has_client(&self) -> bool {
        self.client.is_some()
    }

    pub fn version(&self) -> &'static str {
        VERSION
    }

    pub fn marks_view(&self) -> MarksView {
        self.marks_view
    }

    pub fn set_marks_view(&mut self, view: MarksView, ctx: &Context) {
        self.marks_view = view;
        if view == MarksView::Subjects && matches!(self.ranks.state, Load::Idle) {
            self.load_ranks(ctx);
        }
    }

    pub fn ranks_slot(&self) -> &Slot<(Vec<SubjectRank>, ClassRank)> {
        &self.ranks
    }

    pub fn retry_ranks(&mut self, ctx: &Context) {
        self.ranks.reset();
        self.load_ranks(ctx);
    }

    pub fn attendance_days(&self) -> i64 {
        self.attendance_days
    }

    pub fn set_attendance_days(&mut self, days: i64, ctx: &Context) {
        if self.attendance_days == days {
            return;
        }
        self.attendance_days = days;
        self.attendance.reset();
        self.load_attendance(ctx);
    }

    pub fn attendance_slot(&self) -> &Slot<Vec<AttendanceDay>> {
        &self.attendance
    }

    pub fn retry_attendance(&mut self, ctx: &Context) {
        self.attendance.reset();
        self.load_attendance(ctx);
    }

    pub fn open_subject(&mut self, ctx: &Context, id: i64, name: String) {
        self.subject = Some((id, name));
        self.subject_marks.reset();
        self.load_subject(ctx, id);
    }

    pub fn subject_slot(&self) -> &Slot<SubjectMarks> {
        &self.subject_marks
    }

    pub fn retry_subject(&mut self, ctx: &Context) {
        if let Some((id, _)) = self.subject.clone() {
            self.subject_marks.reset();
            self.load_subject(ctx, id);
        }
    }

    pub fn mark_extra_slot(&self) -> &Slot<MarkDetails> {
        &self.mark_extra
    }

    pub fn cdz_busy(&self) -> bool {
        self.cdz.is_some()
    }

    pub fn dismiss_notice(&mut self) {
        self.notice = None;
    }
}

pub use api::plural;
pub use api::{human_date, human_date_short, iso, parse_iso, shift, today, week_start};
pub use theme::palette;

pub fn saved_at_text(ts: Option<i64>) -> String {
    match ts {
        Some(ts) => chrono::DateTime::from_timestamp(ts, 0)
            .map(|d| {
                d.with_timezone(&Local)
                    .format("%d.%m.%Y %H:%M")
                    .to_string()
            })
            .unwrap_or_default(),
        None => String::new(),
    }
}

pub fn theme_mode_title(m: ThemeMode) -> &'static str {
    match m {
        ThemeMode::Auto => "Как в системе",
        ThemeMode::Light => "Светлая бумага",
        ThemeMode::Dark => "Тёмная бумага",
    }
}

