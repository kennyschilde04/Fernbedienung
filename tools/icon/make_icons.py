"""Erzeugt die Launcher-Icons aus der Vorlage."""
from PIL import Image, ImageDraw

SRC = "tools/icon/quelle.jpg"
RES = "app/src/main/res"
BG = (212, 212, 210)

src = Image.open(SRC).convert("RGB")
w, h = src.size
px = src.load()

# Fernbedienung freistellen: alles, was nicht Hintergrund/Weiss ist.
# Der Koerper ist fast schwarz (30), die Tasten mittelgrau (124), der
# Hintergrund hell (212). Eine Helligkeitsschwelle trennt das sauber und
# faellt nicht auf die JPEG-Artefakte an der Kante des Hintergrunds herein.
THRESHOLD = 185
mask = Image.new("L", (w, h), 0)
mp = mask.load()
for y in range(h):
    for x in range(w):
        r, g, b = px[x, y]
        luma = 0.299 * r + 0.587 * g + 0.114 * b
        if luma >= THRESHOLD:
            mp[x, y] = 0
        else:
            # weiche Kante ueber die letzten Helligkeitsstufen
            mp[x, y] = min(255, int((THRESHOLD - luma) * 12))

remote = Image.new("RGBA", (w, h), (0, 0, 0, 0))
remote.paste(src, (0, 0), mask)
remote = remote.crop(remote.getbbox())
rw, rh = remote.size


def scaled_remote(target_height):
    ratio = target_height / rh
    return remote.resize((max(1, round(rw * ratio)), target_height), Image.LANCZOS)


def rounded_square(size, radius_ratio=0.22):
    img = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(
        [0, 0, size * 4 - 1, size * 4 - 1],
        radius=int(size * 4 * radius_ratio),
        fill=BG + (255,),
    )
    return img.resize((size, size), Image.LANCZOS)


def circle(size):
    img = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=BG + (255,))
    return img.resize((size, size), Image.LANCZOS)


def compose(base, size, height_ratio):
    fg = scaled_remote(max(1, round(size * height_ratio)))
    base = base.copy()
    base.alpha_composite(fg, ((size - fg.width) // 2, (size - fg.height) // 2))
    return base


LEGACY = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]
for folder, size in LEGACY:
    compose(rounded_square(size), size, 0.76).save(f"{RES}/mipmap-{folder}/ic_launcher.png")
    compose(circle(size), size, 0.66).save(f"{RES}/mipmap-{folder}/ic_launcher_round.png")

# Adaptives Icon (ab Android 8): Vordergrund transparent, 108dp Raster,
# Inhalt bleibt in der sicheren Zone von 66 dp.
ADAPTIVE = [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]
for folder, size in ADAPTIVE:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fg = scaled_remote(round(size * 0.56))
    canvas.alpha_composite(fg, ((size - fg.width) // 2, (size - fg.height) // 2))
    canvas.save(f"{RES}/drawable-{folder}/ic_launcher_foreground.png")

print("Icons erzeugt, Fernbedienung freigestellt:", (rw, rh))
