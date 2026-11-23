//! Modal dialogs: Microsoft sign-in (device code) and the start-up warning about unreadable files.

use crate::model::{Model, SignIn};
use crate::theme;
use crate::widgets::{self, ButtonKind};
use eframe::egui::{self, Color32, CornerRadius, Id, Margin, Modal, Stroke, vec2};

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
    if model.sign_in.is_some() {
        sign_in(ctx, model);
    } else if !model.problems.is_empty() {
        problems(ctx, model);
    }
}

fn sign_in(ctx: &egui::Context, model: &mut Model) {
    let state = model.sign_in.clone();
    let modal = Modal::new(Id::unique("sign-in"))
        .frame(frame())
        .backdrop_color(Color32::from_black_alpha(140))
        .show(ctx, |ui| {
            ui.set_width(384.0);
            ui.spacing_mut().item_spacing = vec2(8.0, 0.0);
            widgets::label(ui, "Sign in with Microsoft", theme::bold(18.0), theme::TEXT);
            ui.add_space(6.0);
            let (info, code, uri) = match &state {
                Some(SignIn::Code { user_code, uri }) => (
                    "Go to the address below and enter this code:",
                    user_code.as_str(),
                    uri.as_str(),
                ),
                _ => ("Requesting a code…", " ", " "),
            };
            widgets::label(ui, info, theme::regular(13.0), theme::TEXT_DIM);
            ui.add_space(12.0);
            widgets::label(ui, code, theme::bold(36.0), theme::TEXT);
            ui.add_space(12.0);
            widgets::label(ui, uri, theme::regular(13.0), theme::ACCENT_LIGHT);
            ui.add_space(12.0);
            let ready = matches!(state, Some(SignIn::Code { .. }));
            let mut cancel = false;
            ui.horizontal(|ui| {
                if widgets::button_enabled(ui, "Open browser", ButtonKind::Normal, ready).clicked() {
                    model.open(uri);
                }
                if widgets::button_enabled(ui, "Copy code", ButtonKind::Normal, ready).clicked() {
                    ui.ctx().copy_text(code.to_owned());
                }
                cancel = widgets::button(ui, "Cancel", ButtonKind::Normal).clicked();
            });
            cancel
        });
    if modal.inner || modal.should_close() {
        model.cancel_sign_in();
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
