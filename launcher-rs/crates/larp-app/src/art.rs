//! Artwork, all read from the user's own copy of the Minecraft client jar (nothing is redistributed):
//! the blurred panorama strip behind the Home page and the default skins for avatars. Plus the
//! static layers painted over the panorama, rendered once on the CPU (same maths as the Java
//! launcher's `HomeBackdrop`), so each frame only has to draw two textures.

use larp_core::GameFiles;
use larp_core::files::MC_VERSION;
use std::io::{Cursor, Read};
use std::path::{Path, PathBuf};

/// An sRGB image with straight (unmultiplied) alpha, 4 bytes per pixel.
#[derive(Clone, PartialEq, Eq)]
pub struct Image {
    pub width: usize,
    pub height: usize,
    pub rgba: Vec<u8>,
}

impl std::fmt::Debug for Image {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "Image({}x{})", self.width, self.height)
    }
}

impl Image {
    pub fn new(width: usize, height: usize) -> Self {
        Self {
            width,
            height,
            rgba: vec![0; width * height * 4],
        }
    }

    pub fn pixel(&self, x: usize, y: usize) -> [u8; 4] {
        let i = (y * self.width + x) * 4;
        [self.rgba[i], self.rgba[i + 1], self.rgba[i + 2], self.rgba[i + 3]]
    }

    pub fn set(&mut self, x: usize, y: usize, px: [u8; 4]) {
        let i = (y * self.width + x) * 4;
        self.rgba[i..i + 4].copy_from_slice(&px);
    }
}

/// Decode a PNG into 8-bit RGBA. `None` for anything unreadable.
pub fn decode_png(bytes: &[u8]) -> Option<Image> {
    let mut decoder = png::Decoder::new(Cursor::new(bytes));
    decoder.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut reader = decoder.read_info().ok()?;
    let mut buf = vec![0; reader.output_buffer_size()?];
    let info = reader.next_frame(&mut buf).ok()?;
    let (width, height) = (info.width as usize, info.height as usize);
    let src = &buf[..info.buffer_size()];
    let rgba = match info.color_type {
        png::ColorType::Rgba => src.to_vec(),
        png::ColorType::Rgb => src
            .as_chunks::<3>()
            .0
            .iter()
            .flat_map(|p| [p[0], p[1], p[2], 255])
            .collect(),
        png::ColorType::GrayscaleAlpha => src
            .as_chunks::<2>()
            .0
            .iter()
            .flat_map(|p| [p[0], p[0], p[0], p[1]])
            .collect(),
        png::ColorType::Grayscale => src.iter().flat_map(|&g| [g, g, g, 255]).collect(),
        png::ColorType::Indexed => return None, // EXPAND turns palettes into RGB(A)
    };
    (rgba.len() == width * height * 4).then_some(Image { width, height, rgba })
}

/// Encode as an RGBA PNG.
pub fn encode_png(image: &Image) -> std::io::Result<Vec<u8>> {
    let mut out = Vec::new();
    {
        let width = u32::try_from(image.width).map_err(std::io::Error::other)?;
        let height = u32::try_from(image.height).map_err(std::io::Error::other)?;
        let mut encoder = png::Encoder::new(&mut out, width, height);
        encoder.set_color(png::ColorType::Rgba);
        encoder.set_depth(png::BitDepth::Eight);
        let mut writer = encoder.write_header().map_err(std::io::Error::other)?;
        writer.write_image_data(&image.rgba).map_err(std::io::Error::other)?;
    }
    Ok(out)
}

pub fn client_jar(files: &GameFiles) -> PathBuf {
    files.version_dir(MC_VERSION).join(format!("{MC_VERSION}.jar"))
}

/// Read several images from a jar in one pass. Missing or unreadable entries are `None`.
pub fn read_from_jar(jar: &Path, entries: &[&str]) -> Vec<Option<Image>> {
    let Ok(file) = std::fs::File::open(jar) else {
        return vec![None; entries.len()];
    };
    let Ok(mut zip) = zip::ZipArchive::new(file) else {
        return vec![None; entries.len()];
    };
    entries
        .iter()
        .map(|name| {
            let mut entry = zip.by_name(name).ok()?;
            let mut bytes = Vec::new();
            entry.read_to_end(&mut bytes).ok()?;
            decode_png(&bytes)
        })
        .collect()
}

/// The art the launcher shows, loaded together from the client jar.
#[derive(Debug, Clone, Default)]
pub struct GameArt {
    pub panorama: Option<Image>,
    pub steve: Option<Image>,
    pub alex: Option<Image>,
}

pub fn load(files: &GameFiles) -> GameArt {
    let mut names: Vec<String> = (0..4)
        .map(|i| format!("assets/minecraft/textures/gui/title/background/panorama_{i}.png"))
        .collect();
    names.push("assets/minecraft/textures/entity/steve.png".into());
    names.push("assets/minecraft/textures/entity/alex.png".into());
    let refs: Vec<&str> = names.iter().map(String::as_str).collect();
    let mut images = read_from_jar(&client_jar(files), &refs);
    let alex = images.pop().flatten();
    let steve = images.pop().flatten();
    let faces: Option<Vec<Image>> = images.into_iter().collect();
    GameArt {
        panorama: faces.and_then(|f| panorama_strip(&f)),
        steve,
        alex,
    }
}

/// Front, right, back and left panorama faces side by side, with the first face repeated at the end
/// so the strip loops seamlessly, halved and softly blurred (as in the Java launcher).
pub fn panorama_strip(faces: &[Image]) -> Option<Image> {
    let size = faces.first()?.height;
    if faces.len() != 4 || faces.iter().any(|f| f.width != size || f.height != size) || size < 2 {
        return None;
    }
    let mut strip = Image::new(size * 5, size);
    for i in 0..5 {
        let face = &faces[i % 4];
        for y in 0..size {
            let src = y * size * 4;
            let dst = (y * strip.width + i * size) * 4;
            strip.rgba[dst..dst + size * 4].copy_from_slice(&face.rgba[src..src + size * 4]);
        }
    }
    Some(box_blur(&halve(&strip), 3))
}

/// Halve both dimensions. Java2D's bilinear 0.5× scale samples exactly between two source pixels,
/// so each output pixel is the average of a 2×2 block. Alpha is made opaque (TYPE_INT_RGB).
pub fn halve(src: &Image) -> Image {
    let (w, h) = ((src.width / 2).max(1), (src.height / 2).max(1));
    let mut out = Image::new(w, h);
    for y in 0..h {
        for x in 0..w {
            let mut sum = [0u32; 3];
            for (dx, dy) in [(0, 0), (1, 0), (0, 1), (1, 1)] {
                let p = src.pixel((2 * x + dx).min(src.width - 1), (2 * y + dy).min(src.height - 1));
                for c in 0..3 {
                    sum[c] += u32::from(p[c]);
                }
            }
            let avg = |c: usize| u8::try_from((sum[c] + 2) / 4).unwrap_or(u8::MAX);
            out.set(x, y, [avg(0), avg(1), avg(2), 255]);
        }
    }
    out
}

/// (2r+1)² box blur, with the Java launcher's edges: left and right repeat the edge column, top
/// and bottom blend towards black (the Java code padded those with empty rows).
pub fn box_blur(src: &Image, radius: usize) -> Image {
    let (w, h) = (src.width, src.height);
    let size = 2 * radius + 1;
    let at = |x: isize, y: isize| -> [u32; 3] {
        if y < 0 || y >= h as isize {
            return [0; 3];
        }
        let p = src.pixel(x.clamp(0, w as isize - 1) as usize, y as usize);
        [u32::from(p[0]), u32::from(p[1]), u32::from(p[2])]
    };
    let r = radius as isize;
    let mut out = Image::new(w, h);
    let n = (size * size) as f32;
    for y in 0..h as isize {
        for x in 0..w as isize {
            let mut sum = [0u32; 3];
            for dy in -r..=r {
                for dx in -r..=r {
                    let p = at(x + dx, y + dy);
                    for c in 0..3 {
                        sum[c] += p[c];
                    }
                }
            }
            #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)] // averages of bytes
            let c = |i: usize| (sum[i] as f32 / n) as u8;
            out.set(x as usize, y as usize, [c(0), c(1), c(2), 255]);
        }
    }
    out
}

/// `java.util.Random`, so the film grain is the exact pattern the Java launcher drew.
#[derive(Debug)]
pub struct JavaRandom(u64);

impl JavaRandom {
    const MULTIPLIER: u64 = 0x5DEE_CE66D;
    const MASK: u64 = (1 << 48) - 1;

    pub const fn new(seed: i64) -> Self {
        Self((seed as u64 ^ Self::MULTIPLIER) & Self::MASK)
    }

    fn next(&mut self, bits: u32) -> i32 {
        self.0 = (self.0.wrapping_mul(Self::MULTIPLIER).wrapping_add(0xB)) & Self::MASK;
        #[allow(clippy::cast_possible_truncation, clippy::cast_possible_wrap)] // Java's (int) cast
        let v = (self.0 >> (48 - bits)) as i32;
        v
    }

    /// `nextInt(bound)` for `bound > 0`.
    pub fn next_int(&mut self, bound: i32) -> i32 {
        if bound & -bound == bound {
            #[allow(clippy::cast_possible_truncation)] // Java's (int) cast
            return ((i64::from(bound) * i64::from(self.next(31))) >> 31) as i32;
        }
        loop {
            let bits = self.next(31);
            let val = bits % bound;
            if bits.wrapping_sub(val).wrapping_add(bound - 1) >= 0 {
                return val;
            }
        }
    }
}

/// The monochrome film grain tile (`GameArt.grain(128, 18)` in the Java launcher).
pub fn grain(size: usize, alpha: i32) -> Image {
    let mut img = Image::new(size, size);
    let mut random = JavaRandom::new(7);
    for y in 0..size {
        for x in 0..size {
            let v = u8::try_from(random.next_int(256)).unwrap_or(0);
            let a = u8::try_from(random.next_int(alpha + 1)).unwrap_or(0);
            img.set(x, y, [v, v, v, a]);
        }
    }
    img
}

/// The 8×8 face of a skin with its hat layer on top (64×32 and 64×64 skins).
pub fn face(skin: &Image) -> Option<Image> {
    if skin.width < 64 || skin.height < 16 {
        return None;
    }
    let mut out = Image::new(8, 8);
    for y in 0..8 {
        for x in 0..8 {
            let base = skin.pixel(8 + x, 8 + y);
            let hat = skin.pixel(40 + x, 8 + y);
            out.set(x, y, over(hat, [base[0], base[1], base[2], 255]));
        }
    }
    Some(out)
}

/// Source-over for straight-alpha pixels.
fn over(src: [u8; 4], dst: [u8; 4]) -> [u8; 4] {
    let sa = f32::from(src[3]) / 255.0;
    let da = f32::from(dst[3]) / 255.0;
    let oa = sa + da * (1.0 - sa);
    if oa <= 0.0 {
        return [0; 4];
    }
    let mut out = [0u8; 4];
    for c in 0..3 {
        let v = (f32::from(src[c]) * sa + f32::from(dst[c]) * da * (1.0 - sa)) / oa;
        out[c] = to_byte(v);
    }
    out[3] = to_byte(oa * 255.0);
    out
}

#[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)] // clamped to 0..=255 first
fn to_byte(v: f32) -> u8 {
    v.round().clamp(0.0, 255.0) as u8
}

/// Whether Minecraft 1.8.9 gives this profile the slim (Alex) default skin:
/// `(UUID.hashCode() & 1) == 1`. `uuid` is 32 hex digits.
pub fn is_slim(uuid: &str) -> bool {
    if uuid.len() != 32 {
        return false;
    }
    let (Ok(msb), Ok(lsb)) = (
        u64::from_str_radix(&uuid[..16], 16),
        u64::from_str_radix(&uuid[16..], 16),
    ) else {
        return false;
    };
    let hilo = msb ^ lsb;
    #[allow(clippy::cast_possible_truncation)] // Java's (int) casts
    let hash = ((hilo >> 32) as u32) ^ (hilo as u32);
    hash & 1 == 1
}

/// Linear interpolation of straight-alpha colours (Java's `GradientPaint`, sRGB, no cycling).
fn lerp(a: [f32; 4], b: [f32; 4], t: f32) -> [f32; 4] {
    let t = t.clamp(0.0, 1.0);
    [0, 1, 2, 3].map(|i| a[i] + (b[i] - a[i]) * t)
}

/// Premultiplied source-over into an f32 RGBA accumulator.
fn blend(dst: &mut [f32; 4], src: [f32; 4]) {
    let a = src[3] / 255.0;
    for c in 0..3 {
        dst[c] = src[c] * a + dst[c] * (1.0 - a);
    }
    dst[3] = src[3] + dst[3] * (1.0 - a);
}

/// The Home backdrop's static layers for a `w`×`h` page. With a panorama (`panorama = true`) the
/// result is a translucent overlay drawn over the moving strip: dark tint, legibility gradients,
/// accent glow and grain. Without one it is the opaque fallback background with the same layers.
/// Returned premultiplied, ready for `egui::ColorImage::from_rgba_premultiplied`.
pub fn backdrop_layers(w: usize, h: usize, panorama: bool) -> Image {
    let grain = grain(128, 18);
    let (wf, hf) = (w as f32, h as f32);
    let glow_center = (wf * 0.15, hf * 0.25);
    let glow_radius = wf.max(hf) * 0.55;
    let dark = |a: f32| [11.0, 11.0, 14.0, a];
    let mut out = Image::new(w, h);
    for y in 0..h {
        for x in 0..w {
            let (px, py) = (x as f32 + 0.5, y as f32 + 0.5);
            // Premultiplied accumulator: rgb already multiplied by alpha.
            let mut acc = [0.0f32; 4];
            if panorama {
                blend(&mut acc, [10.0, 9.0, 16.0, 110.0]);
            } else {
                let t = (px * wf + py * hf) / (wf * wf + hf * hf);
                let c = lerp(
                    [0x1A as f32, 0x15 as f32, 0x30 as f32, 255.0],
                    [11.0, 11.0, 14.0, 255.0],
                    t,
                );
                blend(&mut acc, c);
            }
            blend(&mut acc, lerp(dark(235.0), dark(40.0), px / (wf * 0.62)));
            let bottom_start = (hf * 0.55).floor();
            if py >= bottom_start {
                blend(&mut acc, lerp(dark(0.0), dark(230.0), (py - hf * 0.55) / (hf * 0.45)));
            }
            let d = ((px - glow_center.0).powi(2) + (py - glow_center.1).powi(2)).sqrt() / glow_radius;
            blend(&mut acc, lerp([139.0, 92.0, 246.0, 46.0], [139.0, 92.0, 246.0, 0.0], d));
            let g = grain.pixel(x % grain.width, y % grain.height);
            blend(
                &mut acc,
                [f32::from(g[0]), f32::from(g[1]), f32::from(g[2]), f32::from(g[3])],
            );
            out.set(
                x,
                y,
                [to_byte(acc[0]), to_byte(acc[1]), to_byte(acc[2]), to_byte(acc[3])],
            );
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn solid(w: usize, h: usize, px: [u8; 4]) -> Image {
        Image {
            width: w,
            height: h,
            rgba: px.repeat(w * h),
        }
    }

    #[test]
    fn java_random_matches_the_jdk() {
        // new java.util.Random(7): nextInt(256) ×3, then nextInt(19) ×3 (printed by OpenJDK 17).
        let mut r = JavaRandom::new(7);
        let ints: Vec<i32> = (0..3).map(|_| r.next_int(256)).collect();
        let small: Vec<i32> = (0..3).map(|_| r.next_int(19)).collect();
        assert_eq!(ints, [187, 163, 191]);
        assert_eq!(small, [2, 2, 9]);
    }

    #[test]
    fn png_round_trip() {
        let mut img = Image::new(3, 2);
        img.set(1, 1, [10, 20, 30, 40]);
        let back = decode_png(&encode_png(&img).unwrap()).unwrap();
        assert_eq!(back, img);
        assert!(decode_png(b"not a png").is_none());
    }

    #[test]
    fn face_draws_the_hat_over_the_head() {
        let mut skin = solid(64, 64, [0, 0, 0, 0]);
        for y in 8..16 {
            for x in 8..16 {
                skin.set(x, y, [200, 100, 50, 255]);
            }
        }
        skin.set(40, 8, [0, 0, 255, 255]); // opaque hat pixel at the face's top-left
        let face = face(&skin).unwrap();
        assert_eq!(face.pixel(0, 0), [0, 0, 255, 255]);
        assert_eq!(face.pixel(7, 7), [200, 100, 50, 255]);
        assert!(super::face(&solid(8, 8, [0; 4])).is_none());
    }

    #[test]
    fn default_skin_follows_minecraft() {
        // (UUID.hashCode() & 1) == 1, as printed by OpenJDK 17 for these profiles.
        assert!(is_slim(&larp_core::Account::offline("Steve").uuid));
        assert!(!is_slim(&larp_core::Account::offline("Alex").uuid));
        assert!(!is_slim(&larp_core::Account::offline("Dev").uuid));
        assert!(!is_slim("069a79f444e94726a5befca90e38aaf5"));
        assert!(!is_slim("not-a-uuid"));
    }

    #[test]
    fn panorama_strip_loops_and_is_halved() {
        let faces: Vec<Image> = (0..4u8).map(|i| solid(16, 16, [i * 60, 0, 0, 255])).collect();
        let strip = panorama_strip(&faces).unwrap();
        assert_eq!((strip.width, strip.height), (40, 8));
        // The middle row of face 1 is untouched by the blur's edges.
        assert_eq!(strip.pixel(12, 4), [60, 0, 0, 255]);
        // The repeated first face at the end matches the start.
        assert_eq!(strip.pixel(38, 4)[0], strip.pixel(1, 4)[0]);
        assert!(panorama_strip(&faces[..3]).is_none());
    }

    #[test]
    fn backdrop_without_panorama_is_opaque() {
        let layers = backdrop_layers(40, 30, false);
        assert!(layers.rgba.as_chunks::<4>().0.iter().all(|p| p[3] == 255));
        let overlay = backdrop_layers(40, 30, true);
        assert!(overlay.rgba.as_chunks::<4>().0.iter().all(|p| p[3] < 255));
        // Left side is darker than right (legibility gradient).
        assert!(overlay.pixel(0, 5)[3] > overlay.pixel(39, 5)[3]);
    }
}
