//! Modal dialog: the start-up warning about unreadable files.

use crate::model::Model;
use crate::theme;
use crate::widgets::{self, ButtonKind};
use eframe::egui::{self, Color32, CornerRadius, Id, Margin, Modal, Stroke};

fn frame() -> egui::Frame {
    egui::Frame::new()
        .fill(theme::CARD)
        .stroke(Stroke::new(1.0, theme::BORDER))
        .corner_radius(CornerRadius::same(9))
        .inner_margin(Margin {
            left: 28,
            right: 28,
            top: 26,
            bottom: 24,
        })
}

pub fn show(ctx: &egui::Context, model: &mut Model) {
    if !model.problems.is_empty() {
        problems(ctx, model);
    }
}

/// A settings or accounts file was unreadable and has been moved aside: say so (never silently lost).
fn problems(ctx: &egui::Context, model: &mut Model) {
    let folder = model.settings_folder().map(|f| format!("Folder: {}", f.display()));
    let text = model.problems.join("\n\n");
    let modal = Modal::new(Id::unique("problems"))
        .frame(frame())
        .backdrop_color(Color32::from_black_alpha(140))
        .show(ctx, |ui| {
            ui.set_max_width(520.0);
            widgets::label(ui, "LARP Client", theme::bold(18.0), theme::TEXT);
            ui.add_space(10.0);
            ui.add(
                egui::Label::new(
                    egui::RichText::new(text)
                        .font(theme::regular(13.0))
                        .color(theme::TEXT_DIM),
                )
                .wrap(),
            );
            if let Some(folder) = folder {
                ui.add_space(10.0);
                ui.add(
                    egui::Label::new(
                        egui::RichText::new(folder)
                            .font(theme::regular(13.0))
                            .color(theme::TEXT_DIM),
                    )
                    .wrap(),
                );
            }
            ui.add_space(16.0);
            widgets::button(ui, "OK", ButtonKind::Primary).clicked()
        });
    if modal.inner || modal.should_close() {
        model.problems.clear();
    }
}
