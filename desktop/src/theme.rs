//! Бумажная палитра, шрифты и общий вид окна.

use eframe::egui::{
    self, Color32, CornerRadius, FontData, FontDefinitions, FontFamily, FontId, Stroke, TextStyle,
};
use std::path::PathBuf;
use std::sync::Arc;

#[derive(Clone, Copy)]
pub struct Palette {
    pub bg: Color32,
    pub surface: Color32,
    pub surface2: Color32,
    pub text: Color32,
    pub muted: Color32,
    pub accent: Color32,
    pub accent_soft: Color32,
    pub green: Color32,
    pub amber: Color32,
    pub red: Color32,
    pub divider: Color32,
    pub stroke: Color32,
    pub rule: Color32,
    pub current: Color32,
    pub on_accent: Color32,
    pub dark: bool,
}

pub fn palette(dark: bool) -> Palette {
    if dark {
        Palette {
            bg: Color32::from_rgb(0x17, 0x16, 0x13),
            surface: Color32::from_rgb(0x20, 0x1E, 0x1A),
            surface2: Color32::from_rgb(0x2A, 0x27, 0x21),
            text: Color32::from_rgb(0xEA, 0xE3, 0xD5),
            muted: Color32::from_rgb(0xA7, 0x9D, 0x8B),
            accent: Color32::from_rgb(0x8F, 0xB0, 0xD4),
            accent_soft: Color32::from_rgba_unmultiplied(0x8F, 0xB0, 0xD4, 0x2A),
            green: Color32::from_rgb(0x85, 0xB3, 0x94),
            amber: Color32::from_rgb(0xD6, 0xA9, 0x60),
            red: Color32::from_rgb(0xD9, 0x82, 0x7A),
            divider: Color32::from_rgb(0x35, 0x31, 0x2A),
            stroke: Color32::from_rgb(0x3E, 0x39, 0x2F),
            rule: Color32::from_rgb(0x4A, 0x44, 0x38),
            current: Color32::from_rgb(0x2C, 0x2A, 0x20),
            on_accent: Color32::from_rgb(0x17, 0x16, 0x13),
            dark: true,
        }
    } else {
        Palette {
            bg: Color32::from_rgb(0xF5, 0xEF, 0xE2),
            surface: Color32::from_rgb(0xFF, 0xFC, 0xF4),
            surface2: Color32::from_rgb(0xED, 0xE5, 0xD3),
            text: Color32::from_rgb(0x2B, 0x2A, 0x26),
            muted: Color32::from_rgb(0x7A, 0x73, 0x65),
            accent: Color32::from_rgb(0x34, 0x57, 0x7D),
            accent_soft: Color32::from_rgba_unmultiplied(0x34, 0x57, 0x7D, 0x1A),
            green: Color32::from_rgb(0x2F, 0x6B, 0x4F),
            amber: Color32::from_rgb(0x9A, 0x6A, 0x22),
            red: Color32::from_rgb(0xB0, 0x40, 0x3A),
            divider: Color32::from_rgb(0xD9, 0xCE, 0xB8),
            stroke: Color32::from_rgb(0xD3, 0xC7, 0xAE),
            rule: Color32::from_rgb(0xC9, 0xBD, 0xA6),
            current: Color32::from_rgb(0xFF, 0xF8, 0xE6),
            on_accent: Color32::WHITE,
            dark: false,
        }
    }
}

pub fn serif_bold(size: f32) -> FontId {
    FontId::new(size, FontFamily::Name("serif-bold".into()))
}

pub fn sans(size: f32) -> FontId {
    FontId::new(size, FontFamily::Proportional)
}

fn font_candidates() -> Vec<PathBuf> {
    let mut v: Vec<PathBuf> = [
        // Linux
        "/usr/share/fonts/TTF/DejaVuSerif.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf",
        "/usr/share/fonts/liberation/LiberationSerif-Regular.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf",
        "/usr/share/fonts/truetype/paratype/PT_Serif-Web-Regular.ttf",
        "/usr/share/fonts/PT/PT/PT_Serif-Web-Regular.ttf",
        "/usr/share/fonts/noto/NotoSerif-Regular.ttf",
        // macOS
        "/System/Library/Fonts/Supplemental/Georgia.ttf",
        "/System/Library/Fonts/Supplemental/Times New Roman.ttf",
        "/System/Library/Fonts/NewYork.ttf",
        "/Library/Fonts/Georgia.ttf",
    ]
    .iter()
    .map(PathBuf::from)
    .collect();

    let home = std::env::var("HOME").unwrap_or_default();
    if !home.is_empty() {
        let dir = PathBuf::from(home).join(".local/share/fonts");
        if let Ok(rd) = std::fs::read_dir(&dir) {
            for e in rd.flatten() {
                let p = e.path();
                let name = p.file_name().unwrap_or_default().to_string_lossy().to_lowercase();
                if p.extension().map(|x| x == "ttf" || x == "otf").unwrap_or(false)
                    && name.contains("serif")
                    && !name.contains("sans")
                {
                    v.push(p);
                }
            }
        }
    }
    v
}

fn font_bold_candidates() -> Vec<PathBuf> {
    [
        "/usr/share/fonts/TTF/DejaVuSerif-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf",
        "/usr/share/fonts/liberation/LiberationSerif-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf",
        "/usr/share/fonts/truetype/paratype/PT_Serif-Web-Bold.ttf",
        "/usr/share/fonts/noto/NotoSerif-Bold.ttf",
        "/System/Library/Fonts/Supplemental/Georgia Bold.ttf",
        "/System/Library/Fonts/Supplemental/Times New Roman Bold.ttf",
    ]
    .iter()
    .map(PathBuf::from)
    .collect()
}

fn load_first(paths: &[PathBuf]) -> Option<(String, Vec<u8>)> {
    for p in paths {
        if !p.exists() {
            continue;
        }
        if let Ok(bytes) = std::fs::read(p) {
            if bytes.len() > 4096 {
                let name = p
                    .file_stem()
                    .unwrap_or_default()
                    .to_string_lossy()
                    .to_string();
                return Some((name, bytes));
            }
        }
    }
    None
}

/// Ставит системный шрифт с засечками для заголовков.
pub fn install_fonts(ctx: &egui::Context) {
    let mut fonts = FontDefinitions::default();
    let regular = load_first(&font_candidates());
    let bold = load_first(&font_bold_candidates()).or_else(|| regular.clone());

    let mut proportional_fallback = fonts
        .families
        .get(&FontFamily::Proportional)
        .cloned()
        .unwrap_or_else(|| vec!["Ubuntu-Light".to_string()]);
    if let Some((name, bytes)) = regular {
        fonts
            .font_data
            .insert("serif".into(), Arc::new(FontData::from_owned(bytes)));
        fonts.families.insert(
            FontFamily::Name("serif".into()),
            {
                let mut f = vec!["serif".to_string()];
                f.extend(proportional_fallback.clone());
                f
            },
        );
        proportional_fallback.insert(0, "serif".into());
        let _ = name;
    }
    if let Some((name, bytes)) = bold {
        fonts
            .font_data
            .insert("serif-bold".into(), Arc::new(FontData::from_owned(bytes)));
        fonts.families.insert(
            FontFamily::Name("serif-bold".into()),
            {
                let mut f = vec!["serif-bold".to_string()];
                f.extend(proportional_fallback.clone());
                f
            },
        );
        let _ = name;
    }
    if fonts.font_data.contains_key("serif") {
        // если засечек нет, семейства останутся пустыми — подстрахуемся
        if fonts.families.get(&FontFamily::Name("serif".into())).map(|f| f.is_empty()).unwrap_or(true) {
            fonts.families.insert(
                FontFamily::Name("serif".into()),
                vec!["Ubuntu-Light".into()],
            );
        }
        if fonts
            .families
            .get(&FontFamily::Name("serif-bold".into()))
            .map(|f| f.is_empty())
            .unwrap_or(true)
        {
            fonts.families.insert(
                FontFamily::Name("serif-bold".into()),
                vec!["Ubuntu-Light".into()],
            );
        }
    } else {
        fonts.families.insert(
            FontFamily::Name("serif".into()),
            vec!["Ubuntu-Light".into()],
        );
        fonts.families.insert(
            FontFamily::Name("serif-bold".into()),
            vec!["Ubuntu-Light".into()],
        );
    }
    ctx.set_fonts(fonts);
}

pub fn apply_style(ctx: &egui::Context, p: Palette) {
    let mut style = egui::Style::default();
    style.visuals = if p.dark {
        egui::Visuals::dark()
    } else {
        egui::Visuals::light()
    };
    let v = &mut style.visuals;
    v.panel_fill = p.bg;
    v.window_fill = p.surface;
    v.extreme_bg_color = p.surface2;
    v.faint_bg_color = p.surface2;
    v.code_bg_color = p.surface2;
    v.override_text_color = Some(p.text);
    v.weak_text_color = Some(p.muted);
    v.hyperlink_color = p.accent;
    v.selection.bg_fill = p.accent_soft;
    v.selection.stroke = Stroke::new(1.0, p.accent);
    v.window_stroke = Stroke::new(1.0, p.stroke);
    v.window_corner_radius = CornerRadius::same(4);
    v.menu_corner_radius = CornerRadius::same(4);
    v.window_shadow = egui::epaint::Shadow::NONE;

    v.widgets.noninteractive.bg_fill = p.surface;
    v.widgets.noninteractive.weak_bg_fill = p.surface2;
    v.widgets.noninteractive.bg_stroke = Stroke::new(1.0, p.stroke);
    v.widgets.noninteractive.fg_stroke = Stroke::new(1.0, p.muted);
    v.widgets.noninteractive.corner_radius = CornerRadius::same(3);

    v.widgets.inactive.bg_fill = p.surface2;
    v.widgets.inactive.weak_bg_fill = p.surface2;
    v.widgets.inactive.bg_stroke = Stroke::new(1.0, p.stroke);
    v.widgets.inactive.fg_stroke = Stroke::new(1.0, p.text);
    v.widgets.inactive.corner_radius = CornerRadius::same(3);

    v.widgets.hovered.bg_fill = p.current;
    v.widgets.hovered.weak_bg_fill = p.current;
    v.widgets.hovered.bg_stroke = Stroke::new(1.0, p.rule);
    v.widgets.hovered.fg_stroke = Stroke::new(1.0, p.text);
    v.widgets.hovered.corner_radius = CornerRadius::same(3);

    v.widgets.active.bg_fill = p.accent_soft;
    v.widgets.active.weak_bg_fill = p.accent_soft;
    v.widgets.active.bg_stroke = Stroke::new(1.0, p.accent);
    v.widgets.active.fg_stroke = Stroke::new(1.0, p.text);
    v.widgets.active.corner_radius = CornerRadius::same(3);

    v.widgets.open.bg_fill = p.surface2;
    v.widgets.open.weak_bg_fill = p.surface2;
    v.widgets.open.bg_stroke = Stroke::new(1.0, p.rule);
    v.widgets.open.fg_stroke = Stroke::new(1.0, p.text);
    v.widgets.open.corner_radius = CornerRadius::same(3);

    style.spacing.item_spacing = egui::vec2(8.0, 7.0);
    style.spacing.button_padding = egui::vec2(9.0, 4.0);
    style.spacing.scroll.bar_width = 8.0;
    style.spacing.scroll.floating = true;
    style.text_styles = [
        (TextStyle::Body, sans(14.0)),
        (TextStyle::Button, sans(13.5)),
        (TextStyle::Small, sans(11.5)),
        (TextStyle::Monospace, FontId::monospace(13.0)),
        (TextStyle::Heading, serif_bold(24.0)),
    ]
    .into();
    ctx.all_styles_mut(|s| *s = style.clone());
}
