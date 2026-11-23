//! Accounts: the saved offline profiles, and adding one (`AccountsPanel` in the Java launcher).

use super::Textures;
use crate::model::Model;
use crate::theme;
use crate::widgets::{self, ButtonKind};
use eframe::egui::{self, Align, Key, Layout, Margin, Rect, Sense, Ui, UiBuilder, vec2};

pub fn show(ui: &mut Ui, model: &mut Model, textures: &mut Textures, page: Rect) {
    let area = Rect::from_min_max(page.min + vec2(44.0, 40.0), page.max - vec2(44.0, 36.0));
    ui.scope_builder(
        UiBuilder::new().max_rect(area).layout(Layout::top_down(Align::Min)),
        |ui| {
            ui.spacing_mut().item_spacing = vec2(8.0, 0.0);
            widgets::page_header(ui, "Accounts", "Choose who you play as.");
            ui.add_space(22.0);

            widgets::section_label(ui, "Your accounts");
            widgets::card(ui, theme::CARD, Margin::symmetric(14, 4), |ui| {
                let accounts = model.accounts.all().to_vec();
                if accounts.is_empty() {
                    ui.add_space(16.0);
                    ui.horizontal(|ui| {
                        ui.add_space(6.0);
                        widgets::label(
                            ui,
                            "No accounts yet — add one below.",
                            theme::regular(13.0),
                            theme::TEXT_DIM,
                        );
                    });
                    ui.add_space(16.0);
                }
                let selected = model.accounts.selected().map(|a| a.uuid.clone());
                for (i, account) in accounts.iter().enumerate() {
                    if i > 0 {
                        widgets::hairline(ui);
                    }
                    let active = selected.as_deref() == Some(account.uuid.as_str());
                    let row = account_row(ui, model, textures, account, active);
                    match row {
                        RowAction::Use => model.select_account(&account.uuid),
                        RowAction::Remove => model.remove_account(&account.uuid),
                        RowAction::None => {}
                    }
                }
            });
            ui.add_space(22.0);

            widgets::section_label(ui, "Add an account");
            widgets::card(
                ui,
                theme::CARD,
                Margin {
                    left: 20,
                    right: 18,
                    top: 4,
                    bottom: 4,
                },
                |ui| {
                    add_row(
                        ui,
                        "Offline profile",
                        "For singleplayer and offline-mode servers.",
                        |ui| {
                            if widgets::button(ui, "Add", ButtonKind::Primary).clicked() {
                                model.add_offline();
                            }
                            let field =
                                widgets::text_field(ui, &mut model.offline_name, 180.0, "Username", "Offline username");
                            if model.focus_username {
                                field.request_focus();
                                model.focus_username = false;
                            }
                            if field.lost_focus() && ui.input(|i| i.key_pressed(Key::Enter)) {
                                model.add_offline();
                            }
                        },
                    );
                },
            );
            if let Some(message) = &model.accounts_message {
                ui.add_space(10.0);
                ui.horizontal(|ui| {
                    ui.add_space(4.0);
                    let color = if message.error {
                        theme::ERROR_TEXT
                    } else {
                        theme::SUCCESS
                    };
                    widgets::label(ui, &message.text, theme::regular(12.5), color);
                });
            }
        },
    );
}

enum RowAction {
    None,
    Use,
    Remove,
}

fn account_row(
    ui: &mut Ui,
    model: &Model,
    textures: &mut Textures,
    account: &larp_core::Account,
    active: bool,
) -> RowAction {
    let mut action = RowAction::None;
    let (rect, _) = ui.allocate_exact_size(vec2(ui.available_width(), 56.0), Sense::hover());
    let inner = rect.shrink2(vec2(4.0, 10.0));
    let avatar = Rect::from_min_size(inner.min, vec2(36.0, 36.0));
    let face = textures.face(ui.ctx(), model, Some(account));
    widgets::avatar(ui.painter(), avatar, face.as_ref());
    let kind = "Offline profile";
    let name_g = widgets::galley(ui, &account.username, theme::semibold(14.0), theme::TEXT, 0.0);
    let kind_g = widgets::galley(ui, kind, theme::regular(12.0), theme::TEXT_DIM, 0.0);
    let text_top = inner.center().y - (name_g.size().y + 2.0 + kind_g.size().y) / 2.0;
    let text_left = avatar.right() + 14.0;
    let name_height = name_g.size().y;
    ui.painter()
        .galley(egui::pos2(text_left, text_top), name_g, theme::TEXT);
    ui.painter().galley(
        egui::pos2(text_left, text_top + name_height + 2.0),
        kind_g,
        theme::TEXT_DIM,
    );

    let mut actions = ui.new_child(
        UiBuilder::new()
            .max_rect(inner)
            .layout(Layout::right_to_left(Align::Center))
            .id_salt(("account", &account.uuid)),
    );
    actions.spacing_mut().item_spacing = vec2(8.0, 0.0);
    if widgets::button(&mut actions, "Remove", ButtonKind::Borderless).clicked() {
        action = RowAction::Remove;
    }
    if active {
        widgets::badge(&mut actions, "Active");
    } else if widgets::button(&mut actions, "Use", ButtonKind::Normal).clicked() {
        action = RowAction::Use;
    }
    action
}

/// A row of the "Add an account" card: title and hint left, controls right.
fn add_row(ui: &mut Ui, title: &str, hint: &str, controls: impl FnOnce(&mut Ui)) {
    let (rect, _) = ui.allocate_exact_size(vec2(ui.available_width(), 64.0), Sense::hover());
    let inner = rect.shrink2(vec2(0.0, 14.0));
    widgets::row_text(
        &mut ui.new_child(UiBuilder::new().max_rect(inner).id_salt(title)),
        title,
        hint,
    );
    let mut controls_ui = ui.new_child(
        UiBuilder::new()
            .max_rect(inner)
            .layout(Layout::right_to_left(Align::Center))
            .id_salt((title, "controls")),
    );
    controls_ui.spacing_mut().item_spacing = vec2(8.0, 0.0);
    controls(&mut controls_ui);
}
