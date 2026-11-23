//! The window: icon rail, the four pages, overlaid window controls, and the dialogs. Geometry
//! follows the Java launcher (`LauncherFrame` and friends) pixel for pixel where it can.

mod accounts;
mod console;
mod dialogs;
mod home;
mod settings;

use crate::art;
use crate::backend::Effects;
use crate::model::{Model, Page};
use crate::paint::{self, Glyph};
use crate::theme;
use crate::widgets;
use eframe::egui::{
    self, Color32, ColorImage, CursorIcon, Rect, Role, Sense, Stroke, StrokeKind, TextureHandle, TextureOptions, Ui,
    UiBuilder, Vec2, ViewportCommand, WidgetInfo, pos2, vec2,
};
use larp_core::Account;
use std::collections::HashMap;

/// The window's fixed size. Tiling window managers (Hyprland, Sway, i3) float windows whose
/// minimum and maximum sizes are equal.
pub const WINDOW: Vec2 = vec2(1080.0, 680.0);
const RAIL_WIDTH: f32 = 84.0;

/// GPU textures made from the model's images, rebuilt when the art changes.
#[derive(Default)]
struct Textures {
    generation: Option<u64>,
    logo: Option<TextureHandle>,
    panorama: Option<TextureHandle>,
    /// Static Home backdrop layers for (page size in pixels, with panorama).
    backdrop: Option<([usize; 2], bool, TextureHandle)>,
    faces: HashMap<String, Option<TextureHandle>>,
}

impl Textures {
    fn sync(&mut self, ctx: &egui::Context, model: &Model) {
        if self.generation == Some(model.art_generation) {
            return;
        }
        self.generation = Some(model.art_generation);
        self.faces.clear();
        self.panorama = model
            .art
            .panorama
            .as_ref()
            .map(|p| ctx.load_texture("panorama", color_image(p), TextureOptions::LINEAR));
    }

    fn logo(&mut self, ctx: &egui::Context) -> &TextureHandle {
        self.logo.get_or_insert_with(|| {
            let px = (40.0 * ctx.pixels_per_point()).round().max(1.0) as usize;
            ctx.load_texture("logo", color_image(&crate::icon::render(px)), TextureOptions::LINEAR)
        })
    }

    fn face(&mut self, ctx: &egui::Context, model: &Model, account: Option<&Account>) -> Option<TextureHandle> {
        let key = account.map_or_else(String::new, |a| a.uuid.clone());
        self.faces
            .entry(key.clone())
            .or_insert_with(|| {
                let skin = model.skin(account)?;
                let face = art::face(&skin)?;
                Some(ctx.load_texture(format!("face-{key}"), color_image(&face), TextureOptions::NEAREST))
            })
            .clone()
    }

    fn backdrop(&mut self, ctx: &egui::Context, size: Vec2, with_panorama: bool) -> TextureHandle {
        let ppp = ctx.pixels_per_point();
        #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)] // window-sized
        let px = [(size.x * ppp).round() as usize, (size.y * ppp).round() as usize];
        match &self.backdrop {
            Some((s, p, texture)) if *s == px && *p == with_panorama => texture.clone(),
            _ => {
                let layers = art::backdrop_layers(px[0], px[1], with_panorama);
                let image = ColorImage::from_rgba_premultiplied(px, &layers.rgba);
                let texture = ctx.load_texture("backdrop", image, TextureOptions::LINEAR);
                self.backdrop = Some((px, with_panorama, texture.clone()));
                texture
            }
        }
    }
}

fn color_image(image: &art::Image) -> ColorImage {
    ColorImage::from_rgba_unmultiplied([image.width, image.height], &image.rgba)
}

/// UI-only state that doesn't belong in the model.
#[derive(Debug, Default)]
struct Inputs {
    width: String,
    height: String,
}

pub struct App {
    pub model: Model,
    effects: Box<dyn Effects>,
    textures: Textures,
    inputs: Inputs,
}

impl std::fmt::Debug for App {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("App")
            .field("model", &self.model)
            .finish_non_exhaustive()
    }
}

impl App {
    pub fn new(mut model: Model, effects: Box<dyn Effects>, start: Page) -> Self {
        model.show(start);
        let inputs = Inputs {
            width: model.settings.width.to_string(),
            height: model.settings.height.to_string(),
        };
        Self {
            model,
            effects,
            textures: Textures::default(),
            inputs,
        }
    }

    fn pump(&mut self, ctx: &egui::Context) {
        for event in self.effects.poll() {
            if self.model.handle(event) {
                ctx.send_viewport_cmd(ViewportCommand::Close);
            }
        }
    }

    fn flush(&mut self) {
        for request in self.model.take_requests() {
            self.effects.submit(request);
        }
    }
}

impl eframe::App for App {
    fn logic(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.pump(ctx);
        self.flush();
    }

    fn ui(&mut self, ui: &mut Ui, _frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();
        self.textures.sync(&ctx, &self.model);
        let full = ui.max_rect();
        ui.painter().rect_filled(full, 0.0, theme::BG);
        let inner = full.shrink(1.0);
        let rail = Rect::from_min_size(inner.min, vec2(RAIL_WIDTH, inner.height()));
        let content = Rect::from_min_max(pos2(rail.right(), inner.top()), inner.max);

        // Dragging the window by any empty part of the page (registered first, so every control on
        // top of it wins). Wayland only allows compositor-driven moves, which StartDrag requests.
        let drag = ui.interact(content, ui.scope_id().with("window-drag"), Sense::drag());
        if drag.drag_started() {
            ctx.send_viewport_cmd(ViewportCommand::StartDrag);
        }

        self.rail(ui, rail);
        let mut page_ui = ui.new_child(UiBuilder::new().max_rect(content).id_salt(self.model.page));
        page_ui.set_clip_rect(content);
        match self.model.page {
            Page::Home => home::show(&mut page_ui, &mut self.model, &mut self.textures, content),
            Page::Accounts => accounts::show(&mut page_ui, &mut self.model, &mut self.textures, content),
            Page::Settings => settings::show(&mut page_ui, &mut self.model, &mut self.inputs, content),
            Page::Console => console::show(&mut page_ui, &mut self.model, content),
        }
        window_controls(ui, content);
        dialogs::show(&ctx, &mut self.model);
        ui.painter()
            .rect_stroke(full, 0.0, Stroke::new(1.0, theme::WINDOW_BORDER), StrokeKind::Inside);
        self.flush();
    }
}

impl App {
    fn rail(&mut self, ui: &mut Ui, rail: Rect) {
        let painter = ui.painter().clone();
        painter.rect_filled(rail, 0.0, theme::SIDEBAR);
        painter.rect_filled(
            Rect::from_min_max(pos2(rail.right() - 1.0, rail.top()), rail.max),
            0.0,
            theme::white(10),
        );
        let logo = Rect::from_min_size(pos2(rail.center().x - 20.0, rail.top() + 20.0), vec2(40.0, 40.0));
        let texture = self.textures.logo(ui.ctx()).clone();
        painter.image(
            texture.id(),
            logo,
            Rect::from_min_max(pos2(0.0, 0.0), pos2(1.0, 1.0)),
            Color32::WHITE,
        );

        let mut y = logo.bottom() + 28.0;
        for page in Page::ALL {
            let glyph = match page {
                Page::Home => Glyph::Home,
                Page::Accounts => Glyph::User,
                Page::Settings => Glyph::Sliders,
                Page::Console => Glyph::Terminal,
            };
            let rect = Rect::from_min_size(pos2(rail.left(), y), vec2(RAIL_WIDTH, 60.0));
            if nav_item(ui, rect, page.label(), glyph, self.model.page == page, page.label()).clicked() {
                self.model.show(page);
            }
            y += 64.0;
        }
        let files = Rect::from_min_size(pos2(rail.left(), rail.bottom() - 16.0 - 60.0), vec2(RAIL_WIDTH, 60.0));
        if nav_item(ui, files, "Files", Glyph::Folder, false, "Open the game folder").clicked() {
            self.model.open_game_folder();
        }
    }
}

/// One entry of the icon rail (`NavRail`).
fn nav_item(ui: &mut Ui, rect: Rect, label: &str, glyph: Glyph, active: bool, tooltip: &str) -> egui::Response {
    let response = ui
        .interact(rect, ui.scope_id().with(("nav", label)), Sense::click())
        .on_hover_text(tooltip);
    response.widget_info(|| WidgetInfo::selected(Role::Tab, true, active, label));
    let hovered = response.hovered();
    if hovered {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    let painter = ui.painter();
    if active || hovered {
        let bg = Rect::from_min_size(rect.min + vec2(12.0, 4.0), vec2(rect.width() - 24.0, 52.0));
        painter.rect_filled(bg, 7.0, theme::white(if active { 18 } else { 8 }));
    }
    if active {
        painter.rect_filled(
            Rect::from_min_size(rect.min + vec2(0.0, 20.0), vec2(3.0, 20.0)),
            1.5,
            theme::ACCENT,
        );
    }
    let color = if active {
        theme::ACCENT_LIGHT
    } else if hovered {
        theme::TEXT
    } else {
        theme::TEXT_DIM
    };
    paint::glyph(painter, glyph, pos2(rect.center().x - 10.0, rect.top() + 11.0), color);
    let text_color = if active { theme::TEXT } else { theme::TEXT_DIM };
    widgets::text_at(
        painter,
        ui,
        pos2(rect.center().x, rect.top() + 47.0),
        0.5,
        label,
        theme::semibold(10.5),
        text_color,
    );
    response
}

/// Minimise and close, over the top-right corner of the page.
fn window_controls(ui: &mut Ui, content: Rect) {
    let close = Rect::from_min_size(pos2(content.right() - 46.0, content.top()), vec2(46.0, 36.0));
    let minimize = close.translate(vec2(-46.0, 0.0));
    for (rect, glyph, name, danger) in [
        (minimize, Glyph::Minimize, "Minimize", false),
        (close, Glyph::Close, "Close", true),
    ] {
        let response = ui.interact(rect, ui.scope_id().with(name), Sense::click());
        response.widget_info(|| WidgetInfo::labeled(Role::Button, true, name));
        let hovered = response.hovered();
        if hovered {
            ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
            ui.painter()
                .rect_filled(rect, 0.0, if danger { theme::DANGER } else { theme::white(20) });
        }
        let color = if hovered { Color32::WHITE } else { theme::TEXT_DIM };
        paint::glyph(ui.painter(), glyph, rect.center() - vec2(10.0, 10.0), color);
        if response.clicked() {
            let command = if danger {
                ViewportCommand::Close
            } else {
                ViewportCommand::Minimized(true)
            };
            ui.ctx().send_viewport_cmd(command);
        }
    }
}
