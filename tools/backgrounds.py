#!/usr/bin/env python3
"""Prepares your own background pictures for the launcher: crops each to 16:9, scales it to 1920x1080
and saves it as a JPEG in launcher/app/src/main/assets/backgrounds/, where Settings > Backgrounds finds it.
That folder is git-ignored, so personal or licensed pictures never reach GitHub.

    tools/venv/Scripts/python.exe -I tools/backgrounds.py %USERPROFILE%\\Desktop\\backgrounds
"""
import os
import sys

from PIL import Image, ImageOps

W, H = 1920, 1080
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "launcher", "app", "src", "main",
                   "assets", "backgrounds")


def main():
    src = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    for name in sorted(os.listdir(src)):
        stem, ext = os.path.splitext(name)
        if ext.lower() not in (".jpg", ".jpeg", ".png", ".webp"):
            continue
        img = ImageOps.exif_transpose(Image.open(os.path.join(src, name))).convert("RGB")
        img = ImageOps.fit(img, (W, H), Image.LANCZOS, centering=(0.5, 0.5))
        path = os.path.join(OUT, stem.lower().replace(" ", "-") + ".jpg")
        img.save(path, quality=86, optimize=True, progressive=True)
        print(os.path.basename(path), os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    main()
