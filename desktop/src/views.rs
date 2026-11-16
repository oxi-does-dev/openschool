//! Экраны приложения. Всё рисуется egui, без браузера и HTML.

use crate::api::{self, Day, Homework, Lesson, Mark, SearchHit, MONTHS, WEEKDAYS_FULL, WEEKDAYS_SHORT};
use crate::theme::{self, Palette};
use crate::{saved_at_text, theme_mode_title, App, Detail, Load, MarksView, Tab, ThemeMode};
use chrono::{Datelike, NaiveDate};
use eframe::egui::{
    self, Align, Align2, Color32, CornerRadius, FontId, Label, Layout, Margin, Response, RichText,
    Sense, Stroke, StrokeKind, TextEdit, Ui, Vec2,
};

// --------------------------------------------------------------- мелочи

pub fn card<R>(ui: &mut Ui, p: &Palette, add: impl FnOnce(&mut Ui) -> R) -> egui::InnerResponse<R> {
    egui::Frame::NONE
        .fill(p.surface)
        .stroke(Stroke::new(1.0, p.stroke))
        .corner_radius(CornerRadius::same(3))
        .inner_margin(Margin::symmetric(13, 10))
        .show(ui, |ui| {
            // карточка всегда во всю ширину колонки, даже если текста мало
            ui.set_min_width(ui.available_width());
            add(ui)
        })
}

fn card_gap(ui: &mut Ui) {
    ui.add_space(8.0);
}

fn rule(ui: &mut Ui, p: &Palette) {
    let (rect, _) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 1.0), Sense::hover());
    ui.painter().rect_filled(rect, CornerRadius::ZERO, p.rule);
}

fn caps(ui: &mut Ui, p: &Palette, text: &str) {
    ui.label(
        RichText::new(text.to_uppercase())
            .size(10.0)
            .color(p.muted)
            .extra_letter_spacing(0.8),
    );
}

pub fn chip(ui: &mut Ui, text: &str, fg: Color32, bg: Color32) -> Response {
    egui::Frame::NONE
        .fill(bg)
        .corner_radius(CornerRadius::same(3))
        .inner_margin(Margin::symmetric(7, 3))
        .show(ui, |ui| {
            ui.label(RichText::new(text).size(11.0).color(fg));
        })
        .response
}

fn seg(ui: &mut Ui, p: &Palette, selected: bool, text: &str) -> Response {
    let button = egui::Button::new(
        RichText::new(text)
            .size(12.5)
            .color(if selected { p.on_accent } else { p.text }),
    )
    .fill(if selected { p.accent } else { p.surface })
    .stroke(Stroke::new(
        1.0,
        if selected { p.accent } else { p.stroke },
    ))
    .corner_radius(CornerRadius::same(3))
    .min_size(Vec2::new(0.0, 28.0));
    ui.add(button)
}

fn icon_button(ui: &mut Ui, p: &Palette, text: &str) -> Response {
    ui.add(
        egui::Button::new(RichText::new(text).size(16.0).color(p.text))
            .frame(false)
            .min_size(Vec2::new(28.0, 28.0)),
    )
}

fn header(
    ui: &mut Ui,
    p: &Palette,
    title: &str,
    subtitle: &str,
    right: impl FnOnce(&mut Ui),
) {
    ui.horizontal(|ui| {
        ui.vertical(|ui| {
            ui.label(
                RichText::new(title)
                    .font(theme::serif_bold(30.0))
                    .color(p.text),
            );
            if !subtitle.is_empty() {
                ui.label(RichText::new(subtitle).size(12.0).color(p.muted));
            }
        });
        ui.with_layout(Layout::right_to_left(Align::Center), right);
    });
    ui.add_space(9.0);
    rule(ui, p);
    ui.add_space(12.0);
}

fn loading(ui: &mut Ui, p: &Palette) {
    ui.add_space(24.0);
    ui.horizontal(|ui| {
        ui.spinner();
        ui.label(RichText::new("Загружаю…").size(13.0).color(p.muted));
    });
    ui.add_space(24.0);
}

fn failure(ui: &mut Ui, p: &Palette, text: &str, retry: impl FnOnce() -> bool) {
    let r = egui::Frame::NONE
        .fill(if p.dark {
            Color32::from_rgb(0x33, 0x21, 0x1D)
        } else {
            Color32::from_rgb(0xF7, 0xE7, 0xE2)
        })
        .stroke(Stroke::new(
            1.0,
            if p.dark {
                Color32::from_rgb(0x5A, 0x3A, 0x31)
            } else {
                Color32::from_rgb(0xD9, 0xB3, 0xA8)
            },
        ))
        .corner_radius(CornerRadius::same(3))
        .inner_margin(Margin::symmetric(13, 11))
        .show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            ui.label(RichText::new("Не получилось").size(13.5).color(p.red));
            ui.label(RichText::new(text).size(12.0).color(p.text));
            ui.add_space(6.0);
            ui.button("Повторить").clicked()
        });
    if r.inner {
        retry();
    }
}

fn empty(ui: &mut Ui, p: &Palette, text: &str) {
    ui.add_space(18.0);
    ui.label(RichText::new(text).size(13.0).color(p.muted));
    ui.add_space(18.0);
}

// --------------------------------------------------------------- боковая панель

pub fn sidebar(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context) {
    ui.label(
        RichText::new("OpenSchool")
            .font(theme::serif_bold(23.0))
            .color(p.text),
    );
    ui.label(RichText::new("дневник МЭШ").size(11.0).color(p.muted));
    ui.add_space(14.0);

    if let Some(prof) = app.profile() {
        ui.label(RichText::new(&prof.name).size(12.5).color(p.text));
        let line = if prof.class_name.is_empty() {
            prof.school.clone()
        } else {
            format!("{} · {}", prof.class_name, prof.school)
        };
        ui.label(RichText::new(line).size(10.5).color(p.muted));
    } else if let Some(err) = app.profile_error() {
        ui.label(RichText::new("профиль не загрузился").size(11.0).color(p.red))
            .on_hover_text(err);
    } else {
        ui.label(RichText::new("профиль…").size(11.0).color(p.muted));
    }

    ui.add_space(14.0);
    rule(ui, p);
    ui.add_space(8.0);

    for (tab, label) in [
        (Tab::Day, "День"),
        (Tab::Week, "Неделя"),
        (Tab::Hw, "Домашние задания"),
        (Tab::Marks, "Оценки"),
        (Tab::Attendance, "Посещаемость"),
        (Tab::Search, "Поиск"),
        (Tab::More, "Ещё"),
    ] {
        if nav_item(ui, p, label, app.tab() == tab).clicked() {
            app.set_tab(tab);
        }
    }

    ui.with_layout(Layout::bottom_up(Align::LEFT), |ui| {
        ui.label(RichText::new(format!("версия {}", app.version())).size(9.5).color(p.muted));
        ui.add_space(4.0);
        if ui
            .add(
                egui::Button::new(RichText::new("Перечитать cookies").size(11.0))
                    .fill(p.surface2)
                    .stroke(Stroke::new(1.0, p.stroke)),
            )
            .clicked()
        {
            app.reload_session_ui(ctx);
        }
        ui.add_space(4.0);
        let (dot, text) = if app.has_client() {
            (p.green, "Сессия активна")
        } else {
            (p.red, "Нет сессии")
        };
        let resp = ui.horizontal(|ui| {
            let (rect, _) = ui.allocate_exact_size(Vec2::new(7.0, 7.0), Sense::hover());
            ui.painter().circle_filled(rect.center(), 3.5, dot);
            ui.label(RichText::new(text).size(10.5).color(p.muted));
        });
        if let Some(err) = app.session_error() {
            resp.response.on_hover_text(err);
        }
    });
}

fn nav_item(ui: &mut Ui, p: &Palette, label: &str, selected: bool) -> Response {
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 34.0), Sense::click());
    if selected || resp.hovered() {
        ui.painter().rect_filled(
            rect,
            CornerRadius::same(3),
            if selected { p.surface2 } else { p.current },
        );
    }
    if selected {
        let bar = egui::Rect::from_min_size(
            rect.left_top() + Vec2::new(0.0, 8.0),
            Vec2::new(2.0, 18.0),
        );
        ui.painter().rect_filled(bar, CornerRadius::same(1), p.accent);
    }
    ui.painter().text(
        rect.left_center() + Vec2::new(12.0, 0.0),
        Align2::LEFT_CENTER,
        label,
        FontId::proportional(14.0),
        if selected { p.text } else { p.muted },
    );
    resp
}

// --------------------------------------------------------------- день

pub fn day(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context) {
    let date = app.day_date();
    let today = api::today();
    let class = app
        .profile()
        .map(|x| x.class_name.clone())
        .unwrap_or_default();
    let subtitle = {
        let base = format!(
            "{} · {}",
            api::human_date(date),
            WEEKDAYS_FULL[api::dow_index(date)]
        );
        if class.is_empty() {
            base
        } else {
            format!("{} · {}", base, class)
        }
    };

    header(ui, p, "Дневник", &subtitle, |ui| {
        if ui
            .add(
                egui::Button::new(RichText::new("Сегодня").size(12.0).color(p.accent))
                    .fill(p.accent_soft)
                    .stroke(Stroke::new(1.0, p.accent))
                    .corner_radius(CornerRadius::same(3)),
            )
            .clicked()
        {
            app.set_day_date(today, ctx);
        }
        if icon_button(ui, p, "›").clicked() {
            app.set_day_date(api::shift(date, 1), ctx);
        }
        if icon_button(ui, p, "‹").clicked() {
            app.set_day_date(api::shift(date, -1), ctx);
        }
    });

    day_strip(app, ui, p, ctx, date);

    let state = app.day_slot().state.clone();
    match state {
        Load::Loading | Load::Idle => loading(ui, p),
        Load::Failed(e) => {
            failure(ui, p, &e, || {
                app.retry_day(ctx);
                false
            });
        }
        Load::Ready(day) => render_day(app, ui, p, &day),
    }
}

fn day_strip(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context, date: NaiveDate) {
    let today = api::today();
    let start = api::week_start(date);
    let (rect, _) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 58.0), Sense::hover());
    let painter = ui.painter().clone();
    let w = rect.width() / 7.0;

    for (i, weekday) in WEEKDAYS_SHORT.iter().enumerate() {
        let d = api::shift(start, i as i64);
        let cell = egui::Rect::from_min_size(
            rect.left_top() + Vec2::new(w * i as f32, 0.0),
            Vec2::new(w, rect.height()),
        );
        let resp = ui.interact(cell, ui.scope_id().with(("daycell", i)), Sense::click());
        let selected = d == date;
        let is_today = d == today;
        if selected {
            painter.rect_filled(cell.shrink(2.0), CornerRadius::same(3), p.current);
            painter.rect_stroke(
                cell.shrink(2.0),
                CornerRadius::same(3),
                Stroke::new(1.0, p.accent),
                StrokeKind::Inside,
            );
        } else if resp.hovered() {
            painter.rect_filled(cell.shrink(2.0), CornerRadius::same(3), p.current);
        }
        painter.text(
            cell.center_top() + Vec2::new(0.0, 7.0),
            Align2::CENTER_TOP,
            *weekday,
            FontId::proportional(10.0),
            if selected || is_today { p.accent } else { p.muted },
        );
        painter.text(
            cell.center_top() + Vec2::new(0.0, 22.0),
            Align2::CENTER_TOP,
            d.day().to_string(),
            FontId::proportional(17.0),
            if selected { p.accent } else { p.text },
        );
        if is_today {
            painter.circle_filled(
                cell.center_top() + Vec2::new(0.0, 48.0),
                2.0,
                p.accent,
            );
        }
        if resp.clicked() {
            app.set_day_date(d, ctx);
        }
    }
    ui.add_space(6.0);
}

fn render_day(app: &mut App, ui: &mut Ui, p: &Palette, day: &Day) {
    if let Some(err) = &day.error {
        let err = err.clone();
        failure(ui, p, &err, || false);
        return;
    }

    let mut chips = Vec::new();
    if !day.lessons.is_empty() {
        chips.push(api::plural(day.lessons.len(), "урок", "урока", "уроков"));
    }
    if !day.extra.is_empty() {
        chips.push(format!("{} доп.", day.extra.len()));
    }
    if !day.homeworks.is_empty() {
        chips.push(format!("ДЗ {}", day.homeworks.len()));
    }
    if !chips.is_empty() {
        ui.horizontal(|ui| {
            for c in chips {
                chip(ui, &c, p.muted, p.surface2);
            }
        });
        ui.add_space(8.0);
    }

    if day.lessons.is_empty() {
        empty(ui, p, "На этот день уроков нет.");
    }
    for l in &day.lessons {
        if lesson_card(ui, p, l) {
            app.open_detail(Detail::Lesson(l.clone()));
        }
        card_gap(ui);
    }

    if !day.extra.is_empty() {
        ui.add_space(4.0);
        caps(ui, p, "Дополнительно");
        ui.add_space(4.0);
        for l in &day.extra {
            card(ui, p, |ui| {
                ui.horizontal(|ui| {
                    ui.monospace(
                        RichText::new(if l.begin.is_empty() {
                            "—".to_string()
                        } else {
                            l.begin.clone()
                        })
                        .size(11.0)
                        .color(p.muted),
                    );
                    ui.label(RichText::new(&l.subject).size(13.0).color(p.text));
                    if !l.room.is_empty() {
                        ui.label(
                            RichText::new(format!("каб. {}", l.room))
                                .size(10.5)
                                .color(p.muted),
                        );
                    }
                });
            });
            card_gap(ui);
        }
    }

    if !day.homeworks.is_empty() {
        ui.add_space(6.0);
        ui.horizontal(|ui| {
            caps(ui, p, "Домашние задания");
            ui.label(
                RichText::new(day.homeworks.len().to_string())
                    .size(10.0)
                    .color(p.muted),
            );
        });
        ui.add_space(4.0);
        let track = day.homeworks.iter().any(|h| h.done);
        for h in &day.homeworks {
            if hw_card(ui, p, h, false, track) {
                app.open_detail(Detail::Hw(h.clone()));
            }
            card_gap(ui);
        }
    }
    ui.add_space(10.0);
}

fn lesson_card(ui: &mut Ui, p: &Palette, l: &Lesson) -> bool {
    let id = ui.scope_id().with(("lesson", l.id.clone(), l.begin.clone()));
    let inner = card(ui, p, |ui| {
        ui.horizontal_top(|ui| {
            ui.vertical(|ui| {
                ui.set_width(54.0);
                ui.label(
                    RichText::new(if l.begin.is_empty() { "—" } else { &l.begin })
                        .size(14.0)
                        .color(p.text),
                );
                if !l.end.is_empty() {
                    ui.label(
                        RichText::new(format!("до {}", l.end))
                            .size(10.0)
                            .color(p.muted),
                    );
                }
            });
            ui.vertical(|ui| {
                ui.horizontal(|ui| {
                    ui.label(RichText::new(&l.subject).size(15.0).color(p.text));
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        for m in l.marks.iter().rev() {
                            mark_chip(ui, p, m, 26.0);
                        }
                    });
                });
                if !l.topic.is_empty() {
                    ui.label(RichText::new(&l.topic).size(12.5).color(p.text));
                }
                let mut meta = Vec::new();
                if !l.room.is_empty() {
                    meta.push(format!("каб. {}", l.room));
                }
                if !l.teacher.is_empty() {
                    meta.push(l.teacher.clone());
                }
                if l.is_control() {
                    meta.push("контрольная".to_string());
                }
                if !meta.is_empty() {
                    ui.label(RichText::new(meta.join(" · ")).size(10.5).color(p.muted));
                }
                if !l.homework.is_empty() {
                    ui.add_space(3.0);
                    ui.horizontal_top(|ui| {
                        chip(ui, "ДЗ", p.accent, p.accent_soft);
                        ui.label(
                            RichText::new(api::first_line(&l.homework, 140))
                                .size(11.5)
                                .color(p.muted),
                        );
                    });
                }
            });
        });
    });
    let resp = ui.interact(inner.response.rect, id, Sense::click());
    if resp.hovered() {
        ui.painter().rect_stroke(
            inner.response.rect,
            CornerRadius::same(3),
            Stroke::new(1.0, p.accent),
            StrokeKind::Inside,
        );
    }
    resp.clicked()
}

fn mark_chip(ui: &mut Ui, p: &Palette, value: &str, size: f32) {
    let color = grade_color(p, value);
    let (rect, _) = ui.allocate_exact_size(Vec2::new(size, size), Sense::hover());
    ui.painter()
        .rect_filled(rect, CornerRadius::same(2), p.surface2);
    ui.painter().rect_stroke(
        rect,
        CornerRadius::same(2),
        Stroke::new(1.0, p.stroke),
        StrokeKind::Inside,
    );
    ui.painter().text(
        rect.center(),
        Align2::CENTER_CENTER,
        value,
        FontId::proportional(size * 0.55),
        color,
    );
}

fn grade_color(p: &Palette, value: &str) -> Color32 {
    let v: f64 = value.replace(',', ".").parse().unwrap_or(0.0);
    let five = if v <= 5.0 {
        v
    } else if v <= 10.0 {
        v / 2.0
    } else {
        v / 20.0
    };
    if five >= 4.0 {
        p.green
    } else if five >= 3.0 {
        p.amber
    } else {
        p.red
    }
}

fn hw_card(ui: &mut Ui, p: &Palette, h: &Homework, full: bool, track_done: bool) -> bool {
    let id = ui.scope_id().with(("hwcard", h.date.clone(), h.subject.clone(), h.text.clone()));
    let inner = card(ui, p, |ui| {
        ui.horizontal(|ui| {
            ui.label(
                RichText::new(if h.subject.is_empty() {
                    "Задание"
                } else {
                    &h.subject
                })
                .size(14.5)
                .color(p.text),
            );
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                if h.digital() {
                    chip(ui, "ЦДЗ", p.accent, p.accent_soft);
                }
                if track_done {
                    if h.done {
                        chip(ui, "выполнено", p.green, p.accent_soft);
                    } else {
                        chip(ui, "не выполнено", p.amber, p.surface2);
                    }
                }
            });
        });

        if !h.lesson_time.is_empty() {
            let text = if h.lesson_start.is_empty() {
                format!("урок до {}", h.lesson_time)
            } else {
                format!("урок {}–{}", h.lesson_start, h.lesson_time)
            };
            chip(ui, &text, p.muted, p.surface2);
        }

        ui.add_space(2.0);
        let text = if full {
            h.text.clone()
        } else {
            api::first_line(&h.text, 220)
        };
        if text.is_empty() {
            ui.label(RichText::new("(см. материалы)").size(12.5).color(p.muted));
        } else {
            ui.add(Label::new(RichText::new(text).size(12.5).color(p.text)).wrap());
        }

        let mut meta = Vec::new();
        if !h.materials.is_empty() {
            meta.push(api::plural(
                h.materials.len(),
                "материал",
                "материала",
                "материалов",
            ));
        }
        if !h.files.is_empty() {
            meta.push(api::plural(h.files.len(), "файл", "файла", "файлов"));
        }
        if h.has_teacher_answer {
            meta.push("ответ учителя".into());
        }
        if !meta.is_empty() {
            ui.label(RichText::new(meta.join(" · ")).size(10.5).color(p.muted));
        }
    });

    let resp = ui.interact(inner.response.rect, id, Sense::click());
    if resp.hovered() {
        ui.painter().rect_stroke(
            inner.response.rect,
            CornerRadius::same(3),
            Stroke::new(1.0, p.accent),
            StrokeKind::Inside,
        );
    }
    resp.clicked()
}

// --------------------------------------------------------------- неделя

pub fn week(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context) {
    let start = app.week_start();
    let end = api::shift(start, 6);
    let range = if start.month() == end.month() {
        format!("{} – {} {}", start.day(), end.day(), MONTHS[end.month0() as usize])
    } else {
        format!(
            "{} {} – {} {}",
            start.day(),
            MONTHS[start.month0() as usize],
            end.day(),
            MONTHS[end.month0() as usize]
        )
    };

    header(ui, p, "Неделя", &range, |ui| {
        if ui
            .add(
                egui::Button::new(RichText::new("Эта неделя").size(12.0).color(p.accent))
                    .fill(p.accent_soft)
                    .stroke(Stroke::new(1.0, p.accent))
                    .corner_radius(CornerRadius::same(3)),
            )
            .clicked()
        {
            app.shift_week(
                (api::week_start(api::today()) - start).num_days(),
                ctx,
            );
        }
        if icon_button(ui, p, "›").clicked() {
            app.shift_week(7, ctx);
        }
        if icon_button(ui, p, "‹").clicked() {
            app.shift_week(-7, ctx);
        }
    });

    let state = app.week_slot().state.clone();
    match state {
        Load::Loading | Load::Idle => loading(ui, p),
        Load::Failed(e) => {
            failure(ui, p, &e, || {
                app.retry_week(ctx);
                false
            });
        }
        Load::Ready(days) => {
            week_summary(ui, p, &days);
            for d in &days {
                week_day_card(app, ui, p, ctx, d);
                card_gap(ui);
            }
        }
    }
}

fn week_stats(days: &[Day]) -> (usize, usize, usize, usize, usize, usize, bool) {
    let mut total_hw = 0;
    let mut total_left = 0;
    let mut controls = 0;
    let mut digital = 0;
    let mut lessons = 0;
    let mut max_lessons = 0;
    let mut track_done = false;
    for d in days {
        lessons += d.lessons.len();
        max_lessons = max_lessons.max(d.lessons.len());
        for l in &d.lessons {
            if l.is_control() {
                controls += 1;
            }
        }
        let mut seen = std::collections::HashSet::new();
        for h in &d.homeworks {
            if !seen.insert((h.subject.clone(), h.text.clone())) {
                continue;
            }
            total_hw += 1;
            if h.done {
                track_done = true;
            } else {
                total_left += 1;
            }
            if h.digital() {
                digital += 1;
            }
        }
    }
    (
        total_hw,
        total_left,
        controls,
        digital,
        lessons,
        max_lessons,
        track_done,
    )
}

fn week_summary(ui: &mut Ui, p: &Palette, days: &[Day]) {
    let (total_hw, total_left, controls, digital, lessons, max_lessons, track_done) =
        week_stats(days);

    card(ui, p, |ui| {
        ui.horizontal(|ui| {
            ui.label(
                RichText::new(total_hw.to_string())
                    .font(theme::serif_bold(36.0))
                    .color(p.text),
            );
            ui.vertical(|ui| {
                ui.add_space(6.0);
                ui.label(
                    RichText::new(format!(
                        "{} на неделю",
                        if total_hw % 10 == 1 && total_hw % 100 != 11 {
                            "задание"
                        } else if (2..=4).contains(&(total_hw % 10))
                            && !(12..=14).contains(&(total_hw % 100))
                        {
                            "задания"
                        } else {
                            "заданий"
                        }
                    ))
                    .size(11.0)
                    .color(p.muted),
                );
                if total_hw == 0 {
                    ui.label(RichText::new("пока пусто").size(11.0).color(p.muted));
                } else if track_done && total_left == 0 {
                    ui.label(RichText::new("всё сделано").size(11.0).color(p.green));
                } else if track_done {
                    ui.label(
                        RichText::new(format!("{} не сделано", total_left))
                            .size(11.0)
                            .color(p.amber),
                    );
                }
            });
        });

        ui.add_space(8.0);
        ui.horizontal(|ui| {
            if controls > 0 {
                chip(
                    ui,
                    &if controls == 1 {
                        "контрольная".to_string()
                    } else {
                        format!("контрольных {}", controls)
                    },
                    p.red,
                    p.surface2,
                );
            }
            if digital > 0 {
                chip(ui, &format!("ЦДЗ {}", digital), p.accent, p.accent_soft);
            }
            if lessons > 0 {
                chip(
                    ui,
                    &api::plural(lessons, "урок", "урока", "уроков"),
                    p.muted,
                    p.surface2,
                );
            }
        });

        ui.add_space(10.0);
        load_strip(ui, p, days, max_lessons);
        if controls > 0 {
            ui.label(
                RichText::new("точка — контрольная или проверочная")
                    .size(9.0)
                    .color(p.muted),
            );
        }
    });
    ui.add_space(10.0);
}

fn load_strip(ui: &mut Ui, p: &Palette, days: &[Day], max_lessons: usize) {
    let today = api::today();
    let (rect, _) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 52.0), Sense::hover());
    let painter = ui.painter().clone();
    let w = rect.width() / days.len().max(1) as f32;

    for (i, d) in days.iter().enumerate() {
        let cx = rect.left() + w * (i as f32 + 0.5);
        let is_today = api::parse_iso(&d.date) == Some(today);
        let n = d.lessons.len();
        let h = if max_lessons == 0 {
            3.0
        } else {
            3.0 + 26.0 * n as f32 / max_lessons as f32
        };
        let bottom = rect.bottom() - 16.0;
        let bar = egui::Rect::from_min_max(
            egui::pos2(cx - 6.0, bottom - h),
            egui::pos2(cx + 6.0, bottom),
        );
        painter.rect_filled(bar, CornerRadius::same(2), if is_today { p.accent } else { p.rule });
        if d.lessons.iter().any(|l| l.is_control()) {
            painter.circle_filled(egui::pos2(cx, bar.top() - 4.0), 2.0, p.red);
        }
        let label = api::parse_iso(&d.date)
            .map(|x| WEEKDAYS_SHORT[api::dow_index(x)])
            .unwrap_or("");
        painter.text(
            egui::pos2(cx, rect.bottom() - 12.0),
            Align2::CENTER_TOP,
            label,
            FontId::proportional(9.5),
            if is_today { p.accent } else { p.muted },
        );
    }
}

fn week_day_card(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context, d: &Day) {
    let date = api::parse_iso(&d.date);
    let label = date
        .map(|x| {
            format!(
                "{} · {} {}",
                WEEKDAYS_SHORT[api::dow_index(x)],
                x.day(),
                MONTHS[x.month0() as usize]
            )
        })
        .unwrap_or_else(|| d.date.clone());
    let is_today = date == Some(api::today());

    card(ui, p, |ui| {
        let head = ui.horizontal(|ui| {
            ui.label(
                RichText::new(&label)
                    .size(13.5)
                    .color(if is_today { p.accent } else { p.text }),
            );
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                let mut meta = String::new();
                if d.lessons.is_empty() {
                    meta.push_str("нет уроков");
                } else {
                    meta.push_str(&api::plural(d.lessons.len(), "урок", "урока", "уроков"));
                }
                if !d.homeworks.is_empty() {
                    meta.push_str(&format!(" · ДЗ {}", d.homeworks.len()));
                }
                ui.label(RichText::new(meta).size(10.5).color(p.muted));
            });
        });
        if head.response.interact(Sense::click()).clicked() {
            if let Some(x) = date {
                app.open_day(ctx, x);
            }
        }
        if let Some(err) = &d.error {
            ui.label(RichText::new(err).size(11.0).color(p.muted));
            return;
        }

        let mut flags = Vec::new();
        let controls = d.lessons.iter().filter(|l| l.is_control()).count();
        let digital = d.homeworks.iter().filter(|h| h.digital()).count();
        let track = d.homeworks.iter().any(|h| h.done);
        let left = d.homeworks.iter().filter(|h| !h.done).count();
        if controls > 0 {
            flags.push((
                if controls == 1 {
                    "контрольная".to_string()
                } else {
                    format!("контрольных {}", controls)
                },
                p.red,
            ));
        }
        if digital > 0 {
            flags.push((format!("ЦДЗ {}", digital), p.accent));
        }
        if track && left > 0 {
            flags.push((format!("не сделано {}", left), p.amber));
        }
        if !flags.is_empty() {
            ui.horizontal(|ui| {
                for (text, color) in flags {
                    chip(ui, &text, color, p.surface2);
                }
            });
        }

        for l in &d.lessons {
            ui.horizontal(|ui| {
                ui.monospace(
                    RichText::new(if l.begin.is_empty() { "—" } else { &l.begin })
                        .size(10.5)
                        .color(p.muted),
                );
                ui.label(RichText::new(&l.subject).size(12.5).color(p.text));
                if !l.room.is_empty() {
                    ui.label(
                        RichText::new(format!("каб. {}", l.room))
                            .size(10.0)
                            .color(p.muted),
                    );
                }
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    for m in l.marks.iter().rev() {
                        mark_chip(ui, p, m, 20.0);
                    }
                });
            });
        }

        let mut seen = std::collections::HashSet::new();
        let mut shown = 0;
        let mut more = 0;
        for h in &d.homeworks {
            if !seen.insert((h.subject.clone(), h.text.clone())) {
                continue;
            }
            if shown < 3 {
                if week_hw_row(ui, p, h) {
                    app.open_detail(Detail::Hw(h.clone()));
                }
                shown += 1;
            } else {
                more += 1;
            }
        }
        if more > 0
            && ui
                .add(
                    egui::Label::new(
                        RichText::new(format!("ещё {} — откройте день", more))
                            .size(10.5)
                            .color(p.accent),
                    )
                    .sense(Sense::click()),
                )
                .clicked()
        {
            if let Some(x) = date {
                app.open_day(ctx, x);
            }
        }
    });
}

fn week_hw_row(ui: &mut Ui, p: &Palette, h: &Homework) -> bool {
    let text = format!(
        "ДЗ · {}{}",
        if h.subject.is_empty() { "задание" } else { &h.subject },
        if h.text.is_empty() {
            String::new()
        } else {
            format!(" — {}", api::first_line(&h.text, 90))
        }
    );
    let resp = ui.add(
        Label::new(RichText::new(text).size(11.0).color(p.text))
            .wrap()
            .sense(Sense::click()),
    );
    resp.on_hover_text("Открыть задание").clicked()
}

// --------------------------------------------------------------- домашние задания

pub fn homework(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context) {
    let days = app.hw_days();
    let past = app.hw_past();
    let subtitle = if past {
        format!("за прошедшие {} дней", days)
    } else {
        format!("с завтра на {} дней", days)
    };

    header(ui, p, "Домашнее задание", &subtitle, |ui| {
        for d in [30, 14, 7] {
            if seg(ui, p, days == d, &format!("{} дней", d)).clicked() {
                app.set_hw_filter(past, d, ctx);
            }
        }
    });

    ui.horizontal(|ui| {
        if seg(ui, p, !past, "Актуальные").clicked() {
            app.set_hw_filter(false, days, ctx);
        }
        if seg(ui, p, past, "Прошедшие").clicked() {
            app.set_hw_filter(true, days, ctx);
        }
    });
    ui.add_space(10.0);

    let state = app.hw_slot().state.clone();
    match state {
        Load::Loading | Load::Idle => loading(ui, p),
        Load::Failed(e) => {
            failure(ui, p, &e, || {
                app.retry_hw(ctx);
                false
            });
        }
        Load::Ready(items) => {
            if items.is_empty() {
                empty(
                    ui,
                    p,
                    if past {
                        "За этот период заданий нет."
                    } else {
                        "На этот период заданий нет."
                    },
                );
                return;
            }
            let track = items.iter().any(|h| h.done);
            let mut current = String::new();
            for h in items {
                if h.date != current {
                    current = h.date.clone();
                    ui.add_space(6.0);
                    let label = date_heading(&current);
                    ui.label(
                        RichText::new(label)
                            .size(10.0)
                            .color(p.muted)
                            .extra_letter_spacing(0.7),
                    );
                    ui.add_space(4.0);
                }
                if hw_card(ui, p, &h, false, track) {
                    app.open_detail(Detail::Hw(h.clone()));
                }
                card_gap(ui);
            }
            ui.add_space(8.0);
        }
    }
}

fn date_heading(iso: &str) -> String {
    let Some(d) = api::parse_iso(iso) else {
        return iso.to_string();
    };
    let today = api::today();
    let prefix = if d == today {
        "СЕГОДНЯ".to_string()
    } else if d == api::shift(today, 1) {
        "ЗАВТРА".to_string()
    } else if d == api::shift(today, -1) {
        "ВЧЕРА".to_string()
    } else {
        WEEKDAYS_FULL[api::dow_index(d)].to_uppercase()
    };
    format!(
        "{}, {} {}",
        prefix,
        d.day(),
        MONTHS[d.month0() as usize].to_uppercase()
    )
}

// --------------------------------------------------------------- оценки

pub fn marks(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context) {
    let days = app.marks_days();
    let view = app.marks_view();
    let count = match &app.marks_slot().state {
        Load::Ready(v) => format!(" · {}", v.len()),
        _ => String::new(),
    };
    let subtitle = match view {
        MarksView::Dates => format!("за {} дней{}", days, count),
        MarksView::Subjects => "средний балл, итоговые и место в классе".to_string(),
    };

    header(ui, p, "Оценки", &subtitle, |ui| {
        if view == MarksView::Dates {
            for d in [90, 30, 14] {
                if seg(ui, p, days == d, &format!("{} дней", d)).clicked() {
                    app.set_marks_days(d, ctx);
                }
            }
        }
    });

    ui.horizontal(|ui| {
        if seg(ui, p, view == MarksView::Dates, "По датам").clicked() {
            app.set_marks_view(MarksView::Dates, ctx);
        }
        if seg(ui, p, view == MarksView::Subjects, "По предметам").clicked() {
            app.set_marks_view(MarksView::Subjects, ctx);
        }
    });
    ui.add_space(10.0);

    if view == MarksView::Subjects {
        marks_by_subject(app, ui, p, ctx);
        return;
    }

    let state = app.marks_slot().state.clone();
    match state {
        Load::Loading | Load::Idle => loading(ui, p),
        Load::Failed(e) => {
            failure(ui, p, &e, || {
                app.retry_marks(ctx);
                false
            });
        }
        Load::Ready(items) => {
            if items.is_empty() {
                empty(ui, p, "Оценок за этот период нет.");
                return;
            }
            let mut current = String::new();
            for m in items {
                if m.date != current {
                    current = m.date.clone();
                    ui.add_space(6.0);
                    ui.label(
                        RichText::new(date_heading(&current))
                            .size(10.0)
                            .color(p.muted)
                            .extra_letter_spacing(0.7),
                    );
                    ui.add_space(4.0);
                }
                if mark_row(ui, p, &m, false) {
                    app.open_detail(Detail::Mark(m.clone()));
                }
                card_gap(ui);
            }
            ui.add_space(8.0);
        }
    }
}

fn mark_row(ui: &mut Ui, p: &Palette, m: &Mark, show_date: bool) -> bool {
    let id = ui.scope_id().with(("mark", m.id.clone(), m.subject.clone(), m.value.clone()));
    let inner = card(ui, p, |ui| {
        ui.horizontal(|ui| {
            mark_chip(ui, p, &m.value, 30.0);
            ui.vertical(|ui| {
                ui.label(RichText::new(&m.subject).size(13.5).color(p.text));
                let mut meta = Vec::new();
                if show_date {
                    if let Some(d) = api::parse_iso(&m.date) {
                        meta.push(api::human_date(d));
                    }
                }
                if !m.control_form.is_empty() {
                    meta.push(m.control_form.clone());
                }
                if m.is_exam {
                    meta.push("экзамен".into());
                }
                if !meta.is_empty() {
                    ui.label(RichText::new(meta.join(" · ")).size(10.5).color(p.muted));
                }
                if !m.comment.is_empty() {
                    ui.label(
                        RichText::new(api::first_line(&m.comment, 160))
                            .size(11.0)
                            .color(p.muted),
                    );
                }
            });
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                if m.weight > 1 {
                    ui.label(
                        RichText::new(format!("вес {}", m.weight))
                            .size(10.5)
                            .color(p.muted),
                    );
                }
            });
        });
    });
    let resp = ui.interact(inner.response.rect, id, Sense::click());
    if resp.hovered() {
        ui.painter().rect_stroke(
            inner.response.rect,
            CornerRadius::same(3),
            Stroke::new(1.0, p.accent),
            StrokeKind::Inside,
        );
    }
    resp.clicked()
}

// --------------------------------------------------------------- оценки по предметам

fn fmt_mark(v: Option<f64>) -> String {
    match v {
        Some(x) => {
            if (x - x.round()).abs() < 0.005 {
                format!("{}", x.round() as i64)
            } else {
                format!("{:.2}", x).replace('.', ",")
            }
        }
        None => "—".to_string(),
    }
}

fn trend_arrow(trend: &str) -> (&'static str, fn(&Palette) -> Color32) {
    match trend {
        "up" => ("растёт", |p: &Palette| p.green),
        "down" => ("падает", |p: &Palette| p.red),
        _ => ("ровно", |p: &Palette| p.muted),
    }
}

fn marks_by_subject(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context) {
    let state = app.ranks_slot().state.clone();
    match state {
        Load::Loading | Load::Idle => loading(ui, p),
        Load::Failed(e) => {
            failure(ui, p, &e, || {
                app.retry_ranks(ctx);
                false
            });
        }
        Load::Ready((subjects, class)) => {
            if subjects.is_empty() {
                empty(ui, p, "МЭШ не отдал рейтинг по предметам.");
                return;
            }

            card(ui, p, |ui| {
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        ui.label(
                            RichText::new(fmt_mark(class.average))
                                .font(theme::serif_bold(34.0))
                                .color(p.text),
                        );
                        ui.label(RichText::new("средний балл").size(10.5).color(p.muted));
                    });
                    ui.add_space(22.0);
                    ui.vertical(|ui| {
                        ui.label(
                            RichText::new(match (class.place, class.total) {
                                (Some(place), total) if total > 0 => {
                                    format!("{} из {}", place, total)
                                }
                                _ => "—".to_string(),
                            })
                            .font(theme::serif_bold(34.0))
                            .color(p.text),
                        );
                        ui.label(RichText::new("место в классе").size(10.5).color(p.muted));
                    });
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        if let (Some(a30), Some(pl30)) = (class.average30, class.place30) {
                            ui.vertical(|ui| {
                                ui.label(
                                    RichText::new(format!("{} · {} место", fmt_mark(Some(a30)), pl30))
                                        .size(11.0)
                                        .color(p.muted),
                                );
                                ui.label(
                                    RichText::new("за последние 30 дней")
                                        .size(9.5)
                                        .color(p.muted),
                                );
                            });
                        }
                    });
                });
            });
            ui.add_space(10.0);

            let marks = match &app.marks_slot().state {
                Load::Ready(v) => v.clone(),
                _ => Vec::new(),
            };
            let mut by_subject: std::collections::HashMap<i64, usize> =
                std::collections::HashMap::new();
            for m in &marks {
                *by_subject.entry(m.subject_id).or_insert(0) += 1;
            }

            for r in subjects {
                let id = r.subject_id;
                let name = r.name.clone();
                let (arrow, color) = trend_arrow(&r.trend);
                let n = by_subject.get(&id).copied().unwrap_or(0);
                let inner = card(ui, p, |ui| {
                    ui.horizontal(|ui| {
                        ui.vertical(|ui| {
                            ui.label(RichText::new(&r.name).size(14.5).color(p.text));
                            let mut meta = Vec::new();
                            if n > 0 {
                                meta.push(api::plural(n, "оценка", "оценки", "оценок"));
                            }
                            if let Some(place) = r.place {
                                meta.push(format!("{} место", place));
                            }
                            if !meta.is_empty() {
                                ui.label(RichText::new(meta.join(" · ")).size(10.5).color(p.muted));
                            }
                        });
                        ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                            ui.label(
                                RichText::new(fmt_mark(r.average))
                                    .font(theme::serif_bold(20.0))
                                    .color(p.text),
                            );
                            ui.label(RichText::new(arrow).size(9.5).color(color(p)));
                        });
                    });
                });
                let resp = ui.interact(
                    inner.response.rect,
                    ui.scope_id().with(("subject", id)),
                    Sense::click(),
                );
                if resp.hovered() {
                    ui.painter().rect_stroke(
                        inner.response.rect,
                        CornerRadius::same(3),
                        Stroke::new(1.0, p.accent),
                        StrokeKind::Inside,
                    );
                }
                if resp.clicked() {
                    app.open_subject(ctx, id, name);
                }
                card_gap(ui);
            }
            ui.add_space(6.0);
            ui.label(
                RichText::new("Средний балл и место считает МЭШ по текущему аттестационному периоду.")
                    .size(9.5)
                    .color(p.muted),
            );
        }
    }
}

pub fn subject_window(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context) {
    let state = app.subject_slot().state.clone();
    match state {
        Load::Loading | Load::Idle => loading(ui, p),
        Load::Failed(e) => {
            failure(ui, p, &e, || {
                app.retry_subject(ctx);
                false
            });
        }
        Load::Ready(sm) => {
            ui.horizontal(|ui| {
                ui.label(
                    RichText::new(fmt_mark(sm.average_all.parse().ok()))
                        .font(theme::serif_bold(32.0))
                        .color(p.text),
                );
                ui.vertical(|ui| {
                    ui.add_space(6.0);
                    ui.label(RichText::new("средний балл").size(10.5).color(p.muted));
                    if !sm.dynamic.is_empty() {
                        let (arrow, color) = trend_arrow(&sm.dynamic.to_lowercase());
                        ui.label(
                            RichText::new(format!("динамика {}", arrow))
                                .size(10.5)
                                .color(color(p)),
                        );
                    }
                });
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    if !sm.year_mark.is_empty() {
                        ui.vertical(|ui| {
                            ui.label(
                                RichText::new(&sm.year_mark)
                                    .font(theme::serif_bold(28.0))
                                    .color(p.text),
                            );
                            ui.label(RichText::new("за год").size(10.0).color(p.muted));
                        });
                    }
                });
            });
            ui.add_space(8.0);
            rule(ui, p);
            ui.add_space(8.0);

            for period in &sm.periods {
                caps(ui, p, &period.title);
                ui.horizontal(|ui| {
                    let when = if period.start.is_empty() {
                        String::new()
                    } else {
                        format!("{} — {}", period.start, period.end)
                    };
                    if !when.is_empty() {
                        ui.label(RichText::new(when).size(10.5).color(p.muted));
                    }
                    if !period.value.is_empty() {
                        chip(
                            ui,
                            &format!("средний {}", period.value.replace('.', ",")),
                            p.text,
                            p.surface2,
                        );
                    }
                    if !period.fixed.is_empty() {
                        chip(
                            ui,
                            &format!("итоговая {}", period.fixed.replace('.', ",")),
                            p.green,
                            p.accent_soft,
                        );
                    }
                    if !period.target.is_empty() {
                        chip(
                            ui,
                            &format!("цель {}", period.target.replace('.', ",")),
                            p.accent,
                            p.accent_soft,
                        );
                    }
                });
                ui.add_space(4.0);
                for m in &period.marks {
                    if mark_row(ui, p, m, true) {
                        app.open_detail(Detail::Mark(m.clone()));
                    }
                    card_gap(ui);
                }
                ui.add_space(6.0);
            }
        }
    }
}

// --------------------------------------------------------------- посещаемость

pub fn attendance(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context) {
    let days = app.attendance_days();
    let subtitle = format!("за {} дней", days);

    header(ui, p, "Посещаемость", &subtitle, |ui| {
        for d in [180, 90, 30] {
            if seg(ui, p, days == d, &format!("{} дней", d)).clicked() {
                app.set_attendance_days(d, ctx);
            }
        }
    });

    let state = app.attendance_slot().state.clone();
    match state {
        Load::Loading | Load::Idle => loading(ui, p),
        Load::Failed(e) => {
            failure(ui, p, &e, || {
                app.retry_attendance(ctx);
                false
            });
        }
        Load::Ready(items) => {
            if items.is_empty() {
                empty(ui, p, "Пропусков и опозданий за этот период нет.");
                return;
            }
            let lessons: usize = items.iter().map(|d| d.lessons.len()).sum();
            ui.horizontal(|ui| {
                chip(
                    ui,
                    &api::plural(items.len(), "день", "дня", "дней"),
                    p.red,
                    p.surface2,
                );
                if lessons > 0 {
                    chip(
                        ui,
                        &api::plural(lessons, "урок", "урока", "уроков"),
                        p.muted,
                        p.surface2,
                    );
                }
            });
            ui.add_space(8.0);

            for d in &items {
                card(ui, p, |ui| {
                    ui.horizontal(|ui| {
                        let label = api::parse_iso(&d.date)
                            .map(|x| {
                                format!(
                                    "{} · {} {}",
                                    WEEKDAYS_SHORT[api::dow_index(x)],
                                    x.day(),
                                    MONTHS[x.month0() as usize]
                                )
                            })
                            .unwrap_or_else(|| d.date.clone());
                        ui.label(RichText::new(label).size(13.5).color(p.text));
                        ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                            if !d.summary.is_empty() {
                                ui.label(RichText::new(&d.summary).size(10.5).color(p.muted));
                            }
                        });
                    });
                    if !d.description.is_empty() {
                        ui.label(RichText::new(&d.description).size(11.5).color(p.muted));
                    }
                    for l in &d.lessons {
                        ui.horizontal(|ui| {
                            ui.label(RichText::new(&l.subject).size(11.5).color(p.text));
                            let mut meta = Vec::new();
                            if !l.reason.is_empty() {
                                meta.push(l.reason.clone());
                            }
                            if !l.health.is_empty() {
                                meta.push(l.health.clone());
                            }
                            if !meta.is_empty() {
                                ui.label(RichText::new(meta.join(" · ")).size(10.0).color(p.muted));
                            }
                        });
                    }
                });
                card_gap(ui);
            }
            ui.add_space(8.0);
        }
    }
}

// --------------------------------------------------------------- поиск

pub fn search(app: &mut App, ui: &mut Ui, p: &Palette) {
    header(
        ui,
        p,
        "Поиск",
        "задания за ±30 дней, оценки за 90 дней",
        |_ui| {},
    );

    ui.horizontal(|ui| {
        let field = TextEdit::singleline(app.search_query())
            .hint_text("предмет, задание, комментарий")
            .desired_width(380.0);
        ui.add(field);
        if !app.search_query().is_empty() && ui.button("Очистить").clicked() {
            app.search_query().clear();
        }
    });
    ui.add_space(10.0);

    let state = app.search_slot().state.clone();
    match state {
        Load::Loading | Load::Idle => loading(ui, p),
        Load::Failed(e) => {
            failure(ui, p, &e, || false);
        }
        Load::Ready((hw, marks)) => {
            let q = app.search_query().trim().to_lowercase();
            if q.is_empty() {
                empty(
                    ui,
                    p,
                    "Введите слово — найдётся в тексте задания, предмете и комментарии к оценке.",
                );
                return;
            }
            let hits = api::search(&hw, &marks, &q);
            if hits.is_empty() {
                empty(ui, p, "Ничего не нашлось.");
                return;
            }
            ui.label(
                RichText::new(format!("нашлось {}", hits.len()))
                    .size(10.5)
                    .color(p.muted),
            );
            ui.add_space(4.0);
            for hit in hits {
                if search_row(ui, p, &hit) {
                    if let Some(h) = &hit.hw {
                        app.open_detail(Detail::Hw(h.clone()));
                    } else if let Some(m) = &hit.mark {
                        app.open_detail(Detail::Mark(m.clone()));
                    }
                }
                card_gap(ui);
            }
        }
    }
}

fn search_row(ui: &mut Ui, p: &Palette, hit: &SearchHit) -> bool {
    let id = ui.scope_id().with(("hit", hit.title.clone(), hit.subtitle.clone()));
    let inner = card(ui, p, |ui| {
        ui.horizontal(|ui| {
            let tag = if hit.hw.is_some() { "задание" } else { "оценка" };
            chip(
                ui,
                tag,
                if hit.hw.is_some() { p.accent } else { p.green },
                p.surface2,
            );
            ui.label(RichText::new(&hit.title).size(13.0).color(p.text));
        });
        ui.label(RichText::new(&hit.subtitle).size(11.0).color(p.muted));
    });
    let resp = ui.interact(inner.response.rect, id, Sense::click());
    if resp.hovered() {
        ui.painter().rect_stroke(
            inner.response.rect,
            CornerRadius::same(3),
            Stroke::new(1.0, p.accent),
            StrokeKind::Inside,
        );
    }
    resp.clicked()
}

// --------------------------------------------------------------- ещё

pub fn more(app: &mut App, ui: &mut Ui, p: &Palette, ctx: &egui::Context) {
    let class = app
        .profile()
        .map(|x| x.class_name.clone())
        .unwrap_or_default();
    header(ui, p, "Ещё", &class, |_ui| {});

    if let Some(prof) = app.profile() {
        card(ui, p, |ui| {
            ui.label(
                RichText::new(&prof.name)
                    .font(theme::serif_bold(21.0))
                    .color(p.text),
            );
            let line = if prof.class_name.is_empty() {
                prof.school.clone()
            } else {
                format!("класс {} · {}", prof.class_name, prof.school)
            };
            ui.label(RichText::new(line).size(12.0).color(p.muted));
            ui.add_space(6.0);
            rule(ui, p);
            ui.add_space(6.0);
            let human = |iso: &str| {
                api::parse_iso(iso)
                    .map(|d| format!("{} {}", api::human_date(d), d.year()))
                    .unwrap_or_else(|| iso.to_string())
            };
            let rows = [
                ("Учебный год", prof.academic_year.clone()),
                ("Дата зачисления", human(&prof.enrollment_date)),
                ("Дата рождения", human(&prof.birth_date)),
                ("Округ", prof.school_county.clone()),
                ("Телефон школы", prof.school_phone.clone()),
                ("Директор", prof.principal.clone()),
            ];
            for (label, value) in rows {
                if value.is_empty() {
                    continue;
                }
                ui.horizontal(|ui| {
                    ui.add_sized(
                        [130.0, 16.0],
                        egui::Label::new(RichText::new(label).size(11.0).color(p.muted)),
                    );
                    ui.label(RichText::new(value).size(11.5).color(p.text));
                });
            }
            if !prof.representatives.is_empty() {
                ui.add_space(6.0);
                caps(ui, p, "Представители");
                for r in &prof.representatives {
                    ui.label(RichText::new(r).size(11.5).color(p.text));
                }
            }
        });
        card_gap(ui);
    }

    card(ui, p, |ui| {
        caps(ui, p, "Сессия");
        ui.add_space(4.0);
        match app.session_error() {
            Some(err) => {
                ui.label(RichText::new(err).size(12.0).color(p.red));
            }
            None => {
                if let Some(c) = app.cookies() {
                    ui.label(RichText::new(&c.source).size(10.5).color(p.text));
                    let saved = saved_at_text(c.saved_at);
                    if !saved.is_empty() {
                        ui.label(
                            RichText::new(format!("сохранены {}", saved))
                                .size(10.5)
                                .color(p.muted),
                        );
                    }
                    if c.open_permissions {
                        ui.label(
                            RichText::new(
                                "файл cookies доступен другим пользователям — выполните chmod 600",
                            )
                            .size(10.5)
                            .color(p.amber),
                        );
                    }
                }
            }
        }
        ui.add_space(6.0);
        ui.horizontal(|ui| {
            if ui
                .add(
                    egui::Button::new(RichText::new("Перечитать cookies").size(12.0))
                        .fill(p.surface2)
                        .stroke(Stroke::new(1.0, p.stroke)),
                )
                .clicked()
            {
                app.reload_session_ui(ctx);
            }
            ui.label(
                RichText::new("файл можно обновить командой sch login")
                    .size(10.0)
                    .color(p.muted),
            );
        });
    });
    card_gap(ui);

    card(ui, p, |ui| {
        caps(ui, p, "Тема");
        ui.add_space(6.0);
        ui.horizontal(|ui| {
            for mode in [ThemeMode::Auto, ThemeMode::Light, ThemeMode::Dark] {
                if seg(ui, p, app.theme_mode() == mode, theme_mode_title(mode)).clicked() {
                    app.set_theme_mode(mode);
                }
            }
        });
    });
    card_gap(ui);

    card(ui, p, |ui| {
        caps(ui, p, "О приложении");
        ui.add_space(4.0);
        ui.label(
            RichText::new(format!("OpenSchool {} · Rust + egui", app.version()))
                .size(12.0)
                .color(p.text),
        );
        ui.label(
            RichText::new("Без браузера и без сервера: запросы идут с этого компьютера только на school.mos.ru.")
                .size(11.0)
                .color(p.muted),
        );
        ui.add_space(4.0);
        ui.label(
            RichText::new("cookies читаются с диска и не пишутся никуда, кроме памяти процесса.")
                .size(10.5)
                .color(p.muted),
        );
    });
}

// --------------------------------------------------------------- подробности

pub fn lesson_details(app: &App, ui: &mut Ui, p: &Palette, l: &Lesson) {
    ui.label(
        RichText::new(if l.subject.is_empty() {
            "Урок"
        } else {
            &l.subject
        })
        .font(theme::serif_bold(23.0))
        .color(p.text),
    );

    let mut when = String::new();
    if !l.begin.is_empty() {
        when.push_str(&l.begin);
        if !l.end.is_empty() {
            when.push_str(&format!("–{}", l.end));
        }
    }
    if !l.room.is_empty() {
        if !when.is_empty() {
            when.push_str("  ·  ");
        }
        when.push_str(&format!("каб. {}", l.room));
    }
    if !when.is_empty() {
        ui.label(RichText::new(when).size(11.5).color(p.muted));
    }
    if !l.teacher.is_empty() {
        ui.label(RichText::new(&l.teacher).size(11.0).color(p.muted));
    }

    if l.is_control() {
        ui.add_space(6.0);
        chip(ui, "контрольная", p.accent, p.accent_soft);
    }
    if !l.marks.is_empty() {
        ui.add_space(6.0);
        ui.horizontal(|ui| {
            for m in &l.marks {
                mark_chip(ui, p, m, 26.0);
            }
        });
    }

    if !l.topic.is_empty() {
        ui.add_space(8.0);
        rule(ui, p);
        ui.add_space(8.0);
        caps(ui, p, "Тема");
        ui.add(
            Label::new(RichText::new(&l.topic).size(12.5).color(p.text))
                .wrap()
                .selectable(true),
        );
    }

    if !l.homework.is_empty() {
        ui.add_space(8.0);
        caps(ui, p, "Домашнее задание");
        ui.add(
            Label::new(RichText::new(&l.homework).size(12.5).color(p.text))
                .wrap()
                .selectable(true),
        );
    }

    match &app.lesson_slot().state {
        Load::Loading | Load::Idle => {
            ui.add_space(10.0);
            ui.horizontal(|ui| {
                ui.spinner();
                ui.label(RichText::new("уточняю у МЭШ…").size(10.5).color(p.muted));
            });
        }
        Load::Failed(_) => {
            ui.add_space(10.0);
            ui.label(
                RichText::new(
                    "МЭШ не отдал подробности этого урока — видно только расписание.",
                )
                .size(10.5)
                .color(p.muted),
            );
        }
        Load::Ready(d) => {
            if l.marks.is_empty() && !d.marks.is_empty() {
                ui.add_space(6.0);
                ui.horizontal(|ui| {
                    for m in &d.marks {
                        mark_chip(ui, p, &m.value, 26.0);
                    }
                });
            }

            let mut started = !l.topic.is_empty() || !l.homework.is_empty();
            let mut section = |ui: &mut Ui, heading: &str| {
                if started {
                    ui.add_space(8.0);
                    rule(ui, p);
                    ui.add_space(8.0);
                }
                caps(ui, p, heading);
                started = true;
            };

            if !d.topic.is_empty() && d.topic != l.topic {
                section(ui, if l.topic.is_empty() { "Тема" } else { "Тема по плану" });
                ui.add(
                    Label::new(RichText::new(&d.topic).size(12.5).color(p.text))
                        .wrap()
                        .selectable(true),
                );
            }

            if d.missed {
                section(ui, "Посещение");
                chip(ui, "урок пропущен", p.red, p.surface2);
            } else {
                let texts: Vec<&String> = d
                    .homeworks
                    .iter()
                    .filter(|t| t.as_str() != l.homework)
                    .collect();
                if !texts.is_empty() {
                    section(ui, "Домашнее задание");
                    for text in texts {
                        ui.add(
                            Label::new(RichText::new(text).size(12.5).color(p.text))
                                .wrap()
                                .selectable(true),
                        );
                    }
                }
            }

            if !d.materials.is_empty() {
                section(ui, "Материалы");
                let mut digital_note = false;
                for m in &d.materials {
                    ui.add_space(3.0);
                    if !m.kind.is_empty() {
                        ui.label(RichText::new(&m.kind).size(9.5).color(p.muted));
                    }
                    ui.horizontal(|ui| {
                        ui.label(RichText::new(&m.title).size(12.0).color(p.text));
                        if m.action == "Пройти" {
                            chip(ui, "ЦДЗ", p.accent, p.accent_soft);
                        } else if !m.action.is_empty() {
                            chip(ui, &m.action, p.accent, p.accent_soft);
                        }
                        if m.count > 1 {
                            let word = match m.count % 100 {
                                11..=14 => "заданий",
                                _ => match m.count % 10 {
                                    1 => "задание",
                                    2..=4 => "задания",
                                    _ => "заданий",
                                },
                            };
                            ui.label(
                                RichText::new(format!("{} {}", m.count, word))
                                    .size(9.5)
                                    .color(p.muted),
                            );
                        }
                    });
                    if !m.uuid.is_empty() && m.url.is_empty() {
                        digital_note = true;
                    }
                    if !m.url.is_empty() {
                        if api::allowed_url(&m.url) {
                            if ui
                                .add(
                                    egui::Button::new(
                                        RichText::new("Открыть материал").size(11.0),
                                    )
                                    .fill(p.surface2)
                                    .stroke(Stroke::new(1.0, p.stroke)),
                                )
                                .clicked()
                            {
                                let _ = webbrowser::open(&m.url);
                            }
                        } else {
                            ui.label(
                                RichText::new(format!(
                                    "ссылка вне МЭШ ({}) — не открываю",
                                    api::url_host(&m.url)
                                ))
                                .size(10.0)
                                .color(p.muted),
                            );
                        }
                    }
                }
                if digital_note {
                    ui.add_space(4.0);
                    ui.label(
                        RichText::new("цифровые задания открываются в разделе «Домашние задания»")
                            .size(9.5)
                            .color(p.muted),
                    );
                }
            }

            if !d.teacher_comments.is_empty() {
                section(ui, "Комментарии учителя");
                for text in &d.teacher_comments {
                    ui.add(
                        Label::new(RichText::new(text).size(12.0).color(p.text))
                            .wrap()
                            .selectable(true),
                    );
                }
            }

            if !d.comment.is_empty() {
                section(ui, "Комментарий");
                ui.add(
                    Label::new(RichText::new(&d.comment).size(11.5).color(p.muted))
                        .wrap()
                        .selectable(true),
                );
            }
        }
    }

}

pub fn hw_details(
    app: &mut App,
    ui: &mut Ui,
    p: &Palette,
    h: &Homework,
    ctx: &egui::Context,
) {
    ui.label(
        RichText::new(if h.subject.is_empty() {
            "Задание"
        } else {
            &h.subject
        })
        .font(theme::serif_bold(23.0))
        .color(p.text),
    );

    let mut when = String::new();
    if let Some(d) = api::parse_iso(&h.date) {
        when.push_str(&format!(
            "{} · {}",
            api::human_date(d),
            WEEKDAYS_FULL[api::dow_index(d)]
        ));
    }
    if !h.lesson_time.is_empty() {
        if !when.is_empty() {
            when.push_str("  ·  ");
        }
        if h.lesson_start.is_empty() {
            when.push_str(&format!("урок до {}", h.lesson_time));
        } else {
            when.push_str(&format!("урок {}–{}", h.lesson_start, h.lesson_time));
        }
    }
    if !when.is_empty() {
        ui.label(RichText::new(when).size(11.5).color(p.muted));
    }

    ui.add_space(6.0);
    ui.horizontal(|ui| {
        if h.done {
            chip(ui, "выполнено", p.green, p.surface2);
        }
        if h.digital() {
            chip(ui, "ЦДЗ", p.accent, p.accent_soft);
        }
        if h.has_teacher_answer {
            chip(ui, "ответ учителя", p.muted, p.surface2);
        }
        if !h.files.is_empty() {
            chip(
                ui,
                &api::plural(h.files.len(), "файл", "файла", "файлов"),
                p.muted,
                p.surface2,
            );
        }
    });

    ui.add_space(8.0);
    rule(ui, p);
    ui.add_space(8.0);
    caps(ui, p, "Задание");
    ui.add_space(2.0);
    if h.text.is_empty() {
        ui.label(RichText::new("(см. материалы)").size(12.5).color(p.muted));
    } else {
        ui.add(Label::new(RichText::new(&h.text).size(13.0).color(p.text)).wrap().selectable(true));
    }

    if !h.materials.is_empty() {
        ui.add_space(10.0);
        caps(ui, p, "Материалы");
        ui.add_space(2.0);
        for m in &h.materials {
            ui.add_space(3.0);
            ui.label(RichText::new(&m.kind).size(9.5).color(p.muted));
            ui.horizontal(|ui| {
                ui.label(RichText::new(&m.title).size(12.5).color(p.text));
                if m.digital {
                    chip(ui, "ЦДЗ", p.accent, p.accent_soft);
                }
            });
            for f in &m.urls {
                let name = if f.title.is_empty() {
                    "открыть файл"
                } else {
                    &f.title
                };
                if api::allowed_url(&f.url) {
                    if ui
                        .add(
                            egui::Button::new(
                                RichText::new(format!("Открыть · {}", name)).size(11.0),
                            )
                            .fill(p.surface2)
                            .stroke(Stroke::new(1.0, p.stroke)),
                        )
                        .clicked()
                    {
                        let _ = webbrowser::open(&f.url);
                    }
                } else {
                    ui.label(
                        RichText::new(format!(
                            "{} — ссылка вне МЭШ ({}), не открываю",
                            name,
                            api::url_host(&f.url)
                        ))
                        .size(10.5)
                        .color(p.muted),
                    );
                }
            }
            if m.digital && m.urls.is_empty() {
                let can_launch = !h.entry.is_empty() && !m.uuid.is_empty();
                let busy = app.cdz_busy();
                let label = if busy {
                    "Открываю задание…"
                } else if can_launch {
                    "Пройти ЦДЗ"
                } else {
                    "Открыть дневник МЭШ"
                };
                let clicked = ui
                    .add_enabled(
                        !busy,
                        egui::Button::new(
                            RichText::new(label).size(11.5).color(p.on_accent),
                        )
                        .fill(p.accent)
                        .stroke(Stroke::new(1.0, p.accent)),
                    )
                    .clicked();
                if clicked {
                    if can_launch {
                        app.start_cdz(ctx, m.title.clone(), h.entry.clone(), m.uuid.clone());
                    } else {
                        let _ = webbrowser::open("https://school.mos.ru/diary");
                    }
                }
                ui.label(
                    RichText::new(if can_launch {
                        "откроется в браузере сразу на задании, с вашей сессией МЭШ"
                    } else {
                        "у задания нет прямой ссылки — откроется дневник МЭШ"
                    })
                    .size(9.5)
                    .color(p.muted),
                );
            }
        }
    }
}

pub fn mark_details(app: &App, ui: &mut Ui, p: &Palette, m: &Mark) {
    ui.label(
        RichText::new(&m.subject)
            .font(theme::serif_bold(22.0))
            .color(p.text),
    );
    ui.add_space(4.0);
    ui.horizontal(|ui| {
        ui.label(
            RichText::new(&m.value)
                .font(theme::serif_bold(40.0))
                .color(grade_color(p, &m.value)),
        );
        ui.vertical(|ui| {
            ui.add_space(8.0);
            if let Some(d) = api::parse_iso(&m.date) {
                ui.label(
                    RichText::new(format!(
                        "{} · {}",
                        api::human_date(d),
                        WEEKDAYS_FULL[api::dow_index(d)]
                    ))
                    .size(11.5)
                    .color(p.muted),
                );
            }
            if m.weight > 1 {
                ui.label(RichText::new(format!("вес {}", m.weight)).size(11.0).color(p.muted));
            }
            if !m.control_form.is_empty() {
                ui.label(
                    RichText::new(&m.control_form)
                        .size(11.0)
                        .color(p.muted),
                );
            }
        });
    });

    if !m.comment.is_empty() {
        ui.add_space(8.0);
        rule(ui, p);
        ui.add_space(8.0);
        caps(ui, p, "Комментарий");
        ui.add(Label::new(RichText::new(&m.comment).size(12.5).color(p.text)).wrap().selectable(true));
    }

    let mut scales = Vec::new();
    if let Some(v) = m.ten {
        scales.push(format!("10-балльная: {}", fmt_grade(v)));
    }
    if let Some(v) = m.hundred {
        scales.push(format!("100-балльная: {}", fmt_grade(v)));
    }
    if !scales.is_empty() {
        ui.add_space(8.0);
        ui.label(RichText::new(scales.join(" · ")).size(11.0).color(p.muted));
    }

    match &app.mark_extra_slot().state {
        Load::Loading | Load::Idle => {
            ui.add_space(8.0);
            ui.horizontal(|ui| {
                ui.spinner();
                ui.label(RichText::new("уточняю у МЭШ…").size(10.5).color(p.muted));
            });
        }
        Load::Failed(_) => {}
        Load::Ready(extra) => {
            let mut any = false;
            if !extra.teacher.is_empty() || !extra.topic.is_empty() {
                ui.add_space(8.0);
                rule(ui, p);
                ui.add_space(8.0);
                let mut meta = Vec::new();
                if !extra.teacher.is_empty() {
                    meta.push(extra.teacher.clone());
                }
                if !extra.topic.is_empty() {
                    meta.push(extra.topic.clone());
                }
                ui.label(RichText::new(meta.join(" · ")).size(11.5).color(p.muted));
                any = true;
            }

            if !extra.distribution.is_empty() {
                if !any {
                    ui.add_space(8.0);
                    rule(ui, p);
                    ui.add_space(8.0);
                }
                caps(ui, p, "Как у класса");
                ui.add_space(4.0);
                let max = extra
                    .distribution
                    .iter()
                    .map(|(_, n, _)| *n)
                    .max()
                    .unwrap_or(1)
                    .max(1);
                for (value, count, percent) in &extra.distribution {
                    ui.horizontal(|ui| {
                        let (rect, _) = ui.allocate_exact_size(Vec2::new(20.0, 18.0), Sense::hover());
                        ui.painter().text(
                            rect.center(),
                            Align2::CENTER_CENTER,
                            value,
                            FontId::proportional(12.0),
                            grade_color(p, value),
                        );
                        let (bar, _) = ui.allocate_exact_size(
                            Vec2::new(240.0 * *count as f32 / max as f32, 8.0),
                            Sense::hover(),
                        );
                        ui.painter().rect_filled(bar, CornerRadius::same(2), p.rule);
                        let label = if *percent > 0 {
                            format!("{} чел. · {}%", count, percent)
                        } else {
                            format!("{} чел.", count)
                        };
                        ui.label(RichText::new(label).size(10.0).color(p.muted));
                    });
                }
                if extra.total_students > 0 {
                    ui.label(
                        RichText::new(format!("в классе {}", extra.total_students))
                            .size(9.5)
                            .color(p.muted),
                    );
                }
                any = true;
            }

            if !extra.criteria.is_empty() {
                if !any {
                    ui.add_space(8.0);
                    rule(ui, p);
                    ui.add_space(8.0);
                }
                caps(ui, p, "Критерии");
                ui.add(
                    Label::new(RichText::new(&extra.criteria).size(11.5).color(p.text))
                        .wrap()
                        .selectable(true),
                );
            }
        }
    }
}

fn fmt_grade(v: f64) -> String {
    if (v - v.round()).abs() < 0.05 {
        format!("{}", v.round() as i64)
    } else {
        format!("{:.1}", v)
    }
}
