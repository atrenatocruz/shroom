#!/usr/bin/env python3
"""Paints the four time-of-day skies the launcher ships with (dawn, day, dusk, night).

Procedural, so there is no photo licence to worry about. Re-run after tweaking a palette:
    tools/venv/Scripts/python.exe -I tools/wallpapers.py launcher/app/src/main/res/drawable-nodpi
"""
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

W, H = 1920, 1080


def noise(seed, octaves=8, base=3):
    """Fractal value noise in [0, 1], wider than tall like a cloud deck."""
    rng = np.random.default_rng(seed)
    out = np.zeros((H, W), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        gw, gh = base * 2 ** o + 1, max(2, int((base * 2 ** o) * 0.55) + 1)
        grid = Image.fromarray((rng.random((gh, gw)) * 255).astype(np.uint8))
        layer = np.asarray(grid.resize((W, H), Image.BICUBIC), np.float32) / 255
        out += layer * amp
        total += amp
        amp *= 0.62
    return out / total


def lerp(a, b, t):
    return a + (b - a) * t[..., None]


def sky(name, top, horizon, sun_xy, sun_rgb, cloud_dark, cloud_lit, cover, seed, stars=False):
    y = np.linspace(0, 1, H, dtype=np.float32)[:, None].repeat(W, 1)
    x = np.linspace(0, 1, W, dtype=np.float32)[None, :].repeat(H, 0)
    img = lerp(np.array(top, np.float32), np.array(horizon, np.float32), y ** 1.6)

    # Sun glow: a broad warm bloom plus a tighter core.
    d = np.sqrt(((x - sun_xy[0]) * 1.78) ** 2 + (y - sun_xy[1]) ** 2)
    glow = np.exp(-d * 3.4) * 0.7 + np.exp(-d * 14) * 0.35
    img = img + np.array(sun_rgb, np.float32) * glow[..., None]

    if stars:
        rng = np.random.default_rng(seed + 7)
        s = (rng.random((H, W)) > 0.9985).astype(np.float32) * rng.random((H, W)) * (1 - y) ** 1.5 * 1.6
        img += (s * 255)[..., None]

    # Clouds: a thresholded noise deck, denser toward the middle band, lit from the sun's side.
    n = noise(seed)
    band = np.exp(-((y - 0.48) / 0.32) ** 2)
    density = np.clip((n * (0.55 + 0.6 * band) - (1 - cover)) * 5.5, 0, 1) ** 1.3
    shade = noise(seed + 1, octaves=4, base=6)
    lit = np.clip(glow * 1.6 + (shade - 0.5) * 0.9 + (1 - y) * 0.15, 0, 1)
    cloud = lerp(np.array(cloud_dark, np.float32), np.array(cloud_lit, np.float32), lit)
    # Rim light: thin edges of the deck catch the sun.
    edge = np.clip(density * (1 - density) * 4, 0, 1) * glow
    cloud += np.array(sun_rgb, np.float32) * (edge * 0.9)[..., None]
    img = lerp(img, cloud, density * 0.92)

    img = np.clip(img, 0, 255).astype(np.uint8)
    pic = Image.fromarray(img).filter(ImageFilter.GaussianBlur(0.6))
    # A pinch of grain hides banding on 8-bit panels.
    grain = np.random.default_rng(seed + 3).normal(0, 2.2, (H, W, 1)).astype(np.float32)
    pic = Image.fromarray(np.clip(np.asarray(pic, np.float32) + grain, 0, 255).astype(np.uint8))
    return name, pic


SKIES = [
    sky("sky_dawn", (64, 88, 140), (246, 190, 160), (0.72, 0.78), (255, 170, 120),
        (92, 92, 130), (255, 214, 196), 0.55, 11),
    sky("sky_day", (38, 104, 196), (168, 206, 238), (0.82, 0.04), (255, 250, 235),
        (150, 168, 196), (250, 252, 255), 0.5, 23),
    sky("sky_dusk", (30, 34, 78), (226, 92, 40), (0.3, 0.86), (255, 96, 30),
        (38, 40, 82), (255, 120, 60), 0.62, 37),
    sky("sky_night", (6, 9, 22), (26, 34, 70), (0.78, 0.2), (120, 140, 200),
        (16, 20, 40), (70, 86, 130), 0.4, 53, stars=True),
]

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    for name, pic in SKIES:
        path = os.path.join(out, name + ".jpg")
        pic.save(path, quality=84, optimize=True, progressive=True)
        print(path, os.path.getsize(path) // 1024, "KB")
