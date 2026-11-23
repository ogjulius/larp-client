//! Drawing helpers egui lacks: gradient-filled rounded shapes (vertex-coloured meshes with a 1 px
//! anti-aliased edge) and the launcher's 20×20 line icons (`ui/Icons` in the Java launcher).

use eframe::egui::{Color32, Mesh, Painter, Pos2, Rect, Shape, Stroke, epaint::Vertex, pos2, vec2};
use std::f32::consts::{FRAC_PI_2, PI};

/// Outline of a rounded rectangle, clockwise from the top-left corner's arc.
pub fn rounded_rect(rect: Rect, radius: f32) -> Vec<Pos2> {
    let r = radius.min(rect.width() / 2.0).min(rect.height() / 2.0).max(0.0);
    const STEPS: usize = 8;
    let corners = [
        (pos2(rect.left() + r, rect.top() + r), PI),
        (pos2(rect.right() - r, rect.top() + r), PI + FRAC_PI_2),
        (pos2(rect.right() - r, rect.bottom() - r), 0.0),
        (pos2(rect.left() + r, rect.bottom() - r), FRAC_PI_2),
    ];
    let mut points = Vec::with_capacity(4 * (STEPS + 1));
    for (center, start) in corners {
        for i in 0..=STEPS {
            let a = start + FRAC_PI_2 * i as f32 / STEPS as f32;
            points.push(center + vec2(a.cos(), a.sin()) * r);
        }
    }
    points.dedup_by(|a, b| (*a - *b).length_sq() < 1e-6);
    points
}

/// Keep the part of a convex polygon with `min_x <= x <= max_x` (Sutherland–Hodgman).
pub fn clip_x(points: &[Pos2], min_x: f32, max_x: f32) -> Vec<Pos2> {
    let clip = |input: &[Pos2], keep: &dyn Fn(Pos2) -> bool, edge_x: f32| -> Vec<Pos2> {
        let mut out = Vec::with_capacity(input.len() + 2);
        for i in 0..input.len() {
            let (a, b) = (input[i], input[(i + 1) % input.len()]);
            let cross = |a: Pos2, b: Pos2| {
                let t = (edge_x - a.x) / (b.x - a.x);
                pos2(edge_x, a.y + (b.y - a.y) * t)
            };
            match (keep(a), keep(b)) {
                (true, true) => out.push(b),
                (true, false) => out.push(cross(a, b)),
                (false, true) => {
                    out.push(cross(a, b));
                    out.push(b);
                }
                (false, false) => {}
            }
        }
        out
    };
    let left = clip(points, &|p| p.x >= min_x, min_x);
    if left.len() < 3 {
        return Vec::new();
    }
    let both = clip(&left, &|p| p.x <= max_x, max_x);
    if both.len() < 3 { Vec::new() } else { both }
}

/// Fill a convex polygon, colouring each vertex with `color_at` (exact for linear gradients), with
/// a 1 px feathered edge for anti-aliasing.
pub fn fill_convex(painter: &Painter, points: &[Pos2], color_at: impl Fn(Pos2) -> Color32) {
    let n = points.len();
    if n < 3 {
        return;
    }
    // Outward normal at each vertex (polygon is clockwise in screen space).
    let normals: Vec<_> = (0..n)
        .map(|i| {
            let prev = points[(i + n - 1) % n];
            let next = points[(i + 1) % n];
            let e1 = (points[i] - prev).normalized();
            let e2 = (next - points[i]).normalized();
            let normal = vec2(e1.y, -e1.x) + vec2(e2.y, -e2.x);
            if normal.length_sq() < 1e-6 {
                vec2(e1.y, -e1.x)
            } else {
                normal.normalized()
            }
        })
        .collect();
    let mut mesh = Mesh::default();
    for (i, p) in points.iter().enumerate() {
        let inner = *p - normals[i] * 0.5;
        let outer = *p + normals[i] * 0.5;
        let color = color_at(*p);
        mesh.vertices.push(Vertex {
            pos: inner,
            uv: Pos2::ZERO,
            color,
        });
        mesh.vertices.push(Vertex {
            pos: outer,
            uv: Pos2::ZERO,
            color: Color32::TRANSPARENT,
        });
    }
    #[allow(clippy::cast_possible_truncation)] // a handful of vertices
    let idx = |i: usize| i as u32;
    for i in 1..n - 1 {
        mesh.add_triangle(0, idx(2 * i), idx(2 * (i + 1)));
    }
    for i in 0..n {
        let j = (i + 1) % n;
        mesh.add_triangle(idx(2 * i), idx(2 * i + 1), idx(2 * j));
        mesh.add_triangle(idx(2 * j), idx(2 * i + 1), idx(2 * j + 1));
    }
    painter.add(Shape::mesh(mesh));
}

/// Linear blend of two colours (straight alpha), `t` clamped to 0..=1.
pub fn mix(a: Color32, b: Color32, t: f32) -> Color32 {
    let t = t.clamp(0.0, 1.0);
    let (a, b) = (a.to_srgba_unmultiplied(), b.to_srgba_unmultiplied());
    #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)] // between two bytes
    let c = |i: usize| (f32::from(a[i]) + (f32::from(b[i]) - f32::from(a[i])) * t).round() as u8;
    Color32::from_rgba_unmultiplied(c(0), c(1), c(2), c(3))
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Glyph {
    Home,
    User,
    Sliders,
    Terminal,
    Folder,
    Minimize,
    Close,
    Play,
}

/// Draw a 20×20 line icon with its top-left corner at `origin`.
pub fn glyph(painter: &Painter, glyph: Glyph, origin: Pos2, color: Color32) {
    let p = |x: f32, y: f32| origin + vec2(x, y);
    let stroke = Stroke::new(1.7, color);
    let thin = Stroke::new(1.3, color);
    let line = |points: Vec<Pos2>, stroke: Stroke| painter.add(Shape::line(points, stroke));
    let closed = |points: Vec<Pos2>, stroke: Stroke| painter.add(Shape::closed_line(points, stroke));
    match glyph {
        Glyph::Home => {
            line(vec![p(3.0, 9.5), p(10.0, 3.5), p(17.0, 9.5)], stroke);
            line(vec![p(5.0, 8.0), p(5.0, 16.5), p(15.0, 16.5), p(15.0, 8.0)], stroke);
            closed(
                rounded_rect(Rect::from_min_size(p(8.5, 11.5), vec2(3.0, 5.0)), 0.5),
                stroke,
            );
        }
        Glyph::User => {
            painter.circle_stroke(p(10.0, 6.5), 3.5, stroke);
            // Upper half of an ellipse 13×11 at (3.5, 11.5).
            let (c, rx, ry) = (p(10.0, 17.0), 6.5, 5.5);
            let arc = (0..=16).map(|i| {
                let a = PI + PI * i as f32 / 16.0;
                c + vec2(a.cos() * rx, a.sin() * ry)
            });
            line(arc.collect(), stroke);
        }
        Glyph::Sliders => {
            for (i, knob) in [6.5, 12.5, 9.0].into_iter().enumerate() {
                let y = 5.0 + i as f32 * 5.0;
                painter.line_segment([p(3.0, y), p(17.0, y)], stroke);
                painter.circle_filled(p(knob, y), 2.3, color);
            }
        }
        Glyph::Terminal => {
            closed(
                rounded_rect(Rect::from_min_size(p(2.5, 4.0), vec2(15.0, 12.0)), 1.5),
                stroke,
            );
            line(vec![p(6.0, 8.0), p(8.5, 10.0), p(6.0, 12.0)], stroke);
            painter.line_segment([p(10.5, 12.5), p(14.0, 12.5)], stroke);
        }
        Glyph::Folder => {
            closed(
                vec![
                    p(2.5, 6.0),
                    p(3.5, 5.0),
                    p(8.0, 5.0),
                    p(9.5, 7.0),
                    p(17.5, 7.0),
                    p(17.5, 15.5),
                    p(2.5, 15.5),
                ],
                stroke,
            );
        }
        Glyph::Minimize => {
            painter.line_segment([p(6.0, 10.0), p(14.0, 10.0)], thin);
        }
        Glyph::Close => {
            painter.line_segment([p(6.0, 6.0), p(14.0, 14.0)], thin);
            painter.line_segment([p(14.0, 6.0), p(6.0, 14.0)], thin);
        }
        Glyph::Play => {
            painter.add(Shape::convex_polygon(
                vec![p(6.0, 4.0), p(16.0, 10.0), p(6.0, 16.0)],
                color,
                Stroke::NONE,
            ));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clipping_a_rounded_rect() {
        let shape = rounded_rect(Rect::from_min_size(Pos2::ZERO, vec2(100.0, 40.0)), 8.0);
        let half = clip_x(&shape, 0.0, 50.0);
        assert!(half.iter().all(|p| p.x <= 50.0 + 1e-4));
        assert!(half.iter().any(|p| (p.x - 50.0).abs() < 1e-4));
        assert!(clip_x(&shape, 200.0, 300.0).is_empty());
        assert_eq!(clip_x(&shape, -10.0, 110.0).len(), shape.len());
    }

    #[test]
    fn mixing_colours() {
        assert_eq!(
            mix(Color32::BLACK, Color32::WHITE, 0.5),
            Color32::from_rgb(128, 128, 128)
        );
        assert_eq!(mix(Color32::BLACK, Color32::WHITE, 7.0), Color32::WHITE);
    }
}
