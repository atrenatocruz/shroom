#!/usr/bin/env python3
"""Custom app icons and names for the launcher. Put pictures in a folder named after each app's package
(com.netflix.ninja.jpg, ...) plus an optional labels.txt with lines like `ar.tvplayer.tv=TV`, then:

    tools/venv/Scripts/python.exe -I tools/icons.py %USERPROFILE%\\Desktop\\icons

They land in launcher/app/src/main/assets/icons/ (git-ignored: brand logos stay off GitHub).
Find a package name with: tools/venv/Scripts/python.exe -I tools/tv.py "pm list packages -3"
"""
import os
import shutil
import sys

from PIL import Image, ImageOps

SIZE = 360
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "launcher", "app", "src", "main",
                   "assets", "icons")


def main():
    src = sys.argv[1]
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    for name in sorted(os.listdir(src)):
        stem, ext = os.path.splitext(name)
        if name == "labels.txt":
            shutil.copy(os.path.join(src, name), os.path.join(OUT, name))
            print("labels.txt")
        elif ext.lower() in (".jpg", ".jpeg", ".png", ".webp"):
            img = ImageOps.exif_transpose(Image.open(os.path.join(src, name))).convert("RGB")
            img.thumbnail((SIZE, SIZE), Image.LANCZOS)
            img.save(os.path.join(OUT, stem + ".jpg"), quality=90, optimize=True)
            print(stem)


if __name__ == "__main__":
    main()
