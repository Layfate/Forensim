# Room shells for the five rooms added in 2026-11. Run after fz_rooms_common.py and fz_rooms.py
# (same namespace: wall_box, window, picture, clock, pendant, rug, exit_sign, build_room ...).
# Nothing here stands on the floor except flat decals (<= 2.5 mm), so floor evidence stays clear.
import bpy, math, random
mat = fz['mat']
MIRROR = mat("mirror", "#B7C4CC", 0.05, 0.7)
TAN = mat("leather_tan", "#8C6D4F", 0.6)
WOOD_H = mat("wood_walnut", "#5A3E2B", 0.55)
SHADE = mat("lamp_shade", "#E8DCC0", 0.8)
STEEL = mat("steel_brushed", "#B8BCC2", 0.3, 0.9)
TILE_W = mat("tile_white", "#E9E6DF", 0.3)
CURTAIN = mat("curtain_sand", "#B9A27E", 0.95)


def lamp(c, side, W, D, off, y):
    wall_box(c, side, W, D, off, y, 0.08, 0.14, 0.03, M['handle'], "sconce_base")
    wall_box(c, side, W, D, off, y + 0.04, 0.2, 0.2, 0.2, SHADE, "sconce")


def curtains(c, side, W, D, a, b, top, m):
    for o in (a - 0.12, b + 0.12):
        wall_box(c, side, W, D, o, top / 2 + 0.25, 0.24, top - 0.5, 0.08, m, "curtain")
    wall_box(c, side, W, D, (a + b) / 2, top + 0.08, b - a + 0.7, 0.03, 0.1, M['metal'], "rod")


# ---------------------------------------------------------------------------- hotel room
def hotel_extras(c, W, D, H):
    wall_box(c, "north", W, D, 3.6, 1.08, 2.4, 1.0, 0.05, WOOD_H, "headframe")
    wall_box(c, "north", W, D, 3.6, 1.08, 2.24, 0.86, 0.09, TAN, "headboard")
    for k in range(4):
        wall_box(c, "north", W, D, 3.6 - 0.84 + k * 0.56, 1.08, 0.012, 0.84, 0.092, mat("leather_tan_dk", "#6A5039", 0.65), "tuft")
    lamp(c, "north", W, D, 2.2, 1.35); lamp(c, "north", W, D, 5.0, 1.35)
    picture(c, "north", W, D, 3.6, 2.05, 1.3, 0.5, 2)
    # TV over the dresser (east wall)
    wall_box(c, "east", W, D, 2.4, 1.5, 1.1, 0.64, 0.05, M['plastic_blk'], "tv")
    wall_box(c, "east", W, D, 2.4, 1.5, 1.04, 0.58, 0.055, M['screen'], "tvscreen")
    # mirror over the writing desk (west wall)
    wall_box(c, "west", W, D, 4.2, 1.5, 0.8, 0.95, 0.03, WOOD_H, "mframe")
    wall_box(c, "west", W, D, 4.2, 1.5, 0.7, 0.85, 0.035, MIRROR, "mirror")
    # closed bathroom door (west wall)
    wall_box(c, "west", W, D, 6.1, 1.06, 0.96, 2.12, 0.03, M['frame_white'], "bframe")
    wall_box(c, "west", W, D, 6.1, 1.04, 0.84, 2.06, 0.04, M['door'], "bdoor")
    wall_box(c, "west", W, D, 6.4, 1.0, 0.12, 0.03, 0.08, M['handle'], "bhandle")
    curtains(c, "east", W, D, 3.7, 5.3, 2.3, CURTAIN)
    wall_box(c, "south", W, D, 2.4, 2.35, 0.9, 0.3, 0.22, M['frame_white'], "ac")
    rug(c, 6.0, 4.5, 2.2, 1.7, mat("rug_hotel", "#7A5A44", 1.0), mat("rug_hotel_b", "#C9B27C", 1.0))
    picture(c, "south", W, D, 3.2, 1.5, 0.8, 0.6, 0)


# ---------------------------------------------------------------------------- kitchen
def kitchen_extras(c, W, D, H):
    cab = mat("kitchen_cab", "#E2DED3", 0.45)
    for off in (1.3, 1.9, 3.4, 4.0, 5.3):
        wall_box(c, "north", W, D, off, 1.85, 0.59, 0.7, 0.34, cab, "wallcab")
        wall_box(c, "north", W, D, off + 0.2, 1.58, 0.012, 0.12, 0.36, M['chrome'], "wcpull")
    # range hood over the hob
    wall_box(c, "north", W, D, 4.7, 1.62, 0.62, 0.14, 0.5, STEEL, "hood")
    wall_box(c, "north", W, D, 4.7, 2.2, 0.26, 1.0, 0.26, STEEL, "chimney")
    # splashback tiles
    wall_box(c, "north", W, D, 3.3, 1.2, 4.6, 0.55, 0.008, TILE_W, "splash")
    for k in range(1, 4):
        wall_box(c, "north", W, D, 3.3, 0.92 + k * 0.14, 4.6, 0.006, 0.01, M['baseboard'], "grout")
    # wall tap over the sink
    wall_box(c, "north", W, D, 2.7, 1.1, 0.08, 0.08, 0.04, STEEL, "tapbase")
    wall_box(c, "north", W, D, 2.7, 1.1, 0.03, 0.03, 0.24, STEEL, "spout")
    pt, _ = on_wall("north", W, D, 0)
    cyl(c, "nozzle", 0.016, 0.016, 0.06, pt(2.7, 1.06, 0.46), STEEL, verts=8)
    for off in (2.5, 2.9):
        wall_box(c, "north", W, D, off, 1.18, 0.03, 0.05, 0.06, STEEL, "tapknob")
    clock(c, "west", W, D, 4.6, 2.0)
    picture(c, "east", W, D, 4.8, 1.5, 0.5, 0.7, 1)
    pendant(c, 2.9, 2.9, H, 0.85, 0.15); pendant(c, 3.9, 2.9, H, 0.85, 0.15)


# ---------------------------------------------------------------------------- garage
def garage_extras(c, W, D, H):
    door_m = mat("garage_door", "#C9CCCF", 0.5, 0.2)
    # closed sectional door on the north wall, with its tracks and opener
    wall_box(c, "north", W, D, 4.6, 1.25, 3.0, 2.5, 0.03, M['frame_white'], "gdframe")
    for k in range(4):
        wall_box(c, "north", W, D, 4.6, 0.31 + k * 0.6, 2.8, 0.56, 0.06, door_m, "gdpanel")
        for j in range(4):
            wall_box(c, "north", W, D, 3.55 + j * 0.7, 0.31 + k * 0.6, 0.56, 0.38, 0.065, mat("garage_door_in", "#B5B9BD", 0.5, 0.2), "gdinset")
    wall_box(c, "north", W, D, 4.6, 1.0, 0.3, 0.05, 0.1, M['metal'], "gdhandle")
    for x in (3.2, 6.0):
        box(c, "track", (0.06, 3.2, 0.06), (x, -1.6, 2.6), M['steel'])
    box(c, "rail", (0.08, 3.0, 0.06), (4.6, -2.7, H - 0.12), M['steel'])
    box(c, "opener", (0.36, 0.5, 0.18), (4.6, -4.3, H - 0.18), mat("opener_grey", "#9AA0A6", 0.5), bevel=0.02)
    # pegboard with tools over the bench (west wall)
    wall_box(c, "west", W, D, 4.0, 1.55, 1.8, 1.0, 0.02, mat("pegboard", "#A27B55", 0.9), "pegboard")
    r = random.Random(3)
    for k in range(7):
        off = 3.3 + k * 0.22; ln = r.uniform(0.18, 0.32)
        wall_box(c, "west", W, D, off, 1.7, 0.03, ln, 0.035, r.choice([M['metal'], M['red'], M['yellow']]), "tool")
    for k in range(3):
        wall_box(c, "west", W, D, 3.4 + k * 0.5, 1.25, 0.3, 0.04, 0.035, M['steel'], "wrench")
    # bicycle hung on the east wall above the tyres
    pt, _ = on_wall("east", W, D, 0)
    for z in (0.65, 1.45):
        lathe(c, "bwheel", [(0.3, -0.012), (0.34, -0.012), (0.34, 0.012), (0.3, 0.012), (0.3, -0.012)], pt(z, 1.95, 0.12), M['plastic_blk'],
              rot=(0, math.pi / 2, 0), verts=20, cap_top=False, cap_bot=False)
    wall_box(c, "east", W, D, 1.05, 2.05, 0.8, 0.03, 0.04, M['red'], "bframe")
    wall_box(c, "east", W, D, 0.85, 1.9, 0.45, 0.03, 0.04, M['red'], "bframe")
    wall_box(c, "east", W, D, 1.05, 2.35, 0.22, 0.05, 0.08, M['plastic_blk'], "bseat")
    for z in (0.65, 1.45):
        wall_box(c, "east", W, D, z, 2.3, 0.04, 0.12, 0.04, M['metal'], "hook")
    # breaker box and hose reel
    wall_box(c, "east", W, D, 7.6, 1.6, 0.4, 0.55, 0.1, M['frame_white'], "breaker")
    pt, _ = on_wall("south", W, D, 0)
    cyl(c, "reel", 0.22, 0.22, 0.12, pt(6.5, 1.3, 0.1), M['yellow'], rot=(math.pi / 2, 0, 0), verts=14)
    cyl(c, "hose", 0.16, 0.16, 0.13, pt(6.5, 1.3, 0.1), mat("hose_green", "#2E7D32", 0.7), rot=(math.pi / 2, 0, 0), verts=14)
    # floor: oil stain, expansion joints, parking guides (flat decals)
    for x in (3.3, 5.9):
        box(c, "guide", (0.08, 4.0, 0.0015), (x, -4.6, 0.00075), M['yellow'])
    for z in (3.0, 6.0):
        box(c, "joint", (W, 0.012, 0.0012), (W / 2, -z, 0.0006), mat("concrete_joint", "#4E4C48", 1.0))
    box(c, "joint", (0.012, D, 0.0012), (W / 2, -D / 2, 0.0006), mat("concrete_joint", "#4E4C48", 1.0))
    o = cyl(c, "oil", 0.5, 0.5, 0.002, (4.4, -7.3, 0.001), mat("oil_stain", "#2E2C2A", 0.3), verts=14); o.scale = (1.0, 0.65, 1)
    cyl(c, "oil2", 0.18, 0.18, 0.0022, (4.95, -7.55, 0.0011), mat("oil_stain", "#2E2C2A", 0.3), verts=10)


# ---------------------------------------------------------------------------- bar
def bar_extras(c, W, D, H):
    bw = mat("bar_wood", "#5A3A24", 0.45)
    # wainscot (below the windows, around the door)
    for side, L in (("north", W), ("east", D), ("west", D)):
        wall_box(c, side, W, D, L / 2, 0.45, L, 0.85, 0.015, bw, "wainscot")
    for a, b in ((0, 8.8 - 0.1), (10.0 + 0.1, W)):
        wall_box(c, "south", W, D, (a + b) / 2, 0.45, b - a, 0.85, 0.015, bw, "wainscot")
    # mirrored bottle shelves behind the back bar
    wall_box(c, "north", W, D, 4.5, 1.75, 4.4, 1.0, 0.012, MIRROR, "barmirror")
    r = random.Random(21)
    bottles = [mat(f"bottle_{k}", col, 0.15, 0.0, 0.85) for k, col in enumerate(("#5B3A12", "#2E5A2A", "#B88A2E", "#7A1F2B", "#DCE6EA", "#3A4A7A"))]
    pt, _ = on_wall("north", W, D, 0)
    for y in (1.32, 1.72, 2.12):
        wall_box(c, "north", W, D, 4.5, y, 4.4, 0.025, 0.22, M['glass'], "gshelf")
        x = 2.45
        while x < 6.55:
            h = r.uniform(0.24, 0.32)
            lathe(c, "bottle", [(0, 0), (0.04, 0), (0.04, h * 0.62), (0.014, h * 0.82), (0.014, h), (0, h)], pt(x, y + 0.0125, 0.12), r.choice(bottles), verts=8)
            x += r.uniform(0.11, 0.16)
    # neon sign
    neon = mat("neon_pink", "#FF5FA2", 0.2); neon2 = mat("neon_blue", "#4FC3F7", 0.2)
    for (o, y, al, ht) in ((4.5, 2.65, 1.6, 0.03), (4.5, 2.95, 1.6, 0.03), (3.71, 2.8, 0.03, 0.33), (5.29, 2.8, 0.03, 0.33)):
        wall_box(c, "north", W, D, o, y, al, ht, 0.04, neon, "neon")
    for k in range(5):
        wall_box(c, "north", W, D, 3.95 + k * 0.28, 2.8 + (0.05 if k % 2 else -0.05), 0.22, 0.03, 0.045, neon2, "neonwave")
    # pendants over the bar, lamp over the pool table
    for x in (3.0, 4.5, 6.0):
        pendant(c, x, 1.6, H, 1.1, 0.14)
    for x in (3.4, 4.6):
        cyl(c, "chain", 0.006, 0.006, 1.1, (x, -5.0, H - 0.55), M['metal'], verts=6)
    box(c, "poolshade", (1.4, 0.4, 0.16), (4.0, -5.0, H - 1.18), mat("pool_shade", "#1F5A3A", 0.5), bevel=0.02)
    box(c, "poollight", (1.3, 0.32, 0.01), (4.0, -5.0, H - 1.265), M['light_panel'])
    # dartboard and chalk scoreboard (east wall)
    pt, _ = on_wall("east", W, D, 0)
    rot = (0, math.pi / 2, 0)
    for r_, d_, m_ in ((0.23, 0.04, M['plastic_blk']), (0.2, 0.045, mat("dart_cream", "#E8DCC0", 0.8)), (0.15, 0.05, mat("dart_red", "#A3161C", 0.8)),
                       (0.1, 0.055, mat("dart_green", "#1F6B45", 0.8)), (0.03, 0.06, mat("dart_red", "#A3161C", 0.8))):
        cyl(c, "dart", r_, r_, d_, pt(3.0, 1.73, d_), m_, rot=rot, verts=20)
    wall_box(c, "east", W, D, 4.0, 1.6, 0.6, 0.8, 0.02, M['wood_dark'], "chalkframe")
    wall_box(c, "east", W, D, 4.0, 1.6, 0.54, 0.74, 0.025, M['slate'], "chalk")
    # cue rack (west wall), TV in the corner, posters
    wall_box(c, "west", W, D, 6.4, 1.2, 0.6, 0.06, 0.06, bw, "cuerack")
    for k in range(4):
        cyl(c, "cue", 0.012, 0.007, 1.4, (0.04, -(6.2 + k * 0.13), 1.0), mat("cue_wood", "#C9A576", 0.5), verts=6)
    wall_box(c, "east", W, D, 6.6, 2.35, 1.0, 0.6, 0.06, M['plastic_blk'], "tv")
    wall_box(c, "east", W, D, 6.6, 2.35, 0.94, 0.54, 0.065, M['screen'], "tvscreen")
    picture(c, "west", W, D, 2.0, 1.7, 0.6, 0.8, 0); picture(c, "west", W, D, 3.2, 1.7, 0.6, 0.8, 2)
    exit_sign(c, "south", W, D, 9.4, 2.55)


# ---------------------------------------------------------------------------- classroom
def classroom_extras(c, W, D, H):
    wall_box(c, "north", W, D, 5.0, 1.55, 4.1, 1.3, 0.03, M['wood_dark'], "bframe")
    wall_box(c, "north", W, D, 5.0, 1.55, 4.0, 1.2, 0.035, mat("chalkboard", "#2E4A3A", 0.9), "board")
    wall_box(c, "north", W, D, 5.0, 0.92, 4.0, 0.03, 0.08, M['wood_dark'], "tray")
    for k, off in enumerate((3.6, 3.75, 5.9)):
        wall_box(c, "north", W, D, off, 0.945, 0.08, 0.012, 0.03 + 0.005 * k, M['frame_white'], "chalk")
    wall_box(c, "north", W, D, 6.4, 0.955, 0.14, 0.04, 0.05, mat("eraser", "#3A4A7A", 0.8), "eraser")
    for k, y in enumerate((1.9, 1.55, 1.2)):    # faint chalk lines
        wall_box(c, "north", W, D, 4.2 + k * 0.2, y, 1.6 - k * 0.3, 0.012, 0.0365, mat("chalk_line", "#9FB5A3", 0.9), "line")
    clock(c, "north", W, D, 8.4, 2.25)
    # bulletin board (west wall) with pinned papers
    wall_box(c, "west", W, D, 4.0, 1.6, 2.4, 1.0, 0.02, mat("cork", "#B98A5A", 0.95), "cork")
    r = random.Random(8)
    papers = [M['frame_white'], mat("paper_yellow", "#F4D03F", 0.9), mat("paper_blue", "#AED6F1", 0.9), mat("paper_pink", "#F5B7B1", 0.9)]
    for k in range(7):
        wall_box(c, "west", W, D, 3.05 + k * 0.3 + r.uniform(-0.04, 0.04), 1.6 + r.uniform(-0.25, 0.25), 0.21, 0.28, 0.024, r.choice(papers), "paper")
    # louvred windows (east and west walls) and ceiling fans
    for side, a, b in (("east", 3.6, 5.0), ("east", 5.6, 7.0), ("west", 5.8, 7.4)):
        for k in range(6):
            wall_box(c, side, W, D, (a + b) / 2, 1.05 + k * 0.22, b - a - 0.12, 0.012, 0.05, M['glass'], "louvre")
    for (x, z) in ((3.0, 4.4), (7.0, 4.4)):
        cyl(c, "fanrod", 0.015, 0.015, 0.4, (x, -z, H - 0.2), M['metal'], verts=6)
        cyl(c, "fanmotor", 0.1, 0.1, 0.12, (x, -z, H - 0.46), M['frame_white'], verts=12)
        for k in range(3):
            a = k * 2 * math.pi / 3
            box(c, "blade", (0.55, 0.12, 0.012), (x + math.cos(a) * 0.36, -z + math.sin(a) * 0.36, H - 0.47), M['frame_white'], rot=(0.08, 0, a))
    picture(c, "south", W, D, 3.0, 1.6, 1.2, 0.8, 1); picture(c, "south", W, D, 5.2, 1.6, 0.9, 0.7, 2)
    exit_sign(c, "south", W, D, 8.8, 2.45)


CONFIGS_V2 = [
  dict(id="hotel_room", W=8, D=7, H=2.7, wall="#5B5148", floor_col="#4E5A66", floor=('flat',), door=("south", 6.6, 1.0),
       windows=[("east", 3.7, 5.3, 0.6, 2.3)], lights=[(3.6, 3.6, 0.5, 0.5), (6.6, 5.8, 0.4, 0.4)], extras=[hotel_extras]),
  dict(id="kitchen", W=7, D=6, H=2.7, wall="#5E6E60", floor_col="#8A8478", floor=('tiles', 0.3, 1.25), door=("south", 1.2, 1.0),
       windows=[("north", 2.35, 3.05, 1.15, 1.75), ("east", 2.2, 3.6, 0.95, 2.1)], lights=[(2.0, 1.4, 0.5, 0.5), (5.4, 4.4, 0.5, 0.5)],
       extras=[kitchen_extras]),
  dict(id="garage", W=8, D=9, H=3.0, wall="#6B6E70", floor_col="#6E6C68", floor=('flat',), door=("south", 1.0, 1.0), baseboards=False,
       windows=[("south", 4.5, 5.7, 1.4, 2.2)], lights=[(x, z, 0.15, 1.4) for x in (2.2, 7.0) for z in (2.5, 6.5)], extras=[garage_extras]),
  dict(id="bar", W=11, D=8, H=3.2, wall="#3E312A", floor_col="#4A3426", floor=('planks',), door=("south", 9.4, 1.2),
       windows=[("south", 1.0, 3.0, 0.9, 2.2), ("south", 4.0, 6.5, 0.9, 2.2), ("east", 3.9, 4.9, 1.0, 2.2)],
       lights=[(8.6, 3.2, 0.4, 0.4), (9.4, 6.4, 0.4, 0.4), (1.6, 6.4, 0.4, 0.4)], extras=[bar_extras], baseboards=False),
  dict(id="classroom", W=10, D=8, H=3.0, wall="#7E8C6C", floor_col="#9A9488", floor=('tiles', 0.4, 1.1), door=("south", 8.8, 1.0),
       windows=[("east", 3.6, 5.0, 0.9, 2.3), ("east", 5.6, 7.0, 0.9, 2.3), ("west", 5.8, 7.4, 0.9, 2.3)],
       lights=[(x, z, 0.3, 1.2) for x in (2.5, 5.0, 7.5) for z in (2.0, 6.0)], extras=[classroom_extras]),
]

for i, cfg in enumerate(CONFIGS_V2):
    o = build_room(cfg)
    o.location = (126 + i * 32, 0, 0)
