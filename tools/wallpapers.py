#!/usr/bin/env python3
"""Paints the sunset sky the launcher ships with.

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


def aurora(name, seed=11):
    """Ná's Theme: soft lights of colour flowing through the dark, like Apple's own wallpapers."""
    y = np.linspace(0, 1, H, dtype=np.float32)[:, None].repeat(W, 1)
    x = np.linspace(0, 1, W, dtype=np.float32)[None, :].repeat(H, 0)
    # Bend the canvas with slow noise so the lights flow like silk instead of sitting as round blobs.
    wx = x + (noise(seed, octaves=3, base=2) - 0.5) * 0.55
    wy = y + (noise(seed + 1, octaves=3, base=2) - 0.5) * 0.55
    img = np.zeros((H, W, 3), np.float32) + np.array((7, 8, 20), np.float32)
    lights = [  # x, y, radius, colour
        (0.12, 0.30, 0.42, (96, 52, 210)),     # violet
        (0.88, 0.12, 0.40, (214, 50, 128)),    # magenta
        (0.80, 0.92, 0.45, (20, 150, 190)),    # teal
        (0.30, 1.00, 0.40, (34, 70, 215)),     # blue
        (0.55, 0.50, 0.22, (240, 120, 70)),    # a warm ember in the middle
    ]
    for lx, ly, r, rgb in lights:
        d2 = ((wx - lx) * 1.78) ** 2 + (wy - ly) ** 2
        img += np.array(rgb, np.float32) * np.exp(-d2 / (2 * (r * 0.62) ** 2))[..., None] * 0.8
    grain = np.random.default_rng(seed + 3).normal(0, 1.8, (H, W, 1)).astype(np.float32)
    return name, Image.fromarray(np.clip(img + grain, 0, 255).astype(np.uint8))


SKIES = [
    aurora("sky_aurora"),
    # Deep blue overhead, a red-orange sunset burning through the cloud deck from low on the left.
    sky("sky_sunset", (22, 30, 78), (232, 64, 26), (0.22, 0.97), (255, 78, 18),
        (34, 34, 78), (255, 88, 34), 0.68, 37),
]

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    for name, pic in SKIES:
        path = os.path.join(out, name + ".jpg")
        pic.save(path, quality=84, optimize=True, progressive=True)
        print(path, os.path.getsize(path) // 1024, "KB")
