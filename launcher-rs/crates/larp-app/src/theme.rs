//! Colours and fonts: the same palette as the Java launcher (`ui/Palette`) and the in-game theme.

use eframe::egui::{
    self, Color32, FontData, FontDefinitions, FontFamily, FontId, Stroke, Visuals, style::WidgetVisuals,
};
use std::sync::Arc;

pub const BG: Color32 = Color32::from_rgb(0x11, 0x11, 0x15);
pub const SIDEBAR: Color32 = Color32::from_rgb(0x0B, 0x0B, 0x0E);
pub const CARD: Color32 = Color32::from_rgb(0x18, 0x18, 0x1D);
pub const CARD_HOVER: Color32 = Color32::from_rgb(0x22, 0x22, 0x29);
pub const BUTTON_HOVER: Color32 = Color32::from_rgb(0x2B, 0x2B, 0x33);
pub const BORDER: Color32 = Color32::from_rgb(0x26, 0x26, 0x2E);
pub const WINDOW_BORDER: Color32 = Color32::from_rgb(0x23, 0x23, 0x2B);
pub const FIELD: Color32 = Color32::from_rgb(0x0E, 0x0E, 0x12);
pub const CONSOLE: Color32 = Color32::from_rgb(0x0D, 0x0D, 0x10);
pub const CONSOLE_TEXT: Color32 = Color32::from_rgb(0xC9, 0xC9, 0xD4);
pub const ACCENT: Color32 = Color32::from_rgb(0x8B, 0x5C, 0xF6);
pub const ACCENT_LIGHT: Color32 = Color32::from_rgb(0xC4, 0xB5, 0xFD);
pub const ACCENT_DARK: Color32 = Color32::from_rgb(0x5B, 0x3B, 0xA8);
pub const TEXT: Color32 = Color32::from_rgb(0xED, 0xED, 0xF2);
pub const TEXT_DIM: Color32 = Color32::from_rgb(0x8C, 0x8C, 0x9C);
pub const TAGLINE: Color32 = Color32::from_rgb(0xB4, 0xB4, 0xC0);
pub const DANGER: Color32 = Color32::from_rgb(0xE5, 0x48, 0x4D);
pub const ERROR_TEXT: Color32 = Color32::from_rgb(0xFF, 0x7A, 0x7F);
pub const SUCCESS: Color32 = Color32::from_rgb(0x46, 0xC4, 0x7E);

/// `new Color(255, 255, 255, alpha)` from the Java code, as an egui (premultiplied) colour.
pub const fn white(alpha: u8) -> Color32 {
    Color32::from_rgba_unmultiplied_const(255, 255, 255, alpha)
}

/// 1 px lines around cards (`Palette.HAIRLINE`).
pub const HAIRLINE: Color32 = white(14);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Weight {
    Regular,
    SemiBold,
    Bold,
}

pub fn font(weight: Weight, size: f32) -> FontId {
    let family = match weight {
        Weight::Regular => FontFamily::Proportional,
        Weight::SemiBold => FontFamily::Name("semibold".into()),
        Weight::Bold => FontFamily::Name("bold".into()),
    };
    FontId::new(size, family)
}

pub fn regular(size: f32) -> FontId {
    font(Weight::Regular, size)
}

pub fn semibold(size: f32) -> FontId {
    font(Weight::SemiBold, size)
}

pub fn bold(size: f32) -> FontId {
    font(Weight::Bold, size)
}

/// Inter, bundled with the Java launcher (SIL Open Font License, see Inter-LICENSE.txt there).
const INTER_REGULAR: &[u8] = include_bytes!("../../../../launcher/src/main/resources/larp/fonts/Inter-Regular.ttf");
const INTER_SEMIBOLD: &[u8] = include_bytes!("../../../../launcher/src/main/resources/larp/fonts/Inter-SemiBold.ttf");
const INTER_BOLD: &[u8] = include_bytes!("../../../../launcher/src/main/resources/larp/fonts/Inter-Bold.ttf");

fn fonts() -> FontDefinitions {
    let mut fonts = FontDefinitions::default();
    for (name, bytes) in [
        ("inter", INTER_REGULAR),
        ("inter-semibold", INTER_SEMIBOLD),
        ("inter-bold", INTER_BOLD),
    ] {
        fonts
            .font_data
            .insert(name.into(), Arc::new(FontData::from_static(bytes)));
    }
    // Each Inter weight first, then egui's defaults for any glyph Inter lacks.
    let fallbacks = fonts
        .families
        .get(&FontFamily::Proportional)
        .cloned()
        .unwrap_or_default();
    for (family, first) in [
        (FontFamily::Proportional, "inter"),
        (FontFamily::Name("semibold".into()), "inter-semibold"),
        (FontFamily::Name("bold".into()), "inter-bold"),
    ] {
        let mut list = vec![first.to_owned()];
        list.extend(fallbacks.iter().cloned());
        fonts.families.insert(family, list);
    }
    fonts
}

fn widget(fill: Color32, stroke: Color32, text: Color32) -> WidgetVisuals {
    WidgetVisuals {
        bg_fill: fill,
        weak_bg_fill: fill,
        bg_stroke: Stroke::new(1.0, stroke),
        corner_radius: 5.into(),
        fg_stroke: Stroke::new(1.0, text),
        expansion: 0.0,
    }
}

/// Fonts and the egui style for the whole launcher.
pub fn install(ctx: &egui::Context) {
    ctx.set_fonts(fonts());
    let mut visuals = Visuals::dark();
    visuals.panel_fill = BG;
    visuals.window_fill = CARD;
    visuals.window_stroke = Stroke::new(1.0, BORDER);
    visuals.extreme_bg_color = FIELD;
    visuals.override_text_color = None;
    visuals.selection.bg_fill = ACCENT_DARK;
    visuals.selection.stroke = Stroke::new(1.0, ACCENT);
    visuals.hyperlink_color = ACCENT_LIGHT;
    visuals.text_cursor.stroke = Stroke::new(1.5, TEXT);
    visuals.widgets.noninteractive = widget(BG, HAIRLINE, TEXT_DIM);
    visuals.widgets.inactive = widget(CARD_HOVER, BORDER, TEXT);
    visuals.widgets.hovered = widget(BUTTON_HOVER, BORDER, TEXT);
    visuals.widgets.active = widget(BUTTON_HOVER, ACCENT, TEXT);
    visuals.widgets.open = widget(CARD_HOVER, BORDER, TEXT);
    ctx.set_visuals(visuals);
    ctx.all_styles_mut(|style| {
        style.interaction.selectable_labels = false;
        style.spacing.scroll.bar_width = 8.0;
        style.spacing.scroll.floating = true;
        style.spacing.item_spacing = egui::vec2(8.0, 0.0);
        style.text_styles.insert(egui::TextStyle::Body, regular(13.0));
        style.text_styles.insert(egui::TextStyle::Button, semibold(13.0));
        style
            .text_styles
            .insert(egui::TextStyle::Monospace, FontId::monospace(12.0));
    });
}
