#!/usr/bin/env python3
"""Generates RuneDefence's original pixel art. Standard library only.

Everything is drawn from flat-shaded polygons with a dark outline, which gives
the low-poly, chunky look of the game's era without using any extracted assets.

Outputs:
  src/main/resources/com/runedefence/sprites.png   the atlas (indexed PNG)
  src/main/resources/com/runedefence/sprites.json  name -> [x, y, w, h, frames]
  src/main/resources/com/runedefence/panel_icon.png  16x16 navigation icon
  icon.png                                           Plugin Hub icon, not in the jar

Run from anywhere: python3 tools/gen_sprites.py [preview.png]
"""
import json
import math
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "com", "runedefence")

OUTLINE = (24, 18, 12, 255)
SHADOW = (0, 0, 0, 80)
CLEAR = (0, 0, 0, 0)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.p = [[CLEAR] * w for _ in range(h)]

    def px(self, x, y, c):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c

    def rect(self, x, y, w, h, c):
        for yy in range(int(y), int(y + h)):
            for xx in range(int(x), int(x + w)):
                self.px(xx, yy, c)

    def poly(self, pts, c):
        ys = [p[1] for p in pts]
        for y in range(int(math.floor(min(ys))), int(math.ceil(max(ys))) + 1):
            sy = y + 0.5
            xs = []
            for i, (x0, y0) in enumerate(pts):
                x1, y1 = pts[(i + 1) % len(pts)]
                if (y0 <= sy < y1) or (y1 <= sy < y0):
                    xs.append(x0 + (sy - y0) * (x1 - x0) / (y1 - y0))
            xs.sort()
            for a, b in zip(xs[::2], xs[1::2]):
                for x in range(int(round(a)), int(round(b))):
                    self.px(x, y, c)

    def ell(self, cx, cy, rx, ry, c):
        for y in range(int(cy - ry - 1), int(cy + ry + 2)):
            for x in range(int(cx - rx - 1), int(cx + rx + 2)):
                if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1:
                    self.px(x, y, c)

    def line(self, x0, y0, x1, y1, c, w=1):
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) * 2 + 1
        for i in range(steps + 1):
            t = i / steps
            x = x0 + (x1 - x0) * t
            y = y0 + (y1 - y0) * t
            if w <= 1:
                self.px(x, y, c)
            else:
                self.rect(round(x - w / 2), round(y - w / 2), w, w, c)

    def outline(self, c=OUTLINE):
        """Adds a one pixel dark edge around all opaque pixels (shadow excluded)."""
        src = [row[:] for row in self.p]
        for y in range(self.h):
            for x in range(self.w):
                if src[y][x][3] == 255:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.w and 0 <= ny < self.h and src[ny][nx][3] == 255:
                        self.p[y][x] = c
                        break

    def under_shadow(self, cx, cy, rx, ry):
        """Puts a soft ground shadow beneath what has already been drawn."""
        for y in range(self.h):
            for x in range(self.w):
                if self.p[y][x][3] == 0 and ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1:
                    self.p[y][x] = SHADOW

    def blit(self, other, ox, oy):
        for y in range(other.h):
            for x in range(other.w):
                c = other.p[y][x]
                if c[3]:
                    self.px(ox + x, oy + y, c)


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


def polar(x, y, length, deg):
    a = math.radians(deg)
    return x + math.cos(a) * length, y + math.sin(a) * length


def limb(c, x0, y0, x1, y1, w, col):
    c.line(x0, y0, x1, y1, col, w)


# Weapons are drawn from the hand outward at an angle (0 = forward, -90 = up).
def sword(c, x, y, deg, length=9, blade=rgb(0xC8CCD0), edge=rgb(0x8A9096), hilt=rgb(0x6A4A1E)):
    gx, gy = polar(x, y, 2, deg)
    tx, ty = polar(x, y, length, deg)
    c.line(gx, gy, tx, ty, blade, 2)
    c.line(gx, gy + 1, tx, ty + 1, edge)
    a, b = polar(gx, gy, 2, deg + 90), polar(gx, gy, 2, deg - 90)
    c.line(a[0], a[1], b[0], b[1], hilt)


def club(c, x, y, deg, length=9, wood=rgb(0x6E4A24), knob=rgb(0x8C6432), w=2):
    tx, ty = polar(x, y, length, deg)
    c.line(x, y, tx, ty, wood, w)
    c.ell(tx, ty, w + 0.5, w + 0.5, knob)


def spear(c, x, y, deg, length=11):
    bx, by = polar(x, y, -3, deg)
    tx, ty = polar(x, y, length, deg)
    c.line(bx, by, tx, ty, rgb(0x7A5228))
    hx, hy = polar(tx, ty, 3, deg)
    c.line(tx, ty, hx, hy, rgb(0xB8BEC4), 2)


def axe(c, x, y, deg, length=14):
    tx, ty = polar(x, y, length, deg)
    c.line(x, y, tx, ty, rgb(0x5A3C1C), 2)
    a1 = polar(tx, ty, 6, deg + 90)
    a2 = polar(tx, ty, 5, deg - 90)
    f = polar(tx, ty, 5, deg)
    c.poly([a1, f, a2, polar(tx, ty, -2, deg)], rgb(0xD8DEE4))
    c.line(f[0], f[1], a1[0], a1[1], rgb(0xFFFFFF))


# ---- Humanoid rig -------------------------------------------------------------
# A single rig gives every character real poses instead of redrawn frames.

def biped(c, pal, cx=14, ground=25, height=1.0, bob=0, swing=0, lean=0, width=1.0):
    """Draws legs and torso. Returns (shoulder_x, shoulder_y, neck_x, neck_y)."""
    h = height
    hip_y = ground - 6 * h + bob
    sh_y = hip_y - 7 * h
    hw = 3 * width
    hip_x = cx
    sh_x = cx + lean
    # Back leg, then front leg, each with a boot.
    limb(c, hip_x - 1, hip_y, hip_x - 1 - swing, ground - 1, 3, pal["legs2"])
    c.rect(hip_x - 2 - swing, ground - 2, 4, 2, pal["boot"])
    limb(c, hip_x + 1, hip_y, hip_x + 1 + swing, ground - 1, 3, pal["legs"])
    c.rect(hip_x + swing, ground - 2, 4, 2, pal["boot"])
    # Torso is two facets, lit from the front.
    c.poly([(sh_x - hw, sh_y), (sh_x + hw, sh_y), (hip_x + hw, hip_y + 1), (hip_x - hw, hip_y + 1)], pal["body"])
    c.poly([(sh_x - hw, sh_y), (sh_x - 0.5, sh_y), (hip_x - 0.5, hip_y + 1), (hip_x - hw, hip_y + 1)], pal["body2"])
    return sh_x, sh_y


def head(c, x, y, r, skin, skin2):
    """A chunky faceted head centred on x, y. Eye is drawn on the forward side."""
    c.poly([(x - r, y - r + 1), (x - r + 1, y - r), (x + r - 1, y - r), (x + r, y - r + 1),
            (x + r, y + r - 1), (x + r - 1, y + r), (x - r + 1, y + r), (x - r, y + r - 1)], skin)
    c.poly([(x - r, y - r + 1), (x - r + 1, y - r), (x - 1, y - r), (x - 1, y + r), (x - r + 1, y + r),
            (x - r, y + r - 1)], skin2)


PAL_GOBLIN = dict(skin=rgb(0x7FA83A), skin2=rgb(0x5E8428), body=rgb(0x8A6A3A), body2=rgb(0x6E5228),
                  legs=rgb(0x7FA83A), legs2=rgb(0x5E8428), boot=rgb(0x4A3A1E), arm=rgb(0x6F9632))
PAL_GUARD = dict(skin=rgb(0xD0A07A), skin2=rgb(0xB08460), body=rgb(0x9A2E28), body2=rgb(0x74201C),
                 legs=rgb(0x82868C), legs2=rgb(0x62666C), boot=rgb(0x3E3226), arm=rgb(0x9CA2A8))
PAL_ARCHER = dict(skin=rgb(0xD0A07A), skin2=rgb(0xB08460), body=rgb(0x4E7A32), body2=rgb(0x3A5E24),
                  legs=rgb(0x6E5228), legs2=rgb(0x54401E), boot=rgb(0x3E2E18), arm=rgb(0x4E7A32))
PAL_DWARF = dict(skin=rgb(0xD8A882), skin2=rgb(0xB88A66), body=rgb(0x5A6470), body2=rgb(0x444E58),
                 legs=rgb(0x6A4A2A), legs2=rgb(0x52381E), boot=rgb(0x32261A), arm=rgb(0xB88A66))
PAL_RAIDER = dict(skin=rgb(0x5E7A2C), skin2=rgb(0x48601F), body=rgb(0x7A2A22), body2=rgb(0x5C1E18),
                  legs=rgb(0x5E7A2C), legs2=rgb(0x48601F), boot=rgb(0x2E2414), arm=rgb(0x54702A))
PAL_BONE = dict(skin=rgb(0xDCD8C4), skin2=rgb(0xB0AC98), body=rgb(0xC4C0AC), body2=rgb(0x98947E),
                legs=rgb(0xD0CCB8), legs2=rgb(0xA4A08C), boot=rgb(0xB0AC98), arm=rgb(0xD0CCB8))
PAL_GIANT = dict(skin=rgb(0xC69C72), skin2=rgb(0xA47E58), body=rgb(0x6A4E2A), body2=rgb(0x523C1E),
                 legs=rgb(0xC69C72), legs2=rgb(0xA47E58), boot=rgb(0x7E6040), arm=rgb(0xB48C64))
PAL_CHAMP = dict(skin=rgb(0x6E9630), skin2=rgb(0x547624), body=rgb(0xB8922E), body2=rgb(0x8E6E1E),
                 legs=rgb(0x6A6E74), legs2=rgb(0x50545A), boot=rgb(0x3A3024), arm=rgb(0x6E9630))


def goblin_head(c, x, y, pal):
    # Long ears are the goblin silhouette.
    c.poly([(x - 3, y - 2), (x - 9, y - 4), (x - 3, y + 1)], pal["skin2"])
    c.poly([(x + 3, y - 2), (x + 7, y - 4), (x + 3, y + 1)], pal["skin"])
    head(c, x, y, 4, pal["skin"], pal["skin2"])
    c.rect(x + 1, y - 1, 2, 2, rgb(0xE8D020))
    c.px(x + 2, y, OUTLINE)
    c.rect(x + 1, y + 2, 3, 1, OUTLINE)
    c.px(x + 2, y + 2, rgb(0xE8E4D0))


def draw_goblin(c, f):
    # f: 0,1 idle / 2 windup / 3 thrust
    bob = (0, -1, 0, 1)[f]
    deg = (20, 15, -70, 5)[f]
    reach = (4, 4, 2, 6)[f]
    lean = (0, 0, -1, 2)[f]
    pal = PAL_GOBLIN
    sx, sy = biped(c, pal, ground=25, height=0.82, bob=bob, swing=(1, 1, 0, 2)[f], lean=lean)
    goblin_head(c, sx, sy - 4, pal)
    hx, hy = polar(sx, sy + 2, reach, deg)
    spear(c, hx, hy, deg - 5, 9)
    limb(c, sx, sy + 2, hx, hy, 2, pal["arm"])
    c.outline()
    c.under_shadow(14, 25.5, 7, 2.5)


def draw_guard(c, f):
    bob = (0, -1, -1, 1)[f]
    deg = (-55, -60, -125, 15)[f]
    lean = (0, 0, -1, 2)[f]
    pal = PAL_GUARD
    sx, sy = biped(c, pal, ground=25, height=1.05, bob=bob, swing=(1, 1, 0, 3)[f], lean=lean, width=1.1)
    # Mail shoulders under the tabard.
    c.rect(sx - 3, sy, 7, 2, rgb(0x9CA2A8))
    # Steel kettle helm with a plume.
    hx, hy = sx, sy - 4
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    c.poly([(hx - 5, hy - 1), (hx - 4, hy - 5), (hx + 4, hy - 5), (hx + 5, hy - 1)], rgb(0xB4BAC0))
    c.poly([(hx - 5, hy - 1), (hx - 4, hy - 5), (hx - 1, hy - 5), (hx - 1, hy - 1)], rgb(0x84898F))
    c.rect(hx - 6, hy - 1, 12, 1, rgb(0x6A6E74))
    c.rect(hx + 1, hy, 2, 1, OUTLINE)
    c.poly([(hx - 1, hy - 5), (hx - 4, hy - 9), (hx + 1, hy - 8), (hx + 1, hy - 5)], rgb(0xC03830))
    ax, ay = polar(sx + 1, sy + 2, 5, (10, 10, -40, 5)[f])
    sword(c, ax, ay, deg, 11)
    limb(c, sx + 1, sy + 2, ax, ay, 2, pal["arm"])
    # Round shield on the off arm.
    c.ell(sx - 4, sy + 4, 3, 4, rgb(0x6A4A2A))
    c.ell(sx - 4, sy + 4, 1.2, 1.5, rgb(0xB4BAC0))
    c.outline()
    c.under_shadow(14, 25.5, 8, 2.5)


def bow(c, x, y, draw):
    # A curved stave facing forward, with the string pulled back by `draw` pixels.
    wood = rgb(0x8A5E28)
    top, bot = (x - 1, y - 9), (x - 1, y + 9)
    mid = (x + 3, y)
    c.line(top[0], top[1], mid[0], mid[1] - 3, wood, 2)
    c.line(mid[0], mid[1] - 3, mid[0], mid[1] + 3, wood, 2)
    c.line(mid[0], mid[1] + 3, bot[0], bot[1], wood, 2)
    sx = x - 1 - draw
    c.line(top[0], top[1], sx, y, rgb(0xE8E0C8))
    c.line(bot[0], bot[1], sx, y, rgb(0xE8E0C8))
    return sx


def draw_archer(c, f):
    bob = (0, -1, 0, 1)[f]
    draw = (0, 0, 5, 0)[f]
    pal = PAL_ARCHER
    sx, sy = biped(c, pal, ground=25, height=1.0, bob=bob, swing=(1, 1, 2, 2)[f], lean=(0, 0, -1, 1)[f])
    hx, hy = sx, sy - 4
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    # Hood with a feather.
    c.poly([(hx - 5, hy + 2), (hx - 5, hy - 3), (hx - 2, hy - 5), (hx + 3, hy - 5), (hx + 4, hy - 2),
            (hx, hy - 2), (hx - 1, hy + 2)], rgb(0x3A5E24))
    c.line(hx - 3, hy - 5, hx - 7, hy - 8, rgb(0xC03830))
    c.rect(hx + 2, hy - 1, 2, 1, OUTLINE)
    bx, by = sx + 6, sy + 2
    string_x = bow(c, bx, by, draw)
    limb(c, sx, sy + 2, bx, by, 2, pal["skin2"])
    if f == 2:
        c.line(string_x, by, bx + 8, by, rgb(0xE8E0C8))
        c.px(bx + 8, by, rgb(0xB8BEC4))
    limb(c, sx - 1, sy + 3, string_x, by, 2, pal["arm"])
    if f == 3:
        c.line(bx + 5, by, bx + 10, by, rgb(0xFFF4C0))
    c.outline()
    c.under_shadow(14, 25.5, 7, 2.5)


def draw_wizard(c, f):
    bob = (0, -1, -1, 0)[f]
    robe, robe2 = rgb(0x2E2850), rgb(0x1E1A3A)
    trim = rgb(0xC03830)
    # A long robe instead of legs.
    sx, sy = 14 + (0, 0, -1, 1)[f], 11 + bob
    c.poly([(sx - 4, sy), (sx + 4, sy), (21, 25), (7, 25)], robe)
    c.poly([(sx - 4, sy), (sx - 1, sy), (13, 25), (7, 25)], robe2)
    c.rect(8, 23, 13, 1, trim)
    c.line(sx + 1, sy + 1, sx + 1, 22, trim)
    hx, hy = sx, sy - 4
    c.ell(hx, hy, 5, 5, robe2)
    c.poly([(hx - 5, hy - 2), (hx - 1, hy - 9), (hx + 4, hy - 3)], robe)
    c.rect(hx - 1, hy - 2, 5, 5, rgb(0xB89070))
    c.rect(hx + 1, hy - 1, 2, 1, rgb(0xE84030))
    c.rect(hx - 1, hy + 2, 4, 2, rgb(0x8A8A90))
    # Staff is raised to cast; the orb grows on the strike frame.
    tx, ty = ((sx + 7, sy + 2), (sx + 7, sy + 1), (sx + 6, sy - 3), (sx + 9, sy + 2))[f]
    limb(c, sx + 1, sy + 3, tx, ty, 2, robe)
    c.line(tx, ty - 7, tx, ty + 10, rgb(0x5A3C1C))
    orb = (1.6, 1.6, 2.4, 3.2)[f]
    c.ell(tx, ty - 8, orb, orb, rgb(0xB050E0))
    c.px(tx - 1, ty - 9, rgb(0xF0D0FF))
    c.outline()
    c.under_shadow(14, 25.5, 8, 2.5)


def draw_dwarf(c, f):
    bob = (0, -1, -1, 1)[f]
    pal = PAL_DWARF
    sx, sy = biped(c, pal, ground=25, height=0.72, bob=bob, swing=(1, 1, 0, 2)[f], lean=(0, 0, -1, 2)[f], width=1.5)
    hx, hy = sx, sy - 3
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    # A huge orange beard and a horned iron helm.
    c.poly([(hx - 3, hy), (hx + 5, hy), (hx + 4, hy + 7), (hx, hy + 8), (hx - 3, hy + 5)], rgb(0xC8641E))
    c.poly([(hx - 3, hy), (hx, hy), (hx, hy + 8), (hx - 3, hy + 5)], rgb(0xA04E14))
    c.rect(hx + 1, hy - 1, 2, 1, OUTLINE)
    c.poly([(hx - 5, hy - 2), (hx - 4, hy - 6), (hx + 4, hy - 6), (hx + 5, hy - 2)], rgb(0x8C929A))
    c.rect(hx - 5, hy - 2, 10, 1, rgb(0xC8A030))
    c.line(hx - 5, hy - 5, hx - 7, hy - 8, rgb(0xE8E0C8), 2)
    # A sling-carried boulder: wound up, then released.
    deg = (20, 25, -120, 15)[f]
    ax, ay = polar(sx + 2, sy + 2, 6, deg)
    limb(c, sx + 2, sy + 2, ax, ay, 3, pal["arm"])
    if f != 3:
        c.ell(ax, ay - 2, 3, 3, rgb(0x7E7E78))
        c.px(ax - 1, ay - 3, rgb(0xB0B0A8))
    c.outline()
    c.under_shadow(14, 25.5, 9, 2.5)


# ---- Enemies: f is the walk frame, 0..3 ----------------------------------------

def draw_rat(c, f):
    fur, fur2 = rgb(0x7A6048), rgb(0x5A4632)
    y = 19 + (0, -1, 0, -1)[f]
    # Tail.
    wag = (0, 1, 0, -1)[f]
    c.line(7, y + 2, 3, y + wag, rgb(0xC89088))
    c.line(3, y + wag, 1, y - 2 + wag, rgb(0xC89088))
    # Scurrying legs.
    for i, lx in enumerate((10, 14, 18, 21)):
        off = (1, -1)[(i + f) % 2]
        c.line(lx, y + 3, lx + off, 24, fur2)
    c.ell(13, y, 7, 4.5, fur)
    c.poly([(7, y - 3), (15, y - 4), (13, y + 4), (7, y + 3)], fur2)
    c.poly([(18, y - 3), (25, y + 1), (18, y + 3)], fur)
    c.rect(17, y - 5, 3, 3, rgb(0xC89088))
    c.px(21, y - 1, rgb(0xE02020))
    c.px(25, y + 1, rgb(0xC89088))
    c.outline()
    c.under_shadow(14, 25, 9, 2)


def draw_raider(c, f):
    swing = (3, 0, -3, 0)[f]
    bob = (0, -1, 0, -1)[f]
    pal = PAL_RAIDER
    sx, sy = biped(c, pal, ground=25, height=0.9, bob=bob, swing=swing, lean=1)
    goblin_head(c, sx, sy - 4, pal)
    # Red bandana marks it as hostile.
    c.rect(sx - 4, sy - 8, 8, 2, rgb(0xB02A20))
    c.px(sx - 5, sy - 7, rgb(0xB02A20))
    c.px(sx - 6, sy - 6, rgb(0xB02A20))
    deg = -45 + (0, 8, 0, -8)[f]
    hx, hy = sx + 4, sy + 3
    club(c, hx, hy, deg, 8)
    limb(c, sx, sy + 2, hx, hy, 2, pal["arm"])
    c.outline()
    c.under_shadow(14, 25.5, 7, 2.5)


def draw_skeleton(c, f):
    swing = (3, 0, -3, 0)[f]
    bob = (0, -1, 0, -1)[f]
    dark = rgb(0x2A2620)
    pal = PAL_BONE
    # Bare leg bones.
    hip_y = 18 + bob
    c.line(13, hip_y, 13 - swing, 24, pal["legs2"], 2)
    c.line(15, hip_y, 15 + swing, 24, pal["legs"], 2)
    c.rect(12, hip_y - 1, 5, 2, pal["body"])
    # Ribs read as stripes over a dark void.
    sy = 10 + bob
    c.rect(12, sy, 6, 7, dark)
    c.rect(14, sy, 2, 8, pal["body2"])
    for r in range(sy + 1, sy + 7, 2):
        c.rect(11, r, 8, 1, pal["body"])
    # Skull with hollow eyes.
    hx, hy = 15, sy - 4
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    c.rect(hx + 1, hy - 1, 2, 2, dark)
    c.rect(hx - 2, hy - 1, 2, 2, dark)
    c.rect(hx - 1, hy + 3, 5, 1, dark)
    c.px(hx + 1, hy + 3, pal["skin"])
    ax, ay = 21, sy + 3
    sword(c, ax, ay, -35 + (0, 6, 0, -6)[f], 9, blade=rgb(0x9CA0A0), edge=rgb(0x6A6E70))
    c.line(17, sy + 1, ax, ay, pal["arm"], 2)
    c.outline()
    c.under_shadow(14, 25.5, 7, 2.5)


def draw_spider(c, f):
    # Seen from above and slightly behind: a round abdomen and eight long legs.
    body, body2, leg = rgb(0x3E2A4A), rgb(0x2A1C34), rgb(0x7A5C8C)
    y = 15 + (0, -1, 0, -1)[f]
    for i in range(4):
        lift = (2, -1)[(i + f) % 2]
        ly = y - 2 + i * 2
        # Each leg rises to a knee and comes back down, mirrored either side.
        for side in (-1, 1):
            knee = (14 + side * (7 + i % 2), ly - 3 + lift)
            foot = (14 + side * (12 - abs(i - 1)), min(24, ly + 6))
            c.line(14 + side * 3, ly, knee[0], knee[1], leg)
            c.line(knee[0], knee[1], foot[0], foot[1], leg)
    c.ell(14, y + 3, 5.5, 5, body)
    c.ell(13, y + 2, 3, 2.5, rgb(0x6E3E7E))
    c.rect(13, y + 1, 2, 3, rgb(0xB03030))
    c.ell(14, y - 3, 3.5, 3, body2)
    c.px(12, y - 3, rgb(0xF04040))
    c.px(15, y - 3, rgb(0xF04040))
    c.px(13, y - 2, rgb(0xF04040))
    c.px(14, y - 2, rgb(0xF04040))
    c.outline()
    c.under_shadow(14, 25, 11, 2)


def draw_giant(c, f):
    # 40 x 40 cell. Lumbering, with a wide club.
    swing = (4, 0, -4, 0)[f]
    bob = (0, -1, 0, -1)[f]
    pal = PAL_GIANT
    cx = 19
    ground = 37
    hip_y = 24 + bob
    limb(c, cx - 3, hip_y, cx - 3 - swing, ground - 1, 5, pal["legs2"])
    c.rect(cx - 5 - swing, ground - 2, 7, 2, pal["boot"])
    limb(c, cx + 3, hip_y, cx + 3 + swing, ground - 1, 5, pal["legs"])
    c.rect(cx + 1 + swing, ground - 2, 7, 2, pal["boot"])
    c.poly([(cx - 8, 10 + bob), (cx + 9, 10 + bob), (cx + 7, 24 + bob), (cx - 7, 24 + bob)], pal["skin"])
    c.poly([(cx - 8, 10 + bob), (cx - 1, 10 + bob), (cx - 1, 24 + bob), (cx - 7, 24 + bob)], pal["skin2"])
    # Hide loincloth and belly facet.
    c.poly([(cx - 8, 22 + bob), (cx + 8, 22 + bob), (cx + 7, 29 + bob), (cx - 7, 29 + bob)], pal["body"])
    c.rect(cx - 8, 22 + bob, 16, 1, pal["body2"])
    c.rect(cx + 2, 15 + bob, 3, 1, pal["skin2"])
    hx, hy = cx + 2, 6 + bob
    head(c, hx, hy, 5, pal["skin"], pal["skin2"])
    c.rect(hx + 1, hy - 2, 3, 2, rgb(0xE8E4D0))
    c.px(hx + 3, hy - 1, OUTLINE)
    c.rect(hx - 1, hy - 3, 6, 1, rgb(0x5A3C1C))
    c.rect(hx, hy + 2, 5, 1, OUTLINE)
    c.px(hx + 1, hy + 1, rgb(0xE8E4D0))
    c.px(hx + 4, hy + 1, rgb(0xE8E4D0))
    # Hair tuft.
    c.poly([(hx - 5, hy - 3), (hx - 2, hy - 7), (hx + 2, hy - 5)], rgb(0x5A3C1C))
    ax, ay = cx + 13, 18 + bob
    club(c, ax, ay, -60 + (0, 6, 0, -6)[f], 13, w=4)
    limb(c, cx + 7, 12 + bob, ax, ay, 4, pal["arm"])
    limb(c, cx - 7, 12 + bob, cx - 10, 24 + bob - swing // 2, 4, pal["skin2"])
    c.outline()
    c.under_shadow(19, 37.5, 13, 3)


def draw_champion(c, f):
    # 40 x 40 cell. A goblin warlord: gold plate, crown, and a great axe.
    swing = (4, 0, -4, 0)[f]
    bob = (0, -1, 0, -1)[f]
    pal = PAL_CHAMP
    cx = 18
    ground = 37
    hip_y = 25 + bob
    limb(c, cx - 3, hip_y, cx - 3 - swing, ground - 1, 4, pal["legs2"])
    c.rect(cx - 5 - swing, ground - 2, 7, 2, pal["boot"])
    limb(c, cx + 3, hip_y, cx + 3 + swing, ground - 1, 4, pal["legs"])
    c.rect(cx + 1 + swing, ground - 2, 7, 2, pal["boot"])
    c.poly([(cx - 7, 14 + bob), (cx + 7, 14 + bob), (cx + 6, 26 + bob), (cx - 6, 26 + bob)], pal["body"])
    c.poly([(cx - 7, 14 + bob), (cx - 1, 14 + bob), (cx - 1, 26 + bob), (cx - 6, 26 + bob)], pal["body2"])
    c.rect(cx - 6, 24 + bob, 12, 2, rgb(0x6E5218))
    c.rect(cx - 1, 24 + bob, 3, 2, rgb(0xE8D060))
    # Red war cape behind the shoulder and pauldrons.
    c.poly([(cx - 7, 14 + bob), (cx - 11 - swing // 2, 30 + bob), (cx - 6, 28 + bob)], rgb(0xA02820))
    c.ell(cx - 7, 15 + bob, 3, 2.5, rgb(0xD8B040))
    c.ell(cx + 7, 15 + bob, 3, 2.5, rgb(0xD8B040))
    hx, hy = cx + 1, 9 + bob
    c.poly([(hx - 4, hy - 2), (hx - 12, hy - 5), (hx - 4, hy + 2)], pal["skin2"])
    c.poly([(hx + 4, hy - 2), (hx + 11, hy - 5), (hx + 4, hy + 2)], pal["skin"])
    head(c, hx, hy, 5, pal["skin"], pal["skin2"])
    c.rect(hx + 1, hy - 1, 3, 2, rgb(0xE8D020))
    c.px(hx + 3, hy, OUTLINE)
    c.rect(hx, hy + 3, 5, 1, OUTLINE)
    c.px(hx + 1, hy + 2, rgb(0xE8E4D0))
    c.px(hx + 4, hy + 2, rgb(0xE8E4D0))
    # Spiked crown.
    c.rect(hx - 5, hy - 6, 10, 2, rgb(0xE8C030))
    for sp in (-5, -1, 3):
        c.poly([(hx + sp, hy - 6), (hx + sp + 1, hy - 9), (hx + sp + 2, hy - 6)], rgb(0xE8C030))
    c.px(hx, hy - 5, rgb(0xC02020))
    ax, ay = cx + 10, 22 + bob
    axe(c, ax, ay, -75 + (0, 5, 0, -5)[f], 17)
    limb(c, cx + 6, 16 + bob, ax, ay, 3, pal["arm"])
    c.outline()
    c.under_shadow(18, 37.5, 12, 3)


# ---- Scenery and icons ---------------------------------------------------------

def draw_tree(c, f=0):
    # 28 x 36: an oak with stacked low-poly canopy facets.
    c.poly([(11, 34), (17, 34), (16, 20), (12, 20)], rgb(0x5E4224))
    c.poly([(11, 34), (14, 34), (14, 20), (12, 20)], rgb(0x46301A))
    c.ell(14, 16, 12, 11, rgb(0x3E6E24))
    c.ell(10, 19, 7, 6, rgb(0x2E5A1C))
    c.ell(18, 11, 7, 6, rgb(0x56882E))
    c.ell(11, 10, 6, 5, rgb(0x4A7C28))
    c.rect(18, 8, 3, 2, rgb(0x70A03A))
    c.rect(9, 8, 3, 1, rgb(0x62943A))
    c.outline()
    c.under_shadow(14, 34, 10, 2)


def draw_rock(c, f=0):
    c.poly([(5, 24), (3, 17), (8, 9), (17, 7), (24, 13), (25, 24)], rgb(0x7C7A72))
    c.poly([(5, 24), (3, 17), (8, 9), (12, 14), (11, 24)], rgb(0x5C5A54))
    c.poly([(8, 9), (17, 7), (20, 13), (12, 14)], rgb(0x9C9A90))
    c.poly([(17, 7), (24, 13), (20, 13)], rgb(0x8A8880))
    c.outline()
    c.under_shadow(14, 24.5, 12, 2)


def draw_flower(c, f=0):
    for x, y, col in ((7, 10, 0xE8D040), (18, 7, 0xE8E0F0), (13, 19, 0xD04848), (22, 20, 0xE8D040), (5, 22, 0xE8E0F0)):
        c.line(x, y + 1, x, y + 4, rgb(0x2E6A1C))
        c.rect(x - 1, y, 3, 1, rgb(col))
        c.rect(x, y - 1, 1, 3, rgb(col))
        c.px(x, y, rgb(0xB88020))


def draw_keep(c, f=0):
    # 84 x 44 stone keep: the thing you are defending. Two towers and a gate.
    stone, stone2, stone3 = rgb(0x8C8A82), rgb(0x6C6A62), rgb(0xA8A69C)
    c.rect(14, 16, 56, 26, stone)
    c.rect(14, 16, 56, 2, stone3)
    for x in range(14, 70, 8):
        c.rect(x, 11, 5, 5, stone)
        c.rect(x, 11, 5, 1, stone3)
    for side, tx in ((0, 2), (1, 62)):
        c.rect(tx, 8, 20, 34, stone)
        c.rect(tx, 8, 7, 34, stone2)
        for x in range(tx, tx + 20, 6):
            c.rect(x, 3, 4, 5, stone)
            c.rect(x, 3, 4, 1, stone3)
        c.rect(tx + 8, 16, 3, 6, rgb(0x1E1A14))
    # Masonry courses.
    for y in range(20, 42, 5):
        for x in range((14 if y % 2 else 18), 62, 8):
            c.rect(x, y, 5, 1, stone2)
    # Gate: dark arch the enemies walk into.
    c.poly([(32, 42), (32, 28), (35, 23), (49, 23), (52, 28), (52, 42)], rgb(0x4A4238))
    c.poly([(35, 42), (35, 29), (37, 26), (47, 26), (49, 29), (49, 42)], rgb(0x14100C))
    for x in range(37, 49, 3):
        c.rect(x, 26, 1, 6, rgb(0x3A3228))
    c.rect(36, 30, 13, 1, rgb(0x3A3228))
    # Pennant: blue field, gold mark.
    c.line(12, 3, 12, 0, rgb(0x3E3226))
    c.poly([(12, 0), (20, 1), (12, 3)], rgb(0x2A4C9A))
    c.line(72, 3, 72, 0, rgb(0x3E3226))
    c.poly([(72, 0), (80, 1), (72, 3)], rgb(0x2A4C9A))
    c.outline()


def draw_camp(c, f=0):
    # 28 x 28 hide tent where the raiders pour out.
    c.poly([(2, 25), (14, 5), (26, 25)], rgb(0x8A6A3A))
    c.poly([(2, 25), (14, 5), (12, 25)], rgb(0x6E5228))
    c.poly([(11, 25), (15, 13), (20, 25)], rgb(0x1E1610))
    c.line(14, 5, 10, 1, rgb(0x5A3C1C))
    c.line(14, 5, 18, 1, rgb(0x5A3C1C))
    c.rect(18, 2, 4, 2, rgb(0xB02A20))
    c.outline()
    c.under_shadow(14, 25.5, 13, 2)


def draw_water(c, f=0):
    c.ell(14, 15, 12, 10, rgb(0x3A6E9A))
    c.ell(13, 13, 9, 6, rgb(0x4E86B4))
    c.rect(9, 11, 5, 1, rgb(0x9CC8E4))
    c.rect(16, 16, 4, 1, rgb(0x9CC8E4))
    c.outline(rgb(0x2C4A2A))


def draw_pad(c, f=0):
    # Stone footing that shows a defender is yours.
    c.ell(14, 21, 11, 5.5, rgb(0x6C6A62))
    c.ell(14, 20, 10, 4.5, rgb(0x8C8A82))
    c.ell(14, 20, 8, 3.2, rgb(0x9C9A90))


def draw_rock_shot(c, f):
    c.ell(4, 4, 3, 3, rgb(0x7E7E78))
    c.rect(2 + f % 2, 2, 2, 1, rgb(0xB4B4AC))
    c.outline()


def draw_orb(c, f):
    r = (2.6, 3.2)[f]
    c.ell(4, 4, r, r, rgb(0x8030C0))
    c.ell(4, 4, r - 1.2, r - 1.2, rgb(0xE0A0FF))
    c.px(3, 3, rgb(0xFFFFFF))


def draw_blast(c, f):
    # 28 x 28 expanding purple ring for splash hits.
    r = (5, 9, 12)[f]
    col = (rgb(0xE0A0FF), rgb(0xB060E0), rgb(0x7A3AB0))[f]
    for i in range(0, 360, 12):
        a = math.radians(i)
        for t in range(f + 1):
            c.px(14 + math.cos(a) * (r - t), 14 + math.sin(a) * (r - t) * 0.8, col)


def draw_dust(c, f):
    # 28 x 28 death puff, shared by all enemies. Blobs drift apart and shrink.
    spread = (2, 5, 8)[f]
    size = (4, 3, 1.6)[f]
    col = (rgb(0xE0D8C8), rgb(0xB8B0A0), rgb(0x8A8274))[f]
    for dx, dy in ((-1, -0.6), (1, -0.6), (-0.7, 0.7), (0.8, 0.6), (0, -1)):
        c.ell(14 + dx * spread, 16 + dy * spread * 0.7, size, size * 0.9, col)


def draw_coin(c, f=0):
    c.ell(6, 6, 5, 5, rgb(0xC89820))
    c.ell(6, 6, 3.6, 3.6, rgb(0xE8C030))
    c.rect(5, 3, 2, 6, rgb(0xB08010))
    c.rect(4, 3, 2, 1, rgb(0xFFF080))
    c.outline()


def draw_heart(c, f=0):
    c.ell(4, 4, 3, 3, rgb(0xC02820))
    c.ell(8, 4, 3, 3, rgb(0xC02820))
    c.poly([(1, 5), (11, 5), (6, 11)], rgb(0xC02820))
    c.rect(3, 2, 2, 2, rgb(0xF08070))
    c.outline()


def draw_lock(c, f=0):
    c.rect(3, 1, 6, 5, rgb(0x8C8A82))
    c.rect(5, 3, 2, 3, CLEAR)
    c.rect(2, 5, 8, 6, rgb(0xB8922E))
    c.rect(2, 5, 8, 1, rgb(0xE8C850))
    c.rect(5, 7, 2, 2, OUTLINE)
    c.outline()


# ---- Expansion roster: humanoids on the existing rig ---------------------------

def robed(c, f, robe, robe2, trim, skin, orb, orb_hi, hood=True, staff=rgb(0x5A3C1C), beard=rgb(0x8A8A90), eye=None):
    """A long-robed caster. Frames 2 and 3 raise the staff and swell its orb."""
    bob = (0, -1, -1, 0)[f]
    sx, sy = 14 + (0, 0, -1, 1)[f], 11 + bob
    c.poly([(sx - 4, sy), (sx + 4, sy), (21, 25), (7, 25)], robe)
    c.poly([(sx - 4, sy), (sx - 1, sy), (13, 25), (7, 25)], robe2)
    c.rect(8, 23, 13, 1, trim)
    c.line(sx + 1, sy + 1, sx + 1, 22, trim)
    hx, hy = sx, sy - 4
    if hood:
        c.ell(hx, hy, 5, 5, robe2)
        c.poly([(hx - 5, hy - 2), (hx - 1, hy - 9), (hx + 4, hy - 3)], robe)
    c.rect(hx - 1, hy - 2, 5, 5, skin)
    c.rect(hx + 1, hy - 1, 2, 1, eye or OUTLINE)
    c.rect(hx - 1, hy + 2, 4, 2, beard)
    tx, ty = ((sx + 7, sy + 2), (sx + 7, sy + 1), (sx + 6, sy - 3), (sx + 9, sy + 2))[f]
    limb(c, sx + 1, sy + 3, tx, ty, 2, robe)
    c.line(tx, ty - 7, tx, ty + 10, staff)
    r = (1.6, 1.6, 2.4, 3.2)[f]
    c.ell(tx, ty - 8, r, r, orb)
    c.px(tx - 1, ty - 9, orb_hi)
    c.outline()
    c.under_shadow(14, 25.5, 8, 2.5)


def draw_saradomin_wizard(c, f):
    robed(c, f, rgb(0xE8E8F0), rgb(0xB8B8D0), rgb(0x2E5CC8), rgb(0xD8B090), rgb(0x60A0FF), rgb(0xFFFFFF),
          staff=rgb(0xD8B030), beard=rgb(0xE8E8E8))


def draw_skeleton_mage(c, f):
    bone = rgb(0xDCD8C4)
    robed(c, f, rgb(0x3E3A34), rgb(0x2A2622), rgb(0x6E8A3A), bone, rgb(0x70D040), rgb(0xE0FFC0),
          beard=bone, eye=rgb(0x60E040))


def draw_elven_archer(c, f):
    bob = (0, -1, 0, 1)[f]
    draw = (0, 0, 6, 0)[f]
    pal = dict(skin=rgb(0xE8D0B8), skin2=rgb(0xC8B098), body=rgb(0x3A7A8A), body2=rgb(0x2A5A68),
               legs=rgb(0xB8BCC8), legs2=rgb(0x90949E), boot=rgb(0x44505A), arm=rgb(0x3A7A8A))
    sx, sy = biped(c, pal, ground=25, height=1.08, bob=bob, swing=(1, 1, 2, 2)[f], lean=(0, 0, -1, 1)[f], width=0.9)
    hx, hy = sx, sy - 4
    c.poly([(hx - 3, hy - 1), (hx - 8, hy - 4), (hx - 3, hy + 1)], pal["skin2"])
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    # Long pale hair and a slim circlet.
    c.poly([(hx - 5, hy + 5), (hx - 5, hy - 4), (hx + 3, hy - 5), (hx + 3, hy - 3), (hx - 2, hy - 2), (hx - 2, hy + 5)], rgb(0xE8E0A8))
    c.rect(hx - 4, hy - 4, 8, 1, rgb(0x60D0E0))
    c.rect(hx + 2, hy - 1, 2, 1, OUTLINE)
    bx, by = sx + 6, sy + 2
    c.line(bx - 1, by - 10, bx + 3, by - 3, rgb(0x70E0F0), 2)
    c.line(bx + 3, by - 3, bx + 3, by + 3, rgb(0xB0F0FF), 2)
    c.line(bx + 3, by + 3, bx - 1, by + 10, rgb(0x70E0F0), 2)
    string_x = bx - 1 - draw
    c.line(bx - 1, by - 10, string_x, by, rgb(0xFFFFFF))
    c.line(bx - 1, by + 10, string_x, by, rgb(0xFFFFFF))
    limb(c, sx, sy + 2, bx, by, 2, pal["skin2"])
    if f == 2:
        c.line(string_x, by, bx + 9, by, rgb(0xB0F0FF))
    limb(c, sx - 1, sy + 3, string_x, by, 2, pal["arm"])
    c.outline()
    c.under_shadow(14, 25.5, 7, 2.5)


def draw_knight(c, f):
    bob = (0, -1, -1, 1)[f]
    deg = (-55, -60, -125, 15)[f]
    lean = (0, 0, -1, 2)[f]
    plate, plate2 = rgb(0xC4C8D0), rgb(0x8E929A)
    pal = dict(skin=plate, skin2=plate2, body=rgb(0x2C4E9A), body2=rgb(0x1E3672),
               legs=plate, legs2=plate2, boot=rgb(0x6A6E74), arm=plate)
    sx, sy = biped(c, pal, ground=25, height=1.1, bob=bob, swing=(1, 1, 0, 3)[f], lean=lean, width=1.2)
    c.rect(sx - 4, sy, 9, 2, plate)
    c.rect(sx - 1, sy + 3, 2, 6, rgb(0xE8C850))
    hx, hy = sx, sy - 4
    head(c, hx, hy, 4, plate, plate2)
    c.rect(hx, hy - 1, 4, 2, OUTLINE)
    c.px(hx + 2, hy - 1, plate2)
    c.rect(hx - 1, hy - 6, 2, 2, rgb(0xE8C850))
    c.poly([(hx - 1, hy - 6), (hx - 7, hy - 8), (hx - 6, hy - 3), (hx - 1, hy - 4)], rgb(0x2C4E9A))
    ax, ay = polar(sx + 1, sy + 2, 5, (10, 10, -40, 5)[f])
    sword(c, ax, ay, deg, 13, blade=rgb(0xE8ECF4), edge=rgb(0xA8AEB8), hilt=rgb(0xE8C850))
    limb(c, sx + 1, sy + 2, ax, ay, 2, pal["arm"])
    # Kite shield with the blue field.
    c.poly([(sx - 7, sy + 1), (sx - 2, sy + 1), (sx - 2, sy + 6), (sx - 4.5, sy + 10), (sx - 7, sy + 6)], rgb(0xE8E8E8))
    c.poly([(sx - 6, sy + 2), (sx - 3, sy + 2), (sx - 3, sy + 6), (sx - 4.5, sy + 8), (sx - 6, sy + 6)], rgb(0x2C4E9A))
    c.outline()
    c.under_shadow(14, 25.5, 8, 2.5)


def draw_ice_warrior(c, f):
    bob = (0, -1, -1, 1)[f]
    deg = (-55, -60, -125, 15)[f]
    pal = dict(skin=rgb(0xA8DCF0), skin2=rgb(0x70B0D0), body=rgb(0x5898C0), body2=rgb(0x3E7498),
               legs=rgb(0x88C4E0), legs2=rgb(0x5898C0), boot=rgb(0x3E7498), arm=rgb(0xA8DCF0))
    sx, sy = biped(c, pal, ground=25, height=1.05, bob=bob, swing=(1, 1, 0, 3)[f], lean=(0, 0, -1, 2)[f], width=1.2)
    hx, hy = sx, sy - 4
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    c.rect(hx + 1, hy - 1, 2, 2, rgb(0xFFFFFF))
    for off in (-4, -1, 2):
        c.poly([(hx + off, hy - 4), (hx + off + 1, hy - 9), (hx + off + 3, hy - 4)], rgb(0xD8F4FF))
    ax, ay = polar(sx + 1, sy + 2, 5, (10, 10, -40, 5)[f])
    sword(c, ax, ay, deg, 12, blade=rgb(0xD8F4FF), edge=rgb(0x70B0D0), hilt=rgb(0x3E7498))
    limb(c, sx + 1, sy + 2, ax, ay, 2, pal["arm"])
    c.outline()
    c.under_shadow(14, 25.5, 8, 2.5)


def wings(c, x, y, s, spread, col, col2):
    """A pair of bat wings behind a body, raised or lowered by spread."""
    for side, tone in ((-1, col2), (1, col)):
        tip = (x + side * 11 * s, y - spread * s)
        c.poly([(x, y), tip, (x + side * 9 * s, y + 3 * s), (x + side * 6 * s, y + 2 * s),
                (x + side * 5 * s, y + 7 * s), (x, y + 5 * s)], tone)


def draw_lesser_demon(c, f):
    bob = (0, -1, -1, 1)[f]
    pal = dict(skin=rgb(0xB83828), skin2=rgb(0x8A2418), body=rgb(0xB83828), body2=rgb(0x8A2418),
               legs=rgb(0xB83828), legs2=rgb(0x8A2418), boot=rgb(0x3A1810), arm=rgb(0xC84838))
    wings(c, 13, 9 + bob, 0.8, (5, 7, 8, 3)[f], rgb(0x6E1C14), rgb(0x54140E))
    c.line(10, 19 + bob, 5, 23 + bob, pal["skin2"], 2)
    sx, sy = biped(c, pal, ground=25, height=0.95, bob=bob, swing=(1, 1, 0, 2)[f], lean=(0, 0, -1, 1)[f], width=1.1)
    hx, hy = sx, sy - 4
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    c.poly([(hx - 4, hy - 3), (hx - 6, hy - 8), (hx - 2, hy - 4)], rgb(0xE8E0C8))
    c.poly([(hx + 2, hy - 4), (hx + 5, hy - 8), (hx + 4, hy - 3)], rgb(0xE8E0C8))
    c.rect(hx + 1, hy - 1, 2, 1, rgb(0xFFE040))
    c.rect(hx, hy + 2, 4, 1, OUTLINE)
    hxx, hyy = polar(sx + 1, sy + 3, (5, 5, 3, 7)[f], (15, 15, -60, 0)[f])
    limb(c, sx + 1, sy + 3, hxx, hyy, 2, pal["arm"])
    r = (1.5, 1.5, 2.5, 3)[f]
    c.ell(hxx + 1, hyy - 1, r, r, rgb(0xFF9020))
    c.px(hxx + 1, hyy - 1, rgb(0xFFF080))
    c.outline()
    c.under_shadow(14, 25.5, 8, 2.5)


def draw_abyssal_demon(c, f):
    bob = (0, -1, -1, 0)[f]
    body, body2 = rgb(0x4A2A6E), rgb(0x321C4E)
    y = 13 + bob
    # It floats, trailing a tapering tail instead of legs.
    c.poly([(9, y + 6), (19, y + 6), (17, 23), (14, 25), (11, 22)], body2)
    c.ell(14, y + 3, 7, 7, body)
    c.ell(12, y + 2, 4, 5, body2)
    c.poly([(9, y - 2), (6, y - 8), (12, y - 4)], rgb(0x7A4AA0))
    c.poly([(19, y - 2), (22, y - 8), (16, y - 4)], rgb(0x7A4AA0))
    c.rect(11, y - 1, 2, 2, rgb(0xFF4040))
    c.rect(16, y - 1, 2, 2, rgb(0xFF4040))
    c.rect(12, y + 3, 5, 1, OUTLINE)
    # Long clawed arms lash out on the strike frame.
    reach = (6, 6, 3, 11)[f]
    angle = (30, 25, -40, 0)[f]
    ax, ay = polar(19, y + 3, reach, angle)
    c.line(19, y + 3, ax, ay, body, 2)
    for spread in (-30, 0, 30):
        tx, ty = polar(ax, ay, 3, angle + spread)
        c.line(ax, ay, tx, ty, rgb(0xE8E0F0))
    bx, by = polar(9, y + 3, 5, 150)
    c.line(9, y + 3, bx, by, body2, 2)
    if f == 3:
        for r in range(0, 360, 60):
            px_, py_ = polar(14, y + 3, 11, r)
            c.px(px_, py_, rgb(0xC080FF))
    c.outline()
    c.under_shadow(14, 25.5, 6, 2)


def draw_gargoyle(c, f):
    bob = (0, -1, -1, 1)[f]
    stone, stone2 = rgb(0x8C8E90), rgb(0x66686A)
    pal = dict(skin=stone, skin2=stone2, body=stone, body2=stone2, legs=stone, legs2=stone2, boot=rgb(0x4E5052), arm=rgb(0x9E9EA0))
    wings(c, 13, 9 + bob, 0.9, (4, 6, 9, 2)[f], rgb(0x6E7072), rgb(0x54565A))
    sx, sy = biped(c, pal, ground=25, height=0.85, bob=bob, swing=(2, 2, 1, 3)[f], lean=(1, 1, 0, 3)[f], width=1.3)
    hx, hy = sx + 1, sy - 3
    head(c, hx, hy, 4, stone, stone2)
    c.poly([(hx - 3, hy - 3), (hx - 5, hy - 8), (hx - 1, hy - 4)], stone2)
    c.poly([(hx + 1, hy - 4), (hx + 4, hy - 8), (hx + 3, hy - 3)], stone)
    c.rect(hx + 1, hy - 1, 2, 1, rgb(0xF0B040))
    c.rect(hx + 1, hy + 2, 4, 1, OUTLINE)
    ax, ay = polar(sx + 1, sy + 3, (5, 5, 3, 7)[f], (20, 20, -70, 10)[f])
    limb(c, sx + 1, sy + 3, ax, ay, 3, pal["arm"])
    c.rect(ax, ay - 1, 3, 3, rgb(0xC0C0C0))
    c.outline()
    c.under_shadow(14, 25.5, 8, 2.5)


# ---- Monsters -------------------------------------------------------------------

def draw_zombie(c, f):
    swing = (3, 0, -3, 0)[f]
    bob = (0, 1, 0, 1)[f]
    pal = dict(skin=rgb(0x8AA078), skin2=rgb(0x6A8058), body=rgb(0x7E6A52), body2=rgb(0x5E4E3C),
               legs=rgb(0x6E6458), legs2=rgb(0x544C42), boot=rgb(0x3A3028), arm=rgb(0x8AA078))
    sx, sy = biped(c, pal, ground=25, height=1.0, bob=bob, swing=swing, lean=1, width=1.25)
    # Rusted plates, breastplate and helm: this is the armour that makes it awkward to kill.
    c.rect(sx - 3, sy + 1, 7, 5, rgb(0x8E7A62))
    c.rect(sx - 3, sy + 1, 7, 1, rgb(0xB89A72))
    c.px(sx + 2, sy + 3, rgb(0xA05828))
    c.px(sx - 2, sy + 4, rgb(0xA05828))
    hx, hy = sx + 1, sy - 4
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    c.poly([(hx - 5, hy - 1), (hx - 4, hy - 5), (hx + 4, hy - 5), (hx + 5, hy - 1)], rgb(0x8E7A62))
    c.rect(hx - 5, hy - 1, 10, 1, rgb(0xA05828))
    c.rect(hx + 1, hy, 2, 2, rgb(0xE8E060))
    c.rect(hx, hy + 3, 4, 1, OUTLINE)
    # Arms reach forward, the classic shamble.
    limb(c, sx + 1, sy + 2, sx + 8, sy + 2 + (0, 1, 0, 1)[f], 2, pal["arm"])
    limb(c, sx, sy + 4, sx + 7, sy + 5 - (0, 1, 0, 1)[f], 2, pal["skin2"])
    c.outline()
    c.under_shadow(14, 25.5, 7, 2.5)


def draw_basilisk(c, f):
    hide, hide2, belly = rgb(0x5E7A48), rgb(0x445A34), rgb(0xA8B078)
    y = 17 + (0, -1, 0, -1)[f]
    wag = (0, 1, 0, -1)[f]
    c.poly([(9, y - 1), (1, y + 3 + wag), (9, y + 4)], hide2)
    for i, lx in enumerate((8, 12, 16, 19)):
        off = (2, -1)[(i + f) % 2]
        c.line(lx, y + 3, lx + off, 24, hide2, 2)
    c.ell(13, y, 7, 5, hide)
    c.poly([(7, y + 1), (19, y + 1), (17, y + 5), (9, y + 5)], belly)
    # Spined back.
    for sx_ in (8, 11, 14):
        c.poly([(sx_, y - 4), (sx_ + 1, y - 8), (sx_ + 3, y - 4)], rgb(0xB84830))
    c.poly([(18, y - 4), (26, y - 2), (26, y + 2), (18, y + 3)], hide)
    # A huge glaring eye: the part to be afraid of.
    c.ell(21, y - 2, 2.6, 2.6, rgb(0xF0E050))
    c.rect(21, y - 3, 1, 3, OUTLINE)
    c.rect(22, y + 1, 4, 1, OUTLINE)
    c.outline()
    c.under_shadow(14, 25, 10, 2)


def draw_hellhound(c, f):
    fur, fur2 = rgb(0x6E2A1C), rgb(0x4A1A12)
    y = 15 + (0, -1, 0, -1)[f]
    lift = (2, -2, 2, -2)[f]
    c.line(7, y, 2, y - 5, rgb(0xE86020), 2)
    c.px(2, y - 6, rgb(0xFFE060))
    c.line(9, y + 3, 7 + lift, 24, fur2, 2)
    c.line(12, y + 3, 12 - lift, 24, fur, 2)
    c.line(17, y + 3, 15 + lift, 24, fur2, 2)
    c.line(20, y + 3, 20 - lift, 24, fur, 2)
    c.ell(14, y + 1, 8, 4, fur)
    c.poly([(6, y - 1), (14, y - 2), (12, y + 4), (7, y + 3)], fur2)
    for sx_ in range(8, 19, 3):
        c.px(sx_, y - 3, rgb(0xE86020))
    c.poly([(19, y - 3), (22, y - 8), (27, y - 2), (26, y + 2), (20, y + 2)], fur)
    c.poly([(19, y - 3), (20, y - 8), (22, y - 5)], fur2)
    c.px(23, y - 3, rgb(0xFFE040))
    c.rect(23, y, 4, 1, rgb(0xE8E4D0))
    c.outline()
    c.under_shadow(14, 25, 10, 2)


def draw_ankou(c, f):
    robe, robe2, bone = rgb(0x4E5A78), rgb(0x34405A), rgb(0xD8E0E8)
    bob = (0, -1, -2, -1)[f]
    y = 11 + bob
    # Floating, with a tattered hem that ripples as it drifts.
    hem = 22 + bob
    c.poly([(9, y), (19, y), (21, hem), (7, hem)], robe)
    c.poly([(9, y), (13, y), (12, hem), (7, hem)], robe2)
    for i, x in enumerate(range(7, 21, 3)):
        c.rect(x, hem, 2, 2 + (i + f) % 2, robe2 if i % 2 else robe)
    c.ell(14, y - 4, 5, 5, robe2)
    head(c, 15, y - 4, 3, bone, rgb(0xA8B0B8))
    c.px(15, y - 5, rgb(0x60E0F0))
    c.px(17, y - 5, rgb(0x60E0F0))
    c.rect(15, y - 2, 3, 1, OUTLINE)
    tx, ty = 21, y + 1
    c.line(17, y + 2, tx, ty, bone, 2)
    c.line(tx, ty - 8, tx, ty + 9, rgb(0x6E5A3A))
    c.poly([(tx, ty - 8), (tx + 6, ty - 6), (tx + 2, ty - 5)], rgb(0xB8C0C8))
    c.outline()
    c.under_shadow(14, 25.5, 7, 2)


# ---- Big bodies: one brute rig and one dragon rig, scaled to their footprint --------

def brute(c, s, pal, bob, swing, lean=0):
    """A huge humanoid, drawn in a 40 unit cell scaled by s. Returns its key anchors."""
    cx, ground = 19 * s, 37 * s
    hip_y = 24 * s + bob
    top = 10 * s + bob
    tw = 8.5 * s
    limb(c, cx - 3 * s, hip_y, cx - 3 * s - swing, ground - 1, 5 * s, pal["legs2"])
    c.rect(cx - 5 * s - swing, ground - 2 * s, 7 * s, 2 * s, pal["boot"])
    limb(c, cx + 3 * s, hip_y, cx + 3 * s + swing, ground - 1, 5 * s, pal["legs"])
    c.rect(cx + 1 * s + swing, ground - 2 * s, 7 * s, 2 * s, pal["boot"])
    c.poly([(cx - tw + lean, top), (cx + tw + lean, top), (cx + 7 * s, hip_y), (cx - 7 * s, hip_y)], pal["body"])
    c.poly([(cx - tw + lean, top), (cx - s + lean, top), (cx - s, hip_y), (cx - 7 * s, hip_y)], pal["body2"])
    return dict(cx=cx, ground=ground, hip=hip_y, top=top, s=s,
                hx=cx + 2 * s + lean, hy=6 * s + bob,
                rs=(cx + 7 * s + lean, top + 2 * s), ls=(cx - 7 * s + lean, top + 2 * s))


def draw_moss_giant(c, f):
    s = 1.4
    bob = (0, -1, -2, 2)[f]
    pal = dict(skin=rgb(0x8AA050), skin2=rgb(0x6A8038), body=rgb(0x8AA050), body2=rgb(0x6A8038),
               legs=rgb(0x8AA050), legs2=rgb(0x6A8038), boot=rgb(0x4E5A28), arm=rgb(0x8AA050))
    a = brute(c, s, pal, bob, (1, 1, 0, 3)[f], (0, 0, -2, 3)[f])
    c.poly([(a["cx"] - 9, a["hip"] - 4), (a["cx"] + 9, a["hip"] - 4), (a["cx"] + 10, a["hip"] + 6), (a["cx"] - 10, a["hip"] + 6)], rgb(0x5A4428))
    # Moss clumps grow across the shoulders and head.
    for dx, dy in ((-8, 2), (-2, 0), (6, 3), (9, 8), (-6, 9)):
        c.ell(a["cx"] + dx, a["top"] + dy, 3, 2, rgb(0x4E7A28))
    hx, hy = a["hx"], a["hy"]
    head(c, hx, hy, 7, pal["skin"], pal["skin2"])
    c.ell(hx - 1, hy - 7, 6, 3, rgb(0x4E7A28))
    c.rect(hx + 2, hy - 2, 3, 2, rgb(0xE8E4D0))
    c.px(hx + 3, hy - 1, OUTLINE)
    c.rect(hx + 1, hy + 3, 5, 1, OUTLINE)
    rx, ry = a["rs"]
    ang = (-50, -55, -120, 30)[f]
    hand = polar(rx, ry, 11, (20, 20, -60, 30)[f])
    club(c, hand[0], hand[1], ang, 17, w=5)
    limb(c, rx, ry, hand[0], hand[1], 5, pal["arm"])
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 3, a["ls"][1] + 15, 5, pal["skin2"])
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 17, 3.5)


def draw_fire_giant(c, f):
    s = 1.4
    bob = (0, -1, -2, 2)[f]
    pal = dict(skin=rgb(0xC85A28), skin2=rgb(0x9A3E18), body=rgb(0xC85A28), body2=rgb(0x9A3E18),
               legs=rgb(0xC85A28), legs2=rgb(0x9A3E18), boot=rgb(0x3A1C10), arm=rgb(0xD86A30))
    a = brute(c, s, pal, bob, (1, 1, 0, 3)[f], (0, 0, -2, 3)[f])
    c.poly([(a["cx"] - 9, a["hip"] - 3), (a["cx"] + 9, a["hip"] - 3), (a["cx"] + 10, a["hip"] + 5), (a["cx"] - 10, a["hip"] + 5)], rgb(0x3A2418))
    for dx, dy in ((-5, 5), (4, 8), (-1, 13)):
        c.line(a["cx"] + dx, a["top"] + dy, a["cx"] + dx + 3, a["top"] + dy + 2, rgb(0xFFC040))
    hx, hy = a["hx"], a["hy"]
    # Hair of flame, flickering frame to frame.
    for i, dx in enumerate((-6, -2, 2, 6)):
        h = 6 + (i + f) % 3 * 2
        c.poly([(hx + dx - 2, hy - 4), (hx + dx, hy - 5 - h), (hx + dx + 2, hy - 4)], rgb(0xFF8020) if i % 2 else rgb(0xFFC040))
    head(c, hx, hy, 6, pal["skin"], pal["skin2"])
    c.rect(hx + 1, hy - 2, 3, 2, rgb(0xFFF080))
    c.rect(hx, hy + 2, 5, 1, OUTLINE)
    rx, ry = a["rs"]
    hand = polar(rx, ry, 11, (20, 20, -60, 30)[f])
    sword(c, hand[0], hand[1], (-55, -60, -120, 25)[f], 24, blade=rgb(0xFFA030), edge=rgb(0xC85A18), hilt=rgb(0x3A1C10))
    limb(c, rx, ry, hand[0], hand[1], 5, pal["arm"])
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 3, a["ls"][1] + 15, 5, pal["skin2"])
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 17, 3.5)


def draw_sol_heredit(c, f):
    s = 1.4
    bob = (0, -1, -2, 2)[f]
    gold, gold2 = rgb(0xD8A838), rgb(0xA87C20)
    pal = dict(skin=rgb(0xC89868), skin2=rgb(0xA07A50), body=gold, body2=gold2,
               legs=rgb(0xC89868), legs2=rgb(0xA07A50), boot=gold2, arm=rgb(0xC89868))
    a = brute(c, s, pal, bob, (1, 1, 0, 4)[f], (0, 0, -2, 4)[f])
    # Pleated war-skirt, and a radiant sun on the chest.
    for i in range(-3, 4):
        c.rect(a["cx"] + i * 3, a["hip"] - 2, 2, 8, gold if i % 2 else gold2)
    c.ell(a["cx"] + 1, a["top"] + 8, 3, 3, rgb(0xFFF080))
    hx, hy = a["hx"], a["hy"]
    head(c, hx, hy, 6, pal["skin"], pal["skin2"])
    # Crested helm with a sweeping red plume.
    c.poly([(hx - 7, hy - 1), (hx - 6, hy - 7), (hx + 6, hy - 7), (hx + 7, hy - 1), (hx + 2, hy - 1), (hx + 2, hy + 3), (hx - 3, hy + 5), (hx - 7, hy + 3)], gold)
    c.poly([(hx - 5, hy - 7), (hx - 16, hy - 3), (hx - 14, hy - 11), (hx - 2, hy - 12), (hx + 5, hy - 7)], rgb(0xB82820))
    c.rect(hx + 2, hy, 3, 1, OUTLINE)
    # Round shield on the off arm.
    lx, ly = a["ls"]
    c.ell(lx - 2, ly + 9, 8, 9, gold2)
    c.ell(lx - 2, ly + 9, 6, 7, gold)
    c.ell(lx - 2, ly + 9, 2, 2, rgb(0xFFF080))
    rx, ry = a["rs"]
    hand = polar(rx, ry, (9, 9, 6, 13)[f], (15, 15, -30, 0)[f])
    spear(c, hand[0], hand[1], (-70, -72, -40, 0)[f], 22)
    limb(c, rx, ry, hand[0], hand[1], 4, pal["arm"])
    if f == 3:
        for d in range(0, 3):
            c.px(hand[0] + 24 - d * 3, hand[1] - 3 - d, rgb(0xFFF080))
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 17, 3.5)


def draw_ent(c, f):
    bark, bark2 = rgb(0x6E5230), rgb(0x523C20)
    bob = (0, -1, -1, 1)[f]
    base = 52
    # Root feet, trunk, and bark grooves.
    for i, dx in enumerate((-14, -5, 6, 15)):
        c.line(28, 38, 28 + dx, base - (i + f) % 2, bark2, 4)
    c.poly([(19, 18 + bob), (37, 18 + bob), (39, 48), (17, 48)], bark)
    c.poly([(19, 18 + bob), (27, 18 + bob), (27, 48), (17, 48)], bark2)
    for y in (24, 34, 42):
        c.line(23, y + bob, 25, y + 6 + bob, rgb(0x3E2C16))
        c.line(33, y + bob, 32, y + 5 + bob, rgb(0x523C20))
    # Face, carved into the trunk.
    c.rect(24, 24 + bob, 4, 3, rgb(0x1E1408))
    c.rect(31, 24 + bob, 4, 3, rgb(0x1E1408))
    c.px(26, 25 + bob, rgb(0xFFE040))
    c.px(33, 25 + bob, rgb(0xFFE040))
    c.rect(26, 32 + bob, 8, 2, rgb(0x1E1408))
    # Branch arms; the strike frame lashes one out at ground level.
    c.line(20, 26 + bob, 8, (22, 20, 12, 22)[f] + bob, bark2, 3)
    arm = ((36, 26, 48, 20), (36, 26, 48, 18), (36, 26, 46, 8), (36, 28, 52, 38))[f]
    c.line(arm[0], arm[1] + bob, arm[2], arm[3] + bob, bark, 3)
    # Canopy.
    c.ell(28, 12 + bob, 19, 11, rgb(0x3E6E24))
    c.ell(18, 15 + bob, 10, 7, rgb(0x2E5A1C))
    c.ell(37, 8 + bob, 9, 6, rgb(0x56882E))
    c.ell(26, 6 + bob, 9, 5, rgb(0x4A7C28))
    c.outline()
    c.under_shadow(28, 52, 21, 3)


def dragon(c, s, pal, f, walking=False, heads=1, ribs=False, tattered=False, breath=None):
    """A side-on dragon in a 56 unit cell scaled by s. Frame 3 of an attacker breathes."""
    ground = 52 * s
    bob = (0, -1, 0, -1)[f] * s if walking else (0, -1, -1, 1)[f] * s
    wing_up = ((16, 22, 14, 8) if walking else (16, 20, 26, 8))[f] * s
    body_y = 33 * s + bob
    # Tail sweeps behind, behind everything else.
    wag = (0, 2, 0, -2)[f] * s
    c.poly([(16 * s, body_y - 4 * s), (2 * s, body_y + 4 * s + wag), (1 * s, body_y + 9 * s + wag), (16 * s, body_y + 6 * s)], pal["hide2"])
    # Far wing.
    c.poly([(26 * s, body_y - 5 * s), (10 * s, body_y - wing_up - 6 * s), (6 * s, body_y - 6 * s), (22 * s, body_y + 2 * s)], pal["wing2"])
    # Far legs, then near legs. When walking they swing in opposite pairs.
    swing = (4, 0, -4, 0)[f] * s if walking else 0
    for x, sw in ((17, -swing), (33, swing)):
        c.line(x * s, body_y + 4 * s, x * s + sw, ground - 1, pal["hide2"], 4 * s)
    c.ell(25 * s, body_y, 15 * s, 9.5 * s, pal["hide"])
    if ribs:
        for x in range(16, 35, 4):
            c.line(x * s, body_y - 6 * s, x * s + 1 * s, body_y + 6 * s, rgb(0x20283A), max(1, s))
    else:
        c.poly([(13 * s, body_y + 3 * s), (37 * s, body_y + 3 * s), (34 * s, body_y + 9 * s), (17 * s, body_y + 9 * s)], pal["belly"])
    for x, sw in ((21, swing), (37, -swing)):
        c.line(x * s, body_y + 4 * s, x * s + sw, ground - 1, pal["hide"], 4 * s)
        c.rect(x * s + sw - 2 * s, ground - 2 * s, 6 * s, 2 * s, pal["claw"])
    # Neck and head; extra heads fan out behind the first.
    jaw_open = f == 3 and not walking
    for h in range(heads - 1, -1, -1):
        nx = (41 - h * 10) * s
        ny = (18 - h * 5 + (2 if h else 0)) * s + bob
        c.line(33 * s, body_y - 3 * s, nx - 2 * s, ny + 4 * s, pal["hide"] if h == 0 else pal["hide2"], 5 * s)
        tilt = (0, 0, -2, 2)[f] * s if not walking else 0
        snout = [(nx - 5 * s, ny - 4 * s + tilt), (nx + 3 * s, ny - 5 * s + tilt), (nx + 9 * s, ny - 1 * s + tilt), (nx + 8 * s, ny + 1 * s + tilt), (nx - 5 * s, ny + 3 * s + tilt)]
        c.poly(snout, pal["hide"])
        c.poly([(nx - 5 * s, ny - 4 * s + tilt), (nx - 10 * s, ny - 9 * s + tilt), (nx - 3 * s, ny - 4 * s + tilt)], pal["horn"])
        c.poly([(nx - 3 * s, ny - 5 * s + tilt), (nx - 6 * s, ny - 11 * s + tilt), (nx, ny - 5 * s + tilt)], pal["horn"])
        c.rect(nx + 1 * s, ny - 3 * s + tilt, max(2, 2 * s), max(1, 1.5 * s), pal["eye"])
        gape = 4 * s if jaw_open else 1 * s
        c.poly([(nx - 3 * s, ny + 3 * s + tilt), (nx + 7 * s, ny + 2 * s + tilt + gape), (nx + 1 * s, ny + 5 * s + tilt + gape)], pal["hide2"])
        if jaw_open and breath and h == 0:
            c.poly([(nx + 7 * s, ny + 1 * s + tilt), (55 * s, ny - 7 * s + tilt), (55.9 * s, ny + 9 * s + tilt)], breath[0])
            c.poly([(nx + 7 * s, ny + 1 * s + tilt), (52 * s, ny - 2 * s + tilt), (53 * s, ny + 5 * s + tilt)], breath[1])
    # Near wing goes on top, its membrane torn on the undead ones.
    tips = [(28 * s, body_y - 5 * s), (12 * s, body_y - wing_up - 8 * s), (9 * s, body_y - 4 * s)]
    if tattered:
        tips += [(14 * s, body_y - 9 * s), (17 * s, body_y - 2 * s), (20 * s, body_y - 6 * s), (24 * s, body_y + 1 * s)]
    else:
        tips += [(24 * s, body_y + 2 * s)]
    c.poly(tips, pal["wing"])
    c.line(28 * s, body_y - 5 * s, 12 * s, body_y - wing_up - 8 * s, pal["hide2"], max(1, s))
    c.outline()
    c.under_shadow(25 * s, ground + 0.5, 22 * s, 3 * s)


PAL_BLACK_DRAGON = dict(hide=rgb(0x3A3640), hide2=rgb(0x26222C), belly=rgb(0x585060), wing=rgb(0x4E3A4E), wing2=rgb(0x362838),
                        horn=rgb(0xC8C0B0), eye=rgb(0xFF3020), claw=rgb(0xB8B0A0))
PAL_WYVERN = dict(hide=rgb(0x7A9AB0), hide2=rgb(0x587890), belly=rgb(0xC0D8E4), wing=rgb(0x98BCD0), wing2=rgb(0x6E90A8),
                  horn=rgb(0xE8F4FF), eye=rgb(0x40E8FF), claw=rgb(0xD8E8F0))
PAL_VORKATH = dict(hide=rgb(0xA8B0B8), hide2=rgb(0x707A88), belly=rgb(0xC8D0D8), wing=rgb(0x4E6078), wing2=rgb(0x364658),
                   horn=rgb(0xE8E4D0), eye=rgb(0x40C8FF), claw=rgb(0xE8E4D0))
PAL_GREEN = dict(hide=rgb(0x4E8A3A), hide2=rgb(0x386A28), belly=rgb(0xB8C878), wing=rgb(0x5E9A48), wing2=rgb(0x447A34),
                 horn=rgb(0xE8E0C8), eye=rgb(0xFF3020), claw=rgb(0xE8E0C8))
FIRE = (rgb(0xE8601C), rgb(0xFFC840))
FROST = (rgb(0x60B8E8), rgb(0xE0F8FF))
PLAGUE = (rgb(0x70B848), rgb(0x60C8F0))


def draw_black_dragon(c, f):
    dragon(c, 1.0, PAL_BLACK_DRAGON, f, breath=FIRE)


def draw_wyvern(c, f):
    dragon(c, 1.0, PAL_WYVERN, f, tattered=True, breath=FROST)


def draw_vorkath(c, f):
    dragon(c, 1.5, PAL_VORKATH, f, ribs=True, tattered=True, breath=PLAGUE)


def draw_green_dragon(c, f):
    dragon(c, 40 / 56, PAL_GREEN, f, walking=True)


def draw_kbd(c, f):
    dragon(c, 1.0, PAL_BLACK_DRAGON, f, walking=True, heads=3)


def draw_jad(c, f):
    # A molten, hunched beast filling three tiles: massive shoulders, low head.
    s = 2.1
    bob = (0, -1, -3, 3)[f]
    rock, rock2, lava = rgb(0x4A3026), rgb(0x2E1C16), rgb(0xFF8020)
    pal = dict(skin=rock, skin2=rock2, body=rock, body2=rock2, legs=rock, legs2=rock2, boot=rgb(0x1E120E), arm=rock)
    a = brute(c, s, pal, bob, (2, 2, 0, 4)[f], (0, 0, -3, 5)[f])
    cx, top = a["cx"], a["top"]
    # Glowing cracks through the hide.
    for x0, y0, x1, y1 in ((-11, 6, -4, 14), (-4, 14, -8, 24), (4, 4, 10, 16), (10, 16, 5, 26), (-1, 20, 3, 30)):
        c.line(cx + x0, top + y0, cx + x1, top + y1, lava)
    # Boulder shoulders with spikes.
    for side in (-1, 1):
        c.ell(cx + side * 17, top + 4, 10, 8, rock2 if side < 0 else rock)
        for sp in (-5, 1, 6):
            bx = cx + side * 17 + sp
            c.poly([(bx - 2, top - 2), (bx, top - 11), (bx + 3, top - 2)], rgb(0x6A4A3A))
    # Low-slung head, jaws lit from inside.
    hx, hy = cx + 7 + (0, 0, -3, 5)[f], top + 2
    c.poly([(hx - 11, hy - 8), (hx + 9, hy - 9), (hx + 15, hy), (hx + 12, hy + 10), (hx - 9, hy + 11)], rock)
    c.poly([(hx - 11, hy - 8), (hx - 1, hy - 9), (hx - 2, hy + 10), (hx - 9, hy + 11)], rock2)
    c.rect(hx + 2, hy - 4, 6, 3, rgb(0xFFE040))
    gape = (3, 3, 2, 8)[f]
    c.poly([(hx - 3, hy + 3), (hx + 14, hy + 2), (hx + 12, hy + 3 + gape), (hx - 2, hy + 3 + gape)], rgb(0xFFA030))
    for t in range(0, 15, 4):
        c.poly([(hx - 2 + t, hy + 2), (hx + t, hy + 6), (hx + 1 + t, hy + 2)], rgb(0xE8E4D0))
    # Arms, with a magma ball on the strike frames.
    rx, ry = a["rs"]
    hand = polar(rx + 6, ry + 6, 16, (60, 55, -40, 20)[f])
    limb(c, rx + 6, ry + 6, hand[0], hand[1], 8, rock)
    if f >= 2:
        r = (0, 0, 5, 9)[f]
        c.ell(hand[0] + 2, hand[1] - 3, r, r, lava)
        c.ell(hand[0] + 2, hand[1] - 3, r * 0.5, r * 0.5, rgb(0xFFF080))
    lx, ly = a["ls"]
    limb(c, lx - 6, ly + 6, lx - 8, ly + 30, 8, rock2)
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 30, 5)


def draw_zuk(c, f):
    # The biggest thing in the game: an obsidian titan with a charging core.
    s = 2.1
    bob = (0, -1, -3, 3)[f]
    ob, ob2, fire = rgb(0x2A2030), rgb(0x18121E), rgb(0xFF6A18)
    pal = dict(skin=ob, skin2=ob2, body=ob, body2=ob2, legs=ob, legs2=ob2, boot=rgb(0x100C14), arm=ob)
    a = brute(c, s, pal, bob, (1, 1, 0, 2)[f], (0, 0, -1, 2)[f])
    cx, top = a["cx"], a["top"]
    # A blazing core in the chest, which swells through the wind-up.
    r = (5, 5, 8, 11)[f]
    c.ell(cx + 1, top + 16, r + 2, r + 2, rgb(0xB83818))
    c.ell(cx + 1, top + 16, r, r, fire)
    c.ell(cx + 1, top + 16, r * 0.5, r * 0.5, rgb(0xFFF080))
    for x0, y0, x1, y1 in ((-12, 8, -6, 18), (12, 8, 7, 18), (-10, 26, -3, 34), (10, 26, 3, 34)):
        c.line(cx + x0, top + y0, cx + x1, top + y1, fire)
    # Huge pauldrons with rows of spikes.
    for side in (-1, 1):
        c.ell(cx + side * 18, top + 3, 11, 8, ob2 if side < 0 else ob)
        for sp in (-7, -1, 5):
            bx = cx + side * 18 + sp
            c.poly([(bx - 2, top - 2), (bx, top - 13), (bx + 3, top - 2)], rgb(0x4E3A5A))
    # Crowned head with blazing eyes.
    hx, hy = a["hx"], a["hy"] + 2
    head(c, hx, hy, 9, ob, ob2)
    c.rect(hx - 5, hy - 3, 4, 3, fire)
    c.rect(hx + 3, hy - 3, 4, 3, fire)
    c.rect(hx - 4, hy + 4, 10, 2, rgb(0xFFA030))
    for off, tall in ((-8, 10), (-3, 15), (3, 15), (8, 10)):
        c.poly([(hx + off - 3, hy - 8), (hx + off, hy - 8 - tall), (hx + off + 3, hy - 8)], rgb(0x4E3A5A))
    # Both arms rise to hold a growing bolt overhead, then hurl it forward.
    rx, ry = a["rs"]
    lx, ly = a["ls"]
    rh = polar(rx + 7, ry + 5, 19, (70, 65, -80, -10)[f])
    lh = polar(lx - 7, ly + 5, 19, (110, 115, -100, 150)[f])
    limb(c, rx + 7, ry + 5, rh[0], rh[1], 8, ob)
    limb(c, lx - 7, ly + 5, lh[0], lh[1], 8, ob2)
    if f == 2:
        c.ell(hx, hy - 24, 8, 7, fire)
        c.ell(hx, hy - 24, 4, 3.5, rgb(0xFFF080))
    if f == 3:
        c.ell(rh[0] + 2, rh[1], 8, 8, fire)
        c.ell(rh[0] + 2, rh[1], 4, 4, rgb(0xFFF080))
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 30, 5)


def draw_ogre(c, f):
    bob = (0, -1, 0, -1)[f]
    pal = dict(skin=rgb(0x8A9A58), skin2=rgb(0x6A7A40), body=rgb(0x7A5A34), body2=rgb(0x5E4426),
               legs=rgb(0x8A9A58), legs2=rgb(0x6A7A40), boot=rgb(0x4A3A22), arm=rgb(0x8A9A58))
    a = brute(c, 1.0, pal, bob, (4, 0, -4, 0)[f], 1)
    hx, hy = a["hx"], a["hy"] + 2
    head(c, hx, hy, 5, pal["skin"], pal["skin2"])
    c.rect(hx + 1, hy - 2, 3, 2, rgb(0xE8D060))
    c.px(hx + 3, hy - 1, OUTLINE)
    c.rect(hx, hy + 2, 5, 1, OUTLINE)
    c.rect(hx + 1, hy + 1, 1, 2, rgb(0xE8E4D0))
    c.rect(hx + 4, hy + 1, 1, 2, rgb(0xE8E4D0))
    c.rect(hx - 4, hy - 6, 7, 2, rgb(0x3A3020))
    rx, ry = a["rs"]
    club(c, rx + 5, ry + 6, -60 + (0, 6, 0, -6)[f], 14, wood=rgb(0x5A4A2A), knob=rgb(0x8A8A82), w=4)
    limb(c, rx, ry, rx + 5, ry + 6, 4, pal["arm"])
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 3, a["ls"][1] + 11, 4, pal["skin2"])
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 0.5, 13, 3)


def demon_body(c, s, f, skin, skin2, wing, wing2):
    bob = (0, -1, 0, -1)[f]
    pal = dict(skin=skin, skin2=skin2, body=skin, body2=skin2, legs=skin, legs2=skin2, boot=rgb(0x2A100C), arm=skin)
    wings(c, 19 * s, 14 * s + bob, 1.45 * s, (5, 9, 5, 1)[f], wing, wing2)
    a = brute(c, s, pal, bob, (4, 0, -4, 0)[f] if s < 1.2 else (5, 0, -5, 0)[f], s)
    # Whipping tail.
    c.line(a["cx"] - 8 * s, a["hip"], a["cx"] - 16 * s, a["hip"] + (4, 7, 4, 1)[f], skin2, max(2, 2 * s))
    hx, hy = a["hx"], a["hy"] + 2 * s
    head(c, hx, hy, 5 * s, skin, skin2)
    c.poly([(hx - 4 * s, hy - 4 * s), (hx - 9 * s, hy - 11 * s), (hx - 1 * s, hy - 5 * s)], rgb(0xE8E0C8))
    c.poly([(hx + 1 * s, hy - 5 * s), (hx + 7 * s, hy - 11 * s), (hx + 4 * s, hy - 4 * s)], rgb(0xE8E0C8))
    c.rect(hx + 1 * s, hy - 2 * s, 3 * s, 1.5 * s, rgb(0xFFE040))
    c.rect(hx, hy + 2 * s, 5 * s, max(1, s), OUTLINE)
    return a


def draw_greater_demon(c, f):
    a = demon_body(c, 1.0, f, rgb(0xA83228), rgb(0x7E2018), rgb(0x5E1812), rgb(0x46100C))
    rx, ry = a["rs"]
    limb(c, rx, ry, rx + 6, ry + 8, 4, rgb(0xB83C30))
    c.rect(rx + 4, ry + 7, 5, 3, rgb(0xE8E0C8))
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 3, a["ls"][1] + 11, 4, rgb(0x7E2018))
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 0.5, 13, 3)


def draw_kril(c, f):
    # A demon general: darker, broader, carrying a burning greatsword.
    a = demon_body(c, 1.4, f, rgb(0x7A1C24), rgb(0x561218), rgb(0x3A0E18), rgb(0x2A0A12))
    cx, top = a["cx"], a["top"]
    c.poly([(cx - 9, top + 1), (cx + 10, top + 1), (cx + 8, top + 10), (cx - 8, top + 10)], rgb(0x3A3640))
    c.rect(cx - 8, top + 4, 17, 1, rgb(0xC8A030))
    rx, ry = a["rs"]
    hand = polar(rx, ry, 9, 25)
    sword(c, hand[0], hand[1], -65 + (0, 5, 0, -5)[f], 25, blade=rgb(0xFF8020), edge=rgb(0xC83A10), hilt=rgb(0x2A1810))
    limb(c, rx, ry, hand[0], hand[1], 5, rgb(0x8A242C))
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 4, a["ls"][1] + 15, 5, rgb(0x561218))
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 17, 3.5)


# ---- Extra projectiles and bursts -----------------------------------------------

def orb_of(col, core):
    def draw(c, f):
        r = (2.6, 3.2)[f]
        c.ell(4, 4, r, r, col)
        c.ell(4, 4, r - 1.2, r - 1.2, core)
        c.px(3, 3, rgb(0xFFFFFF))
    return draw


def draw_great_bolt(c, f):
    r = (5.2, 6.2, 5.6)[f]
    c.ell(7, 7, r, r, rgb(0xB83818))
    c.ell(7, 7, r - 1.6, r - 1.6, rgb(0xFF6A18))
    c.ell(7, 7, r - 3.4, r - 3.4, rgb(0xFFF080))


def burst_of(cols):
    def draw(c, f):
        r = (5, 9, 12)[f]
        for i in range(0, 360, 12):
            a = math.radians(i)
            for t in range(f + 1):
                c.px(14 + math.cos(a) * (r - t), 14 + math.sin(a) * (r - t) * 0.8, cols[f])
    return draw


# ---- Scenery and creatures for the other maps ------------------------------------

def draw_dead_tree(c, f=0):
    bark, bark2 = rgb(0x4A3E34), rgb(0x332A22)
    c.poly([(11, 34), (17, 34), (16, 14), (12, 14)], bark)
    c.poly([(11, 34), (14, 34), (14, 14), (12, 14)], bark2)
    for x0, y0, x1, y1 in ((13, 20, 4, 11), (15, 16, 24, 6), (14, 14, 11, 3), (8, 15, 4, 18), (20, 11, 25, 13), (15, 24, 22, 18)):
        c.line(x0, y0, x1, y1, bark, 2)
    c.outline()
    c.under_shadow(14, 34, 9, 2)


def draw_swamp(c, f=0):
    c.ell(14, 15, 13, 10, rgb(0x3A4A2A))
    c.ell(13, 14, 10, 7, rgb(0x4E6034))
    c.ell(10, 12, 3, 2, rgb(0x6E8040))
    c.ell(19, 17, 3, 1.5, rgb(0x6E8040))
    c.px(14, 14, rgb(0xA8B868))
    c.outline(rgb(0x1E2416))


def draw_lava(c, f=0):
    c.ell(14, 15, 13, 10, rgb(0xB83818))
    c.ell(14, 15, 10, 7, rgb(0xE86818))
    c.ell(12, 14, 6, 4, rgb(0xFFB030))
    c.ell(11, 13, 2.5, 1.5, rgb(0xFFF080))
    c.outline(rgb(0x1E1410))


def draw_dark_rock(c, f=0):
    c.poly([(5, 24), (3, 17), (8, 9), (17, 7), (24, 13), (25, 24)], rgb(0x3E3238))
    c.poly([(5, 24), (3, 17), (8, 9), (12, 14), (11, 24)], rgb(0x2A2228))
    c.poly([(8, 9), (17, 7), (20, 13), (12, 14)], rgb(0x564850))
    c.line(14, 15, 18, 22, rgb(0xE86818))
    c.outline()
    c.under_shadow(14, 24.5, 12, 2)


def draw_bones(c, f=0):
    bone, bone2 = rgb(0xD8D4C0), rgb(0xA8A490)
    c.ell(11, 16, 4.5, 4, bone)
    c.rect(8, 15, 2, 2, OUTLINE)
    c.rect(12, 15, 2, 2, OUTLINE)
    c.rect(9, 19, 5, 2, bone2)
    c.line(16, 21, 25, 16, bone, 2)
    c.line(17, 15, 24, 22, bone2, 2)
    c.line(3, 23, 9, 24, bone2, 2)
    c.outline()


def draw_cave_mouth(c, f=0):
    c.poly([(2, 26), (4, 10), (10, 4), (19, 4), (25, 11), (26, 26)], rgb(0x3E3238))
    c.poly([(2, 26), (4, 10), (10, 4), (11, 26)], rgb(0x2A2228))
    c.poly([(8, 26), (10, 12), (18, 12), (21, 26)], rgb(0x140C08))
    c.rect(11, 24, 8, 2, rgb(0xE86818))
    c.outline()


def recolour(draw, mapping):
    """Redraws a sprite with some colours swapped, for cheap themed variants."""
    def wrapped(c, f=0):
        draw(c, f)
        for y in range(c.h):
            for x in range(c.w):
                v = c.p[y][x]
                if v in mapping:
                    c.p[y][x] = mapping[v]
    return wrapped


KEEP_STONE = (rgb(0x8C8A82), rgb(0x6C6A62), rgb(0xA8A69C))
KEEP_FLAG = rgb(0x2A4C9A)


def keep_variant(stone, stone2, stone3, flag):
    return recolour(draw_keep, {KEEP_STONE[0]: stone, KEEP_STONE[1]: stone2, KEEP_STONE[2]: stone3, KEEP_FLAG: flag})


def draw_vampyre(c, f):
    # A pale aristocrat under a high-collared black cape.
    swing = (2, 0, -2, 0)[f]
    bob = (0, -1, 0, -1)[f]
    pal = dict(skin=rgb(0xD8D0D0), skin2=rgb(0xB0A8A8), body=rgb(0x2A1E2E), body2=rgb(0x1C141E),
               legs=rgb(0x2A1E2E), legs2=rgb(0x1C141E), boot=rgb(0x100A12), arm=rgb(0x2A1E2E))
    c.poly([(9, 10 + bob), (6, 24), (13, 22)], rgb(0x6A1420))
    sx, sy = biped(c, pal, ground=25, height=1.05, bob=bob, swing=swing, lean=1, width=1.1)
    hx, hy = sx, sy - 4
    c.poly([(hx - 6, hy + 4), (hx - 5, hy - 3), (hx - 3, hy + 2)], rgb(0x6A1420))
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    c.rect(hx - 4, hy - 4, 8, 2, rgb(0x1C141E))
    c.px(hx + 2, hy - 1, rgb(0xE02020))
    c.rect(hx, hy + 2, 3, 1, OUTLINE)
    c.px(hx + 1, hy + 3, rgb(0xFFFFFF))
    c.outline()
    c.under_shadow(14, 25.5, 7, 2.5)


def draw_tzhaar_ket(c, f):
    swing = (3, 0, -3, 0)[f]
    bob = (0, -1, 0, -1)[f]
    rock, rock2 = rgb(0x4A3026), rgb(0x2E1C16)
    pal = dict(skin=rock, skin2=rock2, body=rock, body2=rock2, legs=rock, legs2=rock2, boot=rgb(0x1E120E), arm=rock)
    sx, sy = biped(c, pal, ground=25, height=1.05, bob=bob, swing=swing, lean=1, width=1.35)
    c.line(sx - 2, sy + 2, sx + 2, sy + 8, rgb(0xFF8020))
    hx, hy = sx + 1, sy - 3
    head(c, hx, hy, 4, rock, rock2)
    c.rect(hx + 1, hy - 1, 3, 1, rgb(0xFFC030))
    c.rect(hx, hy + 2, 4, 1, rgb(0xFF8020))
    c.poly([(hx - 4, hy - 3), (hx - 2, hy - 7), (hx, hy - 4)], rgb(0x6A4A3A))
    c.poly([(hx + 1, hy - 4), (hx + 3, hy - 7), (hx + 4, hy - 3)], rgb(0x6A4A3A))
    # An obsidian maul.
    ax, ay = sx + 6, sy + 3
    club(c, ax, ay, -60 + (0, 6, 0, -6)[f], 9, wood=rgb(0x2E1C16), knob=rgb(0x14100E), w=3)
    limb(c, sx + 1, sy + 2, ax, ay, 2, rock)
    c.outline()
    c.under_shadow(14, 25.5, 8, 2.5)


def draw_revenant(c, f):
    # A spectral warrior, all pale green glow.
    glow, glow2 = rgb(0x9CD8B0), rgb(0x68A880)
    bob = (0, -1, -2, -1)[f]
    y = 10 + bob
    c.poly([(9, y), (20, y), (19, 20 + bob), (15, 24 + bob), (11, 20 + bob)], glow)
    c.poly([(9, y), (13, y), (13, 22 + bob), (11, 20 + bob)], glow2)
    head(c, 15, y - 4, 4, rgb(0xD8F0E0), glow)
    c.rect(16, y - 5, 2, 2, rgb(0x103020))
    c.rect(13, y - 5, 2, 2, rgb(0x103020))
    c.rect(11, y - 9, 8, 2, rgb(0x6A7A70))
    tx, ty = 22, y + 4
    c.line(18, y + 2, tx, ty, glow, 2)
    sword(c, tx, ty, -40 + (0, 6, 0, -6)[f], 10, blade=rgb(0xC8F8E0), edge=rgb(0x68A880), hilt=rgb(0x3E5A48))
    c.outline(rgb(0x1E3C2C))
    c.under_shadow(14, 25.5, 6, 2)


def draw_vampyre_lord(c, f):
    bob = (0, -1, 0, -1)[f]
    skin, skin2 = rgb(0xC8BCC4), rgb(0xA096A0)
    pal = dict(skin=skin, skin2=skin2, body=rgb(0x2A1E2E), body2=rgb(0x1C141E), legs=rgb(0x2A1E2E), legs2=rgb(0x1C141E),
               boot=rgb(0x100A12), arm=rgb(0x2A1E2E))
    wings(c, 27, 20 + bob, 2.0, (6, 11, 6, 2)[f], rgb(0x5A1420), rgb(0x420E18))
    a = brute(c, 1.4, pal, bob, (5, 0, -5, 0)[f], 2)
    hx, hy = a["hx"], a["hy"] + 2
    c.poly([(hx - 10, hy + 7), (hx - 8, hy - 6), (hx - 3, hy + 4)], rgb(0x7A1420))
    c.poly([(hx + 10, hy + 7), (hx + 8, hy - 6), (hx + 3, hy + 4)], rgb(0x7A1420))
    head(c, hx, hy, 6, skin, skin2)
    c.rect(hx - 6, hy - 6, 12, 3, rgb(0x1C141E))
    c.rect(hx + 1, hy - 2, 3, 2, rgb(0xE02020))
    c.rect(hx, hy + 2, 5, 1, OUTLINE)
    c.px(hx + 1, hy + 3, rgb(0xFFFFFF))
    c.px(hx + 4, hy + 3, rgb(0xFFFFFF))
    limb(c, a["rs"][0], a["rs"][1], a["rs"][0] + 6, a["rs"][1] + 14, 5, skin2)
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 5, a["ls"][1] + 14, 5, skin2)
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 17, 3.5)


def draw_obsidian_golem(c, f):
    bob = (0, 1, 0, 1)[f]
    ob, ob2, fire = rgb(0x322836), rgb(0x1E1822), rgb(0xFF7A18)
    pal = dict(skin=ob, skin2=ob2, body=ob, body2=ob2, legs=ob, legs2=ob2, boot=rgb(0x141018), arm=ob)
    a = brute(c, 1.4, pal, bob, (4, 0, -4, 0)[f], 0)
    cx, top = a["cx"], a["top"]
    for x0, y0, x1, y1 in ((-8, 4, -3, 12), (7, 3, 3, 13), (-4, 14, 4, 18)):
        c.line(cx + x0, top + y0, cx + x1, top + y1, fire)
    for side in (-1, 1):
        c.ell(cx + side * 11, top + 2, 6, 5, ob2 if side < 0 else ob)
    hx, hy = a["hx"], a["hy"] + 3
    head(c, hx, hy, 6, ob, ob2)
    c.rect(hx - 3, hy - 2, 7, 2, fire)
    limb(c, a["rs"][0] + 2, a["rs"][1] + 2, a["rs"][0] + 6, a["rs"][1] + 17, 7, ob)
    limb(c, a["ls"][0] - 2, a["ls"][1] + 2, a["ls"][0] - 5, a["ls"][1] + 17, 7, ob2)
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 17, 3.5)


# ---- Raid maps: scenery and creatures ------------------------------------------

def draw_blood_pool(c, f=0):
    c.ell(14, 16, 13, 9, rgb(0x5A0E12))
    c.ell(13, 15, 10, 6.5, rgb(0x8E1418))
    c.ell(10, 13, 3.5, 2, rgb(0xC02A28))
    c.px(9, 12, rgb(0xE86060))
    c.ell(24, 8, 2, 1.5, rgb(0x8E1418))


def draw_gothic_pillar(c, f=0):
    # 28 x 36: a tall carved column with a dark red banner.
    stone, stone2, stone3 = rgb(0x5A5258), rgb(0x3E383E), rgb(0x736A70)
    c.rect(6, 30, 16, 5, stone2)
    c.rect(4, 33, 20, 2, stone)
    c.rect(9, 6, 10, 25, stone)
    c.rect(9, 6, 4, 25, stone2)
    c.rect(6, 2, 16, 5, stone3)
    c.rect(6, 5, 16, 2, stone2)
    c.poly([(11, 9), (17, 9), (17, 22), (14, 26), (11, 22)], rgb(0x7A1016))
    c.line(14, 12, 14, 19, rgb(0xC8A030))
    c.outline()
    c.under_shadow(14, 35, 11, 1.5)


def draw_crystal(c, f=0):
    teal, teal2, hi = rgb(0x38A8A0), rgb(0x20706C), rgb(0xB0F4E8)
    for x, top, w in ((8, 9, 5), (14, 4, 6), (20, 13, 4)):
        c.poly([(x - w, 25), (x - w + 1, top + 5), (x, top), (x + w - 1, top + 5), (x + w, 25)], teal)
        c.poly([(x - w, 25), (x - w + 1, top + 5), (x, top), (x, 25)], teal2)
        c.line(x + 1, top + 3, x + 2, top + 10, hi)
    c.outline()
    c.under_shadow(14, 25.5, 12, 2)


def draw_xeric_statue(c, f=0):
    stone, stone2 = rgb(0x6A7870), rgb(0x4A5650)
    c.rect(5, 29, 18, 6, stone2)
    c.poly([(8, 29), (20, 29), (19, 13), (9, 13)], stone)
    c.poly([(8, 29), (13, 29), (13, 13), (9, 13)], stone2)
    head(c, 14, 8, 5, stone, stone2)
    c.rect(11, 7, 2, 2, rgb(0x60F0E0))
    c.rect(16, 7, 2, 2, rgb(0x60F0E0))
    c.poly([(9, 4), (6, 0), (12, 3)], stone2)
    c.poly([(19, 4), (22, 0), (16, 3)], stone)
    c.outline()
    c.under_shadow(14, 35, 11, 1.5)


def draw_nylocas(c, f):
    # A pale segmented crawler, with a scuttling rank of legs and a blood-red eye.
    shell, shell2, leg = rgb(0xC8C0B0), rgb(0x968E7E), rgb(0x8A8070)
    y = 16 + (0, -1, 0, -1)[f]
    for i, x in enumerate((8, 12, 16, 20)):
        lift = (2, -1)[(i + f) % 2]
        c.line(x, y + 3, x - 3, y + 8 + lift, leg)
        c.line(x, y + 3, x + 3, y + 8 - lift, leg)
    for i, (x, r) in enumerate(((7, 3.5), (12, 4.5), (17, 4.5))):
        c.ell(x, y, r, r - 0.5, shell if i % 2 else shell2)
    c.poly([(19, y - 4), (26, y - 1), (26, y + 2), (19, y + 3)], shell)
    c.px(23, y - 1, rgb(0xE01818))
    c.px(24, y - 1, rgb(0xE01818))
    c.rect(23, y + 1, 3, 1, OUTLINE)
    c.outline()
    c.under_shadow(14, 25, 11, 2)


def draw_hatchling(c, f):
    shell, leg = rgb(0xD8D0C0), rgb(0x8A8070)
    y = 20 + (0, -1, 0, -1)[f]
    for i, x in enumerate((11, 14, 17)):
        lift = (1, -1)[(i + f) % 2]
        c.line(x, y + 1, x - 2, y + 4 + lift, leg)
        c.line(x, y + 1, x + 2, y + 4 - lift, leg)
    c.ell(14, y, 5, 3, shell)
    c.ell(18, y - 1, 2.5, 2.5, shell)
    c.px(19, y - 1, rgb(0xE01818))
    c.outline()
    c.under_shadow(14, 25, 7, 1.5)


def draw_bloat(c, f):
    # 40 x 40: a huge stitched sack on tiny legs, swollen and swaying.
    flesh, flesh2, seam = rgb(0xA8807A), rgb(0x7E5A56), rgb(0x3E2A28)
    bob = (0, 1, 0, 1)[f]
    swing = (2, 0, -2, 0)[f]
    c.line(15, 30, 15 - swing, 37, flesh2, 3)
    c.line(24, 30, 24 + swing, 37, flesh2, 3)
    c.ell(20, 19 + bob, 16, 14, flesh)
    c.ell(14, 21 + bob, 9, 11, flesh2)
    for y in (12, 19, 26):
        c.line(11, y + bob, 29, y + 1 + bob, seam)
    for x in range(12, 30, 3):
        c.line(x, 11 + bob, x, 14 + bob, seam)
    c.ell(29, 15 + bob, 3, 3, rgb(0xE8E0B0))
    c.px(30, 15 + bob, OUTLINE)
    c.rect(26, 24 + bob, 6, 2, rgb(0x5A0E12))
    c.outline()
    c.under_shadow(20, 37.5, 15, 2.5)


def draw_verzik(c, f):
    # 56 x 56: a pale queen riding a body of long spider legs.
    legc, legc2 = rgb(0x4A3A50), rgb(0x322638)
    bob = (0, -1, 0, -1)[f]
    y = 30 + bob
    for i in range(4):
        lift = (3, -1)[(i + f) % 2]
        for side in (-1, 1):
            base = (28 + side * (5 + i), y + i)
            knee = (28 + side * (14 + i * 3), y - 12 + i * 3 + lift)
            foot = (28 + side * (17 + i * 3), min(53, y + 10 + i * 4))
            c.line(base[0], base[1], knee[0], knee[1], legc if side > 0 else legc2, 2)
            c.line(knee[0], knee[1], foot[0], foot[1], legc if side > 0 else legc2, 2)
    c.ell(28, y + 4, 11, 8, rgb(0x5A1A2A))
    c.ell(25, y + 2, 5, 4, rgb(0x8E2A38))
    # The rider.
    c.poly([(23, y - 2), (33, y - 2), (31, y - 16), (25, y - 16)], rgb(0xC8B8C0))
    c.poly([(23, y - 2), (28, y - 2), (28, y - 16), (25, y - 16)], rgb(0xA090A0))
    head(c, 28, y - 21, 5, rgb(0xE0D8DC), rgb(0xB8B0B8))
    c.rect(29, y - 22, 3, 2, rgb(0xE01818))
    c.rect(28, y - 18, 4, 1, OUTLINE)
    c.poly([(22, y - 25), (24, y - 32), (27, y - 26)], rgb(0xC8A030))
    c.poly([(26, y - 26), (28, y - 34), (30, y - 26)], rgb(0xC8A030))
    c.poly([(29, y - 26), (32, y - 32), (34, y - 25)], rgb(0xC8A030))
    c.line(25, y - 14, 15, y - 9, rgb(0xC8B8C0), 2)
    c.line(31, y - 14, 41, y - 19 + (0, 2, 0, -2)[f], rgb(0xC8B8C0), 2)
    c.outline()
    c.under_shadow(28, 53, 24, 2.5)


def draw_shaman(c, f):
    # A hunched lizard caster in a bone mask.
    swing = (2, 0, -2, 0)[f]
    bob = (0, -1, 0, -1)[f]
    hide, hide2 = rgb(0x4E7A58), rgb(0x365A40)
    pal = dict(skin=hide, skin2=hide2, body=rgb(0x7A5A3A), body2=rgb(0x5E442A), legs=hide, legs2=hide2, boot=rgb(0x28402E), arm=hide)
    c.line(9, 19 + bob, 3, 23 + bob, hide2, 2)
    sx, sy = biped(c, pal, ground=25, height=0.95, bob=bob, swing=swing, lean=2)
    hx, hy = sx + 1, sy - 3
    c.poly([(hx - 4, hy - 4), (hx + 3, hy - 4), (hx + 8, hy), (hx + 6, hy + 3), (hx - 4, hy + 3)], rgb(0xE0D8C0))
    c.rect(hx + 1, hy - 2, 2, 2, rgb(0x40E0A0))
    c.rect(hx + 2, hy + 1, 4, 1, OUTLINE)
    tx, ty = sx + 6, sy + 3
    c.line(tx, ty - 9, tx, ty + 9, rgb(0x5A3C1C))
    c.ell(tx, ty - 10, 2, 2, rgb(0x40E0A0))
    limb(c, sx + 1, sy + 2, tx, ty, 2, hide)
    c.outline()
    c.under_shadow(14, 25.5, 7, 2.5)


def draw_vanguard(c, f):
    # Crystal-plated warrior: tough against swords, but cracked by magic.
    swing = (3, 0, -3, 0)[f]
    bob = (0, -1, 0, -1)[f]
    plate, plate2 = rgb(0x38A8A0), rgb(0x20706C)
    pal = dict(skin=plate, skin2=plate2, body=plate, body2=plate2, legs=plate2, legs2=rgb(0x18544F), boot=rgb(0x143E3A), arm=plate)
    sx, sy = biped(c, pal, ground=25, height=1.05, bob=bob, swing=swing, lean=1, width=1.3)
    c.line(sx - 2, sy + 2, sx + 2, sy + 7, rgb(0xB0F4E8))
    hx, hy = sx + 1, sy - 4
    head(c, hx, hy, 4, plate, plate2)
    c.rect(hx + 1, hy - 1, 3, 1, rgb(0xFFFFFF))
    c.poly([(hx - 3, hy - 3), (hx - 1, hy - 9), (hx + 1, hy - 3)], rgb(0xB0F4E8))
    ax, ay = sx + 6, sy + 3
    sword(c, ax, ay, -40 + (0, 6, 0, -6)[f], 10, blade=rgb(0xB0F4E8), edge=plate2, hilt=plate2)
    limb(c, sx + 1, sy + 2, ax, ay, 2, plate)
    c.outline()
    c.under_shadow(14, 25.5, 8, 2.5)


def draw_guardian(c, f):
    # 40 x 40: a walking stone statue, slow and all but immune to blades.
    bob = (0, 1, 0, 1)[f]
    stone, stone2 = rgb(0x6A7870), rgb(0x4A5650)
    pal = dict(skin=stone, skin2=stone2, body=stone, body2=stone2, legs=stone, legs2=stone2, boot=rgb(0x343C38), arm=stone)
    a = brute(c, 1.0, pal, bob, (3, 0, -3, 0)[f], 0)
    cx, top = a["cx"], a["top"]
    c.line(cx - 5, top + 3, cx - 1, top + 11, rgb(0x343C38))
    c.line(cx + 5, top + 2, cx + 2, top + 8, rgb(0x343C38))
    hx, hy = a["hx"], a["hy"] + 3
    head(c, hx, hy, 5, stone, stone2)
    c.rect(hx + 1, hy - 1, 3, 2, rgb(0x60F0E0))
    rx, ry = a["rs"]
    hand = (rx + 5, ry + 8)
    limb(c, rx, ry, hand[0], hand[1], 4, stone)
    c.line(hand[0], hand[1] + 4, hand[0] + 1, hand[1] - 11, rgb(0x5A3C1C), 2)
    c.poly([(hand[0] - 6, hand[1] - 11), (hand[0] + 8, hand[1] - 13), (hand[0] + 3, hand[1] - 9)], rgb(0xB8C0C4))
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 3, a["ls"][1] + 11, 4, stone2)
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 0.5, 13, 3)


def draw_olm(c, f):
    # 56 x 56: a colossal pale lizard, its back overgrown with crystal.
    hide, hide2, belly = rgb(0x8A9478), rgb(0x626C54), rgb(0xB8BC9C)
    bob = (0, -1, 0, -1)[f]
    swing = (4, 0, -4, 0)[f]
    y = 34 + bob
    c.poly([(11, y - 2), (1, y + 6 + (0, 2, 0, -2)[f]), (11, y + 7)], hide2)
    for x, sw in ((15, -swing), (33, swing)):
        c.line(x, y + 6, x + sw, 51, hide2, 5)
    c.ell(24, y, 17, 12, hide)
    c.poly([(10, y + 5), (38, y + 5), (34, y + 11), (14, y + 11)], belly)
    for x, sw in ((20, swing), (37, -swing)):
        c.line(x, y + 6, x + sw, 51, hide, 5)
        c.rect(x + sw - 3, 49, 7, 3, rgb(0xE0D8C0))
    # Crystals erupting from the spine.
    for x, h, w in ((13, 11, 3), (20, 17, 4), (28, 14, 4), (34, 9, 3)):
        c.poly([(x - w, y - 8), (x, y - 8 - h), (x + w, y - 8)], rgb(0x38A8A0))
        c.poly([(x - w, y - 8), (x, y - 8 - h), (x, y - 8)], rgb(0x20706C))
        c.px(x + 1, y - 10 - h // 2, rgb(0xB0F4E8))
    # The huge skull.
    hx, hy = 41, y - 7
    c.poly([(hx - 7, hy - 8), (hx + 7, hy - 8), (hx + 14, hy), (hx + 12, hy + 4), (hx - 7, hy + 7)], rgb(0xE0D8C0))
    c.poly([(hx - 7, hy - 8), (hx, hy - 8), (hx, hy + 6), (hx - 7, hy + 7)], rgb(0xB8B098))
    c.ell(hx + 2, hy - 3, 2.5, 2.5, rgb(0x60F0E0))
    c.px(hx + 2, hy - 3, OUTLINE)
    c.poly([(hx - 3, hy + 5), (hx + 12, hy + 4), (hx + 8, hy + 9), (hx - 2, hy + 9)], rgb(0xB8B098))
    for t in range(0, 12, 3):
        c.poly([(hx - 1 + t, hy + 4), (hx + t, hy + 7), (hx + 1 + t, hy + 4)], rgb(0xFFFFFF))
    c.outline()
    c.under_shadow(24, 52, 24, 3)


# ---- Map-specific monsters --------------------------------------------------------

def walker(c, f, pal, hat, weapon=None, height=1.0, width=1.1, lean=1):
    """A walking humanoid monster: the rig, a head decorated by hat(), and a held weapon."""
    swing = (3, 0, -3, 0)[f]
    bob = (0, -1, 0, -1)[f]
    sx, sy = biped(c, pal, ground=25, height=height, bob=bob, swing=swing, lean=lean, width=width)
    hx, hy = sx + 1, sy - 4
    head(c, hx, hy, 4, pal["skin"], pal["skin2"])
    hat(c, hx, hy)
    ax, ay = sx + 6, sy + 3
    if weapon:
        weapon(c, ax, ay, f)
    limb(c, sx + 1, sy + 2, ax, ay, 2, pal["arm"])
    c.outline()
    c.under_shadow(14, 25.5, 7, 2.5)


def draw_barbarian(c, f):
    pal = dict(skin=rgb(0xD8A882), skin2=rgb(0xB88A66), body=rgb(0xD8A882), body2=rgb(0xB88A66),
               legs=rgb(0x6E5228), legs2=rgb(0x54401E), boot=rgb(0x3E2E18), arm=rgb(0xD8A882))

    def hat(c, hx, hy):
        c.rect(hx - 4, hy - 4, 8, 2, rgb(0x8C929A))
        c.poly([(hx - 4, hy - 3), (hx - 7, hy - 8), (hx - 2, hy - 4)], rgb(0xE8E0C8))
        c.poly([(hx + 2, hy - 4), (hx + 6, hy - 8), (hx + 4, hy - 3)], rgb(0xE8E0C8))
        c.rect(hx + 1, hy - 1, 2, 1, OUTLINE)
        c.rect(hx - 1, hy + 2, 5, 2, rgb(0xC8641E))

    walker(c, f, pal, hat, lambda c, x, y, f: axe(c, x, y, -60 + (0, 6, 0, -6)[f], 8), width=1.2)


def draw_hobgoblin(c, f):
    pal = dict(skin=rgb(0x6E8A5A), skin2=rgb(0x526C42), body=rgb(0x5A4A3A), body2=rgb(0x42362A),
               legs=rgb(0x6E8A5A), legs2=rgb(0x526C42), boot=rgb(0x2E2418), arm=rgb(0x6E8A5A))

    def hat(c, hx, hy):
        c.poly([(hx - 3, hy - 2), (hx - 7, hy - 5), (hx - 3, hy + 1)], rgb(0x526C42))
        c.rect(hx + 1, hy - 1, 2, 1, rgb(0xFFE040))
        c.rect(hx, hy + 2, 4, 1, OUTLINE)
        c.px(hx + 1, hy + 3, rgb(0xE8E4D0))

    walker(c, f, pal, hat, lambda c, x, y, f: spear(c, x, y, -70 + (0, 5, 0, -5)[f], 9), height=1.08)


def draw_black_knight(c, f):
    plate, plate2 = rgb(0x3A3A44), rgb(0x24242C)
    pal = dict(skin=plate, skin2=plate2, body=plate, body2=plate2, legs=plate, legs2=plate2, boot=rgb(0x18181E), arm=plate)

    def hat(c, hx, hy):
        c.rect(hx, hy - 1, 4, 2, rgb(0xC03830))
        c.poly([(hx - 1, hy - 4), (hx - 6, hy - 8), (hx + 1, hy - 7)], rgb(0x7A1016))

    walker(c, f, pal, hat, lambda c, x, y, f: sword(c, x, y, -45 + (0, 6, 0, -6)[f], 10, blade=rgb(0x8A8E96), edge=rgb(0x54565E), hilt=rgb(0x7A1016)), width=1.25)


def draw_ghoul(c, f):
    pal = dict(skin=rgb(0x98A890), skin2=rgb(0x74846E), body=rgb(0x5A5048), body2=rgb(0x423A34),
               legs=rgb(0x98A890), legs2=rgb(0x74846E), boot=rgb(0x74846E), arm=rgb(0x98A890))

    def hat(c, hx, hy):
        c.rect(hx + 1, hy - 1, 2, 2, rgb(0xE8E060))
        c.rect(hx, hy + 2, 4, 2, OUTLINE)
        c.px(hx + 1, hy + 2, rgb(0xE8E4D0))
        c.px(hx + 3, hy + 2, rgb(0xE8E4D0))

    def claws(c, x, y, f):
        for d in (-20, 15):
            tx, ty = polar(x, y, 4, d)
            c.line(x, y, tx, ty, rgb(0xE8E4D0))

    walker(c, f, pal, hat, claws, height=0.9, lean=3)


def draw_bloodveld(c, f):
    # A hunched mass of raw flesh, with a long lashing tongue.
    flesh, flesh2 = rgb(0xB84848), rgb(0x8A2E30)
    bob = (0, -1, 0, -1)[f]
    swing = (3, 0, -3, 0)[f]
    c.line(11, 19 + bob, 11 - swing, 24, flesh2, 3)
    c.line(17, 19 + bob, 17 + swing, 24, flesh2, 3)
    c.ell(13, 13 + bob, 8, 8, flesh)
    c.ell(10, 15 + bob, 5, 5, flesh2)
    c.ell(19, 8 + bob, 4.5, 4, flesh)
    for x in (9, 13, 17):
        c.px(x, 6 + bob, rgb(0xE8D0C0))
    c.px(20, 7 + bob, rgb(0xFFE040))
    tip = (0, 2, 0, -2)[f]
    c.line(22, 10 + bob, 27, 7 + bob + tip, rgb(0xE86080), 2)
    c.outline()
    c.under_shadow(14, 25, 8, 2)


def draw_frog(c, f):
    # Hops: squashed on the ground, then stretched in the air.
    skin, skin2, belly = rgb(0x5E9A40), rgb(0x44782C), rgb(0xC8D890)
    air = (0, 3, 6, 3)[f]
    y = 19 - air
    c.line(9, y + 2, 5, y + 5 + air // 2, skin2, 3)
    c.line(19, y + 3, 22, y + 6 + air // 2, skin2, 2)
    c.ell(14, y, 8, 5.5, skin)
    c.poly([(8, y + 2), (20, y + 2), (18, y + 5), (10, y + 5)], belly)
    c.ell(18, y - 5, 2.5, 2.5, skin)
    c.ell(13, y - 5, 2.5, 2.5, skin2)
    c.px(18, y - 5, OUTLINE)
    c.px(13, y - 5, OUTLINE)
    c.rect(16, y + 1, 5, 1, OUTLINE)
    c.outline()
    c.under_shadow(14, 25, 7 - air * 0.4, 2)


def draw_minotaur(c, f):
    bob = (0, -1, 0, -1)[f]
    fur, fur2 = rgb(0x7A5430), rgb(0x5A3C20)
    pal = dict(skin=fur, skin2=fur2, body=fur, body2=fur2, legs=fur, legs2=fur2, boot=rgb(0x2E2014), arm=fur)
    a = brute(c, 1.0, pal, bob, (4, 0, -4, 0)[f], 1)
    hx, hy = a["hx"], a["hy"] + 2
    head(c, hx, hy, 5, fur, fur2)
    c.poly([(hx - 4, hy - 3), (hx - 12, hy - 8), (hx - 2, hy - 5)], rgb(0xE8E0C8))
    c.poly([(hx + 2, hy - 5), (hx + 11, hy - 8), (hx + 4, hy - 3)], rgb(0xE8E0C8))
    c.rect(hx + 1, hy - 1, 3, 1, rgb(0xFF4030))
    c.rect(hx + 1, hy + 2, 5, 2, rgb(0xB89070))
    c.px(hx + 2, hy + 3, rgb(0xC8A030))
    rx, ry = a["rs"]
    axe(c, rx + 5, ry + 6, -65 + (0, 5, 0, -5)[f], 12)
    limb(c, rx, ry, rx + 5, ry + 6, 4, fur)
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 3, a["ls"][1] + 11, 4, fur2)
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 0.5, 13, 3)


def draw_goblin_warlord(c, f):
    # 56 x 56: bigger than the Champion, in black plate with a red war banner.
    bob = (0, -1, 0, -1)[f]
    skin, skin2 = rgb(0x6E9630), rgb(0x547624)
    pal = dict(skin=skin, skin2=skin2, body=rgb(0x2E2E36), body2=rgb(0x1E1E26), legs=rgb(0x3A3A44), legs2=rgb(0x24242C),
               boot=rgb(0x18181E), arm=skin)
    a = brute(c, 1.4, pal, bob, (5, 0, -5, 0)[f], 1)
    cx, top = a["cx"], a["top"]
    c.line(cx - 9, top - 2, cx - 9, top - 20, rgb(0x5A3C1C), 2)
    c.poly([(cx - 9, top - 20), (cx - 21, top - 17 + (0, 2, 0, -2)[f]), (cx - 9, top - 10)], rgb(0xB02A20))
    c.rect(cx - 8, top + 4, 17, 2, rgb(0xC8A030))
    hx, hy = a["hx"], a["hy"] + 2
    c.poly([(hx - 5, hy - 2), (hx - 15, hy - 6), (hx - 5, hy + 2)], skin2)
    c.poly([(hx + 5, hy - 2), (hx + 14, hy - 6), (hx + 5, hy + 2)], skin)
    head(c, hx, hy, 6, skin, skin2)
    c.rect(hx + 1, hy - 1, 3, 2, rgb(0xFF3020))
    c.rect(hx, hy + 3, 6, 1, OUTLINE)
    c.px(hx + 1, hy + 2, rgb(0xE8E4D0))
    c.px(hx + 5, hy + 2, rgb(0xE8E4D0))
    c.rect(hx - 6, hy - 8, 12, 2, rgb(0x3A3A44))
    for sp in (-6, -2, 2):
        c.poly([(hx + sp, hy - 8), (hx + sp + 2, hy - 13), (hx + sp + 4, hy - 8)], rgb(0x3A3A44))
    rx, ry = a["rs"]
    hand = polar(rx, ry, 9, 25)
    axe(c, hand[0], hand[1], -70 + (0, 5, 0, -5)[f], 22)
    limb(c, rx, ry, hand[0], hand[1], 5, skin)
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 4, a["ls"][1] + 15, 5, skin2)
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 17, 3.5)


def draw_delrith(c, f):
    a = demon_body(c, 1.4, f, rgb(0x5A2A7A), rgb(0x3E1C56), rgb(0x2E1440), rgb(0x220E30))
    rx, ry = a["rs"]
    hand = polar(rx, ry, 9, 25)
    sword(c, hand[0], hand[1], -65 + (0, 5, 0, -5)[f], 23, blade=rgb(0xC080FF), edge=rgb(0x7A3AB0), hilt=rgb(0x220E30))
    limb(c, rx, ry, hand[0], hand[1], 5, rgb(0x6A3490))
    limb(c, a["ls"][0], a["ls"][1], a["ls"][0] - 4, a["ls"][1] + 15, 5, rgb(0x3E1C56))
    c.outline()
    c.under_shadow(a["cx"], a["ground"] + 1, 17, 3.5)


def draw_tz_kih(c, f):
    # A flapping lava bat.
    body, wing, wing2 = rgb(0x4A3026), rgb(0x8A3A18), rgb(0x5E2410)
    y = 13 + (0, -2, 0, 2)[f]
    up = (7, 2, -3, 2)[f]
    for side, tone in ((-1, wing2), (1, wing)):
        c.poly([(14, y), (14 + side * 13, y - up), (14 + side * 10, y + 3), (14 + side * 6, y + 2), (14 + side * 4, y + 6)], tone)
    c.ell(14, y + 2, 4, 5, body)
    c.rect(11, y - 3, 7, 4, body)
    c.px(12, y - 2, rgb(0xFFC030))
    c.px(16, y - 2, rgb(0xFFC030))
    c.poly([(11, y - 3), (10, y - 7), (13, y - 3)], body)
    c.poly([(15, y - 3), (18, y - 7), (18, y - 3)], body)
    c.outline()
    c.under_shadow(14, 25, 6, 1.5)


def draw_tz_kek(c, f):
    # A molten boulder that rolls along and cracks in two when killed.
    rock, rock2, lava = rgb(0x4A3026), rgb(0x2E1C16), rgb(0xFF8020)
    y = 15 + (0, -1, 0, -1)[f]
    c.ell(14, y, 10, 9.5, rock)
    c.ell(10, y + 2, 6, 6, rock2)
    turn = (0, 2, 4, 2)[f]
    c.line(7 + turn, y - 5, 12 + turn, y + 1, lava)
    c.line(12 + turn, y + 1, 9 + turn, y + 7, lava)
    c.line(18 - turn, y - 6, 15, y - 1, lava)
    c.rect(16, y - 2, 4, 2, rgb(0xFFC030))
    c.outline()
    c.under_shadow(14, 25, 10, 2)


def draw_tz_kek_small(c, f):
    rock, rock2, lava = rgb(0x4A3026), rgb(0x2E1C16), rgb(0xFF8020)
    y = 19 + (0, -1, 0, -1)[f]
    c.ell(14, y, 5.5, 5, rock)
    c.ell(12, y + 1, 3, 3, rock2)
    c.line(11, y - 2, 15, y + 2, lava)
    c.px(16, y - 1, rgb(0xFFC030))
    c.outline()
    c.under_shadow(14, 25, 6, 1.5)


def draw_chaos_druid(c, f):
    robed(c, (0, 1, 0, 1)[f], rgb(0x4E5A2C), rgb(0x38421E), rgb(0xC03830), rgb(0xC8A080), rgb(0xE84030), rgb(0xFFD0C0),
          beard=rgb(0xD8D8D8))


# name, cell width, cell height, frames, draw function
SPRITES = [
    ("goblin", 28, 28, 4, draw_goblin),
    ("guard", 28, 28, 4, draw_guard),
    ("archer", 28, 28, 4, draw_archer),
    ("dark_wizard", 28, 28, 4, draw_wizard),
    ("dwarf", 28, 28, 4, draw_dwarf),
    ("giant_rat", 28, 28, 4, draw_rat),
    ("goblin_raider", 28, 28, 4, draw_raider),
    ("skeleton", 28, 28, 4, draw_skeleton),
    ("spider", 28, 28, 4, draw_spider),
    ("hill_giant", 40, 40, 4, draw_giant),
    ("goblin_champion", 40, 40, 4, draw_champion),
    ("skeleton_mage", 28, 28, 4, draw_skeleton_mage),
    ("lesser_demon", 28, 28, 4, draw_lesser_demon),
    ("saradomin_wizard", 28, 28, 4, draw_saradomin_wizard),
    ("elven_archer", 28, 28, 4, draw_elven_archer),
    ("knight_of_ardougne", 28, 28, 4, draw_knight),
    ("ice_warrior", 28, 28, 4, draw_ice_warrior),
    ("abyssal_demon", 28, 28, 4, draw_abyssal_demon),
    ("gargoyle", 28, 28, 4, draw_gargoyle),
    ("armoured_zombie", 28, 28, 4, draw_zombie),
    ("basilisk", 28, 28, 4, draw_basilisk),
    ("hellhound", 28, 28, 4, draw_hellhound),
    ("ankou", 28, 28, 4, draw_ankou),
    ("ogre", 40, 40, 4, draw_ogre),
    ("greater_demon", 40, 40, 4, draw_greater_demon),
    ("green_dragon", 40, 40, 4, draw_green_dragon),
    ("ent", 56, 56, 4, draw_ent),
    ("moss_giant", 56, 56, 4, draw_moss_giant),
    ("fire_giant", 56, 56, 4, draw_fire_giant),
    ("black_dragon", 56, 56, 4, draw_black_dragon),
    ("sol_heredit", 56, 56, 4, draw_sol_heredit),
    ("ancient_wyvern", 56, 56, 4, draw_wyvern),
    ("king_black_dragon", 56, 56, 4, draw_kbd),
    ("kril_tsutsaroth", 56, 56, 4, draw_kril),
    ("jad", 84, 84, 4, draw_jad),
    ("vorkath", 84, 84, 4, draw_vorkath),
    ("zuk", 84, 84, 4, draw_zuk),
    ("vampyre", 28, 28, 4, draw_vampyre),
    ("tzhaar_ket", 28, 28, 4, draw_tzhaar_ket),
    ("revenant", 28, 28, 4, draw_revenant),
    ("vampyre_lord", 56, 56, 4, draw_vampyre_lord),
    ("obsidian_golem", 56, 56, 4, draw_obsidian_golem),
    ("dead_tree", 28, 36, 1, draw_dead_tree),
    ("swamp", 28, 28, 1, draw_swamp),
    ("lava", 28, 28, 1, draw_lava),
    ("dark_rock", 28, 28, 1, draw_dark_rock),
    ("bones", 28, 28, 1, draw_bones),
    ("cave_mouth", 28, 28, 1, draw_cave_mouth),
    ("keep_haunted", 84, 44, 1, keep_variant(rgb(0x5C5A60), rgb(0x444248), rgb(0x747278), rgb(0x6A1420))),
    ("keep_obsidian", 84, 44, 1, keep_variant(rgb(0x3E3238), rgb(0x2A2228), rgb(0x564850), rgb(0xE86818))),
    ("keep_ruined", 84, 44, 1, keep_variant(rgb(0x7A7464), rgb(0x5A5648), rgb(0x90897A), rgb(0x2A2A2A))),
    ("nylocas", 28, 28, 4, draw_nylocas),
    ("nylocas_hatchling", 28, 28, 4, draw_hatchling),
    ("lizardman_shaman", 28, 28, 4, draw_shaman),
    ("vanguard", 28, 28, 4, draw_vanguard),
    ("bloat", 40, 40, 4, draw_bloat),
    ("guardian", 40, 40, 4, draw_guardian),
    ("verzik", 56, 56, 4, draw_verzik),
    ("great_olm", 56, 56, 4, draw_olm),
    ("blood_pool", 28, 28, 1, draw_blood_pool),
    ("gothic_pillar", 28, 36, 1, draw_gothic_pillar),
    ("crystal", 28, 28, 1, draw_crystal),
    ("xeric_statue", 28, 36, 1, draw_xeric_statue),
    ("keep_blood", 84, 44, 1, keep_variant(rgb(0x4A3438), rgb(0x34242A), rgb(0x644850), rgb(0x8E1418))),
    ("keep_xeric", 84, 44, 1, keep_variant(rgb(0x5A6A62), rgb(0x424E48), rgb(0x74847A), rgb(0x38A8A0))),
    ("blood_blast", 28, 28, 3, burst_of((rgb(0xE86060), rgb(0xC02A28), rgb(0x7A1016)))),
    ("big_frog", 28, 28, 4, draw_frog),
    ("giant_spider", 28, 28, 4, recolour(draw_spider, {rgb(0x3E2A4A): rgb(0x5E3E22), rgb(0x2A1C34): rgb(0x3E2814), rgb(0x7A5C8C): rgb(0x9A7A50), rgb(0x6E3E7E): rgb(0xA0702E)})),
    ("barbarian", 28, 28, 4, draw_barbarian),
    ("hobgoblin", 28, 28, 4, draw_hobgoblin),
    ("black_knight", 28, 28, 4, draw_black_knight),
    ("ghoul", 28, 28, 4, draw_ghoul),
    ("bloodveld", 28, 28, 4, draw_bloodveld),
    ("banshee", 28, 28, 4, recolour(draw_ankou, {rgb(0x4E5A78): rgb(0xD8D0E8), rgb(0x34405A): rgb(0xA89CC0), rgb(0x6E5A3A): rgb(0xA89CC0), rgb(0xB8C0C8): rgb(0xA89CC0)})),
    ("tz_kih", 28, 28, 4, draw_tz_kih),
    ("tz_kek", 28, 28, 4, draw_tz_kek),
    ("tz_kek_small", 28, 28, 4, draw_tz_kek_small),
    ("skeletal_mystic", 28, 28, 4, recolour(draw_skeleton, {rgb(0xDCD8C4): rgb(0x90E8E0), rgb(0xB0AC98): rgb(0x58B0AC), rgb(0xC4C0AC): rgb(0x78D0C8), rgb(0x98947E): rgb(0x489890), rgb(0xD0CCB8): rgb(0x90E8E0), rgb(0xA4A08C): rgb(0x58B0AC)})),
    ("chaos_druid", 28, 28, 4, draw_chaos_druid),
    ("blood_spawn", 28, 28, 4, recolour(draw_hatchling, {rgb(0xD8D0C0): rgb(0xB82A28), rgb(0x8A8070): rgb(0x6E1416)})),
    ("minotaur", 40, 40, 4, draw_minotaur),
    ("ogre_chieftain", 40, 40, 4, recolour(draw_ogre, {rgb(0x7A5A34): rgb(0xB8922E), rgb(0x5E4426): rgb(0x8E6E1E), rgb(0x8A9A58): rgb(0x6E8040), rgb(0x6A7A40): rgb(0x50602C), rgb(0x3A3020): rgb(0xE8C030)})),
    ("goblin_warlord", 56, 56, 4, draw_goblin_warlord),
    ("delrith", 56, 56, 4, draw_delrith),
    ("tree", 28, 36, 1, draw_tree),
    ("rock", 28, 28, 1, draw_rock),
    ("flower", 28, 28, 1, draw_flower),
    ("water", 28, 28, 1, draw_water),
    ("pad", 28, 28, 1, draw_pad),
    ("camp", 28, 28, 1, draw_camp),
    ("keep", 84, 44, 1, draw_keep),
    ("blast", 28, 28, 3, draw_blast),
    ("dust", 28, 28, 3, draw_dust),
    ("rock_shot", 9, 9, 2, draw_rock_shot),
    ("orb", 9, 9, 2, draw_orb),
    ("flame", 9, 9, 2, orb_of(rgb(0xE8601C), rgb(0xFFC840))),
    ("ice", 9, 9, 2, orb_of(rgb(0x60B8E8), rgb(0xE0F8FF))),
    ("holy", 9, 9, 2, orb_of(rgb(0x4070E0), rgb(0xFFF8C0))),
    ("curse", 9, 9, 2, orb_of(rgb(0x4E8A30), rgb(0xC0FF90))),
    ("great_bolt", 15, 15, 3, draw_great_bolt),
    ("fire_blast", 28, 28, 3, burst_of((rgb(0xFFE060), rgb(0xFF8020), rgb(0xB83818)))),
    ("ice_blast", 28, 28, 3, burst_of((rgb(0xFFFFFF), rgb(0x90D8F8), rgb(0x4898C8)))),
    ("dust_blast", 28, 28, 3, burst_of((rgb(0xE8E0C8), rgb(0xB8A880), rgb(0x806E50)))),
    ("coin", 13, 13, 1, draw_coin),
    ("heart", 13, 13, 1, draw_heart),
    ("lock", 13, 13, 1, draw_lock),
]


def render(fn, w, h, f):
    c = Canvas(w, h)
    fn(c, f)
    return c


def build_atlas():
    atlas_w = 336
    placed = []
    rows = []  # [y, height, used width]
    bottom = 0
    # Tallest first, filling gaps in earlier rows, which keeps the decoded image small.
    for name, w, h, frames, fn in sorted(SPRITES, key=lambda sprite: -sprite[2]):
        total = w * frames
        for row in rows:
            if row[2] + total <= atlas_w and h <= row[1]:
                break
        else:
            row = [bottom, h, 0]
            rows.append(row)
            bottom += h
        placed.append((name, row[2], row[0], w, h, frames, fn))
        row[2] += total
    x, y, row_h = 0, bottom, 0
    atlas = Canvas(atlas_w, y + row_h)
    index = {}
    for name, px, py, w, h, frames, fn in placed:
        for f in range(frames):
            atlas.blit(render(fn, w, h, f), px + f * w, py)
        index[name] = [px, py, w, h, frames]
    return atlas, index


def reduce_palette(colours, limit):
    """Merges the closest pairs of colours until they fit an indexed PNG. Alpha is left alone."""
    remap = {}
    live = list(colours)
    while len(live) > limit:
        best = None
        for i, p in enumerate(live):
            for q in live[i + 1:]:
                if p[3] == q[3]:
                    d = sum((p[k] - q[k]) ** 2 for k in range(3))
                    if best is None or d < best[0]:
                        best = (d, p, q)
        _, keep, drop = best
        live.remove(drop)
        remap[drop] = keep
        for k, v in remap.items():
            if v == drop:
                remap[k] = keep
    return remap


def write_png(path, c):
    """Writes a palette PNG, which is far smaller than RGBA for this art."""
    colours = {}
    for row in c.p:
        for v in row:
            if v[3] == 0:
                v = CLEAR
            colours.setdefault(v, len(colours))
    remap = reduce_palette(list(colours), 256)
    order = []
    index = {}
    for v in colours:
        target = remap.get(v, v)
        if target not in index:
            index[target] = len(order)
            order.append(target)
    raw = bytearray()
    for row in c.p:
        raw.append(0)
        raw.extend(index[remap.get(v if v[3] else CLEAR, v if v[3] else CLEAR)] for v in row)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", c.w, c.h, 8, 3, 0, 0, 0))
    png += chunk(b"PLTE", bytes(v for col in order for v in col[:3]))
    png += chunk(b"tRNS", bytes(col[3] for col in order))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)
    return len(png), len(order)


def scale(c, factor):
    out = Canvas(c.w * factor, c.h * factor)
    for y in range(c.h):
        for x in range(c.w):
            v = c.p[y][x]
            if v[3]:
                out.rect(x * factor, y * factor, factor, factor, v)
    return out


def nav_icon():
    # Keeps the sidebar icon legible at 16 px: a shield with a rune-like mark.
    c = Canvas(16, 16)
    c.poly([(2, 1), (14, 1), (14, 9), (8, 15), (2, 9)], rgb(0x8C8A82))
    c.poly([(2, 1), (8, 1), (8, 15), (2, 9)], rgb(0x6C6A62))
    c.poly([(4, 3), (12, 3), (12, 8), (8, 13), (4, 8)], rgb(0x2A4C9A))
    c.line(8, 4, 8, 11, rgb(0xE8C030))
    c.line(8, 5, 11, 7, rgb(0xE8C030))
    c.line(8, 7, 5, 9, rgb(0xE8C030))
    return c


def hub_icon():
    # Plugin Hub icon: the same shield, scaled up.
    c = Canvas(48, 48)
    c.blit(scale(nav_icon(), 3), 0, 0)
    return c


def preview(c, path, factor=3):
    # Flattened onto grass so that shadows and outlines can be judged in context.
    bg = Canvas(c.w * factor, c.h * factor)
    grass = (0x5A, 0x7A, 0x34)
    for y in range(c.h):
        for x in range(c.w):
            v = c.p[y][x]
            a = v[3] / 255
            col = tuple(int(v[i] * a + grass[i] * (1 - a)) for i in range(3)) + (255,)
            bg.rect(x * factor, y * factor, factor, factor, col)
    write_png(path, bg)


def main():
    atlas, index = build_atlas()
    os.makedirs(RES, exist_ok=True)
    size, colours = write_png(os.path.join(RES, "sprites.png"), atlas)
    with open(os.path.join(RES, "sprites.json"), "w") as f:
        json.dump(index, f, separators=(",", ":"))
    icon_size, _ = write_png(os.path.join(RES, "panel_icon.png"), nav_icon())
    write_png(os.path.join(ROOT, "icon.png"), hub_icon())
    print("atlas %dx%d, %d colours, %d bytes; nav icon %d bytes" % (atlas.w, atlas.h, colours, size, icon_size))
    if len(sys.argv) > 1:
        preview(atlas, sys.argv[1])


if __name__ == "__main__":
    main()
