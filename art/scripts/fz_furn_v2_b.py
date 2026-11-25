# Furniture for the garage, bar and classroom (2026-11). Run after fz_furn_v2_a.py (same namespace).
import bpy, math, random
mat = fz['mat']
CAR = mat("car_paint", "#2F4A6E", 0.3, 0.5)
CAR_GLASS = mat("car_glass", "#1A2430", 0.08)
TYRE = mat("tyre_rubber", "#1B1B1D", 0.9)
RIM = mat("wheel_rim", "#AEB3B9", 0.3, 0.9)
LAMP = mat("car_lamp", "#F2EFE6", 0.2)
TAIL = mat("car_tail", "#A3161C", 0.3)
PLATE = mat("car_plate", "#F4F4F0", 0.6)
TRIM = mat("car_trim", "#1E2024", 0.5)
RED_TOOL = mat("tool_red", "#B03A2E", 0.35, 0.3)
FREEZER = mat("freezer_white", "#E8E8E4", 0.4)
STEEL = mat("steel_brushed", "#B8BCC2", 0.3, 0.9)


def car():
    """Low-poly hatchback, nose to the south (Blender -Y), along Y."""
    c = piece("car"); W, D, H = 1.8, 4.3, 1.45; L = D / 2; bw = 1.74
    wr, wy = 0.31, 1.38                      # wheel radius, axle distance from centre
    def arch(cy):
        return [(cy + wr + 0.06, 0.2), (cy + wr + 0.04, 0.45), (cy + 0.18, 0.62), (cy - 0.18, 0.62), (cy - wr - 0.04, 0.45), (cy - wr - 0.06, 0.2)]
    # side profile (u = y, v = z) for the lower body, with wheel arches cut out
    prof = [(-L + 0.05, 0.22), (-L, 0.36), (-L + 0.02, 0.62), (-L + 0.18, 0.78)]      # nose (south)
    prof += [(-1.0, 0.92), (1.25, 0.96), (L - 0.06, 0.92), (L, 0.62), (L - 0.02, 0.3), (L - 0.08, 0.22)]  # bonnet .. tail
    prof += list(reversed(arch(wy)))[::-1] if False else arch(wy)
    prof += arch(-wy)
    o = prism(c, "body", [(-v[0], v[1]) for v in prof][::-1] if False else prof, bw, (0, 0, 0), CAR)
    o.rotation_euler = (0, 0, math.pi / 2)           # profile u -> Blender Y, extruded along X
    # glasshouse: windscreen (south) to hatch (north)
    gh = [(-0.95, 0.92), (-0.25, 1.38), (1.05, 1.4), (1.55, 1.0), (1.6, 0.94)]
    g = prism(c, "cabin", gh, 1.46, (0, 0, 0), CAR_GLASS); g.rotation_euler = (0, 0, math.pi / 2)
    box(c, "roof", (1.36, 1.24, 0.05), (0, 0.4, H - 0.025), CAR, bevel=0.015)
    for x in (-0.705, 0.705):
        box(c, "bpillar", (0.04, 0.12, 0.42), (x, 0.32, 1.16), TRIM)
        box(c, "sill", (0.03, 2.0, 0.06), (math.copysign(bw / 2 + 0.005, x), 0.1, 0.27), TRIM)
        box(c, "doorline", (0.004, 0.006, 0.6), (math.copysign(bw / 2 + 0.002, x), -0.3, 0.6), TRIM)
        box(c, "doorline", (0.004, 0.006, 0.6), (math.copysign(bw / 2 + 0.002, x), 0.85, 0.6), TRIM)
        box(c, "handle", (0.012, 0.12, 0.025), (math.copysign(bw / 2 + 0.006, x), 0.05, 0.82), TRIM)
        box(c, "mirror", (0.05, 0.1, 0.07), (math.copysign(W / 2 - 0.025, x), -0.78, 1.0), CAR, bevel=0.01)
    for (y, side) in ((-wy, 1), (wy, 1)):
        for x in (-0.76, 0.76):
            cyl(c, "tyre", wr, wr, 0.2, (x, y, wr), TYRE, rot=(0, math.pi / 2, 0), verts=16)
            cyl(c, "rim", wr * 0.62, wr * 0.62, 0.205, (x, y, wr), RIM, rot=(0, math.pi / 2, 0), verts=10)
    for x in (-0.58, 0.58):
        box(c, "headlamp", (0.36, 0.04, 0.1), (x, -L + 0.03, 0.66), LAMP, rot=(math.radians(-18), 0, 0))
        box(c, "taillamp", (0.3, 0.04, 0.12), (x, L - 0.04, 0.76), TAIL)
    box(c, "grille", (0.7, 0.03, 0.12), (0, -L + 0.012, 0.46), TRIM)
    box(c, "bumper_f", (1.6, 0.06, 0.12), (0, -L + 0.035, 0.28), TRIM, bevel=0.02)
    box(c, "bumper_r", (1.6, 0.06, 0.12), (0, L - 0.04, 0.3), TRIM, bevel=0.02)
    box(c, "plate_f", (0.46, 0.012, 0.11), (0, -L + 0.001, 0.4), PLATE)
    box(c, "plate_r", (0.46, 0.012, 0.11), (0, L - 0.006, 0.52), PLATE)
    o = join(c, "car"); fz['fit'](o, W, D, H)
    return finish(c, "car", (W, D, H))


def garage_bench():   # west wall, front faces app +x (Blender +X)
    c = piece("garage_bench"); W, D, H = 0.7, 2.0, 0.92; t = 0.05
    box(c, "top", (W, D, t), (0, 0, H - t / 2), M['wood_light'], bevel=0.006)
    for x in (-W / 2 + 0.04, W / 2 - 0.04):
        for y in (-D / 2 + 0.05, D / 2 - 0.05):
            box(c, "leg", (0.05, 0.05, H - t), (x, y, (H - t) / 2), M['steel'])
    box(c, "shelf", (W - 0.06, D - 0.08, 0.025), (0, 0, 0.18), M['wood'])
    for y in (-D / 2 + 0.05, D / 2 - 0.05):
        box(c, "rail", (W - 0.06, 0.04, 0.04), (0, y, H - t - 0.02), M['steel'])
    box(c, "drawer", (0.02, 0.5, 0.1), (W / 2 - 0.05, -0.4, H - t - 0.06), RED_TOOL, bevel=0.003)
    box(c, "dpull", (0.02, 0.16, 0.015), (W / 2 - 0.035, -0.4, H - t - 0.05), CHROME)
    box(c, "dbody", (W - 0.12, 0.52, 0.1), (-0.02, -0.4, H - t - 0.06), M['steel'])
    r = random.Random(11)
    for i, y in enumerate((-0.7, -0.35, 0.05, 0.45, 0.75)):     # boxes and paint tins on the lower shelf
        if i % 2 == 0:
            box(c, "box", (0.4, 0.3, 0.22), (-0.05, y, 0.1925 + 0.11), M['cardboard'], bevel=0.006)
        else:
            for k in range(2):
                cyl(c, "tin", 0.08, 0.08, 0.17, (-0.1 + k * 0.18, y, 0.1925 + 0.085), r.choice(M['prod']), verts=12)
    return finish(c, "garage_bench", (W, D, H))


def tool_chest():   # west wall, front faces app +x (Blender +X)
    c = piece("tool_chest"); W, D, H = 0.6, 0.6, 1.0
    box(c, "body", (W - 0.03, D, H - 0.12), (-0.015, 0, 0.1 + (H - 0.12) / 2), RED_TOOL, bevel=0.008)
    box(c, "mat", (W - 0.03, D - 0.02, 0.02), (-0.015, 0, H - 0.01), M['plastic_blk'])
    hs = [0.08, 0.08, 0.08, 0.12, 0.12, 0.18]
    z = 0.12
    for h in hs:
        box(c, "drw", (0.012, D - 0.06, h - 0.01), (W / 2 - 0.03 + 0.006 + 0.0, 0, z + h / 2), RED_TOOL, bevel=0.002)
        box(c, "bar", (0.02, D - 0.16, 0.015), (W / 2 - 0.01, 0, z + h - 0.025), CHROME)
        z += h
    for x in (-W / 2 + 0.07, W / 2 - 0.1):
        for y in (-D / 2 + 0.07, D / 2 - 0.07):
            cyl(c, "caster", 0.045, 0.045, 0.03, (x, y, 0.045), M['plastic_blk'], rot=(math.pi / 2, 0, 0), verts=10)
            box(c, "fork", (0.05, 0.05, 0.03), (x, y, 0.1 - 0.015), M['steel'])
    o = join(c, "tool_chest"); fz['fit'](o, W, D, H)
    return finish(c, "tool_chest", (W, D, H))


def storage_shelf():   # east wall, front faces app -x (Blender -X)
    c = piece("storage_shelf"); W, D, H = 0.5, 2.2, 1.9
    for x in (-W / 2 + 0.02, W / 2 - 0.02):
        for y in (-D / 2 + 0.02, 0, D / 2 - 0.02):
            box(c, "post", (0.04, 0.04, H), (x, y, H / 2), M['steel'])
    levels = [0.12, 0.6, 1.08, 1.56]
    for z in levels + [H - 0.02]:
        box(c, "deck", (W - 0.02, D - 0.02, 0.02), (0, 0, z), M['steel'] if z > 1.8 else M['metal'])
    r = random.Random(5)
    for z in levels:
        y = -D / 2 + 0.08
        while y < D / 2 - 0.3:
            k = r.random(); w = r.uniform(0.25, 0.45)
            if k < 0.45:
                hh = r.uniform(0.2, 0.36)
                box(c, "carton", (r.uniform(0.3, 0.42), w, hh), (0.0, y + w / 2, z + 0.01 + hh / 2), M['cardboard'], bevel=0.005)
            elif k < 0.75:
                for j in range(2):
                    cyl(c, "can", 0.085, 0.085, 0.19, (-0.06 + j * 0.0, y + 0.09 + j * 0.18, z + 0.01 + 0.095), r.choice(M['prod']), verts=12)
                w = 0.36
            else:
                box(c, "bin", (0.36, w, 0.24), (0.0, y + w / 2, z + 0.01 + 0.12), M['blue'], bevel=0.01)
            y += w + 0.05
    return finish(c, "storage_shelf", (W, D, H))


def tire_stack():
    c = piece("tire_stack"); W, H = 0.7, 0.85; n = 4; th = H / n; R = W / 2
    prof = [(0.2, 0.0), (R - 0.04, 0.0), (R, 0.03), (R, th - 0.03), (R - 0.04, th), (0.2, th), (0.19, th - 0.02), (0.19, 0.02)]
    for i in range(n):
        o = lathe(c, f"tyre{i}", prof + [prof[0]], (0, 0, i * th), TYRE, verts=20, cap_top=False, cap_bot=False)
        o.rotation_euler = (0, 0, i * 0.35)
        for k in range(10):      # tread blocks
            a = 2 * math.pi * k / 10 + i * 0.3
            box(c, "tread", (0.012, 0.08, th - 0.08), ((R + 0.001) * math.cos(a), (R + 0.001) * math.sin(a), i * th + th / 2), M['metal'], rot=(0, 0, a))
    o = join(c, "tire_stack"); fz['fit'](o, W, W, H)
    return finish(c, "tire_stack", (W, W, H))


def chest_freezer():
    c = piece("chest_freezer"); W, D, H = 1.3, 0.7, 0.88
    box(c, "body", (W, D - 0.02, H - 0.1), (0, 0.01, 0.04 + (H - 0.1) / 2), FREEZER, bevel=0.015)
    box(c, "lid", (W - 0.01, D - 0.03, 0.06), (0, 0.0, H - 0.03), FREEZER, bevel=0.012)
    box(c, "seal", (W - 0.02, D - 0.04, 0.012), (0, 0.0, H - 0.066), M['plastic_blk'])
    box(c, "handle", (0.3, 0.02, 0.03), (0, -D / 2 + 0.01, H - 0.05), M['plastic_blk'], bevel=0.005)
    box(c, "badge", (0.14, 0.006, 0.05), (-0.4, -D / 2 + 0.012, 0.55), M['steel'])
    for i in range(6):
        box(c, "vent", (0.02, 0.008, 0.12), (0.35 + i * 0.04, -D / 2 + 0.011, 0.2), M['plastic_blk'])
    box(c, "base", (W - 0.06, D - 0.08, 0.04), (0, 0.02, 0.02), M['plastic_blk'])
    return finish(c, "chest_freezer", (W, D, H))


# ---------------------------------------------------------------------------- bar
BAR_WOOD = mat("bar_wood", "#5A3A24", 0.45)
BAR_DK = mat("bar_wood_dk", "#3F2A1C", 0.5)
BAR_TOP = mat("bar_top", "#2A1A10", 0.25)
FELT = mat("pool_felt", "#1F6B45", 0.95)
LEATHER = mat("leather_red", "#7A1F2B", 0.55)


def bar_counter():
    c = piece("bar_counter"); W, D, H = 5.0, 0.7, 1.1; t = 0.06
    box(c, "top", (W, D, t), (0, 0, H - t / 2), BAR_TOP, bevel=0.02, segs=2)
    box(c, "body", (W - 0.1, 0.25, H - t - 0.1), (0, -0.025, 0.1 + (H - t - 0.1) / 2), BAR_WOOD)
    box(c, "kick", (W - 0.14, 0.22, 0.1), (0, -0.01, 0.05), BAR_DK)
    for x in (-W / 2 + 0.07, W / 2 - 0.07):
        box(c, "end", (0.04, 0.44, H - t), (x, 0.1, (H - t) / 2), BAR_DK)
    box(c, "floorboard", (W - 0.18, 0.22, 0.03), (0, 0.22, 0.05), BAR_DK)
    n = 12
    for i in range(n):     # panelled front (customers' side, -Y)
        x = -W / 2 + 0.05 + (i + 0.5) * (W - 0.1) / n
        box(c, "panel", ((W - 0.1) / n - 0.06, 0.02, 0.62), (x, -0.16, 0.22 + 0.36), BAR_DK, bevel=0.006)
    box(c, "trim", (W - 0.1, 0.03, 0.05), (0, -0.165, 0.97), BAR_DK)
    # brass foot rail on brackets
    cyl(c, "footrail", 0.025, 0.025, W - 0.3, (0, -0.3, 0.2), M['handle'], rot=(0, math.pi / 2, 0), verts=10)
    for x in (-2.1, -1.05, 0.0, 1.05, 2.1):
        box(c, "bracket", (0.03, 0.15, 0.03), (x, -0.24, 0.2), M['handle'])
    # bartender side: open shelves with glasses
    for z in (0.35, 0.7):
        box(c, "shelf", (W - 0.18, 0.22, 0.02), (0, 0.22, z), BAR_DK)
    r = random.Random(4)
    for z in (0.36, 0.71):
        for k in range(14):
            x = -W / 2 + 0.3 + k * 0.32 + r.uniform(-0.05, 0.05)
            lathe(c, "glass", [(0, 0), (0.035, 0), (0.04, 0.12), (0.036, 0.12), (0.031, 0.01), (0, 0.01)], (x, 0.22, z + 0.01), M['glass'], verts=10)
    return finish(c, "bar_counter", (W, D, H))


def back_bar():
    c = piece("back_bar"); W, D, H = 4.6, 0.5, 1.0
    box(c, "top", (W, D, 0.04), (0, 0, H - 0.02), BAR_TOP, bevel=0.008)
    box(c, "body", (W - 0.02, D - 0.04, H - 0.04 - 0.08), (0, 0.02, 0.08 + (H - 0.12) / 2), BAR_DK)
    box(c, "kick", (W - 0.06, D - 0.08, 0.08), (0, 0.04, 0.04), M['plastic_blk'])
    for i, x in enumerate((-1.8, -1.05, 1.05, 1.8)):
        box(c, "door", (0.72, 0.02, 0.8), (x, -D / 2 + 0.03, 0.08 + 0.42), BAR_WOOD, bevel=0.004)
        box(c, "pull", (0.014, 0.02, 0.14), (x + (0.3 if i % 2 else -0.3), -D / 2 + 0.012, 0.6), M['handle'])
    # bottle fridge with glass doors in the middle
    box(c, "fridge", (1.36, 0.02, 0.8), (0, -D / 2 + 0.03, 0.08 + 0.42), M['metal'], bevel=0.004)
    box(c, "fglass", (1.26, 0.006, 0.7), (0, -D / 2 + 0.018, 0.08 + 0.42), M['glass'])
    r = random.Random(9)
    for z in (0.18, 0.5):
        for k in range(9):
            x = -0.56 + k * 0.14
            lathe(c, "bottle", [(0, 0), (0.032, 0), (0.032, 0.14), (0.012, 0.2), (0.012, 0.24), (0, 0.24)], (x, -0.12, z),
                  mat("beer_" + str(k % 3), ("#5B3A12", "#2E5A2A", "#B88A2E")[k % 3], 0.2), verts=8)
    return finish(c, "back_bar", (W, D, H))


def bar_stools():
    c = piece("bar_stools"); W, D, H = 3.8, 0.4, 0.75; n = 5
    for i in range(n):
        x = -W / 2 + 0.2 + i * (W - 0.4) / (n - 1)
        lathe(c, "seat", [(0, H - 0.07), (0.19, H - 0.07), (0.2, H - 0.04), (0.19, H), (0, H)], (x, 0, 0), LEATHER, verts=16)
        cyl(c, "post", 0.03, 0.03, H - 0.1, (x, 0, 0.03 + (H - 0.1) / 2), M['chrome'], verts=10)
        lathe(c, "base", [(0, 0), (0.2, 0), (0.19, 0.03), (0.04, 0.05), (0, 0.05)], (x, 0, 0), M['metal'], verts=16)
        lathe(c, "ring", [(0.15, 0.3), (0.165, 0.3), (0.165, 0.32), (0.15, 0.32)], (x, 0, 0), M['chrome'], verts=16)
        for a in range(4):
            box(c, "spoke", (0.15, 0.015, 0.015), (x + math.cos(a * math.pi / 2) * 0.08, math.sin(a * math.pi / 2) * 0.08, 0.31), M['chrome'], rot=(0, 0, a * math.pi / 2))
    return finish(c, "bar_stools", (W, D, H))


def pool_table():
    c = piece("pool_table"); W, D, H = 2.5, 1.4, 0.82; felt = 0.78; rw = 0.12
    box(c, "bed", (W - 2 * rw + 0.02, D - 2 * rw + 0.02, 0.06), (0, 0, felt - 0.03), FELT)
    for y in (-D / 2 + rw / 2, D / 2 - rw / 2):
        box(c, "rail", (W, rw, 0.07), (0, y, H - 0.035), BAR_WOOD, bevel=0.01)
        box(c, "cushion", (W - 2 * rw - 0.12, 0.03, 0.03), (0, y - math.copysign(rw / 2 + 0.012, y), felt + 0.016), FELT)
    for x in (-W / 2 + rw / 2, W / 2 - rw / 2):
        box(c, "rail", (rw, D - 2 * rw, 0.07), (x, 0, H - 0.035), BAR_WOOD)
        box(c, "cushion", (0.03, D - 2 * rw - 0.12, 0.03), (x - math.copysign(rw / 2 + 0.012, x), 0, felt + 0.016), FELT)
    for x in (-W / 2 + rw, 0, W / 2 - rw):
        for y in (-D / 2 + rw, D / 2 - rw):
            cyl(c, "pocket", 0.06, 0.06, 0.012, (x, y, felt + 0.002), M['plastic_blk'], verts=12)
    box(c, "apron", (W - 0.12, D - 0.12, 0.2), (0, 0, H - 0.07 - 0.1), BAR_DK)
    for x in (-W / 2 + 0.2, W / 2 - 0.2):
        for y in (-D / 2 + 0.2, D / 2 - 0.2):
            box(c, "leg", (0.14, 0.14, H - 0.27), (x, y, (H - 0.27) / 2), BAR_DK, bevel=0.01)
    return finish(c, "pool_table", (W, D, H))


def pub_table(fid):
    c = piece(fid); W, H = 0.8, 1.05
    lathe(c, "top", [(0, H - 0.04), (W / 2 - 0.01, H - 0.04), (W / 2, H - 0.03), (W / 2, H - 0.01), (W / 2 - 0.01, H), (0, H)], (0, 0, 0), BAR_WOOD, verts=24)
    cyl(c, "column", 0.04, 0.04, H - 0.1, (0, 0, 0.04 + (H - 0.1) / 2), M['metal'], verts=10)
    lathe(c, "foot", [(0, 0), (0.3, 0), (0.29, 0.03), (0.06, 0.05), (0, 0.05)], (0, 0, 0), M['metal'], verts=20)
    lathe(c, "ring", [(0.2, 0.28), (0.215, 0.28), (0.215, 0.3), (0.2, 0.3)], (0, 0, 0), M['handle'], verts=16)
    for a in range(4):
        box(c, "spoke", (0.2, 0.015, 0.015), (math.cos(a * math.pi / 2) * 0.11, math.sin(a * math.pi / 2) * 0.11, 0.29), M['handle'], rot=(0, 0, a * math.pi / 2))
    box(c, "coaster", (0.09, 0.09, 0.003), (0.18, -0.12, H - 0.0015), mat("coaster", "#C9B27C", 0.9))
    return finish(c, fid, (W, W, H))


def jukebox():   # west wall, front faces app +x (Blender +X)
    c = piece("jukebox"); W, D, H = 0.6, 0.9, 1.55
    # arched side profile in (u = y, v = z), extruded along X
    prof = [(-D / 2, 0)] + [(-D / 2 + 0.0, 1.1)] + [(math.cos(math.pi - a * math.pi / 8) * D / 2, 1.1 + math.sin(math.pi - a * math.pi / 8) * 0.45) for a in range(1, 8)] + [(D / 2, 1.1), (D / 2, 0)]
    o = prism(c, "cab", [(u, v) for (u, v) in prof], W - 0.04, (0, 0, 0), mat("jukebox_body", "#7A1F2B", 0.35)); o.rotation_euler = (0, 0, math.pi / 2)
    o.location.x = -0.02
    glow = [mat("juke_amber", "#F2A93B", 0.3), mat("juke_blue", "#4FA3E0", 0.3), mat("juke_pink", "#E05A9B", 0.3)]
    for i, y in enumerate((-D / 2 + 0.07, D / 2 - 0.07)):
        box(c, "tube", (0.03, 0.06, 1.0), (W / 2 - 0.035, y, 0.55), glow[i])
    box(c, "window", (0.02, D - 0.24, 0.32), (W / 2 - 0.03, 0, 1.05), M['glass'])
    box(c, "records", (0.02, D - 0.3, 0.26), (W / 2 - 0.045, 0, 1.05), M['plastic_blk'])
    box(c, "panel", (0.02, D - 0.24, 0.24), (W / 2 - 0.03, 0, 0.72), glow[0])
    for k in range(10):
        box(c, "btn", (0.02, 0.04, 0.02), (W / 2 - 0.012, -0.25 + k * 0.055, 0.62), glow[k % 3])
    box(c, "grille", (0.02, D - 0.24, 0.36), (W / 2 - 0.03, 0, 0.3), M['metal'])
    for k in range(6):
        box(c, "slot", (0.025, D - 0.3, 0.018), (W / 2 - 0.02, 0, 0.17 + k * 0.05), M['handle'])
    box(c, "plinth", (W, D, 0.06), (0, 0, 0.03), M['metal'])
    o = join(c, "jukebox"); fz['fit'](o, W, D, H)
    return finish(c, "jukebox", (W, D, H))


# ---------------------------------------------------------------------------- classroom
DESK_WOOD = mat("desk_laminate", "#B08A5A", 0.5)
DESK_FRAME = mat("desk_frame", "#2E4A6B", 0.45, 0.4)
SEAT = mat("chair_seat", "#2E4A6B", 0.5)


def teacher_desk():
    c = piece("teacher_desk"); W, D, H = 1.5, 0.75, 0.76; t = 0.035
    box(c, "top", (W, D, t), (0, 0, H - t / 2), mat("wood_teak", "#6B4A32", 0.5), bevel=0.006)
    box(c, "modesty", (W - 0.06, 0.02, 0.5), (0, -D / 2 + 0.03, H - t - 0.25), M['wood'])   # faces the class (south)
    pw = 0.42
    for x in (-W / 2 + pw / 2 + 0.02, W / 2 - pw / 2 - 0.02):
        box(c, "ped", (pw, D - 0.06, H - t), (x, 0.01, (H - t) / 2), M['wood'])
        for i in range(3):
            z = 0.08 + i * 0.21
            box(c, "drw", (pw - 0.04, 0.014, 0.19), (x, D / 2 - 0.02 + 0.007, z + 0.095), M['wood_light'], bevel=0.003)
            box(c, "pull", (0.12, 0.012, 0.014), (x, D / 2 - 0.006, z + 0.16), M['chrome'])
    box(c, "blotter", (0.6, 0.4, 0.002), (0, 0.0, H - 0.001), mat("blotter_green", "#2F4A3A", 0.7))
    o = join(c, "teacher_desk")
    return finish(c, "teacher_desk", (W, D, H))


def student_desks(fid):
    """Four single desks in a row (north half) with their chairs tucked in behind (south half)."""
    c = piece(fid); W, D, H = 3.2, 1.0, 0.76; n = 4; dw = W / n - 0.004
    for i in range(n):
        x = -W / 2 + (i + 0.5) * W / n
        box(c, "top", (dw, 0.5, 0.025), (x, D / 2 - 0.25, H - 0.0125), DESK_WOOD, bevel=0.004)
        box(c, "tray", (dw - 0.1, 0.36, 0.015), (x, D / 2 - 0.24, H - 0.14), DESK_FRAME)
        for sx in (-dw / 2 + 0.03, dw / 2 - 0.03):
            for y in (D / 2 - 0.04, 0.04):
                box(c, "dleg", (0.025, 0.025, H - 0.025), (x + sx, y, (H - 0.025) / 2), DESK_FRAME)
            box(c, "foot", (0.03, 0.46, 0.02), (x + sx, D / 2 - 0.25, 0.01), DESK_FRAME)
        # chair, facing north (towards the desk and the board)
        cy = -0.24; sw = 0.4
        box(c, "seat", (sw, 0.38, 0.03), (x, cy, 0.44), SEAT, bevel=0.006)
        box(c, "back", (sw, 0.025, 0.2), (x, cy - 0.2, H - 0.1), SEAT, rot=(math.radians(8), 0, 0), bevel=0.006)
        for sx in (-sw / 2 + 0.02, sw / 2 - 0.02):
            for y in (cy - 0.17, cy + 0.17):
                hh = 0.43 if y > cy else H - 0.03
                box(c, "cleg", (0.02, 0.02, hh), (x + sx, y, hh / 2), DESK_FRAME)
    o = join(c, fid); fz['fit'](o, W, D, H)
    return finish(c, fid, (W, D, H))


def class_cabinet():   # east wall, front faces app -x (Blender -X)
    c = piece("class_cabinet"); W, D, H = 0.5, 1.6, 1.0
    box(c, "top", (W, D, 0.03), (0, 0, H - 0.015), mat("wood_teak", "#6B4A32", 0.5), bevel=0.005)
    box(c, "body", (W - 0.02, D - 0.02, H - 0.03 - 0.06), (0.01, 0, 0.06 + (H - 0.09) / 2), M['wood'])
    box(c, "plinth", (W - 0.06, D - 0.06, 0.06), (0.02, 0, 0.03), M['wood_dark'])
    box(c, "shelfback", (0.01, D - 0.12, 0.38), (W / 2 - 0.03, 0, 0.75), M['wood_dark'])
    box(c, "shelf", (W - 0.06, D - 0.12, 0.02), (0.0, 0, 0.56), M['wood_light'])
    r = random.Random(17); y = -D / 2 + 0.1
    while y < D / 2 - 0.12:   # books on the open upper shelf
        bw = r.uniform(0.03, 0.06); bh = r.uniform(0.2, 0.3)
        box(c, "book", (0.22, bw, bh), (-0.05, y + bw / 2, 0.57 + bh / 2), r.choice(M['book']))
        y += bw + 0.004
    for y in (-D / 4, D / 4):
        box(c, "door", (0.02, D / 2 - 0.04, 0.46), (-W / 2 + 0.02, y, 0.06 + 0.25), M['wood_light'], bevel=0.003)
        box(c, "knob", (0.02, 0.03, 0.03), (-W / 2 + 0.01, y - math.copysign(0.3, y), 0.42), M['handle'])
    return finish(c, "class_cabinet", (W, D, H))


BUILD_B = [car, garage_bench, tool_chest, storage_shelf, tire_stack, chest_freezer, bar_counter, back_bar, bar_stools, pool_table,
           lambda: pub_table("pub_table_a"), lambda: pub_table("pub_table_b"), jukebox, teacher_desk,
           lambda: student_desks("student_desks_a"), lambda: student_desks("student_desks_b"), lambda: student_desks("student_desks_c"),
           lambda: student_desks("student_desks_d"), class_cabinet]
for i, f in enumerate(BUILD_B):
    f().location = (i * 5.5, -40, 0)
