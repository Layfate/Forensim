# Evidence and red herrings added in 2026-11 (second set). Run after fz_ev_common.py / fz_ev_common2.py
# in the same namespace (ev, done, lie, mat2, mm, lathe, box, cyl, prism, tube, hull ...).
# Modelled at real size in millimetres, long axis along Blender Y (app z), resting on z = 0.
import bpy, math, random
from mathutils import Vector, Matrix


def ring(c, name, r_in, r_out, z0, z1, loc, m, verts=24):
    return lathe(c, name, [(r_in, z0), (r_out, z0), (r_out, z1), (r_in, z1), (r_in, z0)], loc, m, verts=verts, cap_top=False, cap_bot=False)


# ---------------------------------------------------------------------------- evidence
def duct_tape():
    c = ev("duct_tape")
    tape = mat2("ev_duct_tape", "#A9AEB4", 0.35, 0.35); core = mat2("ev_tape_core", "#B08A5A", 0.9); edge = mat2("ev_tape_edge", "#8C9196", 0.5, 0.3)
    ring(c, "roll", 38 * mm, 50 * mm, 0, 48 * mm, (0, 10 * mm, 0), tape, verts=28)
    ring(c, "core", 34 * mm, 38 * mm, 0, 48 * mm, (0, 10 * mm, 0), core, verts=28)
    for z in (0.2 * mm, 47.8 * mm):
        ring(c, "side", 38.2 * mm, 49.8 * mm, z - 0.2 * mm, z + 0.2 * mm, (0, 10 * mm, 0), edge, verts=28)
    # loose end peeled off the roll down to the surface, torn ragged
    box(c, "peel", (44 * mm, 0.6 * mm, 30 * mm), (0, -40.5 * mm, 22 * mm), tape, rot=(math.radians(-28), 0, 0))
    pts = [(-22, 0), (22, 0), (22, -22), (16, -27), (11, -23), (6, -29), (0, -24), (-5, -30), (-11, -25), (-16, -29), (-22, -24)]
    o = prism(c, "tail", [(u * mm, v * mm) for u, v in pts], 0.6 * mm, (0, -46 * mm, 0.3 * mm), tape, axis='Z')
    return done(c, "duct_tape", (0.1, 0.05, 0.13), smooth=40)


def zip_ties():
    c = ev("zip_ties")
    nyl = mat2("ev_zip_nylon", "#1E1F22", 0.45)
    # a long tie lying straight
    box(c, "strap", (4.8 * mm, 190 * mm, 1.3 * mm), (-38 * mm, 0, 0.65 * mm), nyl)
    box(c, "head", (8 * mm, 9 * mm, 5 * mm), (-38 * mm, 95 * mm, 2.5 * mm), nyl, bevel=0.8 * mm)
    for k in range(18):   # ratchet teeth
        box(c, "tooth", (4 * mm, 0.6 * mm, 0.4 * mm), (-38 * mm, -60 * mm + k * 6 * mm, 1.5 * mm), nyl)
    # a loop that was pulled tight round wrists, then cut open
    pts = []
    for k in range(15):
        a = math.radians(20 + k * 22)
        pts.append((22 * mm + 30 * mm * math.cos(a), 20 * mm + 38 * mm * math.sin(a), 2.4 * mm))
    o = tube(c, "loop", pts, 2.2 * mm, nyl, sides=4, taper=False)
    box(c, "lhead", (8 * mm, 9 * mm, 5 * mm), (pts[-1][0], pts[-1][1], 2.5 * mm), nyl, bevel=0.8 * mm)
    box(c, "ltail", (4.8 * mm, 70 * mm, 1.3 * mm), (pts[-1][0] + 12 * mm, pts[-1][1] - 38 * mm, 0.65 * mm), nyl, rot=(0, 0, 0.35))
    return done(c, "zip_ties", (0.12, 0.006, 0.2), smooth=30)


def bolt_cutters():
    c = ev("bolt_cutters")
    red = mat2("ev_cutter_red", "#C0392B", 0.4, 0.3); grip = mat2("ev_grip_black", "#141517", 0.9); steel = EM['steel_dk']; jaw = EM['steel']
    for s in (-1, 1):   # handles spread slightly towards the grips
        a = s * math.radians(2.8)
        h = box(c, "handle", (16 * mm, 400 * mm, 16 * mm), (s * 18 * mm, 60 * mm, 20 * mm), red, rot=(0, 0, a), bevel=3 * mm)
        g = box(c, "grip", (24 * mm, 170 * mm, 24 * mm), (s * 26 * mm, 185 * mm, 20 * mm), grip, rot=(0, 0, a), bevel=6 * mm, segs=2)
        box(c, "arm", (14 * mm, 120 * mm, 12 * mm), (s * 9 * mm, -170 * mm, 20 * mm), steel, rot=(0, 0, -a * 2))
        pts = [(0, 0), (s * 17, 0), (s * 16, -28), (s * 9, -52), (s * 0.5, -64)]
        prism(c, "jaw", [(u * mm, v * mm) for u, v in pts], 14 * mm, (s * 0.3 * mm, -236 * mm, 20 * mm), steel, axis='Z')
        prism(c, "edge", [(u * 0.55 * mm, v * mm) for u, v in pts[2:] + [(0, -28)]], 14.4 * mm, (s * 0.3 * mm, -236 * mm, 20 * mm), jaw, axis='Z')
    for y in (-230 * mm, -110 * mm):
        cyl(c, "pivot", 9 * mm, 9 * mm, 40 * mm, (0, y, 20 * mm), steel, rot=(0, math.pi / 2, 0), verts=10)
    box(c, "plate", (44 * mm, 60 * mm, 4 * mm), (0, -230 * mm, 28 * mm), steel, bevel=1 * mm)
    return done(c, "bolt_cutters", (0.14, 0.04, 0.6), smooth=35)


def flashlight():
    c = ev("flashlight")
    body = mat2("ev_torch_body", "#24262A", 0.4, 0.6); knurl = mat2("ev_torch_knurl", "#121315", 0.8); lens = mat2("ev_torch_lens", "#D8E8F0", 0.05, 0.0, 0.6)
    refl = EM['steel']
    lathe(c, "tail", [(0, 0), (14 * mm, 0), (15 * mm, 3 * mm), (15 * mm, 20 * mm)], m=knurl, verts=16, cap_top=False)
    lathe(c, "barrel", [(15 * mm, 20 * mm), (15 * mm, 140 * mm), (15.5 * mm, 140 * mm)], m=body, verts=16, cap_top=False, cap_bot=False)
    lathe(c, "head", [(15.5 * mm, 140 * mm), (19.5 * mm, 165 * mm), (20 * mm, 196 * mm), (18 * mm, 199 * mm)], m=body, verts=16, cap_top=False, cap_bot=False)
    lathe(c, "refl", [(18 * mm, 199 * mm), (6 * mm, 192 * mm), (0, 192 * mm)], m=refl, verts=16, cap_top=False, cap_bot=False)
    lathe(c, "lens", [(0, 197 * mm), (17.8 * mm, 197 * mm)], m=lens, verts=16, cap_top=False, cap_bot=False)
    for k in range(6):
        ring(c, "grip", 15 * mm, 15.6 * mm, (40 + k * 12) * mm, (44 + k * 12) * mm, (0, 0, 0), knurl, verts=16)
    box(c, "switch", (8 * mm, 6 * mm, 14 * mm), (0, 15.5 * mm, 150 * mm), mat2("ev_torch_btn", "#3A3D42", 0.6), bevel=2 * mm)
    lie(c)
    return done(c, "flashlight", (0.04, 0.04, 0.2), smooth=40)


def handbag():
    c = ev("handbag")
    lea = mat2("ev_bag_leather", "#7A2E2A", 0.5); dark = mat2("ev_bag_lining", "#1E1416", 0.9); gold = EM['brass']
    pts = [(-150, 0), (150, 0), (130, 190), (-130, 190)]
    prism(c, "body", [(u * mm, v * mm) for u, v in pts], 130 * mm, (0, 0, 0), lea, bevel=6 * mm)
    box(c, "opening", (240 * mm, 100 * mm, 4 * mm), (0, 0, 189 * mm), dark)
    for y in (-52 * mm, 52 * mm):
        box(c, "zip", (250 * mm, 6 * mm, 3 * mm), (0, y, 191 * mm), gold)
    box(c, "pull", (8 * mm, 3 * mm, 22 * mm), (118 * mm, -56 * mm, 182 * mm), gold, bevel=1 * mm)
    for y in (-35 * mm, 35 * mm):
        pts3 = [(-75 * mm + 150 * mm * k / 10, y, 190 * mm + 48 * mm * math.sin(math.pi * k / 10)) for k in range(11)]
        tube(c, "strap", pts3, 5 * mm, lea, sides=6, taper=False)
        for x in (-75 * mm, 75 * mm):
            box(c, "ring", (12 * mm, 4 * mm, 12 * mm), (x, y, 186 * mm), gold)
    box(c, "flapseam", (300 * mm - 10 * mm, 132 * mm, 3 * mm), (0, 0, 30 * mm), mat2("ev_bag_seam", "#5E211E", 0.6))
    return done(c, "handbag", (0.3, 0.24, 0.14), smooth=35)


def broken_bottle():
    c = ev("broken_bottle")
    g = mat2("ev_bottle_green", "#2F6B3A", 0.05, 0.0, 0.75); blood = EM['blood']
    prof = [(16 * mm, 0), (37 * mm, 0), (37 * mm, 130 * mm), (33 * mm, 160 * mm), (14 * mm, 192 * mm), (13 * mm, 228 * mm), (15 * mm, 232 * mm), (15 * mm, 240 * mm), (11 * mm, 240 * mm)]
    o = lathe(c, "glass", prof, m=g, verts=16, cap_top=False, cap_bot=False)
    r = random.Random(4)
    for v in o.data.vertices:      # jagged break where the base snapped off
        if v.co.z < 1e-4:
            v.co.z += r.uniform(0, 38) * mm
    stain = lathe(c, "stain", [(37.3 * mm, 26 * mm), (37.3 * mm, 60 * mm)], m=blood, verts=16, cap_top=False, cap_bot=False)
    for v in stain.data.vertices:
        v.co.z += r.uniform(-8, 10) * mm
    lie(c)
    return done(c, "broken_bottle", (0.075, 0.075, 0.24), smooth=40)


def brass_knuckles():
    c = ev("brass_knuckles")
    b = mat2("ev_knuckle", "#B8913A", 0.3, 1.0); blood = EM['blood']
    for i in range(4):
        x = (-40.5 + i * 27) * mm
        ring(c, "loop", 10 * mm, 14 * mm, 0, 12 * mm, (x, 6 * mm, 0), b, verts=16)
        box(c, "knuckle", (22 * mm, 8 * mm, 12 * mm), (x, 22 * mm, 6 * mm), b, bevel=3 * mm)
    pts = [(-55, -6), (55, -6), (48, -18), (30, -28), (0, -31), (-30, -28), (-48, -18)]
    prism(c, "grip", [(u * mm, v * mm) for u, v in pts], 12 * mm, (0, 0, 6 * mm), b, axis='Z', bevel=1.5 * mm)
    box(c, "bridge", (110 * mm, 6 * mm, 12 * mm), (0, -6 * mm, 6 * mm), b, bevel=1.5 * mm)
    for x in (-30, -6, 16):
        box(c, "blood", (12 * mm, 4 * mm, 9 * mm), (x * mm, 26.2 * mm, 6 * mm), blood, bevel=1 * mm)
    return done(c, "brass_knuckles", (0.12, 0.02, 0.065), smooth=35)


def shotgun_shell():
    c = ev("shotgun_shell")
    hull_m = mat2("ev_shell_red", "#B0282A", 0.4); brass = EM['brass']; dark = mat2("ev_shell_dark", "#5A1214", 0.5)
    lathe(c, "head", [(0, 0), (12.2 * mm, 0), (12.2 * mm, 1.5 * mm), (11.6 * mm, 1.8 * mm), (11.6 * mm, 15 * mm)], m=brass, verts=18, cap_top=False)
    lathe(c, "hull", [(11.6 * mm, 15 * mm), (11.6 * mm, 66 * mm), (10.4 * mm, 69 * mm), (4 * mm, 70 * mm), (0, 69 * mm)], m=hull_m, verts=18, cap_bot=False, cap_top=False)
    for k in range(6):
        a = k * math.pi / 3
        box(c, "crimp", (0.8 * mm, 7 * mm, 0.8 * mm), (math.cos(a) * 6 * mm, math.sin(a) * 6 * mm, 69.6 * mm), dark, rot=(0, 0, a - math.pi / 2))
    cyl(c, "primer", 3 * mm, 3 * mm, 0.6 * mm, (0, 0, 0.0), EM['nickel'], verts=10)
    lie(c)
    return done(c, "shotgun_shell", (0.024, 0.024, 0.07), smooth=40)


def ammo_box():
    c = ev("ammo_box")
    card = mat2("ev_ammo_card", "#3D5A3A", 0.7); tray = mat2("ev_ammo_tray", "#E8E2D0", 0.8); label = mat2("ev_ammo_label", "#E0B040", 0.6)
    W, L, H = 100 * mm, 140 * mm, 38 * mm
    # outer sleeve, open at the front
    box(c, "top", (W, L, 1.5 * mm), (0, 30 * mm, H - 0.75 * mm), card)
    box(c, "bottom", (W, L, 1.5 * mm), (0, 30 * mm, 0.75 * mm), card)
    for x in (-W / 2 + 0.75 * mm, W / 2 - 0.75 * mm):
        box(c, "side", (1.5 * mm, L, H), (x, 30 * mm, H / 2), card)
    box(c, "back", (W, 1.5 * mm, H), (0, 30 * mm + L / 2 - 0.75 * mm, H / 2), card)
    box(c, "label", (60 * mm, 40 * mm, 0.6 * mm), (0, 50 * mm, H + 0.3 * mm), label)
    # tray slid half out with rounds standing nose-up, several missing
    ty = -40 * mm
    box(c, "tray", (W - 4 * mm, 120 * mm, 3 * mm), (0, ty + 10 * mm, 3 * mm), tray)
    box(c, "grid", (W - 4 * mm, 120 * mm, 18 * mm), (0, ty + 10 * mm, 13 * mm), tray)
    r = random.Random(12)
    for i in range(5):
        for j in range(10):
            if (i * 10 + j) in (2, 3, 7, 13, 14, 26, 31, 32, 45):
                continue
            x = (-36 + i * 18) * mm; y = ty + 10 * mm + (-54 + j * 12) * mm
            if y > -10 * mm:
                continue     # still inside the sleeve
            lathe(c, "rnd", [(0, 4 * mm), (4.8 * mm, 4 * mm), (4.8 * mm, 22 * mm), (4.5 * mm, 26 * mm), (2.5 * mm, 31 * mm), (0, 32 * mm)], (x, y, 0),
                  EM['copper'] if r.random() < 0.5 else EM['brass'], verts=8, cap_bot=False)
    return done(c, "ammo_box", (0.1, 0.04, 0.2), smooth=30)


def burnt_spoon():
    c = ev("burnt_spoon")
    st = EM['steel']; soot = mat2("ev_soot", "#1A1612", 0.95); res = mat2("ev_residue", "#6B4A22", 0.7)
    bowl = lathe(c, "bowl", [(0, 0), (12 * mm, 0.6 * mm), (19 * mm, 3 * mm), (21 * mm, 7 * mm), (20 * mm, 7.5 * mm), (17.5 * mm, 4 * mm), (10 * mm, 2 * mm), (0, 1.6 * mm)], (0, 0, 0), st, verts=18, cap_top=False, cap_bot=False)
    bowl.scale = (1.0, 1.35, 1.0)
    burnt = lathe(c, "soot", [(0, -0.2 * mm), (14 * mm, 0.5 * mm), (18.5 * mm, 2.8 * mm)], (0, 0, 0), soot, verts=18, cap_top=False, cap_bot=False)
    burnt.scale = (1.0, 1.35, 1.0)
    cyl(c, "residue", 11 * mm, 11 * mm, 0.6 * mm, (0, -2 * mm, 2.2 * mm), res, verts=14).scale = (1.0, 1.3, 1.0)
    pts = [(-4, 0), (4, 0), (6, 60), (7, 96), (0, 102), (-7, 96), (-6, 60)]
    h = prism(c, "handle", [(u * mm, v * mm) for u, v in pts], 2 * mm, (0, 24 * mm, 8 * mm), st, axis='Z')
    for v in h.data.vertices:     # handle rises from the bowl rim then flattens
        v.co.z += min(v.co.y, 0.03) * 0.25 - 0.0075
    return done(c, "burnt_spoon", (0.04, 0.02, 0.15), smooth=35)


def glass_pipe():
    c = ev("glass_pipe")
    gl = mat2("ev_pipe_glass", "#BFD9E0", 0.05, 0.0, 0.55); swirl = mat2("ev_pipe_swirl", "#3A7BD5", 0.2, 0.0, 0.8); soot = mat2("ev_soot", "#1A1612", 0.95)
    prof = [(4 * mm, 0), (6 * mm, 0), (6.5 * mm, 60 * mm), (9 * mm, 80 * mm), (15 * mm, 92 * mm), (17 * mm, 102 * mm), (15 * mm, 112 * mm), (9 * mm, 118 * mm), (7 * mm, 120 * mm)]
    lathe(c, "pipe", prof, m=gl, verts=16, cap_top=False, cap_bot=False)
    ring(c, "swirl", 6.6 * mm, 7.2 * mm, 30 * mm, 38 * mm, (0, 0, 0), swirl, verts=16)
    ring(c, "swirl2", 15.2 * mm, 17.3 * mm, 98 * mm, 104 * mm, (0, 0, 0), swirl, verts=16)
    lathe(c, "ash", [(0, 117 * mm), (6.5 * mm, 118 * mm)], m=soot, verts=16, cap_top=False, cap_bot=False)
    lie(c)
    # the bowl opening faces up: turn the bulb's hole towards +Z
    return done(c, "glass_pipe", (0.035, 0.035, 0.12), smooth=40)


def paint_marker():
    c = ev("paint_marker")
    body = mat2("ev_marker_body", "#E8E6E0", 0.5); band = mat2("ev_marker_blue", "#2B6CB0", 0.4); tip = mat2("ev_marker_tip", "#1F5FA8", 0.2)
    lathe(c, "end", [(0, 0), (10 * mm, 0), (11 * mm, 3 * mm), (11 * mm, 10 * mm)], m=band, verts=16, cap_top=False)
    lathe(c, "barrel", [(11 * mm, 10 * mm), (11 * mm, 112 * mm), (8 * mm, 120 * mm)], m=body, verts=16, cap_top=False, cap_bot=False)
    lathe(c, "collar", [(8 * mm, 120 * mm), (6 * mm, 124 * mm), (4 * mm, 124 * mm)], m=band, verts=16, cap_top=False, cap_bot=False)
    lathe(c, "nib", [(4 * mm, 124 * mm), (4 * mm, 134 * mm), (2 * mm, 138 * mm), (0, 138.5 * mm)], m=tip, verts=12, cap_bot=False)
    ring(c, "label", 11 * mm, 11.3 * mm, 40 * mm, 90 * mm, (0, 0, 0), band, verts=16)
    lie(c)
    return done(c, "paint_marker", (0.025, 0.025, 0.14), smooth=40)


# ---------------------------------------------------------------------------- red herrings
def stapler():
    c = ev("stapler")
    blk = mat2("rh_stapler", "#2E2F33", 0.45); chrome = EM['steel']; rub = mat2("rh_rubber", "#141517", 0.9)
    box(c, "base", (40 * mm, 165 * mm, 8 * mm), (0, 0, 4 * mm), blk, bevel=3 * mm)
    box(c, "anvil", (14 * mm, 22 * mm, 1.5 * mm), (0, -66 * mm, 8.6 * mm), chrome)
    box(c, "magazine", (22 * mm, 140 * mm, 14 * mm), (0, 4 * mm, 18 * mm), chrome)
    arm = box(c, "arm", (44 * mm, 158 * mm, 22 * mm), (0, 2 * mm, 36 * mm), blk, bevel=6 * mm, segs=2)
    box(c, "hinge", (30 * mm, 20 * mm, 30 * mm), (0, 70 * mm, 22 * mm), blk, bevel=4 * mm)
    box(c, "foot", (36 * mm, 150 * mm, 1.6 * mm), (0, 0, 0.8 * mm), rub)
    return done(c, "stapler", (0.045, 0.06, 0.17), smooth=35)


def calculator():
    c = ev("calculator")
    body = mat2("rh_calc", "#3A3D44", 0.5); lcd = mat2("rh_calc_lcd", "#8E9B7E", 0.2); key = mat2("rh_calc_key", "#22242A", 0.5)
    key2 = mat2("rh_calc_key2", "#C0392B", 0.5); solar = mat2("rh_solar", "#2A2030", 0.15)
    box(c, "body", (85 * mm, 150 * mm, 11 * mm), (0, 0, 5.5 * mm), body, bevel=3 * mm)
    box(c, "lcd", (66 * mm, 24 * mm, 1 * mm), (0, 46 * mm, 11.2 * mm), lcd)
    box(c, "solar", (36 * mm, 9 * mm, 1 * mm), (14 * mm, 64 * mm, 11.2 * mm), solar)
    for i in range(4):
        for j in range(5):
            box(c, "key", (14 * mm, 11 * mm, 4 * mm), ((-27 + i * 18) * mm, (18 - j * 16) * mm, 13 * mm), key2 if (i == 3 and j == 4) else key, bevel=1.2 * mm)
    return done(c, "calculator", (0.085, 0.015, 0.15), smooth=30)


def lighter():
    c = ev("lighter")
    body = mat2("rh_lighter", "#2E86C1", 0.15, 0.0, 0.85); cap = EM['steel']; btn = mat2("rh_lighter_btn", "#C0392B", 0.5)
    box(c, "body", (25 * mm, 62 * mm, 12 * mm), (0, -9 * mm, 6 * mm), body, bevel=4 * mm, segs=2)
    box(c, "hood", (24 * mm, 18 * mm, 11 * mm), (0, 30 * mm, 6 * mm), cap, bevel=1.5 * mm)
    cyl(c, "wheel", 4.5 * mm, 4.5 * mm, 8 * mm, (0, 33 * mm, 8 * mm), mat2("rh_wheel", "#5A5D63", 0.6, 0.8), rot=(0, math.pi / 2, 0), verts=12)
    box(c, "button", (14 * mm, 8 * mm, 3 * mm), (0, 24 * mm, 11.5 * mm), btn, bevel=1 * mm)
    return done(c, "lighter", (0.025, 0.012, 0.08), smooth=35)


def hand_sanitizer():
    c = ev("hand_sanitizer")
    gel = mat2("rh_gel", "#A8D8E8", 0.05, 0.0, 0.6); pump = mat2("rh_pump", "#F2F2EE", 0.4); lab = mat2("rh_san_label", "#1F7A8C", 0.6)
    lathe(c, "bottle", [(0, 0), (28 * mm, 0), (30 * mm, 4 * mm), (30 * mm, 96 * mm), (26 * mm, 106 * mm), (11 * mm, 110 * mm)], m=gel, verts=20, cap_top=False)
    ring(c, "label", 30 * mm, 30.4 * mm, 25 * mm, 75 * mm, (0, 0, 0), lab, verts=20)
    lathe(c, "collar", [(11 * mm, 108 * mm), (13 * mm, 108 * mm), (13 * mm, 120 * mm), (5 * mm, 122 * mm)], m=pump, verts=16, cap_bot=False)
    cyl(c, "stem", 4 * mm, 4 * mm, 24 * mm, (0, 0, 132 * mm), pump, verts=10)
    box(c, "head", (16 * mm, 40 * mm, 14 * mm), (0, -8 * mm, 151 * mm), pump, bevel=3 * mm)
    cyl(c, "nozzle", 3 * mm, 3 * mm, 8 * mm, (0, -28 * mm, 150 * mm), pump, rot=(math.pi / 2, 0, 0), verts=8)
    return done(c, "hand_sanitizer", (0.06, 0.16, 0.06), smooth=40)


def game_controller():
    c = ev("game_controller")
    body = mat2("rh_pad", "#1E1F23", 0.55); stick = mat2("rh_stick", "#2E3036", 0.7)
    cols = [mat2("rh_btn_" + n, h, 0.4) for n, h in (("g", "#2ECC71"), ("r", "#E74C3C"), ("b", "#3498DB"), ("y", "#F1C40F"))]
    box(c, "core", (100 * mm, 55 * mm, 26 * mm), (0, 10 * mm, 14 * mm), body, bevel=9 * mm, segs=2)
    for s in (-1, 1):
        g = box(c, "grip", (38 * mm, 70 * mm, 28 * mm), (s * 50 * mm, -14 * mm, 14 * mm), body, rot=(0, 0, s * 0.42), bevel=12 * mm, segs=2)
        box(c, "bumper", (36 * mm, 10 * mm, 10 * mm), (s * 40 * mm, 38 * mm, 22 * mm), body, bevel=3 * mm)
    for (x, y) in ((-32 * mm, 12 * mm), (14 * mm, -10 * mm)):
        cyl(c, "stickbase", 10 * mm, 10 * mm, 6 * mm, (x, y, 29 * mm), body, verts=14)
        cyl(c, "stick", 9 * mm, 9 * mm, 6 * mm, (x, y, 35 * mm), stick, verts=14)
    for k, (dx, dy) in enumerate(((0, -9), (9, 0), (-9, 0), (0, 9))):
        cyl(c, "btn", 4.5 * mm, 4.5 * mm, 4 * mm, ((34 + dx) * mm, (14 + dy) * mm, 28 * mm), cols[k], verts=10)
    box(c, "dpad", (18 * mm, 6 * mm, 3 * mm), (-16 * mm, -10 * mm, 28.5 * mm), stick)
    box(c, "dpad", (6 * mm, 18 * mm, 3 * mm), (-16 * mm, -10 * mm, 28.5 * mm), stick)
    return done(c, "game_controller", (0.155, 0.05, 0.1), smooth=35)


def sticky_notes():
    c = ev("sticky_notes")
    y = mat2("rh_sticky", "#F4D03F", 0.9); y2 = mat2("rh_sticky_edge", "#E2BD2A", 0.9)
    box(c, "pad", (76 * mm, 76 * mm, 16 * mm), (0, 0, 8 * mm), y2)
    box(c, "top", (76 * mm, 76 * mm, 1 * mm), (0, 0, 16.5 * mm), y)
    s = box(c, "curl", (76 * mm, 30 * mm, 0.4 * mm), (0, -26 * mm, 18 * mm), y, rot=(math.radians(5), 0, 0))
    return done(c, "sticky_notes", (0.076, 0.02, 0.076), smooth=20)


def tape_measure():
    c = ev("tape_measure")
    yel = mat2("rh_tm_yellow", "#F1C40F", 0.45); blk = mat2("rh_tm_black", "#1C1C1E", 0.8); blade = mat2("rh_tm_blade", "#F7DC6F", 0.4)
    box(c, "case", (66 * mm, 66 * mm, 34 * mm), (0, 4 * mm, 17 * mm), yel, bevel=14 * mm, segs=3)
    for z in (1 * mm, 33 * mm):
        cyl(c, "side", 22 * mm, 22 * mm, 2 * mm, (0, 4 * mm, z), blk, verts=16)
    box(c, "lock", (12 * mm, 10 * mm, 6 * mm), (0, -22 * mm, 34 * mm - 3 * mm), blk, bevel=2 * mm)
    box(c, "blade", (20 * mm, 10 * mm, 1 * mm), (0, -33 * mm, 3 * mm), blade)
    box(c, "hook", (22 * mm, 2 * mm, 8 * mm), (0, -38.5 * mm, 4 * mm), EM['steel'])
    box(c, "clip", (30 * mm, 40 * mm, 2 * mm), (0, 6 * mm, 1 * mm), EM['steel'])
    return done(c, "tape_measure", (0.07, 0.035, 0.075), smooth=35)


def flip_flop():
    c = ev("flip_flop")
    sole = mat2("rh_slipper", "#2E86C1", 0.85); top = mat2("rh_slipper_top", "#5DADE2", 0.85); strap = mat2("rh_strap", "#F2F2EE", 0.6)
    worn = mat2("rh_slipper_worn", "#1F5F8B", 0.9)
    outline = [(0, -130), (26, -124), (38, -100), (40, -60), (36, -20), (42, 30), (50, 75), (46, 110), (30, 128), (8, 132), (-14, 126), (-34, 110), (-44, 80),
               (-42, 40), (-34, 0), (-36, -50), (-38, -100), (-26, -124)]
    pts = [(u * mm, v * mm) for u, v in outline]
    prism(c, "sole", pts, 10 * mm, (0, 0, 5 * mm), sole, axis='Z')
    prism(c, "footbed", [(u * 0.98, v * 0.98) for u, v in pts], 3 * mm, (0, 0, 11.5 * mm), top, axis='Z')
    o = cyl(c, "heelwear", 28 * mm, 28 * mm, 0.6 * mm, (0, -95 * mm, 13.2 * mm), worn, verts=14); o.scale = (1.0, 1.2, 1.0)
    post = (4 * mm, 92 * mm, 13 * mm)
    cyl(c, "post", 4 * mm, 4 * mm, 14 * mm, (post[0], post[1], 19 * mm), strap, verts=8)
    for s in (-1, 1):
        a = Vector((post[0], post[1], 26 * mm)); b = Vector((s * 40 * mm, 10 * mm, 13 * mm))
        mid = (a + b) / 2 + Vector((s * 6 * mm, 0, 6 * mm))
        tube(c, "strap", [tuple(a), tuple(mid), tuple(b)], 5 * mm, strap, sides=6, taper=False)
    return done(c, "flip_flop", (0.1, 0.03, 0.26), smooth=35)


BUILD_EV = [duct_tape, zip_ties, bolt_cutters, flashlight, handbag, broken_bottle, brass_knuckles, shotgun_shell, ammo_box, burnt_spoon,
            glass_pipe, paint_marker, stapler, calculator, lighter, hand_sanitizer, game_controller, sticky_notes, tape_measure, flip_flop]
for i, f in enumerate(BUILD_EV):
    f().location = (i * 0.8, -50, 0)
