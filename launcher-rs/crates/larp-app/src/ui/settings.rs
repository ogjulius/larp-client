//! Settings, grouped like a native preferences pane and saved on every change (`SettingsPanel`).
//! Unlike the Java page, every row fits the window: no horizontal scrolling, nothing cut off.

use super::Inputs;
use crate::model::Model;
use crate::theme;
use crate::widgets::{self, ButtonKind};
use eframe::egui::{self, Align, Layout, Margin, Rect, ScrollArea, Sense, Ui, UiBuilder, vec2};

/// Window size limits (the Java spinners' ranges).
const WIDTH_RANGE: std::ops::RangeInclusive<u32> = 640..=7680;
const HEIGHT_RANGE: std::ops::RangeInclusive<u32> = 480..=4320;

pub fn show(ui: &mut Ui, model: &mut Model, inputs: &mut Inputs, page: Rect) {
    let area = Rect::from_min_max(page.min + vec2(44.0, 40.0), page.max - vec2(44.0, 0.0));
    ui.scope_builder(
        UiBuilder::new().max_rect(area).layout(Layout::top_down(Align::Min)),
        |ui| {
            ui.spacing_mut().item_spacing = vec2(8.0, 0.0);
            widgets::page_header(ui, "Settings", "Changes are saved automatically.");
            ui.add_space(22.0);
            ScrollArea::vertical().auto_shrink(false).show(ui, |ui| {
                ui.spacing_mut().item_spacing = vec2(8.0, 0.0);
                let mut changed = false;
                group(ui, "Game", |ui| {
                    row(ui, "Memory", "RAM for Minecraft. 2–4 GB is plenty for 1.8.9.", |ui| {
                        let max = model.max_memory_mb();
                        let mut value = model.settings.memory_mb.min(max);
                        let (rect, _) = ui.allocate_exact_size(vec2(56.0, 20.0), Sense::hover());
                        let text = format!("{:.1} GB", value as f64 / 1024.0);
                        let g = widgets::galley(ui, &text, theme::semibold(13.0), theme::TEXT, 0.0);
                        ui.painter().galley(
                            egui::pos2(rect.left(), rect.center().y - g.size().y / 2.0),
                            g,
                            theme::TEXT,
                        );
                        let slider = widgets::slider(ui, &mut value, 1024..=max, 256, 220.0, "Memory");
                        if slider.changed() {
                            model.settings.memory_mb = value;
                            changed = true;
                        }
                    });
                    widgets::hairline(ui);
                    row(ui, "Window size", "Size of the game window when it opens.", |ui| {
                        changed |= number(
                            ui,
                            &mut inputs.height,
                            &mut model.settings.height,
                            HEIGHT_RANGE,
                            "Window height",
                        );
                        widgets::label(ui, "×", theme::regular(13.0), theme::TEXT_DIM);
                        changed |= number(
                            ui,
                            &mut inputs.width,
                            &mut model.settings.width,
                            WIDTH_RANGE,
                            "Window width",
                        );
                    });
                    widgets::hairline(ui);
                    row(
                        ui,
                        "Start in fullscreen",
                        "Compositor-managed fullscreen: works on Hyprland, Sway, KDE and GNOME.",
                        |ui| {
                            changed |=
                                widgets::toggle(ui, &mut model.settings.fullscreen, "Start in fullscreen").changed()
                        },
                    );
                    widgets::hairline(ui);
                    row(
                        ui,
                        "Threaded OpenGL",
                        "Mesa glthread / NVIDIA threaded optimisations. Usually a large FPS boost.",
                        |ui| {
                            changed |= widgets::toggle(ui, &mut model.settings.threaded_gl, "Threaded OpenGL").changed()
                        },
                    );
                });
                group(ui, "Java", |ui| {
                    row(
                        ui,
                        "Java 8 executable",
                        "Leave on automatic to use your system Java 8 or a managed runtime.",
                        |ui| {
                            if widgets::button(ui, "Browse", ButtonKind::Normal).clicked() {
                                model.pick_java();
                            }
                            let java = &mut model.settings.java_path;
                            changed |= widgets::text_field(ui, java, 260.0, "Automatic", "Java 8 executable").changed();
                        },
                    );
                    widgets::hairline(ui);
                    row(
                        ui,
                        "JVM arguments",
                        "Advanced. Defaults are tuned for consistent frame times.",
                        |ui| {
                            let args = &mut model.settings.jvm_args;
                            changed |= widgets::text_field(ui, args, 340.0, "", "JVM arguments").changed();
                        },
                    );
                    if let Err(problem) = larp_launch::jvm_args::split(&model.settings.jvm_args) {
                        ui.horizontal(|ui| {
                            widgets::label(
                                ui,
                                &format!("JVM arguments: {problem}"),
                                theme::regular(12.0),
                                theme::ERROR_TEXT,
                            );
                        });
                        ui.add_space(10.0);
                    }
                });
                group(ui, "Launcher", |ui| {
                    row(
                        ui,
                        "Keep launcher open",
                        "Stay open while playing to show live logs. Off: the launcher closes once the game starts.",
                        |ui| {
                            let keep = &mut model.settings.keep_launcher_open;
                            changed |= widgets::toggle(ui, keep, "Keep launcher open").changed();
                        },
                    );
                    widgets::hairline(ui);
                    row(
                        ui,
                        "Microsoft sign-in",
                        "Client ID of an Azure app approved for Minecraft (see DEVELOPMENT.md).",
                        |ui| {
                            let id = &mut model.settings.microsoft_client_id;
                            let field = widgets::text_field(
                                ui,
                                id,
                                300.0,
                                "Azure application (client) ID",
                                "Microsoft client ID",
                            );
                            changed |= field.changed();
                        },
                    );
                    widgets::hairline(ui);
                    row(
                        ui,
                        "Game folder",
                        "Worlds, resource packs, screenshots and logs.",
                        |ui| {
                            if widgets::button(ui, "Open", ButtonKind::Normal).clicked() {
                                model.open_game_folder();
                            }
                            let path = model.files.root.display().to_string();
                            let mut job =
                                egui::text::LayoutJob::simple_singleline(path, theme::regular(12.0), theme::TEXT_DIM);
                            job.wrap = egui::text::TextWrapping::truncate_at_width(360.0);
                            let g = ui.fonts_mut(|f| f.layout_job(job));
                            let (rect, _) = ui.allocate_exact_size(g.size(), Sense::hover());
                            ui.painter().galley(rect.min, g, theme::TEXT_DIM);
                        },
                    );
                });
                ui.add_space(40.0);
                if changed {
                    model.settings_changed();
                }
            });
        },
    );
}

/// A titled card of rows.
fn group(ui: &mut Ui, title: &str, rows: impl FnOnce(&mut Ui)) {
    widgets::section_label(ui, title);
    widgets::card(
        ui,
        theme::CARD,
        Margin {
            left: 20,
            right: 18,
            top: 4,
            bottom: 4,
        },
        rows,
    );
    ui.add_space(22.0);
}

/// Title and hint on the left, controls (added right to left) on the right.
fn row(ui: &mut Ui, title: &str, hint: &str, controls: impl FnOnce(&mut Ui)) {
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

/// A whole-number field; the setting changes only while the text is a number in `range`.
fn number(ui: &mut Ui, text: &mut String, value: &mut u32, range: std::ops::RangeInclusive<u32>, name: &str) -> bool {
    let field = widgets::text_field(ui, text, 84.0, "", name);
    if field.changed() {
        text.retain(|c| c.is_ascii_digit());
        if let Ok(v) = text.parse::<u32>()
            && range.contains(&v)
            && v != *value
        {
            *value = v;
            return true;
        }
    }
    if field.lost_focus() {
        *text = value.to_string(); // drop an out-of-range entry
    }
    false
}
