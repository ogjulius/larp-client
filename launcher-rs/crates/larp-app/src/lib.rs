//! The graphical LARP launcher (egui): Home, Accounts, Settings and Console, with the Java
//! launcher's design. All behaviour lives in [`model`]; [`backend`] performs the side effects.

pub mod art;
pub mod backend;
pub mod icon;
pub mod model;
mod paint;
pub mod theme;
pub mod ui;
mod widgets;

use eframe::egui;
pub use model::Page;
use std::sync::Arc;

/// Open the launcher window on `start` and run until it closes.
pub fn run(start: Page) -> Result<(), String> {
    let (files, config) = backend::dirs().ok_or("cannot determine the home directory")?;
    let icon = icon::render(128);
    let viewport = egui::ViewportBuilder::default()
        .with_title("LARP Client")
        .with_app_id("larp-client")
        .with_inner_size(ui::WINDOW)
        .with_min_inner_size(ui::WINDOW)
        .with_max_inner_size(ui::WINDOW)
        .with_resizable(false)
        .with_decorations(false)
        .with_icon(Arc::new(egui::IconData {
            rgba: icon.rgba,
            width: 128,
            height: 128,
        }));
    let options = eframe::NativeOptions {
        viewport,
        renderer: eframe::Renderer::Glow,
        ..Default::default()
    };
    eframe::run_native(
        "LARP Client",
        options,
        Box::new(move |cc| {
            theme::install(&cc.egui_ctx);
            let backend = backend::Backend::new(cc.egui_ctx.clone(), files.clone())?;
            let model = model::Model::new(files, &config, backend::total_memory_mb());
            Ok(Box::new(ui::App::new(model, Box::new(backend), start)))
        }),
    )
    .map_err(|e| e.to_string())
}
