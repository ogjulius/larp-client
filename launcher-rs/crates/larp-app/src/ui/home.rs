//! Home: the panning panorama, the wordmark, the Launch button with inline progress, and the
//! active account (`HomePanel`, `HomeBackdrop`, `LaunchButton` in the Java launcher).

use super::Textures;
use crate::model::{Launch, Model, Page};
use crate::paint;
use crate::theme;
use crate::widgets;
use eframe::egui::{
    self, Color32, CursorIcon, Rect, Role, Sense, Stroke, StrokeKind, Ui, UiBuilder, WidgetInfo, pos2, vec2,
};
use larp_core::files::MC_VERSION;
use std::time::Duration;

const PAD_TOP: f32 = 64.0;
const PAD_LEFT: f32 = 56.0;
const PAD_BOTTOM: f32 = 44.0;
const PAD_RIGHT: f32 = 48.0;
/// Panorama pan speed, px/s (Java: 6).
const PAN_SPEED: f64 = 6.0;

pub fn show(ui: &mut Ui, model: &mut Model, textures: &mut Textures, page: Rect) {
    backdrop(ui, textures, page);

    // Hero: version pill, wordmark, tagline.
    let hero = Rect::from_min_max(
        page.min + vec2(PAD_LEFT, PAD_TOP),
        page.max - vec2(PAD_RIGHT, PAD_BOTTOM),
    );
    ui.scope_builder(UiBuilder::new().max_rect(hero), |ui| {
        ui.spacing_mut().item_spacing = vec2(0.0, 0.0);
        widgets::pill(ui, &format!("MINECRAFT {MC_VERSION}  ·  FORGE"));
        ui.add_space(18.0);
        let wordmark = widgets::galley(ui, "LARP", theme::bold(88.0), theme::TEXT, -0.035);
        let (rect, _) = ui.allocate_exact_size(wordmark.size(), Sense::hover());
        ui.painter().galley(rect.min, wordmark, theme::TEXT);
        ui.add_space(2.0);
        widgets::label(
            ui,
            "A quiet, fast client for 1.8.9 PvP.",
            theme::regular(18.0),
            theme::TAGLINE,
        );
    });

    // Bottom row: Launch button and status on the left, account card on the right. The status line
    // sits on the bottom padding; the button and the card share their top edge.
    let status_height = 15.0;
    let bottom = page.bottom() - PAD_BOTTOM;
    let button = Rect::from_min_size(
        pos2(page.left() + PAD_LEFT, bottom - status_height - 12.0 - 60.0),
        vec2(300.0, 60.0),
    );
    launch_button(ui, model, button);
    let (status, error) = model.status_line();
    if !status.is_empty() {
        let color = if error { theme::ERROR_TEXT } else { theme::TEXT_DIM };
        let g = widgets::galley(ui, &status, theme::regular(12.5), color, 0.0);
        let max_width = page.right() - PAD_RIGHT - button.left() - 4.0;
        let g = if g.size().x > max_width {
            elided(ui, &status, theme::regular(12.5), color, max_width)
        } else {
            g
        };
        ui.painter()
            .galley(pos2(button.left() + 4.0, button.bottom() + 12.0), g, color);
    }
    account_card(ui, model, textures, pos2(page.right() - PAD_RIGHT, button.top()));
}

fn elided(ui: &Ui, text: &str, font: egui::FontId, color: Color32, max_width: f32) -> std::sync::Arc<egui::Galley> {
    let mut job = egui::text::LayoutJob::simple_singleline(text.to_owned(), font, color);
    job.wrap = egui::text::TextWrapping::truncate_at_width(max_width);
    ui.fonts_mut(|f| f.layout_job(job))
}

/// The game's own panorama, blurred and slowly panning, under the static layers.
fn backdrop(ui: &mut Ui, textures: &mut Textures, page: Rect) {
    let ctx = ui.ctx().clone();
    let painter = ui.painter_at(page);
    let uv = Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0));
    match textures.panorama.clone() {
        Some(panorama) => {
            painter.rect_filled(page, 0.0, theme::BG);
            let [tw, th] = panorama.size().map(|v| v as f32);
            let scale = page.height() * 1.08 / th;
            let face_width = th * scale; // one cube face
            let cycle = f64::from(face_width * 4.0);
            let time = ui.input(|i| i.time);
            #[allow(clippy::cast_possible_truncation)] // within one cycle
            let offset = ((time * PAN_SPEED) % cycle) as f32;
            let size = vec2(tw * scale, th * scale);
            let y = page.top() + (page.height() - size.y) / 2.0;
            let mut x = page.left() - offset;
            while x < page.right() {
                painter.image(panorama.id(), Rect::from_min_size(pos2(x, y), size), uv, Color32::WHITE);
                x += face_width * 4.0;
            }
            let layers = textures.backdrop(&ctx, page.size(), true);
            painter.image(layers.id(), page, uv, Color32::WHITE);
            // ~30 fps while visible: the pan is slow, so this is plenty smooth.
            ctx.request_repaint_after(Duration::from_millis(33));
        }
        None => {
            let layers = textures.backdrop(&ctx, page.size(), false);
            painter.image(layers.id(), page, uv, Color32::WHITE);
        }
    }
}

/// The primary call to action. While busy it becomes its own progress bar.
fn launch_button(ui: &mut Ui, model: &mut Model, rect: Rect) {
    let busy = model.launch != Launch::Idle;
    let label = match &model.launch {
        Launch::Idle => model.launch_label().to_owned(),
        Launch::Busy { label, .. } => label.clone(),
    };
    let response = ui.interact(rect, ui.scope_id().with("launch"), Sense::click());
    response.widget_info(|| WidgetInfo::labeled(Role::Button, !busy, &label));
    let hovered = response.hovered() && !busy;
    if hovered {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    let hover = ui.ctx().animate_bool_with_time(response.id, hovered, 0.12);
    let pressed = !busy && response.is_pointer_button_down_on();
    let rect = rect.shrink(if pressed { 1.5 } else { 0.0 });
    let shape = paint::rounded_rect(rect, 8.0);
    let painter = ui.painter();

    match &model.launch {
        Launch::Busy { detail, fraction, .. } => {
            paint::fill_convex(painter, &shape, |_| Color32::from_rgb(0x1E, 0x1A, 0x2E));
            let (from, to) = match fraction {
                Some(f) => (rect.left(), rect.left() + rect.width() * f.clamp(0.0, 1.0) as f32),
                None => {
                    // Indeterminate: a third-width segment sweeping across (Java: x = (ms/4) % (w+seg) − seg).
                    let segment = rect.width() / 3.0;
                    let ms = ui.input(|i| i.time) * 1000.0;
                    #[allow(clippy::cast_possible_truncation)] // within one sweep
                    let x = ((ms / 4.0) % f64::from(rect.width() + segment)) as f32 - segment;
                    ui.ctx().request_repaint();
                    (rect.left() + x, rect.left() + x + segment)
                }
            };
            let bar = paint::clip_x(&shape, from, to);
            let (left, width) = (rect.left(), rect.width());
            paint::fill_convex(painter, &bar, |p| {
                paint::mix(theme::ACCENT_DARK, theme::ACCENT, (p.x - left) / width)
            });
            if detail.is_empty() {
                let g = widgets::galley(ui, &label, theme::semibold(18.0), Color32::WHITE, 0.0);
                widgets::centered(painter, rect, g, Color32::WHITE);
            } else {
                let mid = rect.top() + (rect.height() / 2.0).floor();
                widgets::text_at(
                    painter,
                    ui,
                    pos2(rect.left() + 22.0, mid - 2.0),
                    0.0,
                    &label,
                    theme::semibold(15.0),
                    Color32::WHITE,
                );
                let dim = Color32::from_rgba_unmultiplied(255, 255, 255, 190);
                widgets::text_at(
                    painter,
                    ui,
                    pos2(rect.left() + 22.0, mid + 15.0),
                    0.0,
                    detail,
                    theme::regular(12.0),
                    dim,
                );
            }
        }
        Launch::Idle => {
            let top = paint::mix(
                Color32::from_rgb(0x8B, 0x5C, 0xF6),
                Color32::from_rgb(0x9D, 0x74, 0xFF),
                hover,
            );
            let bottom = paint::mix(
                Color32::from_rgb(0x76, 0x46, 0xE8),
                Color32::from_rgb(0x8B, 0x5C, 0xF6),
                hover,
            );
            let (y0, h) = (rect.top(), rect.height());
            paint::fill_convex(painter, &shape, |p| paint::mix(top, bottom, (p.y - y0) / h));
            painter.rect_stroke(
                rect.shrink(0.5),
                7.5,
                Stroke::new(1.0, theme::white(36)),
                StrokeKind::Middle,
            );
            let g = widgets::galley(ui, &label, theme::semibold(18.0), Color32::WHITE, 0.0);
            let play = label == "Launch";
            let icon_space = if play { 26.0 } else { 0.0 };
            let text_x = rect.left() + ((rect.width() - g.size().x - icon_space) / 2.0).floor() + icon_space;
            let text_y = rect.center().y - g.size().y / 2.0;
            if play {
                paint::glyph(
                    painter,
                    paint::Glyph::Play,
                    pos2(text_x - 28.0, rect.center().y - 10.0),
                    Color32::WHITE,
                );
            }
            painter.galley(pos2(text_x, text_y.round()), g, Color32::WHITE);
        }
    }
    if response.clicked() && !busy {
        model.launch_clicked();
    }
}

/// The active account, bottom right; clicking it opens Accounts.
fn account_card(ui: &mut Ui, model: &mut Model, textures: &mut Textures, top_right: egui::Pos2) {
    let account = model.accounts.selected().cloned();
    let (name, kind) = match &account {
        None => ("No account".to_owned(), "Add one to play  ›".to_owned()),
        Some(a) => (a.username.clone(), "Offline profile  ·  switch".to_owned()),
    };
    let name_g = widgets::galley(ui, &name, theme::semibold(14.0), theme::TEXT, 0.0);
    let kind_g = widgets::galley(ui, &kind, theme::regular(12.0), theme::TEXT_DIM, 0.0);
    let text_width = name_g.size().x.max(kind_g.size().x);
    let text_height = name_g.size().y + 2.0 + kind_g.size().y;
    let size = vec2(
        12.0 + 36.0 + 12.0 + text_width + 18.0,
        12.0 + text_height.max(36.0) + 12.0,
    );
    let rect = Rect::from_min_size(pos2(top_right.x - size.x, top_right.y), size);
    let response = ui.interact(rect, ui.scope_id().with("account-card"), Sense::click());
    response.widget_info(|| WidgetInfo::labeled(Role::Button, true, format!("{name}, {kind}")));
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    let painter = ui.painter();
    painter.rect(
        rect,
        8.0,
        Color32::from_rgba_unmultiplied(16, 16, 20, 200),
        Stroke::new(1.0, theme::HAIRLINE),
        StrokeKind::Inside,
    );
    let avatar = Rect::from_min_size(pos2(rect.left() + 12.0, rect.center().y - 18.0), vec2(36.0, 36.0));
    let face = textures.face(ui.ctx(), model, account.as_ref());
    widgets::avatar(painter, avatar, face.as_ref());
    let text_top = rect.center().y - text_height / 2.0;
    let text_left = avatar.right() + 12.0;
    painter.galley(pos2(text_left, text_top), name_g.clone(), theme::TEXT);
    painter.galley(
        pos2(text_left, text_top + name_g.size().y + 2.0),
        kind_g,
        theme::TEXT_DIM,
    );
    if response.clicked() {
        model.show(Page::Accounts);
    }
}
