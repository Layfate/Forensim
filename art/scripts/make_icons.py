"""Renders Forensim's glossy maroon icon set from the launcher foreground.

Outputs (paths relative to the project root):
  app/src/main/res/mipmap-*/ic_launcher.webp, ic_launcher_round.webp   legacy icons
  docs/play-store/icon-512.png                                        Play Store app icon
  docs/play-store/feature-graphic.png                                 Play Store feature graphic
  docs/images/forensim-logo.png                                       README logo (rounded)

The background layers match app/src/main/res/drawable/ic_launcher_background.xml (108-unit
viewport), so the adaptive icon on phones and these images look the same.

Run: python art/scripts/make_icons.py   (needs Pillow and numpy)
"""
import pathlib

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
FOREGROUND = RES / "mipmap-xxxhdpi/ic_launcher_foreground.png"
# Legacy launchers and the Play icon show the middle 78% of the 108dp adaptive canvas.
VISIBLE = 0.782


def hexa(c: str):
    c = c.lstrip("#")
    a, r, g, b = (int(c[i:i + 2], 16) / 255 for i in (0, 2, 4, 6))
    return np.array([r, g, b]), a


def ramp(t, stops):
    """Colour and alpha along gradient stops [(offset, '#AARRGGBB'), ...] for t in [0, 1]."""
    t = np.clip(t, 0, 1)
    offs = np.array([o for o, _ in stops])
    cols = np.array([hexa(c)[0] for _, c in stops])
    alph = np.array([hexa(c)[1] for _, c in stops])
    rgb = np.stack([np.interp(t, offs, cols[:, k]) for k in range(3)], -1)
    return rgb, np.interp(t, offs, alph)


def over(base, rgb, a):
    return base * (1 - a[..., None]) + rgb * a[..., None]


def background(w: int, h: int, units_w: float = 108, units_h: float = 108) -> np.ndarray:
    """The gradient layers, rendered for a w x h image covering units_w x units_h viewport units."""
    x = (np.arange(w) + 0.5) * units_w / w
    y = (np.arange(h) + 0.5) * units_h / h
    X, Y = np.meshgrid(x, y)
    # Base: linear, top-left to bottom-right (scaled so the diagonal spans the whole image).
    t = (X / units_w + Y / units_h) / 2
    img, _ = ramp(t, [(0, "#FFB8395A"), (0.45, "#FF85233F"), (0.8, "#FF571229"), (1, "#FF3A0A19")])
    # Shine.
    r = np.hypot(X - 30 * units_w / 108, Y - 22 * units_h / 108) / (64 * max(units_w, units_h) / 108)
    rgb, a = ramp(r, [(0, "#4DFFE0E8"), (0.55, "#12FFE0E8"), (1, "#00FFE0E8")])
    img = over(img, rgb, a)
    # Gloss over the upper half.
    rgb, a = ramp(Y / (58 * units_h / 108), [(0, "#26FFFFFF"), (1, "#00FFFFFF")])
    img = over(img, rgb, a)
    # Depth in the bottom-right corner.
    r = np.hypot(X - 96 * units_w / 108, Y - 100 * units_h / 108) / (58 * max(units_w, units_h) / 108)
    rgb, a = ramp(r, [(0, "#40000000"), (1, "#00000000")])
    img = over(img, rgb, a)
    return img


def to_image(arr: np.ndarray) -> Image.Image:
    return Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB").convert("RGBA")


def icon(size: int) -> Image.Image:
    """Square icon: background plus the foreground, cropped to the visible part of the canvas."""
    canvas = round(size / VISIBLE)
    img = to_image(background(canvas, canvas))
    fg = Image.open(FOREGROUND).convert("RGBA").resize((canvas, canvas), Image.LANCZOS)
    img.alpha_composite(fg)
    off = (canvas - size) // 2
    return img.crop((off, off, off + size, off + size))


def masked(img: Image.Image, radius: int | None) -> Image.Image:
    """Circle (radius None) or rounded-square mask, drawn 4x and scaled down for smooth edges."""
    s = img.size[0]
    mask = Image.new("L", (s * 4, s * 4), 0)
    d = ImageDraw.Draw(mask)
    if radius is None:
        d.ellipse((0, 0, s * 4 - 1, s * 4 - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, s * 4 - 1, s * 4 - 1), radius * 4, fill=255)
    out = img.copy()
    out.putalpha(mask.resize((s, s), Image.LANCZOS))
    return out


def feature_graphic(path: pathlib.Path):
    w, h = 1024, 500
    img = to_image(background(w, h, 108 * w / h, 108))
    # The detective badge on the left, with a soft shadow.
    badge = masked(icon(340), 76)
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 110), (88, 92), badge)
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18)))
    img.alpha_composite(badge, (80, 80))
    fonts = RES / "font"
    title = ImageFont.truetype(str(fonts / "poppins_bold.ttf"), 104)
    sub = ImageFont.truetype(str(fonts / "poppins_medium.ttf"), 38)
    d = ImageDraw.Draw(img)
    d.text((468, 150), "Forensim", font=title, fill=(255, 245, 240, 255))
    d.text((474, 288), "Forensic Science Lab", font=sub, fill=(250, 214, 222, 255))
    d.text((474, 340), "3D crime scenes · AR · AI tutor", font=ImageFont.truetype(str(fonts / "poppins_regular.ttf"), 28),
           fill=(240, 196, 206, 255))
    img.convert("RGB").save(path, optimize=True)


def main():
    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        base = icon(size)
        base.save(RES / f"mipmap-{density}/ic_launcher.webp", lossless=True)
        masked(base, None).save(RES / f"mipmap-{density}/ic_launcher_round.webp", lossless=True)
    store = ROOT / "docs/play-store"
    # Play Store icon: full square, no transparency (Play applies its own rounded mask).
    icon(512).convert("RGB").save(store / "icon-512.png", optimize=True)
    masked(icon(512), 110).save(ROOT / "docs/images/forensim-logo.png", optimize=True)
    feature_graphic(store / "feature-graphic.png")
    print("icons written")


if __name__ == "__main__":
    main()
