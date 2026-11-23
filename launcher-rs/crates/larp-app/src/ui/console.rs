//! Console: live output from the game and the launcher (`ConsolePanel`).

use crate::model::Model;
use crate::theme;
use crate::widgets::{self, ButtonKind};
use eframe::egui::{self, Align, Layout, Margin, Rect, ScrollArea, Ui, UiBuilder, vec2};

pub fn show(ui: &mut Ui, model: &mut Model, page: Rect) {
    let area = Rect::from_min_max(page.min + vec2(44.0, 40.0), page.max - vec2(44.0, 36.0));
    ui.scope_builder(
        UiBuilder::new().max_rect(area).layout(Layout::top_down(Align::Min)),
        |ui| {
            ui.spacing_mut().item_spacing = vec2(8.0, 0.0);
            let header =
                ui.horizontal(|ui| widgets::page_header(ui, "Console", "Live output from the game and the launcher."));
            let actions = Rect::from_min_max(
                egui::pos2(area.left(), area.top()),
                egui::pos2(area.right() - 8.0, header.response.rect.bottom()),
            );
            let mut buttons = ui.new_child(
                UiBuilder::new()
                    .max_rect(actions)
                    .layout(Layout::right_to_left(Align::Center)),
            );
            buttons.spacing_mut().item_spacing = vec2(8.0, 0.0);
            if widgets::button(&mut buttons, "Logs folder", ButtonKind::Normal).clicked() {
                model.open_logs_folder();
            }
            if widgets::button(&mut buttons, "Clear", ButtonKind::Normal).clicked() {
                model.console.clear();
            }
            if widgets::button(&mut buttons, "Copy", ButtonKind::Normal).clicked() {
                buttons.ctx().copy_text(model.console.text());
            }
            ui.add_space(22.0);
            widgets::card(ui, theme::CONSOLE, Margin::same(2), |ui| {
                ui.set_height(ui.available_height());
                if model.console.is_empty() {
                    let at = ui.max_rect().min + vec2(18.0, 32.0);
                    widgets::text_at(
                        ui.painter(),
                        ui,
                        at,
                        0.0,
                        "Game output will appear here after you press Launch.",
                        theme::regular(13.0),
                        theme::TEXT_DIM,
                    );
                    return;
                }
                let font = egui::FontId::monospace(12.0);
                let row_height = ui.fonts_mut(|f| f.row_height(&font));
                let lines: Vec<&str> = model.console.lines().collect();
                ScrollArea::both().auto_shrink(false).stick_to_bottom(true).show_rows(
                    ui,
                    row_height,
                    lines.len(),
                    |ui, range| {
                        egui::Frame::new()
                            .inner_margin(Margin::symmetric(16, 0))
                            .show(ui, |ui| {
                                for line in &lines[range] {
                                    ui.add(
                                        egui::Label::new(
                                            egui::RichText::new(*line).font(font.clone()).color(theme::CONSOLE_TEXT),
                                        )
                                        .extend()
                                        .selectable(true),
                                    );
                                }
                            });
                    },
                );
            });
        },
    );
}
