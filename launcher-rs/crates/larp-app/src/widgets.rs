//! The launcher's controls, styled like the Java launcher's FlatLaf theme. Each reports an
//! accessible name, so screen readers (and UI tests) can find it.

use crate::theme::{self, Weight};
use eframe::egui::{
    self, Color32, CornerRadius, CursorIcon, FontId, Galley, Painter, Pos2, Rect, Response, Role, Sense, Stroke,
    StrokeKind, TextureHandle, Ui, WidgetInfo, pos2, text::LayoutJob, vec2,
};
use std::sync::Arc;

/// Lay out one line of text, optionally with extra letter spacing (Java's `TextAttribute.TRACKING`,
/// given in ems).
pub fn galley(ui: &Ui, text: &str, font: FontId, color: Color32, tracking_em: f32) -> Arc<Galley> {
    let mut job = LayoutJob::single_section(
        text.to_owned(),
        egui::TextFormat {
            extra_letter_spacing: tracking_em * font.size,
            font_id: font,
            color,
            ..Default::default()
        },
    );
    job.wrap.max_width = f32::INFINITY;
    ui.fonts_mut(|f| f.layout_job(job))
}

/// Distance from a galley's top to its first baseline.
pub fn baseline(galley: &Galley) -> f32 {
    galley
        .rows
        .first()
        .and_then(|row| row.glyphs.first().map(|g| row.pos.y + g.pos.y))
        .unwrap_or(0.0)
}

/// Draw text with its baseline at `pos.y` (Java's `drawString`). `align_x` is 0 left, 0.5 centre.
pub fn text_at(painter: &Painter, ui: &Ui, pos: Pos2, align_x: f32, text: &str, font: FontId, color: Color32) -> Rect {
    let g = galley(ui, text, font, color, 0.0);
    let top_left = pos2(pos.x - g.size().x * align_x, pos.y - baseline(&g));
    let rect = Rect::from_min_size(top_left, g.size());
    painter.galley(top_left, g, color);
    rect
}

/// A non-interactive line of text placed in the layout.
pub fn label(ui: &mut Ui, text: &str, font: FontId, color: Color32) -> Response {
    let g = galley(ui, text, font, color, 0.0);
    let (rect, response) = ui.allocate_exact_size(g.size(), Sense::hover());
    ui.painter().galley(rect.min, g, color);
    response
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ButtonKind {
    /// Grey FlatLaf button.
    Normal,
    /// Accent ("default") button.
    Primary,
    /// No background until hovered (e.g. "Remove").
    Borderless,
}

/// A FlatLaf-style button: 13 px semibold, at least 72 px wide.
pub fn button(ui: &mut Ui, text: &str, kind: ButtonKind) -> Response {
    button_enabled(ui, text, kind, true)
}

pub fn button_enabled(ui: &mut Ui, text: &str, kind: ButtonKind, enabled: bool) -> Response {
    let font = theme::semibold(13.0);
    let g = galley(ui, text, font, Color32::WHITE, 0.0);
    let size = vec2((g.size().x + 28.0).max(72.0), 24.0);
    let sense = if enabled { Sense::click() } else { Sense::hover() };
    let (rect, response) = ui.allocate_exact_size(size, sense);
    response.widget_info(|| WidgetInfo::labeled(Role::Button, enabled, text));
    let hovered = enabled && response.hovered();
    if hovered {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    let (fill, stroke, color) = match kind {
        ButtonKind::Normal => (
            if hovered {
                theme::BUTTON_HOVER
            } else {
                theme::CARD_HOVER
            },
            theme::BORDER,
            theme::TEXT,
        ),
        ButtonKind::Primary => (
            if hovered {
                Color32::from_rgb(0x9D, 0x74, 0xFF)
            } else {
                theme::ACCENT
            },
            theme::ACCENT,
            Color32::WHITE,
        ),
        ButtonKind::Borderless => (
            if hovered {
                theme::white(16)
            } else {
                Color32::TRANSPARENT
            },
            Color32::TRANSPARENT,
            if hovered { theme::TEXT } else { theme::TEXT_DIM },
        ),
    };
    let color = if enabled { color } else { theme::TEXT_DIM };
    let painter = ui.painter();
    painter.rect(rect, 5.0, fill, Stroke::new(1.0, stroke), StrokeKind::Inside);
    let g = galley(ui, text, theme::semibold(13.0), color, 0.0);
    let pos = rect.center() - g.size() / 2.0;
    painter.galley(pos2(pos.x.round(), pos.y.round()), g, color);
    response
}

/// A single-line text field: dark fill, hairline border, accent border while focused.
pub fn text_field(ui: &mut Ui, value: &mut String, width: f32, hint: &str, name: &str) -> Response {
    let (rect, _) = ui.allocate_exact_size(vec2(width, 32.0), Sense::hover());
    ui.painter().rect_filled(rect, 5.0, theme::FIELD);
    let edit = egui::TextEdit::singleline(value)
        .hint_text(egui::RichText::new(hint).color(theme::TEXT_DIM))
        .font(theme::regular(13.0))
        .text_color(theme::TEXT)
        .frame(egui::Frame::NONE.inner_margin(egui::Margin::symmetric(9, 8)))
        .desired_width(width - 18.0);
    let response = ui.put(rect, edit);
    response.widget_info(|| WidgetInfo::labeled(Role::TextInput, true, name));
    let stroke = if response.has_focus() {
        theme::ACCENT
    } else {
        theme::BORDER
    };
    ui.painter().rect(
        rect,
        5.0,
        Color32::TRANSPARENT,
        Stroke::new(1.0, stroke),
        StrokeKind::Inside,
    );
    response
}

/// An on/off switch (`changed()` when flipped).
pub fn toggle(ui: &mut Ui, on: &mut bool, name: &str) -> Response {
    let (rect, mut response) = ui.allocate_exact_size(vec2(36.0, 20.0), Sense::click());
    if response.clicked() {
        *on = !*on;
        response.mark_changed();
    }
    response.widget_info(|| WidgetInfo::selected(Role::Switch, true, *on, name));
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    let t = ui.ctx().animate_bool(response.id, *on);
    let track = crate::paint::mix(Color32::from_rgb(0x3A, 0x3A, 0x44), theme::ACCENT, t);
    let painter = ui.painter();
    painter.rect_filled(rect, 10.0, track);
    let x = egui::lerp(rect.left() + 10.0..=rect.right() - 10.0, t);
    painter.circle_filled(pos2(x, rect.center().y), 7.0, Color32::WHITE);
    response
}

/// A horizontal slider over `range`, snapping to `step` (FlatLaf look: 4 px track, white thumb).
pub fn slider(
    ui: &mut Ui,
    value: &mut i64,
    range: std::ops::RangeInclusive<i64>,
    step: i64,
    width: f32,
    name: &str,
) -> Response {
    let (rect, mut response) = ui.allocate_exact_size(vec2(width, 24.0), Sense::click_and_drag());
    let (lo, hi) = (*range.start(), *range.end().max(range.start()));
    let span = (hi - lo).max(1) as f32;
    let track = Rect::from_min_max(
        pos2(rect.left() + 7.0, rect.center().y - 2.0),
        pos2(rect.right() - 7.0, rect.center().y + 2.0),
    );
    let snap = |v: i64| ((v.clamp(lo, hi) / step) * step).clamp(lo, hi);
    let before = *value;
    if let Some(pos) = response
        .interact_pointer_pos()
        .filter(|_| response.dragged() || response.clicked())
    {
        let t = ((pos.x - track.left()) / track.width()).clamp(0.0, 1.0);
        #[allow(clippy::cast_possible_truncation)] // within the slider's i64 range
        let v = lo + (t * span).round() as i64;
        *value = snap(v);
    }
    if response.has_focus() {
        let (left, right) = ui.input(|i| {
            (
                i.key_pressed(egui::Key::ArrowLeft),
                i.key_pressed(egui::Key::ArrowRight),
            )
        });
        if left {
            *value = snap(*value - step);
        }
        if right {
            *value = snap(*value + step);
        }
    }
    if *value != before {
        response.mark_changed();
    }
    #[allow(clippy::cast_precision_loss)] // memory sizes in MB
    let shown = *value as f64;
    response.widget_info(|| WidgetInfo::slider(true, shown, name));
    if response.hovered() || response.dragged() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    #[allow(clippy::cast_precision_loss)]
    let t = (((*value).clamp(lo, hi) - lo) as f32 / span).clamp(0.0, 1.0);
    let x = track.left() + track.width() * t;
    let painter = ui.painter();
    painter.rect_filled(track, 2.0, theme::BORDER);
    painter.rect_filled(Rect::from_min_max(track.min, pos2(x, track.max.y)), 2.0, theme::ACCENT);
    painter.circle_filled(pos2(x, rect.center().y), 7.0, Color32::WHITE);
    response
}

/// A rounded card (`Components.Card`): fill, hairline border, radius 8.
pub fn card<R>(ui: &mut Ui, fill: Color32, margin: egui::Margin, add: impl FnOnce(&mut Ui) -> R) -> R {
    egui::Frame::new()
        .fill(fill)
        .stroke(Stroke::new(1.0, theme::HAIRLINE))
        .corner_radius(CornerRadius::same(8))
        .inner_margin(margin)
        .show(ui, |ui| {
            ui.set_width(ui.available_width());
            add(ui)
        })
        .inner
}

/// 1 px separator between rows.
pub fn hairline(ui: &mut Ui) {
    let (rect, _) = ui.allocate_exact_size(vec2(ui.available_width(), 1.0), Sense::hover());
    ui.painter().rect_filled(rect, 0.0, theme::white(12));
}

/// Small uppercase label above a card.
pub fn section_label(ui: &mut Ui, text: &str) {
    ui.horizontal(|ui| {
        ui.add_space(4.0);
        label(ui, &text.to_uppercase(), theme::semibold(11.0), theme::TEXT_DIM);
    });
    ui.add_space(8.0);
}

/// Page title and subtitle.
pub fn page_header(ui: &mut Ui, title: &str, subtitle: &str) {
    ui.vertical(|ui| {
        label(ui, title, theme::bold(26.0), theme::TEXT);
        ui.add_space(4.0);
        label(ui, subtitle, theme::regular(13.0), theme::TEXT_DIM);
    });
}

/// Title and hint on the left of a settings row.
pub fn row_text(ui: &mut Ui, title: &str, hint: &str) {
    ui.vertical(|ui| {
        label(ui, title, theme::semibold(14.0), theme::TEXT);
        ui.add_space(3.0);
        label(ui, hint, theme::regular(12.0), theme::TEXT_DIM);
    });
}

/// A pill-shaped tag (`MINECRAFT 1.8.9 · FORGE`).
pub fn pill(ui: &mut Ui, text: &str) {
    let g = galley(ui, text, theme::font(Weight::SemiBold, 11.0), theme::ACCENT_LIGHT, 0.08);
    let size = g.size() + vec2(24.0, 12.0);
    let (rect, _) = ui.allocate_exact_size(size, Sense::hover());
    let painter = ui.painter();
    let r = rect.height() / 2.0;
    painter.rect(
        rect,
        r,
        theme::white(14),
        Stroke::new(1.0, theme::white(26)),
        StrokeKind::Inside,
    );
    painter.galley(rect.min + vec2(12.0, 6.0), g, theme::ACCENT_LIGHT);
}

/// Green "Active" badge.
pub fn badge(ui: &mut Ui, text: &str) -> Response {
    let g = galley(ui, text, theme::semibold(11.5), theme::SUCCESS, 0.0);
    let (rect, response) = ui.allocate_exact_size(g.size() + vec2(20.0, 8.0), Sense::hover());
    ui.painter().rect_filled(
        rect,
        rect.height() / 2.0,
        Color32::from_rgba_unmultiplied(70, 196, 126, 36),
    );
    ui.painter().galley(rect.min + vec2(10.0, 4.0), g, theme::SUCCESS);
    response
}

/// A 36 px rounded avatar: the skin's face, or an accent square while none is known.
pub fn avatar(painter: &Painter, rect: Rect, face: Option<&TextureHandle>) {
    match face {
        Some(texture) => {
            let uv = Rect::from_min_max(Pos2::ZERO, pos2(1.0, 1.0));
            let shape = egui::epaint::RectShape::filled(rect, 5.0, Color32::WHITE).with_texture(texture.id(), uv);
            painter.add(shape);
        }
        None => {
            painter.rect_filled(rect, 5.0, theme::ACCENT_DARK);
        }
    }
}

/// Centre `galley` text in `rect`.
pub fn centered(painter: &Painter, rect: Rect, g: Arc<Galley>, color: Color32) {
    let pos = rect.center() - g.size() / 2.0;
    painter.galley(pos2(pos.x.round(), pos.y.round()), g, color);
}
