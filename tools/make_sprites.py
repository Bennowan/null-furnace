#!/usr/bin/env python3
"""Erzeugt die Platzhalter-Sprites des Mods (reproduzierbar). Benoetigt: pip install pillow"""
from PIL import Image, ImageDraw
import math, os, sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "sprites")
os.makedirs(OUT, exist_ok=True)

PALETTES = {
    # Serpulo: kuehles Stahlgrau
    "": dict(OUTLINE=(38, 41, 52, 255), DARK=(58, 63, 76, 255), MID=(92, 99, 114, 255), LIGHT=(128, 136, 152, 255),
             HI=(170, 178, 194, 255), BLUE=(74, 168, 255, 255), PURPLE=(154, 92, 255, 255), WHITE=(240, 246, 255, 255)),
    # Erekir: dunkles Rotbraun mit Cyan/Magenta-Akzent
    "-erekir": dict(OUTLINE=(40, 26, 30, 255), DARK=(66, 42, 46, 255), MID=(108, 70, 68, 255), LIGHT=(150, 102, 94, 255),
                    HI=(196, 146, 124, 255), BLUE=(90, 230, 255, 255), PURPLE=(255, 90, 200, 255), WHITE=(255, 244, 236, 255)),
}
OUTLINE = DARK = MID = LIGHT = HI = BLUE = PURPLE = WHITE = None
SUF = ""

def set_palette(suffix):
    global OUTLINE, DARK, MID, LIGHT, HI, BLUE, PURPLE, WHITE, SUF
    SUF = suffix
    for k, v in PALETTES[suffix].items():
        globals()[k] = v

def img(n):
    return Image.new("RGBA", (n, n), (0, 0, 0, 0))

def panel(d, box, fill=None, edge=None, hi=None, lo=None):
    fill = MID if fill is None else fill
    edge = OUTLINE if edge is None else edge
    hi = LIGHT if hi is None else hi
    lo = DARK if lo is None else lo
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=edge)
    d.rectangle((x0 + 1, y0 + 1, x1 - 1, y1 - 1), fill=fill)
    d.line((x0 + 1, y0 + 1, x1 - 1, y0 + 1), fill=hi)
    d.line((x0 + 1, y0 + 1, x0 + 1, y1 - 1), fill=hi)
    d.line((x0 + 1, y1 - 1, x1 - 1, y1 - 1), fill=lo)
    d.line((x1 - 1, y0 + 1, x1 - 1, y1 - 1), fill=lo)

def rivets(d, box, step=8, inset=4):
    x0, y0, x1, y1 = box
    for x in range(x0 + inset, x1 - inset + 1, step):
        for y in (y0 + inset, y1 - inset):
            d.point((x, y), fill=HI); d.point((x + 1, y + 1), fill=OUTLINE)
    for y in range(y0 + inset, y1 - inset + 1, step):
        for x in (x0 + inset, x1 - inset):
            d.point((x, y), fill=HI); d.point((x + 1, y + 1), fill=OUTLINE)

def core(d, cx, cy, r, c1=None, c2=None):
    c1 = BLUE if c1 is None else c1
    c2 = WHITE if c2 is None else c2
    d.ellipse((cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1), fill=OUTLINE)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=DARK)
    d.ellipse((cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2), fill=c1)
    d.ellipse((cx - r // 2, cy - r // 2, cx + r // 2, cy + r // 2), fill=c2)

def save(im, name):
    import re
    m = re.match(r"^(flux-[a-z]+|skillable-turret)(.*)$", name)
    im.save(os.path.join(OUT, m.group(1) + SUF + m.group(2)))

# --- Flux Node (32x32): kleiner Mast mit Kristall
def node():
    im = img(32); d = ImageDraw.Draw(im)
    panel(d, (4, 4, 27, 27))
    rivets(d, (4, 4, 27, 27), step=6, inset=3)
    core(d, 15, 15, 6, PURPLE, WHITE)
    save(im, "flux-node.png")

# --- Konverter (2x2 = 64, 3x3 = 96)
def converter(name, n, accent, coils):
    im = img(n); d = ImageDraw.Draw(im)
    panel(d, (2, 2, n - 3, n - 3))
    rivets(d, (2, 2, n - 3, n - 3), step=8, inset=4)
    # Spulen links/rechts
    m = n // 2
    for i in range(coils):
        off = (i - (coils - 1) / 2) * 11
        for sx in (8, n - 18):
            panel(d, (sx, int(m + off - 4), sx + 9, int(m + off + 4)), fill=DARK, hi=MID, lo=OUTLINE)
            d.line((sx + 2, int(m + off), sx + 7, int(m + off)), fill=accent)
    core(d, m, m, n // 5 + 2, accent, WHITE)
    # Ecken-Akzente
    for cx, cy in ((9, 9), (n - 10, 9), (9, n - 10), (n - 10, n - 10)):
        d.rectangle((cx - 2, cy - 2, cx + 2, cy + 2), fill=OUTLINE)
        d.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=accent)
    save(im, name)

# --- Turm: Basis (96), Kopf (96), Klinge (96, links)
def turret_base():
    im = img(96); d = ImageDraw.Draw(im)
    panel(d, (2, 2, 93, 93), fill=DARK, hi=MID, lo=OUTLINE)
    rivets(d, (2, 2, 93, 93), step=10, inset=5)
    d.ellipse((14, 14, 81, 81), fill=OUTLINE)
    d.ellipse((16, 16, 79, 79), fill=MID)
    d.ellipse((22, 22, 73, 73), fill=DARK)
    for a in range(0, 360, 45):
        x = 48 + 38 * math.cos(math.radians(a)); y = 48 + 38 * math.sin(math.radians(a))
        d.rectangle((x - 2, y - 2, x + 2, y + 2), fill=BLUE if a % 90 == 0 else LIGHT)
    save(im, "skillable-turret-base.png")

def turret_head():
    # Blickrichtung nach OBEN (Mindustry dreht um -90 Grad)
    im = img(96); d = ImageDraw.Draw(im)
    # Rumpf
    d.polygon([(30, 70), (24, 44), (36, 26), (60, 26), (72, 44), (66, 70)], fill=OUTLINE)
    d.polygon([(32, 68), (27, 45), (38, 28), (58, 28), (69, 45), (64, 68)], fill=MID)
    d.polygon([(38, 62), (34, 46), (42, 33), (54, 33), (62, 46), (58, 62)], fill=LIGHT)
    d.line((48, 30, 48, 66), fill=DARK)
    # Lauf (zentrale Schiene)
    panel(d, (42, 6, 53, 40), fill=DARK, hi=MID, lo=OUTLINE)
    d.rectangle((46, 4, 49, 12), fill=OUTLINE)
    d.rectangle((47, 5, 48, 11), fill=BLUE)
    # Spulenringe am Lauf
    for y in (16, 24, 32):
        d.rectangle((40, y, 55, y + 2), fill=OUTLINE)
        d.rectangle((41, y + 1, 54, y + 1), fill=PURPLE)
    # Kern
    core(d, 48, 52, 7, BLUE, WHITE)
    save(im, "skillable-turret.png")

def turret_blade():
    im = img(96); d = ImageDraw.Draw(im)
    # linke Klinge (Spiegelung wird von RegionPart erzeugt)
    d.polygon([(26, 64), (18, 42), (24, 22), (32, 22), (34, 40), (34, 62)], fill=OUTLINE)
    d.polygon([(27, 62), (20, 42), (25, 24), (31, 24), (32, 40), (32, 60)], fill=LIGHT)
    d.line((27, 28, 28, 56), fill=BLUE)
    d.line((29, 28, 30, 56), fill=PURPLE)
    save(im, "skillable-turret-blade.png")

# --- Flux Fuser (3x3): Kammer in der Mitte, Fluessigkeits-Ports links/rechts, Material-Ports oben/unten
def fuser():
    n = 96
    im = img(n); d = ImageDraw.Draw(im)
    panel(d, (2, 2, n - 3, n - 3))
    rivets(d, (2, 2, n - 3, n - 3), step=10, inset=5)
    # Material-Ports (oben/unten)
    for cx in (28, 68):
        panel(d, (cx - 7, 6, cx + 7, 16), fill=DARK, hi=MID, lo=OUTLINE)
        panel(d, (cx - 7, n - 17, cx + 7, n - 7), fill=DARK, hi=MID, lo=OUTLINE)
        d.line((cx - 4, 11, cx + 4, 11), fill=BLUE); d.line((cx - 4, n - 12, cx + 4, n - 12), fill=BLUE)
    # Fluessigkeits-Rohre (links/rechts)
    for cy, col in ((34, BLUE), (62, PURPLE)):
        d.rectangle((2, cy - 6, 18, cy + 6), fill=OUTLINE); d.rectangle((3, cy - 5, 17, cy + 5), fill=DARK)
        d.rectangle((5, cy - 3, 15, cy + 3), fill=col)
        d.rectangle((n - 19, cy - 6, n - 3, cy + 6), fill=OUTLINE); d.rectangle((n - 18, cy - 5, n - 4, cy + 5), fill=DARK)
        d.rectangle((n - 16, cy - 3, n - 6, cy + 3), fill=col)
    # Reaktionskammer
    m = n // 2
    d.ellipse((m - 25, m - 25, m + 25, m + 25), fill=OUTLINE)
    d.ellipse((m - 23, m - 23, m + 23, m + 23), fill=MID)
    d.ellipse((m - 19, m - 19, m + 19, m + 19), fill=DARK)
    d.ellipse((m - 14, m - 14, m + 14, m + 14), fill=OUTLINE)
    d.ellipse((m - 12, m - 12, m + 12, m + 12), fill=BLUE)
    d.ellipse((m - 7, m - 7, m + 7, m + 7), fill=WHITE)
    for a in range(0, 360, 60):
        x = m + 21 * math.cos(math.radians(a)); y = m + 21 * math.sin(math.radians(a))
        d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=PURPLE)
    save(im, "flux-fuser.png")

# --- Item-Icons (32x32), je eigene Form und Oberflaeche (keine umgefaerbten Standard-Items)
def cooler():
    """Kuehlmantel fuer den Flux Fuser (2x2): Rippenpaket, Kuehlmittelfenster, Anschluesse links/rechts."""
    n = 64
    im = img(n); d = ImageDraw.Draw(im)
    panel(d, (1, 1, n - 2, n - 2))
    rivets(d, (1, 1, n - 2, n - 2), step=10, inset=4)
    # Kuehlrippen oben und unten
    for y0 in (6, n - 17):
        for x in range(10, n - 10, 6):
            d.rectangle((x, y0, x + 3, y0 + 10), fill=OUTLINE)
            d.rectangle((x + 1, y0 + 1, x + 2, y0 + 9), fill=BLUE)
    # Anschlussrohre links/rechts
    for cx in (1, n - 14):
        d.rectangle((cx, 26, cx + 12, 38), fill=OUTLINE); d.rectangle((cx + 1, 27, cx + 11, 37), fill=DARK)
        d.rectangle((cx + 3, 29, cx + 9, 35), fill=BLUE)
    # Kuehlmittelfenster mit Frostkristall
    m = n // 2
    d.rectangle((m - 12, m - 12, m + 12, m + 12), fill=OUTLINE)
    d.rectangle((m - 10, m - 10, m + 10, m + 10), fill=DARK)
    d.rectangle((m - 8, m - 8, m + 8, m + 8), fill=BLUE)
    for dx, dy in ((0, -6), (0, 6), (-6, 0), (6, 0), (-4, -4), (4, 4), (-4, 4), (4, -4)):
        d.line((m, m, m + dx, m + dy), fill=WHITE)
    d.rectangle((m - 1, m - 1, m + 1, m + 1), fill=WHITE)
    save(im, "flux-cooler.png")

def item_carbon_conductor():
    im = img(32); d = ImageDraw.Draw(im)
    for dx in (-6, 0, 6):  # drei schwarze Stangen mit Kupferringen
        x = 16 + dx
        d.rectangle((x - 3, 4, x + 3, 27), fill=(24, 26, 34, 255))
        d.line((x - 2, 5, x - 2, 26), fill=(70, 78, 96, 255))
        d.line((x + 2, 5, x + 2, 26), fill=(14, 15, 20, 255))
        for y in (8, 15, 22):
            d.rectangle((x - 3, y, x + 3, y + 2), fill=(196, 120, 70, 255))
            d.line((x - 3, y, x + 3, y), fill=(240, 170, 110, 255))
    for (x, y) in ((8, 5), (24, 10), (16, 28)):
        d.point((x, y), fill=(130, 190, 255, 255)); d.point((x + 1, y), fill=(255, 255, 255, 255))
    im.save(os.path.join(OUT, "carbon-conductor.png"))

def item_ferrite_composite():
    im = img(32); d = ImageDraw.Draw(im)
    pts = [(16, 3), (28, 10), (28, 22), (16, 29), (4, 22), (4, 10)]  # Sechseck-Platte
    d.polygon(pts, fill=(52, 34, 30, 255))
    d.polygon([(16, 5), (26, 11), (26, 21), (16, 27), (6, 21), (6, 11)], fill=(168, 105, 74, 255))
    d.polygon([(16, 8), (23, 12), (23, 20), (16, 24), (9, 20), (9, 12)], fill=(122, 74, 54, 255))
    for k in range(3):  # Lagen / Chevrons
        d.line((9, 13 + 3 * k, 16, 16 + 3 * k), fill=(214, 150, 110, 255)); d.line((16, 16 + 3 * k, 23, 13 + 3 * k), fill=(214, 150, 110, 255))
    for (x, y) in ((11, 11), (21, 14), (14, 21), (20, 19)):
        d.point((x, y), fill=(110, 220, 200, 255))
    d.line((8, 8, 16, 4), fill=(235, 190, 150, 255))
    im.save(os.path.join(OUT, "ferrite-composite.png"))

def item_synaptic_alloy():
    im = img(32); d = ImageDraw.Draw(im)
    nodes = [(16, 16), (6, 8), (26, 7), (5, 24), (27, 25), (16, 4), (16, 29)]
    for (x, y) in nodes[1:]:
        d.line((16, 16, x, y), fill=(120, 90, 210, 255), width=2)
    d.line((6, 8, 16, 4), fill=(90, 65, 170, 255)); d.line((26, 7, 16, 4), fill=(90, 65, 170, 255))
    d.line((5, 24, 16, 29), fill=(90, 65, 170, 255)); d.line((27, 25, 16, 29), fill=(90, 65, 170, 255))
    for (x, y) in nodes[1:]:
        d.ellipse((x - 3, y - 3, x + 3, y + 3), fill=(40, 28, 80, 255)); d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=(169, 139, 255, 255))
    d.ellipse((16 - 6, 16 - 6, 16 + 6, 16 + 6), fill=(40, 28, 80, 255)); d.ellipse((16 - 5, 16 - 5, 16 + 5, 16 + 5), fill=(190, 165, 255, 255))
    d.ellipse((16 - 2, 16 - 2, 16 + 2, 16 + 2), fill=(255, 255, 255, 255))
    im.save(os.path.join(OUT, "synaptic-alloy.png"))

def item_pyroglass():
    im = img(32); d = ImageDraw.Draw(im)
    shards = [[(16, 2), (23, 14), (16, 30), (9, 14)], [(5, 12), (11, 20), (7, 29), (2, 20)], [(27, 12), (30, 20), (25, 29), (21, 20)]]
    cols = [((255, 122, 69, 255), (255, 190, 120, 255)), ((214, 80, 40, 255), (255, 150, 90, 255)), ((230, 100, 50, 255), (255, 170, 100, 255))]
    for pts, (c1, c2) in zip(shards, cols):
        d.polygon(pts, fill=(70, 28, 20, 255))
        inner = [(px + (16 - px) * 0.0, py) for px, py in pts]
        cx = sum(p[0] for p in pts) / 4; cy = sum(p[1] for p in pts) / 4
        small = [(cx + (px - cx) * 0.72, cy + (py - cy) * 0.72) for px, py in pts]
        d.polygon(small, fill=c1)
        d.line((small[0][0], small[0][1], small[3][0], small[3][1]), fill=c2)
    d.line((15, 6, 13, 14), fill=(255, 245, 225, 255)); d.point((14, 18), fill=(255, 255, 255, 255))
    im.save(os.path.join(OUT, "pyroglass.png"))

def item_material_base():
    """Neutrales Platzhalter-Icon der Material-Traeger-Items (bis das echte Icon im Spiel erzeugt wird)."""
    im = img(32); d = ImageDraw.Draw(im)
    d.polygon([(16, 4), (27, 12), (27, 22), (16, 29), (5, 22), (5, 12)], fill=(120, 128, 150, 255), outline=(40, 44, 58, 255))
    d.polygon([(16, 4), (27, 12), (16, 16), (5, 12)], fill=(160, 170, 195, 255))
    d.line([(16, 16), (16, 29)], fill=(90, 98, 120, 255))
    im.save(os.path.join(OUT, "material-base.png"))

def mod_icon():
    im = img(128); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 127, 127), fill=(24, 26, 36, 255))
    for r, c in ((52, PURPLE), (38, BLUE), (22, WHITE)):
        d.ellipse((64 - r, 64 - r, 64 + r, 64 + r), outline=c, width=4)
    core(d, 64, 64, 12, BLUE, WHITE)
    im.save(os.path.join(OUT, "..", "..", "icon.png"))

if __name__ == "__main__":
    for suf in PALETTES:
        set_palette(suf)
        node()
        converter("flux-stabilizer.png", 64, BLUE, 2)
        converter("flux-accelerator.png", 64, PURPLE, 3)
        converter("flux-quantum.png", 96, (205, 130, 255, 255) if suf == "" else (255, 170, 120, 255), 4)
        turret_base(); turret_head(); turret_blade(); fuser(); cooler()
    set_palette("")
    item_carbon_conductor(); item_ferrite_composite(); item_synaptic_alloy(); item_pyroglass(); item_material_base()
    mod_icon()
    print("Sprites erzeugt in", os.path.abspath(OUT))
