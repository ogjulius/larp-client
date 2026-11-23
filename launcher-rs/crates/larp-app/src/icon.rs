//! The app icon, drawn in code like the Java launcher's `Icons.appIcon` (no binary assets): a
//! violet rounded square with a soft highlight and a geometric "L".

use crate::art::{self, Image};
use std::path::Path;

/// Signed distance from `(x, y)` to a rounded rectangle (negative inside).
fn rounded_rect_sdf(x: f32, y: f32, rx: f32, ry: f32, w: f32, h: f32, r: f32) -> f32 {
    let (cx, cy) = (rx + w / 2.0, ry + h / 2.0);
    let qx = (x - cx).abs() - (w / 2.0 - r);
    let qy = (y - cy).abs() - (h / 2.0 - r);
    let outside = (qx.max(0.0).powi(2) + qy.max(0.0).powi(2)).sqrt();
    outside + qx.max(qy).min(0.0) - r
}

/// Source-over of a straight-alpha colour with `coverage` onto a straight-alpha pixel.
fn paint(dst: &mut [f32; 4], color: [f32; 3], alpha: f32) {
    let out_a = alpha + dst[3] * (1.0 - alpha);
    if out_a > 0.0 {
        for c in 0..3 {
            dst[c] = (color[c] * alpha + dst[c] * dst[3] * (1.0 - alpha)) / out_a;
        }
    }
    dst[3] = out_a;
}

/// Render the icon at `size`×`size` pixels (4×4 supersampled).
pub fn render(size: usize) -> Image {
    let s = size as f32;
    let radius = s * 0.15; // Java: arc width 0.3 × size
    let u = s / 16.0;
    let bars = [
        (4.5 * u, 3.5 * u, 2.6 * u, 9.0 * u),
        (4.5 * u, 9.9 * u, 7.0 * u, 2.6 * u),
    ];
    let (top, bottom) = (
        [0x9F as f32, 0x7A as f32, 0xFF as f32],
        [0x5B as f32, 0x3B as f32, 0xA8 as f32],
    );
    const N: usize = 4;
    let mut img = Image::new(size, size);
    for py in 0..size {
        for px in 0..size {
            let mut acc = [0.0f32; 4];
            for sy in 0..N {
                for sx in 0..N {
                    let x = px as f32 + (sx as f32 + 0.5) / N as f32;
                    let y = py as f32 + (sy as f32 + 0.5) / N as f32;
                    let mut sample = [0.0f32; 4];
                    if rounded_rect_sdf(x, y, 0.0, 0.0, s, s, radius) <= 0.0 {
                        let t = ((x + y) / (2.0 * s)).clamp(0.0, 1.0);
                        let c = [0, 1, 2].map(|i| top[i] + (bottom[i] - top[i]) * t);
                        paint(&mut sample, c, 1.0);
                        if rounded_rect_sdf(x, y, 0.0, 0.0, s, s * 0.5, radius) <= 0.0 {
                            paint(&mut sample, [255.0; 3], 40.0 / 255.0);
                        }
                        if bars
                            .iter()
                            .any(|&(bx, by, bw, bh)| rounded_rect_sdf(x, y, bx, by, bw, bh, u / 2.0) <= 0.0)
                        {
                            paint(&mut sample, [255.0; 3], 1.0);
                        }
                    }
                    // Average premultiplied samples.
                    for c in 0..3 {
                        acc[c] += sample[c] * sample[3];
                    }
                    acc[3] += sample[3];
                }
            }
            let a = acc[3] / (N * N) as f32;
            let px_out = if a > 0.0 {
                let c = |i: usize| (acc[i] / acc[3]).round().clamp(0.0, 255.0);
                [c(0), c(1), c(2), (a * 255.0).round()]
            } else {
                [0.0; 4]
            };
            #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)] // clamped to 0..=255
            img.set(px, py, px_out.map(|v| v as u8));
        }
    }
    img
}

/// `--export-icon FILE.png`: the 256 px icon used for the Linux desktop entry.
pub fn export_png(path: &Path) -> std::io::Result<()> {
    let png = art::encode_png(&render(256))?;
    std::fs::write(path, png)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn icon_shape() {
        let icon = render(64);
        assert_eq!(icon.pixel(0, 0)[3], 0, "rounded corner is transparent");
        assert_eq!(icon.pixel(32, 60)[3], 255, "body is opaque");
        // The vertical bar of the "L" is white; the area right of it is violet.
        assert_eq!(icon.pixel(23, 32), [255, 255, 255, 255]);
        let violet = icon.pixel(40, 32);
        assert!(violet[2] > violet[1] && violet[0] < 255, "{violet:?}");
    }

    #[test]
    fn exports_a_png() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("icon.png");
        export_png(&path).unwrap();
        let back = art::decode_png(&std::fs::read(&path).unwrap()).unwrap();
        assert_eq!((back.width, back.height), (256, 256));
    }
}
