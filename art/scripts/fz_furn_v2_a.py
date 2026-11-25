# Furniture for the hotel room and kitchen (2026-11). Run after fz_lib / fz_furn_common.
# Blender X = app x, Blender Y = -app z, so "front" (app +z) is Blender -Y. Origin bottom centre.
import bpy, math, random
mat = fz['mat']

WOOD_H = mat("wood_walnut", "#5A3E2B", 0.55)
WOOD_H2 = mat("wood_walnut_dk", "#3E2A1C", 0.6)
LINEN = mat("linen_white", "#ECE9E2", 0.9)
RUNNER = mat("fabric_burgundy", "#6E2232", 0.95)
TAN = mat("leather_tan", "#8C6D4F", 0.6)
TAN_DK = mat("leather_tan_dk", "#6A5039", 0.65)
CLOTH = mat("tablecloth", "#EAE6DC", 0.9)
BRASS = M['handle']; CHROME = M['chrome']


def legs4(c, W, D, h, inset, r=0.022, m=None, verts=8, z0=0.0, taper=0.7):
    for x in (-W / 2 + inset, W / 2 - inset):
        for y in (-D / 2 + inset, D / 2 - inset):
            cyl(c, "leg", r, r * taper, h, (x, y, z0 + h / 2), m or WOOD_H2, verts=verts)


# ---------------------------------------------------------------------------- hotel room
def hotel_bed():
    c = piece("hotel_bed"); W, D, H = 2.0, 2.3, 0.6      # pillows at the back (Blender +Y, against the wall headboard)
    box(c, "plinth", (W - 0.12, D - 0.12, 0.08), (0, 0, 0.04), WOOD_H2)
    box(c, "frame", (W, D, 0.24), (0, 0, 0.08 + 0.12), WOOD_H, bevel=0.012)
    box(c, "mattress", (W - 0.04, D - 0.04, 0.2), (0, 0, 0.32 + 0.1), LINEN, bevel=0.035, segs=2)
    # duvet top 2 mm under the box top, so items on the bed rest on it (the runner and pillows reach 0.6)
    box(c, "duvet", (W, D - 0.62, 0.078), (0, -0.31, 0.52 + 0.039), LINEN, bevel=0.022, segs=2)
    box(c, "turn", (W, 0.22, 0.078), (0, D / 2 - 0.62 - 0.11 + 0.0, 0.52 + 0.039), LINEN, bevel=0.03, segs=2)
    box(c, "runner", (W, 0.46, 0.012), (0, -D / 2 + 0.42, 0.58 + 0.006 + 0.008), RUNNER, bevel=0.004)
    for x in (-0.47, 0.47):
        box(c, "pillow", (0.82, 0.44, 0.068), (x, D / 2 - 0.3, 0.532 + 0.034), LINEN, bevel=0.028, segs=2)
    box(c, "cushion", (0.5, 0.16, 0.06), (0, D / 2 - 0.58, 0.548), RUNNER, rot=(math.radians(-12), 0, 0), bevel=0.02, segs=2)
    return finish(c, "hotel_bed", (W, D, H))


def nightstand(fid):
    c = piece(fid); W, D, H = 0.5, 0.45, 0.55
    box(c, "top", (W, D, 0.03), (0, 0, H - 0.015), WOOD_H, bevel=0.006)
    box(c, "body", (W - 0.04, D - 0.03, H - 0.03 - 0.1), (0, 0.005, 0.1 + (H - 0.13) / 2), WOOD_H2)
    for i, z in enumerate((0.19, 0.38)):
        box(c, f"drw{i}", (W - 0.08, 0.014, 0.16), (0, -D / 2 + 0.012, z), WOOD_H, bevel=0.003)
        cyl(c, "knob", 0.012, 0.01, 0.02, (0, -D / 2 + 0.01, z), BRASS, rot=(math.pi / 2, 0, 0), verts=8)
    legs4(c, W, D, 0.1, 0.05, 0.018)
    return finish(c, fid, (W, D, H))


def writing_desk():   # west wall, faces app +x (Blender +X)
    c = piece("writing_desk"); W, D, H = 0.6, 1.3, 0.75; t = 0.03
    box(c, "top", (W, D, t), (0, 0, H - 0.003 - t / 2), WOOD_H, bevel=0.006)
    box(c, "pad", (0.4, 0.62, 0.003), (0.02, 0.1, H - 0.0015), mat("leather_green", "#2F4A3A", 0.55))
    box(c, "apron", (W - 0.08, D - 0.08, 0.1), (0, 0, H - t - 0.05), WOOD_H2)
    box(c, "drawer", (0.014, 0.5, 0.075), (W / 2 - 0.04 + 0.007, 0.0, H - t - 0.05), WOOD_H, bevel=0.003)
    cyl(c, "knob", 0.011, 0.009, 0.02, (W / 2 - 0.026, 0.0, H - t - 0.05), BRASS, rot=(0, math.pi / 2, 0), verts=8)
    legs4(c, W, D, H - t - 0.003, 0.045, 0.024, verts=8)
    box(c, "stretcher", (0.03, D - 0.12, 0.03), (-W / 2 + 0.045, 0, 0.18), WOOD_H2)
    return finish(c, "writing_desk", (W, D, H))


def tv_dresser():    # east wall, faces app -x (Blender -X)
    c = piece("tv_dresser"); W, D, H = 0.55, 1.6, 0.8
    box(c, "top", (W, D, 0.03), (0, 0, H - 0.015), WOOD_H, bevel=0.006)
    box(c, "body", (W - 0.03, D - 0.02, H - 0.03 - 0.08), (0.015, 0, 0.08 + (H - 0.11) / 2), WOOD_H2)
    box(c, "plinth", (W - 0.08, D - 0.06, 0.08), (0.03, 0, 0.04), WOOD_H2)
    for col, y in enumerate((-0.4, 0.4)):
        for row in range(3):
            z = 0.08 + 0.02 + row * 0.2 + 0.09
            box(c, "drw", (0.014, 0.74, 0.18), (-W / 2 + 0.022, y, z), WOOD_H, bevel=0.003)
            box(c, "pull", (0.014, 0.14, 0.014), (-W / 2 + 0.008, y, z + 0.04), BRASS)
    return finish(c, "tv_dresser", (W, D, H))


def armchair():
    c = piece("armchair"); W, D, H = 0.8, 0.8, 0.85
    box(c, "base", (W - 0.16, D - 0.08, 0.22), (0, 0.02, 0.12 + 0.11), TAN_DK, bevel=0.02)
    box(c, "seat", (W - 0.2, D - 0.24, 0.12), (0, -0.07, 0.34 + 0.06), TAN, bevel=0.04, segs=2)
    box(c, "back", (W - 0.06, 0.18, H - 0.12), (0, D / 2 - 0.09, 0.12 + (H - 0.12) / 2), TAN_DK, rot=(math.radians(-6), 0, 0), bevel=0.04, segs=2)
    box(c, "backcush", (W - 0.22, 0.12, 0.38), (0, D / 2 - 0.22, 0.46 + 0.19), TAN, rot=(math.radians(-10), 0, 0), bevel=0.04, segs=2)
    for x in (-W / 2 + 0.07, W / 2 - 0.07):
        box(c, "arm", (0.14, D - 0.04, 0.5), (x, 0.0, 0.12 + 0.25), TAN_DK, bevel=0.035, segs=2)
    legs4(c, W, D, 0.12, 0.06, 0.02, verts=8)
    o = join(c, "armchair"); fz['fit'](o, W, D, H)
    return finish(c, "armchair", (W, D, H))


def service_cart():
    c = piece("service_cart"); W, D, H = 0.9, 0.55, 0.85
    box(c, "cloth", (W - 0.02, D - 0.02, 0.01), (0, 0, H - 0.005), CLOTH)
    for y in (-D / 2 + 0.01, D / 2 - 0.01):
        box(c, "skirt", (W - 0.02, 0.006, 0.3), (0, y + (0.003 if y < 0 else -0.003), H - 0.16), CLOTH)
    for x in (-W / 2 + 0.01, W / 2 - 0.01):
        box(c, "skirt", (0.006, D - 0.02, 0.3), (x + (0.003 if x < 0 else -0.003), 0, H - 0.16), CLOTH)
    box(c, "tray", (W - 0.06, D - 0.06, 0.02), (0, 0, H - 0.02), CHROME)
    box(c, "shelf", (W - 0.1, D - 0.1, 0.015), (0, 0, 0.2), CHROME)
    for x in (-W / 2 + 0.05, W / 2 - 0.05):
        for y in (-D / 2 + 0.05, D / 2 - 0.05):
            cyl(c, "post", 0.012, 0.012, H - 0.15, (x, y, 0.08 + (H - 0.15) / 2), CHROME, verts=8)
            cyl(c, "wheel", 0.04, 0.04, 0.025, (x, y, 0.04), M['plastic_blk'], rot=(0, math.pi / 2, 0), verts=10)
    box(c, "handle", (0.02, D - 0.14, 0.02), (W / 2 - 0.012, 0, H - 0.08), CHROME)
    box(c, "napkin", (0.16, 0.16, 0.012), (0.24, 0.08, H + 0.006 - 0.012), mat("napkin_red", "#8E2B2B", 0.9))
    o = join(c, "service_cart"); fz['fit'](o, W, D, H)
    return finish(c, "service_cart", (W, D, H))


# ---------------------------------------------------------------------------- kitchen
CAB = mat("kitchen_cab", "#E2DED3", 0.45)
CAB_DK = mat("kitchen_cab_dk", "#B9B3A6", 0.5)
WORK = mat("worktop_stone", "#3D3B39", 0.35)
STEEL = mat("steel_brushed", "#B8BCC2", 0.3, 0.9)
HOB = mat("hob_glass", "#0E0F11", 0.08)
RING = mat("hob_ring", "#4A4D53", 0.4)
OAK = mat("wood_oak", "#8A6446", 0.55)
OAK_DK = mat("wood_oak_dk", "#6A4A32", 0.6)


def kitchen_counter():
    c = piece("kitchen_counter"); W, D, H = 4.6, 0.65, 0.92; t = 0.04
    sx0, sx1 = -0.9, -0.3           # sink (app x 2.4..3.0)
    hx0, hx1 = 1.1, 1.7             # hob (app x 4.4..5.0)
    sy0, sy1 = -0.24, 0.2
    def top(x0, x1, y0, y1):
        if x1 - x0 > 1e-3 and y1 - y0 > 1e-3:
            box(c, "work", (x1 - x0, y1 - y0, t), ((x0 + x1) / 2, (y0 + y1) / 2, H - t / 2), WORK)
    top(-W / 2, sx0, -D / 2, D / 2); top(sx1, hx0, -D / 2, D / 2); top(hx1, W / 2, -D / 2, D / 2)
    for (a, b) in ((sx0, sx1), (hx0, hx1)):
        top(a, b, -D / 2, sy0); top(a, b, sy1, D / 2)
    # sink basin
    bz = 0.2
    box(c, "basin_b", (sx1 - sx0, sy1 - sy0, 0.01), ((sx0 + sx1) / 2, (sy0 + sy1) / 2, H - bz), STEEL)
    for x in (sx0 + 0.005, sx1 - 0.005):
        box(c, "basin_s", (0.01, sy1 - sy0, bz), (x, (sy0 + sy1) / 2, H - bz / 2), STEEL)
    for y in (sy0 + 0.005, sy1 - 0.005):
        box(c, "basin_s", (sx1 - sx0 - 0.02, 0.01, bz), ((sx0 + sx1) / 2, y, H - bz / 2), STEEL)
    cyl(c, "drain", 0.035, 0.035, 0.004, ((sx0 + sx1) / 2, (sy0 + sy1) / 2, H - bz + 0.007), M['metal'], verts=12)
    # hob: black glass flush with the worktop, burner rings 2 mm proud of the glass (top = H)
    box(c, "hob", (hx1 - hx0, sy1 - sy0, 0.03), ((hx0 + hx1) / 2, (sy0 + sy1) / 2, H - 0.002 - 0.015), HOB)
    for (dx, dy, r) in ((-0.15, -0.1, 0.09), (0.15, -0.1, 0.07), (-0.15, 0.1, 0.07), (0.15, 0.1, 0.09)):
        cx, cy = (hx0 + hx1) / 2 + dx, (sy0 + sy1) / 2 + dy
        lathe(c, "ring", [(r - 0.008, H - 0.002), (r, H - 0.002), (r, H), (r - 0.008, H)], (cx, cy, 0), RING, verts=20)
    # base cabinets
    box(c, "carcass", (W, D - 0.05, H - t - 0.1), (0, 0.025, 0.1 + (H - t - 0.1) / 2), CAB_DK)
    box(c, "kick", (W - 0.02, 0.02, 0.1), (0, -D / 2 + 0.07, 0.05), M['plastic_blk'])
    n = 0; x = -W / 2 + 0.01
    widths = [0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.98]
    for i, wd in enumerate(widths):
        cx = x + wd / 2 - 0.005
        if abs(cx - (hx0 + hx1) / 2) < 0.31:      # oven under the hob
            box(c, "oven", (wd - 0.01, 0.02, 0.6), (cx, -D / 2 + 0.04, 0.12 + 0.3), STEEL, bevel=0.004)
            box(c, "ovenwin", (wd - 0.16, 0.004, 0.3), (cx, -D / 2 + 0.028, 0.12 + 0.28), HOB)
            box(c, "ovenbar", (wd - 0.12, 0.03, 0.02), (cx, -D / 2 + 0.012, 0.12 + 0.55), CHROME)
            box(c, "knobs", (wd - 0.01, 0.02, 0.12), (cx, -D / 2 + 0.04, 0.12 + 0.66), M['plastic_blk'])
        else:
            box(c, "drawer", (wd - 0.01, 0.02, 0.15), (cx, -D / 2 + 0.04, H - t - 0.09), CAB, bevel=0.003)
            box(c, "door", (wd - 0.01, 0.02, 0.52), (cx, -D / 2 + 0.04, 0.12 + 0.27), CAB, bevel=0.003)
            box(c, "dpull", (0.16, 0.02, 0.012), (cx, -D / 2 + 0.02, H - t - 0.07), CHROME)
            box(c, "pull", (0.012, 0.02, 0.14), (cx + wd / 2 - 0.07, -D / 2 + 0.02, 0.12 + 0.44), CHROME)
        x += wd
    return finish(c, "kitchen_counter", (W, D, H))


def kitchen_fridge():
    c = piece("kitchen_fridge"); W, D, H = 0.8, 0.7, 1.85
    box(c, "body", (W, D - 0.04, H - 0.04), (0, 0.02, 0.04 + (H - 0.04) / 2), STEEL, bevel=0.012)
    box(c, "freezer", (W - 0.01, 0.04, 0.55), (0, -D / 2 + 0.02, H - 0.285), STEEL, bevel=0.01)
    box(c, "fresh", (W - 0.01, 0.04, 1.2), (0, -D / 2 + 0.02, 0.04 + 0.61), STEEL, bevel=0.01)
    box(c, "gap", (W - 0.02, 0.03, 0.012), (0, -D / 2 + 0.03, H - 0.566), M['metal'])
    for (z, ln) in ((H - 0.29, 0.34), (0.04 + 0.9, 0.5)):
        box(c, "handle", (0.025, 0.03, ln), (-W / 2 + 0.07, -D / 2 - 0.0 + 0.015, z), CHROME, bevel=0.006)
    for x, z, col in ((0.12, 1.15, "#F4D03F"), (0.24, 1.05, "#7FB3D5"), (0.05, 0.95, "#F5B7B1")):
        box(c, "note", (0.09, 0.003, 0.09), (x, -D / 2 - 0.0, z), mat("note_" + col[1:], col, 0.9), rot=(0, 0.06, 0))
    box(c, "feet", (W - 0.06, D - 0.1, 0.04), (0, 0.04, 0.02), M['plastic_blk'])
    o = join(c, "kitchen_fridge"); fz['fit'](o, W, D, H)
    return finish(c, "kitchen_fridge", (W, D, H))


def stool(c, x, y, h, m_seat, m_leg):
    lathe(c, "seat", [(0, h - 0.05), (0.17, h - 0.05), (0.18, h - 0.03), (0.17, h), (0, h)], (x, y, 0), m_seat, verts=16)
    for a in range(4):
        ang = math.pi / 4 + a * math.pi / 2
        lx, ly = math.cos(ang) * 0.13, math.sin(ang) * 0.13
        box(c, "sleg", (0.03, 0.03, h - 0.05), (x + lx, y + ly, (h - 0.05) / 2), m_leg)
    lathe(c, "ring", [(0.15, 0.24), (0.165, 0.24), (0.165, 0.26), (0.15, 0.26)], (x, y, 0), m_leg, verts=16)


def kitchen_island():
    c = piece("kitchen_island"); W, D, H = 1.8, 0.9, 0.92; t = 0.04
    box(c, "work", (W, D, t), (0, 0, H - t / 2), WORK, bevel=0.004)
    box(c, "body", (W - 0.04, 0.56, H - t - 0.08), (0, 0.13, 0.08 + (H - t - 0.08) / 2), CAB_DK)
    box(c, "kick", (W - 0.08, 0.5, 0.08), (0, 0.16, 0.04), M['plastic_blk'])
    for i, x in enumerate((-0.6, 0.0, 0.6)):   # doors on the back (cook's side)
        box(c, "door", (0.58, 0.02, 0.72), (x, 0.42, 0.08 + 0.37), CAB, bevel=0.003)
        box(c, "pull", (0.16, 0.02, 0.012), (x, 0.44, 0.08 + 0.68), CHROME)
    for x in (-0.6, 0.0, 0.6):   # stools tucked under the overhang (south side)
        stool(c, x, -0.27, 0.66, OAK, M['metal'])
    return finish(c, "kitchen_island", (W, D, H))


def kitchen_table():
    c = piece("kitchen_table"); W, D, H = 1.2, 0.8, 0.75; t = 0.035
    box(c, "top", (W, D, t), (0, 0, H - t / 2), OAK, bevel=0.008)
    box(c, "apron", (W - 0.12, D - 0.12, 0.08), (0, 0, H - t - 0.04), OAK_DK)
    legs4(c, W, D, H - t, 0.07, 0.03, OAK_DK, verts=8, taper=0.75)
    box(c, "runner", (0.3, D - 0.14, 0.002), (0, 0, H + 0.001 - 0.002), mat("table_runner", "#C9B27C", 0.9))
    return finish(c, "kitchen_table", (W, D, H))


def kitchen_chair(fid, back_y):   # back_y +1: backrest north (+Y); -1: south
    c = piece(fid); W, D, H = 0.45, 0.45, 0.9; s = 0.46
    box(c, "seat", (W, D, 0.04), (0, 0, s - 0.02), OAK, bevel=0.008)
    for x in (-W / 2 + 0.03, W / 2 - 0.03):
        for y in (-D / 2 + 0.03, D / 2 - 0.03):
            hgt = H if (y > 0) == (back_y > 0) else s - 0.04
            box(c, "cleg", (0.035, 0.035, hgt), (x, y, hgt / 2), OAK_DK)
    yb = back_y * (D / 2 - 0.03)
    for z in (0.62, 0.74, 0.86):
        box(c, "slat", (W - 0.06, 0.02, 0.06), (0, yb, z), OAK)
    box(c, "rail", (W - 0.06, 0.02, 0.03), (0, 0, 0.15), OAK_DK, rot=(0, 0, math.pi / 2))
    return finish(c, fid, (W, D, H))


def pantry():   # west wall, faces app +x (Blender +X)
    c = piece("pantry"); W, D, H = 0.6, 0.9, 2.1
    box(c, "body", (W - 0.02, D, H - 0.08), (-0.01, 0, 0.08 + (H - 0.08) / 2), CAB_DK)
    box(c, "kick", (W - 0.08, D - 0.04, 0.08), (-0.03, 0, 0.04), M['plastic_blk'])
    for y in (-D / 4, D / 4):
        box(c, "door", (0.02, D / 2 - 0.006, H - 0.1), (W / 2 - 0.01, y, 0.08 + (H - 0.1) / 2 + 0.01), CAB, bevel=0.003)
        box(c, "pull", (0.02, 0.012, 0.3), (W / 2 - 0.0 - 0.01 + 0.01, y - math.copysign(0.17, y), 1.05), CHROME)
    o = join(c, "pantry"); fz['fit'](o, W, D, H)
    return finish(c, "pantry", (W, D, H))


BUILD_A = [hotel_bed, lambda: nightstand("nightstand_l"), lambda: nightstand("nightstand_r"), writing_desk, tv_dresser, armchair,
           service_cart, kitchen_counter, kitchen_fridge, kitchen_island, kitchen_table,
           lambda: kitchen_chair("kitchen_chair_n", 1), lambda: kitchen_chair("kitchen_chair_s", -1), pantry]
for i, f in enumerate(BUILD_A):
    f().location = (i * 2.6, -30, 0)
