"""
Forensim victim bodies: male / female, face up / face down, as four static .glb models.

Run inside Blender (tested on 5.2):
    ns = {}; exec(open(r"<repo>/art/scripts/fz_victim.py").read(), ns)
    ns["build_all"](r"<repo>")            # builds, bakes and exports all four

Pipeline per variant:
  1. Body parts (torso, head with face, neck, arms with hands and fingers, legs, shoes, hair,
     skirt) are lofted from cross-section tables directly in the lying pose. Every vertex carries
     a colour (skin, clothes, hair, face details) and a surface kind (skin / cloth / hair / shoe).
  2. The parts are joined and voxel-remeshed into one smooth watertight body (high poly), the
     colours are carried over by nearest source vertex, and clothes get fine wrinkles.
  3. A decimated copy (12k triangles, more of them in the head and hands) is UV-unwrapped, and the high-poly colour and ambient
     occlusion (with the floor) are baked into one 1024 px texture.
  4. Exported as models/victim/victim_<sex>_<pose>.glb.

Conventions (art/README.md): metres, Blender Z up (exported Y up). The origin is the centre of
the body on the floor; the head points to +X, the top of the head at x = length / 2 (1.76 m male,
1.64 m female). The footprint stays inside VictimPlacer's collision box.
"""
import bpy, bmesh, math, random, os
from mathutils import Vector, Matrix, kdtree

V = Vector
SKIN, CLOTH, HAIR, SHOE = 0, 1, 2, 3

PAL = {
    "skin_m": (0.72, 0.56, 0.47), "skin_f": (0.81, 0.65, 0.56),
    "lips_m": (0.60, 0.42, 0.42), "lips_f": (0.64, 0.38, 0.40),
    "lid": (0.42, 0.29, 0.26), "lash": (0.12, 0.09, 0.08),
    "hair_m": (0.10, 0.075, 0.06), "hair_f": (0.30, 0.18, 0.09),
    "shirt": (0.21, 0.32, 0.50), "shirt_dark": (0.15, 0.23, 0.37), "button": (0.86, 0.84, 0.78),
    "trousers": (0.17, 0.18, 0.21), "trousers_dark": (0.12, 0.13, 0.15),
    "belt": (0.24, 0.15, 0.08), "buckle": (0.72, 0.66, 0.48),
    "blouse": (0.58, 0.15, 0.21), "blouse_dark": (0.42, 0.10, 0.15),
    "skirt": (0.14, 0.15, 0.24), "skirt_dark": (0.10, 0.11, 0.18),
    "shoe_m": (0.07, 0.06, 0.06), "shoe_f": (0.33, 0.08, 0.10), "sole": (0.16, 0.15, 0.14), "lace": (0.03, 0.03, 0.03),
    "nail": (0.80, 0.62, 0.58),
}


def interp(table, x):
    """Smooth (Catmull-Rom) interpolation in a sorted [(x, value...)] table, so lofted profiles
    have no creases at the table rows."""
    if x <= table[0][0]:
        return table[0][1:]
    if x >= table[-1][0]:
        return table[-1][1:]
    n = len(table)
    for k in range(n - 1):
        if x <= table[k + 1][0]:
            break
    x0, x1 = table[k][0], table[k + 1][0]
    t = (x - x0) / (x1 - x0)
    p0, p1, p2 = table[max(0, k - 1)][1:], table[k][1:], table[k + 1][1:]
    p3 = table[min(n - 1, k + 2)][1:]
    t2, t3 = t * t, t * t * t
    out = []
    for a, b, c, d in zip(p0, p1, p2, p3):
        v = 0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
        out.append(min(max(v, min(b, c) - abs(c - b) * 0.25), max(b, c) + abs(c - b) * 0.25))
    return tuple(out)


def smooth01(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def frame(t, up=V((0, 0, 1))):
    """Unit (u, v) perpendicular to tangent t: u lateral, v as close to `up` as possible."""
    t = t.normalized()
    u = t.cross(up)
    if u.length < 1e-4:
        u = t.cross(V((1, 0, 0)))
    u.normalize()
    v = u.cross(t).normalized()
    if v.dot(up) < 0:
        u, v = -u, -v
    return u, v


def mix(a, b, t):
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def jitter(c, amount, rnd):
    f = 1 + (rnd.random() * 2 - 1) * amount
    return tuple(min(1.0, x * f) for x in c)


class Builder:
    """Collects closed lofted parts with a colour and surface kind per vertex."""

    def __init__(self, seed):
        self.verts, self.cols, self.kinds, self.faces = [], [], [], []
        self.rnd = random.Random(seed)

    def loft(self, rings, segs, paint, cap=True):
        """rings: (centre, u, v, ru, rv_pos, rv_neg, exponent). paint(i/n, angle, point) -> (rgb, kind)."""
        base = len(self.verts)
        n = len(rings)
        for i, (c, u, v, ru, rvp, rvn, p) in enumerate(rings):
            for j in range(segs):
                a = 2 * math.pi * j / segs
                ca, sa = math.cos(a), math.sin(a)
                x = math.copysign(abs(ca) ** (2 / p), ca)
                y = math.copysign(abs(sa) ** (2 / p), sa)
                pt = c + u * (ru * x) + v * ((rvp if y > 0 else rvn) * y)
                col, kind = paint(i / max(1, n - 1), a, pt)
                self.verts.append(pt); self.cols.append(col); self.kinds.append(kind)
        for i in range(n - 1):
            for j in range(segs):
                a = base + i * segs + j
                b = base + i * segs + (j + 1) % segs
                self.faces.append((a, b, b + segs, a + segs))
        if cap:
            for ri, flip in ((0, True), (n - 1, False)):
                ci = len(self.verts)
                self.verts.append(rings[ri][0].copy())
                self.cols.append(self.cols[base + ri * segs]); self.kinds.append(self.kinds[base + ri * segs])
                for j in range(segs):
                    a = base + ri * segs + j
                    b = base + ri * segs + (j + 1) % segs
                    self.faces.append((b, a, ci) if flip else (a, b, ci))

    def capsule(self, pts, radii, paint, segs=28, step=0.006, flat=1.0, up=V((0, 0, 1)), caps=(True, True)):
        """Round tube through pts with radii (linear between points), hemispherical ends.
        flat < 1 squashes the section along v. paint(s, angle, point) with s = arc length 0..1."""
        lengths = [0.0]
        for a, b in zip(pts, pts[1:]):
            lengths.append(lengths[-1] + (b - a).length)
        total = lengths[-1]
        rings = []
        ss = []

        def at(s):
            for k in range(len(pts) - 1):
                if s <= lengths[k + 1] or k == len(pts) - 2:
                    seg = max(1e-6, lengths[k + 1] - lengths[k])
                    t = max(0.0, min(1.0, (s - lengths[k]) / seg))
                    return pts[k].lerp(pts[k + 1], t), radii[k] + (radii[k + 1] - radii[k]) * t, (pts[k + 1] - pts[k])
            return pts[-1], radii[-1], pts[-1] - pts[-2]

        def ring(c, tan, r, s):
            u, v = frame(tan, up)
            rings.append((c, u, v, r, r * flat, r * flat, 2.0)); ss.append(s)

        cap_n = 7
        if caps[0]:
            c0, r0, t0 = at(0.0)
            tn = t0.normalized()
            for k in range(cap_n, 0, -1):
                phi = k / cap_n * math.pi / 2
                ring(c0 - tn * (r0 * math.sin(phi)), t0, max(r0 * math.cos(phi), r0 * 0.05), 0.0)
        steps = max(2, int(total / step))
        for k in range(steps + 1):
            s = total * k / steps
            c, r, t = at(s)
            ring(c, t, r, s / total if total else 0)
        if caps[1]:
            c1, r1, t1 = at(total)
            tn = t1.normalized()
            for k in range(1, cap_n + 1):
                phi = k / cap_n * math.pi / 2
                ring(c1 + tn * (r1 * math.sin(phi)), t1, max(r1 * math.cos(phi), r1 * 0.05), 1.0)
        n = len(rings)
        idx = [0]

        def p(i, a, pt):
            return paint(ss[min(len(ss) - 1, round(i * (n - 1)))], a, pt)
        self.loft(rings, segs, p)

    def blob(self, centre, axes, radii, paint, segs=28, rings_n=18):
        """Closed ellipsoid-like lobe (lathed profile) with axes (a, b, c) and radii (ra, rb, rc)."""
        a, b, c = axes
        ra, rb, rc = radii
        rings = []
        for k in range(rings_n + 1):
            th = -math.pi / 2 + math.pi * k / rings_n
            rings.append((centre + a * (ra * math.sin(th)), b, c, max(rb * math.cos(th), rb * 0.04),
                          max(rc * math.cos(th), rc * 0.04), max(rc * math.cos(th), rc * 0.04), 2.0))
        self.loft(rings, segs, lambda t, ang, pt: paint(pt))

    def to_object(self, name):
        me = bpy.data.meshes.new(name)
        me.from_pydata([tuple(v) for v in self.verts], [], self.faces)
        me.validate()
        col = me.color_attributes.new("Col", "FLOAT_COLOR", "POINT")
        for i, c in enumerate(self.cols):
            col.data[i].color = (c[0], c[1], c[2], 1.0)
        kind = me.attributes.new("kind", "INT", "POINT")
        kind.data.foreach_set("value", self.kinds)
        ob = bpy.data.objects.new(name, me)
        bpy.context.scene.collection.objects.link(ob)
        return ob


# --------------------------------------------------------------------------- body

class Spec:
    def __init__(self, sex, pose):
        self.sex, self.pose = sex, pose
        self.male = sex == "male"
        self.up = pose == "face_up"
        self.L = 1.76 if self.male else 1.64
        self.k = self.L / 1.76  # lengths along the body scale with height
        self.s = 1.0 if self.up else -1.0  # which way the front of the torso faces (+z face up)
        self.skin = PAL["skin_m" if self.male else "skin_f"]
        self.hair = PAL["hair_m" if self.male else "hair_f"]

    def X(self, x):
        return x * self.k


# Torso cross-sections (male): x, half width, front depth, back depth, superellipse exponent.
TORSO_M = [(-0.10, 0.10, 0.05, 0.06, 2.0), (-0.075, 0.15, 0.08, 0.095, 2.1), (-0.035, 0.172, 0.095, 0.112, 2.2),
           (0.03, 0.174, 0.097, 0.108, 2.2), (0.11, 0.160, 0.098, 0.094, 2.2), (0.19, 0.150, 0.100, 0.088, 2.2),
           (0.29, 0.156, 0.105, 0.092, 2.25), (0.39, 0.166, 0.112, 0.098, 2.35), (0.47, 0.172, 0.108, 0.100, 2.35),
           (0.53, 0.176, 0.090, 0.094, 2.3), (0.565, 0.150, 0.068, 0.076, 2.2), (0.595, 0.100, 0.052, 0.060, 2.1),
           (0.625, 0.062, 0.045, 0.05, 2.0)]
TORSO_F = [(-0.10, 0.11, 0.05, 0.065, 2.0), (-0.075, 0.165, 0.08, 0.10, 2.1), (-0.035, 0.188, 0.095, 0.120, 2.2),
           (0.03, 0.184, 0.094, 0.110, 2.2), (0.11, 0.150, 0.088, 0.088, 2.15), (0.19, 0.132, 0.086, 0.080, 2.15),
           (0.29, 0.140, 0.092, 0.086, 2.2), (0.39, 0.150, 0.098, 0.090, 2.25), (0.47, 0.154, 0.094, 0.092, 2.25),
           (0.53, 0.158, 0.080, 0.086, 2.2), (0.565, 0.132, 0.062, 0.068, 2.1), (0.595, 0.088, 0.048, 0.054, 2.05),
           (0.625, 0.055, 0.042, 0.046, 2.0)]


def torso_ring(sp, x_raw):
    """Centre z and section of the torso at body coordinate x_raw (male proportions)."""
    hw, f, b, p = interp(TORSO_M if sp.male else TORSO_F, x_raw)
    # Lying on the back the lumbar curve lifts the waist a little; on the front, the belly takes the weight.
    lift = 0.012 * smooth01(1 - abs(x_raw - 0.2) / 0.12) if sp.up else 0.0
    zc = (b if sp.up else f) + lift
    return hw, f, b, p, zc


def paint_torso(sp, rnd):
    def paint(x_raw, ang, pt):
        front = math.sin(ang) > 0
        y = pt.y
        if sp.male:
            if 0.025 < x_raw < 0.07:  # belt
                if front and abs(y) < 0.019 and 0.034 < x_raw < 0.062:
                    return (PAL["buckle"] if abs(y) > 0.013 or x_raw < 0.039 or x_raw > 0.057 else PAL["belt"]), CLOTH
                return PAL["belt"], CLOTH
            if x_raw <= 0.025:
                c = PAL["trousers"]
                if front and abs(y) < 0.004:
                    c = PAL["trousers_dark"]  # fly seam
                return jitter(c, 0.03, rnd), CLOTH
            c = PAL["shirt"]
            if front:
                if abs(y) < 0.016 and x_raw < 0.57:
                    c = PAL["shirt_dark"] if abs(abs(y) - 0.014) < 0.002 else c  # placket edges
                    for bx in (0.12, 0.21, 0.30, 0.39, 0.48):
                        if (x_raw - bx) ** 2 + y * y < 0.0047 ** 2:
                            return mix(PAL["button"], PAL["shirt"], 0.25), CLOTH
                if x_raw > 0.555 and abs(y) < 0.10:
                    c = PAL["shirt_dark"] if x_raw > 0.6 else c  # collar
                if 0.39 < x_raw < 0.46 and 0.05 < y < 0.11:
                    c = PAL["shirt_dark"] if (abs(x_raw - 0.39) < 0.003 or abs(y - 0.05) < 0.003 or abs(y - 0.11) < 0.003) else c  # pocket
            if x_raw > 0.6 and not front:
                c = PAL["shirt_dark"]
            # Skin at the open collar.
            if front and x_raw > 0.575 and abs(y) < 0.03 + (x_raw - 0.575) * 0.6:
                return sp.skin, SKIN
            return jitter(c, 0.03, rnd), CLOTH
        # Female: blouse (with a V neckline), skirt waistband handled by the skirt part.
        if x_raw <= 0.06:
            return jitter(PAL["skirt"], 0.03, rnd), CLOTH
        if front and x_raw > 0.48 and abs(y) < (x_raw - 0.48) * 0.9:
            return sp.skin, SKIN
        c = PAL["blouse"]
        if front and abs(y) < 0.003 and x_raw < 0.48:
            c = PAL["blouse_dark"]
        if x_raw > 0.6:
            c = PAL["blouse_dark"]
        return jitter(c, 0.03, rnd), CLOTH
    return paint


def build_torso(B, sp):
    rings = []
    xs = [-0.10 + i * 0.003 for i in range(int((0.625 + 0.10) / 0.003) + 1)]
    raws = []
    for xr in xs:
        hw, f, b, p, zc = torso_ring(sp, xr)
        u = V((0, 1, 0))
        v = V((0, 0, sp.s))
        rings.append((V((sp.X(xr), 0, zc)), u, v, hw, f, b, p))
        raws.append(xr)
    paint = paint_torso(sp, B.rnd)
    n = len(rings)
    B.loft(rings, 180, lambda t, a, pt: paint(raws[min(n - 1, round(t * (n - 1)))], a, pt))
    if not sp.male and sp.up:
        # Breasts under the blouse.
        for side in (1, -1):
            # Lying on the back they flatten and fall a little to the sides.
            hw, f, b, p, zc = torso_ring(sp, 0.405)
            c = V((sp.X(0.405), 0.07 * side, zc + f * 0.70))
            B.blob(c, (V((0, 0, 1)), V((1, 0, 0)), V((0, 1, 0))), (0.028, 0.075, 0.075),
                   lambda pt: (jitter(PAL["blouse"], 0.03, B.rnd), CLOTH), segs=40, rings_n=24)


# --------------------------------------------------------------------------- head

# Head profile (male), along the head axis a (metres, 0 = eye line, + towards the crown):
# half width, face-side depth, back-of-skull depth.
HEAD = [(-0.135, 0.012, 0.035, 0.010), (-0.128, 0.030, 0.058, 0.020), (-0.115, 0.046, 0.080, 0.030),
        (-0.095, 0.056, 0.090, 0.045), (-0.07, 0.062, 0.094, 0.065), (-0.045, 0.067, 0.096, 0.085),
        (-0.02, 0.072, 0.096, 0.098), (0.0, 0.075, 0.093, 0.104), (0.022, 0.077, 0.097, 0.106),
        (0.045, 0.076, 0.092, 0.104), (0.068, 0.070, 0.080, 0.094), (0.085, 0.058, 0.064, 0.076),
        (0.098, 0.040, 0.044, 0.052), (0.106, 0.020, 0.022, 0.026), (0.109, 0.004, 0.004, 0.005)]


def head_frame(sp):
    """Head axes (A towards the crown, F out of the face, Lat) and the head origin (eye line)."""
    A = V((1, 0, 0))
    if sp.up:
        F = V((0, 0.30, 0.954)).normalized()  # face up, turned a little to one side
    else:
        F = V((0, -0.93, -0.36)).normalized()  # face down, turned to the side (cheek on the floor)
    Lat = F.cross(A).normalized()
    return A, F, Lat


def build_head(B, sp):
    A, F, Lat = head_frame(sp)
    hs = 1.0 if sp.male else 0.95
    crown = sp.L / 2
    O0 = V((crown - 0.109 * hs, 0, 0))
    hair_t = 0.007 if sp.male else 0.011
    skin = sp.skin
    rnd = B.rnd

    def surface(a, ang, hair_on=True):
        hw, fd, bd = interp(HEAD, a / hs)
        hw, fd, bd = hw * hs, fd * hs, bd * hs
        ca, sa = math.cos(ang), math.sin(ang)
        p = 2.3
        x = math.copysign(abs(ca) ** (2 / p), ca)
        y = math.copysign(abs(sa) ** (2 / p), sa)
        d = (fd if y > 0 else bd) * y
        w = hw * x
        # Narrow jaw and cheeks towards the face.
        if y > 0 and a < -0.03:
            w *= 1 - (0.08 if sp.male else 0.12) * y * smooth01((-0.03 - a) / 0.08)
        # Eye sockets: a shallow dent either side of the nose.
        eye = math.exp(-(((a - 0.0) / 0.014) ** 2) - ((abs(w) - 0.033 * hs) / 0.016) ** 2) if y > 0.6 else 0
        d -= 0.006 * eye
        # Brow ridge.
        d += 0.005 * math.exp(-(((a - 0.022) / 0.012) ** 2)) * max(0, y) * (abs(w) < 0.06)
        local = V((a, w, d))
        hair = hair_on and is_hair(a / hs, y, w / hs, sp)
        if hair:
            n = V((0.6 * a, w, d)).normalized()
            local = local + n * hair_t
        return local, hair, y

    def is_hair(a, y, w, sp):
        if a > 0.062:
            return True
        if sp.male:
            return y < 0.15 and a > -0.035 and not (abs(w) > 0.06 and a < 0.03 and y > -0.5)
        return y < 0.35 and a > -0.06 and not (abs(w) > 0.06 and a < 0.02 and y > -0.4)

    def to_world(local):
        return A * local.x + Lat * local.y + F * local.z

    # Sample rings along the head axis.
    rings_a = [-0.135 * hs + i * 0.0018 for i in range(int((0.244) / 0.0018) + 1)]
    segs = 220
    pts, cols, kinds = [], [], []
    for a in rings_a:
        for j in range(segs):
            ang = 2 * math.pi * j / segs
            local, hair, y = surface(a, ang)
            pts.append(local)
            if hair:
                cols.append(jitter(sp.hair, 0.12, rnd)); kinds.append(HAIR)
                continue
            w = local.y
            c = skin
            if y > 0.45:
                ah = a / hs
                aw = abs(w) / hs
                e = (aw - 0.034) / 0.016  # -1..1 across each eye
                # Closed eyes: an arched lash line, the lid above it and a soft socket shadow.
                lash_a = -0.001 + 0.0035 * (1 - e * e)
                brow_a = 0.021 + 0.004 * (1 - ((aw - 0.030) / 0.026) ** 2)
                brow_w = 0.0042 * (1 - smooth01((aw - 0.03) / 0.03) * 0.55)
                if abs(e) < 1 and abs(ah - lash_a) < 0.0011 + 0.0007 * (1 - abs(e)):
                    c = PAL["lash"]
                elif abs(e) < 1.05 and lash_a < ah < lash_a + 0.0075:
                    c = mix(skin, PAL["lid"], 0.45 * (1 - (ah - lash_a) / 0.0075))
                elif abs(e) < 1.25 and lash_a - 0.006 < ah < lash_a:
                    c = mix(skin, PAL["lid"], 0.2 * (1 - abs(e) / 1.25))
                # Eyebrows: thicker inside, tapering out.
                elif 0.010 < aw < 0.058 and abs(ah - brow_a) < brow_w:
                    c = mix(sp.hair, skin, 0.2 + 0.3 * smooth01((aw - 0.04) / 0.02))
                # Lips (colour carried by the lip shapes too; this softens the edge).
                elif abs(ah + 0.075) < 0.006 and aw < 0.022 - abs(ah + 0.075) * 0.9:
                    c = mix(skin, PAL["lips_m" if sp.male else "lips_f"], 0.7)
                    if abs(ah + 0.0748) < 0.0008:
                        c = mix(c, PAL["lash"], 0.55)
                elif sp.male and ah < -0.05:
                    # Stubble shadow over the jaw, fading in softly.
                    k = smooth01((-0.05 - ah) / 0.025) * smooth01((y - 0.45) / 0.3) * 0.16
                    if abs(ah + 0.075) < 0.008 and aw < 0.03:
                        k *= 0.3
                    c = mix(skin, sp.hair, k)
            cols.append(jitter(c, 0.02, rnd)); kinds.append(SKIN)
    # Place the head so it rests on the floor.
    world = [to_world(p) for p in pts]
    minz = min(p.z for p in world)
    O = O0 + V((0, 0, -minz))
    base = len(B.verts)
    for p, c, kd in zip(world, cols, kinds):
        B.verts.append(p + O); B.cols.append(c); B.kinds.append(kd)
    n = len(rings_a)
    for i in range(n - 1):
        for j in range(segs):
            a = base + i * segs + j
            b = base + i * segs + (j + 1) % segs
            B.faces.append((a, b, b + segs, a + segs))
    for ri, flip in ((0, True), (n - 1, False)):
        ci = len(B.verts)
        c = sum((B.verts[base + ri * segs + j] for j in range(segs)), V()) / segs
        B.verts.append(c); B.cols.append(B.cols[base + ri * segs]); B.kinds.append(B.kinds[base + ri * segs])
        for j in range(segs):
            a = base + ri * segs + j
            b = base + ri * segs + (j + 1) % segs
            B.faces.append((b, a, ci) if flip else (a, b, ci))

    def W(a, lat, f):
        return O + to_world(V((a * hs, lat * hs, f * hs)))

    skin_paint = lambda *args: (jitter(skin, 0.02, rnd), SKIN)
    # Nose: bridge to tip to base.
    # Nose: a narrow bridge widening to the tip, sitting in the face rather than on it.
    nose_r = 1.0 if sp.male else 0.88
    B.capsule([W(0.008, 0, 0.088), W(-0.012, 0, 0.097), W(-0.032, 0, 0.106), W(-0.042, 0, 0.108), W(-0.049, 0, 0.099)],
              [0.0055 * nose_r, 0.0065 * nose_r, 0.0085 * nose_r, 0.010 * nose_r, 0.007 * nose_r], skin_paint,
              segs=20, step=0.0025, flat=0.85, up=F)
    lips = PAL["lips_m" if sp.male else "lips_f"]
    for side in (1, -1):
        # Nostril wings.
        B.blob(W(-0.045, 0.0125 * side * nose_r, 0.095), (A, Lat, F), (0.0075 * hs, 0.0085 * hs * nose_r, 0.0075 * hs),
               lambda pt: (skin, SKIN), segs=16, rings_n=10)
        # Ears.
        B.blob(W(-0.012, 0.074 * side, -0.012), (A, F, Lat), (0.031 * hs, 0.017 * hs, 0.009 * hs),
               lambda pt: (jitter(skin, 0.02, rnd), SKIN), segs=24, rings_n=14)
        # Lips: upper and lower, tapering to the corners.
        B.capsule([W(-0.0728, 0.023 * side, 0.088), W(-0.0712, 0.011 * side, 0.0935), W(-0.0718, 0, 0.0942)],
                  [0.0022, 0.0036, 0.0038], lambda s, a, pt: (lips, SKIN), segs=16, step=0.0015, flat=0.75, up=F)
        B.capsule([W(-0.0752, 0.021 * side, 0.088), W(-0.0782, 0.010 * side, 0.0925), W(-0.0788, 0, 0.0932)],
                  [0.0022, 0.0042, 0.0046], lambda s, a, pt: (lips, SKIN), segs=16, step=0.0015, flat=0.75, up=F)
    # Chin.
    chin = mix(skin, sp.hair, 0.15) if sp.male else skin
    B.blob(W(-0.110, 0, 0.076), (A, Lat, F), (0.014 * hs, 0.026 * hs, 0.008 * hs), lambda pt: (chin, SKIN), segs=20, rings_n=12)
    return O, A, F, Lat, hs


def build_neck(B, sp, head):
    O, A, F, Lat, hs = head
    hw, f, b, p, zc = torso_ring(sp, 0.60)
    start = V((sp.X(0.56), 0, zc))
    end = O + A * (-0.075 * hs) - F * (0.02 * hs)
    r = 0.056 if sp.male else 0.047
    B.capsule([start, start.lerp(end, 0.5), end], [r * 1.15, r, r * 0.95], lambda s, a, pt: (jitter(sp.skin, 0.02, B.rnd), SKIN), segs=32)


def build_long_hair(B, sp, head):
    """Female: long hair spread on the floor round the head (face up) or over the nape (face down)."""
    O, A, F, Lat, hs = head
    hair = sp.hair
    rnd = B.rnd
    paint = lambda s, a, pt: (jitter(mix(hair, (0.0, 0.0, 0.0), 0.15 * s), 0.12, rnd), HAIR)
    crown = sp.L / 2
    if sp.up:
        # A soft fan of hair on the floor round the back of the head: many overlapping locks.
        centre = V((O.x - 0.01, O.y * 0.5, 0.0))
        for k in range(15):
            ang = -95 + k * (190 / 14)
            r = math.radians(ang + (rnd.random() - 0.5) * 6)
            d = V((math.cos(r), math.sin(r), 0))
            length = 0.15 + 0.035 * math.cos(math.radians(ang)) + rnd.random() * 0.02
            start = centre + d * 0.03 + V((0, 0, 0.03))
            mid = centre + d * (length * 0.55) + V((0, 0, 0.012))
            end = centre + d * length + V((0, 0, 0.005))
            if end.x > crown + 0.03:
                end = end - d * ((end.x - crown - 0.03) / max(0.2, d.x))
            B.capsule([start, mid, end], [0.03, 0.032, 0.018], paint, segs=20, flat=0.3)
    else:
        top = O + A * 0.06 - F * 0.05
        for k, off in enumerate((-0.05, -0.02, 0.01, 0.04)):
            start = top + Lat * off
            nape = O + A * (-0.09) - F * 0.07 + Lat * (off * 1.3)
            end = V((sp.X(0.48), nape.y + off * 0.8, 0))
            hw, f, b, p, zc = torso_ring(sp, 0.48)
            end.z = zc + b * 0.92
            B.capsule([start, nape, end], [0.030, 0.026, 0.016], paint, segs=20, flat=0.45)


# --------------------------------------------------------------------------- limbs

def build_arm(B, sp, side, raised):
    """side +1/-1 (y). raised: forearm up beside the head (face down)."""
    X = sp.X
    rnd = B.rnd
    hw, f, b, p, zc = torso_ring(sp, 0.53)
    sh = V((X(0.535), (hw - 0.02) * side, zc * 0.95))
    r_up = (0.050, 0.042, 0.039) if sp.male else (0.043, 0.036, 0.033)
    r_fore = (0.040, 0.036, 0.026) if sp.male else (0.034, 0.030, 0.022)
    if raised:
        el = V((X(0.64), 0.335 * side, r_up[2] + 0.002))
        wr = V((X(0.76), 0.24 * side, r_fore[2]))
        hand_dir = V((0.55, -0.83 * side, 0)).normalized()
    else:
        el = V((X(0.28), (0.25 if sp.male else 0.225) * side, r_up[2] + 0.004))
        wr = V((X(0.035), (0.29 if sp.male else 0.26) * side, r_fore[2] + 0.002))
        hand_dir = V((-0.97, 0.22 * side, 0)).normalized()
    mid_up = sh.lerp(el, 0.45)
    mid_up.z = max(mid_up.z, r_up[1])
    mid_fore = el.lerp(wr, 0.35)
    mid_fore.z = r_fore[1] + 0.002
    sleeve_end = 0.92 if sp.male else 0.0  # fraction of the forearm covered (male long sleeves)
    short_sleeve = None if sp.male else 0.42  # fraction of the upper arm (female short sleeves)
    top = PAL["shirt"] if sp.male else PAL["blouse"]
    dark = PAL["shirt_dark"] if sp.male else PAL["blouse_dark"]

    def paint_up(s, a, pt):
        if short_sleeve is not None and s > short_sleeve:
            return jitter(sp.skin, 0.02, rnd), SKIN
        if short_sleeve is not None and s > short_sleeve - 0.04:
            return dark, CLOTH
        return jitter(top, 0.03, rnd), CLOTH

    def paint_fore(s, a, pt):
        if s < sleeve_end:
            return (dark if s > sleeve_end - 0.08 else jitter(top, 0.03, rnd)), CLOTH
        return jitter(sp.skin, 0.02, rnd), SKIN
    # Deltoid, upper arm, forearm (sleeves a little fuller than the arm).
    cuff = 1.06 if sp.male else 1.0
    B.capsule([sh, mid_up, el], [r_up[0] * 1.05, r_up[1], r_up[2]], paint_up, segs=32)
    B.capsule([el, mid_fore, wr], [r_fore[0] * cuff, r_fore[1] * cuff, r_fore[2] * (cuff if sp.male else 1)], paint_fore, segs=32)
    build_hand(B, sp, wr, hand_dir, side, raised)
    return sh


def build_hand(B, sp, wr, d, side, raised):
    """Relaxed hand lying palm down on the floor: palm, four fingers and a thumb."""
    rnd = B.rnd
    s = 1.0 if sp.male else 0.9
    up = V((0, 0, 1))
    lat = up.cross(d).normalized()  # left of the pointing direction
    # The thumb is on the side towards the body's midline.
    thumb_side = 1 if (wr + lat * 0.05).y * wr.y < (wr - lat * 0.05).y * wr.y else -1
    skin = lambda *args: (jitter(sp.skin, 0.02, rnd), SKIN)
    nail = lambda s_, a, pt: ((PAL["nail"], SKIN) if s_ > 0.75 and (pt.z > 0.012) else (jitter(sp.skin, 0.02, rnd), SKIN))
    zc = 0.016 * s
    p0 = wr + d * 0.01 * s
    p0.z = zc + 0.004
    p1 = wr + d * 0.085 * s
    p1.z = zc
    rings = []
    n = 14
    for i in range(n + 1):
        t = i / n
        c = p0.lerp(p1, t)
        w = (0.030 + 0.012 * smooth01(t * 1.6)) * s
        h = (0.018 - 0.004 * t) * s
        rings.append((c, lat, up, w, h, h * 0.9, 2.6))
    B.loft(rings, 32, lambda t, a, pt: skin())
    # Fingers: index to little, slightly spread and curled down to the floor.
    for k, (off, ln, r) in enumerate(((0.024, 0.075, 0.0085), (0.008, 0.083, 0.0088), (-0.009, 0.078, 0.0085), (-0.025, 0.062, 0.0075))):
        off = off * thumb_side * s
        spread = (k - 1.5) * 0.06 * -thumb_side
        dd = (d + lat * spread).normalized()
        a = p1 + lat * off + up * 0.002
        b_ = a + dd * (ln * 0.55 * s)
        b_.z = r * s + 0.003
        c = b_ + dd * (ln * 0.45 * s)
        c.z = r * s * 0.85
        B.capsule([a, b_, c], [r * s * 1.05, r * s, r * s * 0.85], nail, segs=16, step=0.003)
    # Thumb.
    a = p0.lerp(p1, 0.35) + lat * (0.03 * thumb_side * s)
    a.z = zc
    tdir = (d * 0.6 + lat * (0.8 * thumb_side)).normalized()
    b_ = a + tdir * 0.035 * s
    c = b_ + (d * 0.9 + lat * 0.4 * thumb_side).normalized() * 0.03 * s
    b_.z = 0.012 * s
    c.z = 0.0095 * s
    B.capsule([a, b_, c], [0.012 * s, 0.0105 * s, 0.009 * s], nail, segs=16, step=0.003)


def build_leg(B, sp, side, bent):
    X = sp.X
    rnd = B.rnd
    y0 = (0.085 if sp.male else 0.09) * side
    r_th = (0.088, 0.070, 0.054) if sp.male else (0.086, 0.066, 0.050)
    r_calf = (0.050, 0.060, 0.042, 0.034) if sp.male else (0.046, 0.054, 0.038, 0.030)
    hip = V((X(-0.035), y0, 0.088 if sp.male else 0.09))
    if bent:
        knee = V((X(-0.40), 0.19 * side, r_th[2] + 0.004))
        ankle = V((X(-0.735), 0.175 * side, r_calf[3] + 0.012))
    else:
        knee = V((X(-0.43), (0.11 if sp.male else 0.10) * side, r_th[2] + 0.004))
        ankle = V((X(-0.795) if sp.up else X(-0.76), (0.135 if sp.male else 0.12) * side, r_calf[3] + (0.024 if sp.up else 0.012)))
    th_mid = hip.lerp(knee, 0.5)
    th_mid.z = r_th[1] + 0.004
    calf = knee.lerp(ankle, 0.35)
    calf.z = r_calf[1] + 0.004
    if sp.male:
        trou = lambda s, a, pt: (jitter(PAL["trousers"], 0.03, rnd), CLOTH)
        B.capsule([hip, th_mid, knee], [r_th[0] * 1.05, r_th[1] * 1.06, r_th[2] * 1.1], trou, segs=36)
        # The trouser hem is wider than the ankle and falls over the top of the shoe.
        hem = ankle + (ankle - calf).normalized() * 0.035
        hem.z = max(hem.z, 0.048)
        B.capsule([knee, calf, ankle, hem], [r_calf[0] * 1.15, r_calf[1] * 1.12, r_calf[2] * 1.25, 0.049, 0.05],
                  lambda s, a, pt: ((PAL["trousers_dark"] if s > 0.95 else jitter(PAL["trousers"], 0.03, rnd)), CLOTH), segs=36)
    else:
        sk = lambda s, a, pt: (jitter(sp.skin, 0.02, rnd), SKIN)
        B.capsule([hip, th_mid, knee], list(r_th), sk, segs=36)
        B.capsule([knee, calf, ankle], [r_calf[0], r_calf[1], r_calf[2], r_calf[3]], sk, segs=36)
    build_shoe(B, sp, ankle, side)
    return hip


def build_shoe(B, sp, ankle, side):
    """Face up: heel on the floor, toes up and turned out. Face down: toes on the floor, soles up."""
    rnd = B.rnd
    s = 1.0 if sp.male else 0.9
    if sp.up:
        heel = V((ankle.x - 0.05 * s, ankle.y, 0.035 * s))
        toe_dir = V((-0.28, 0.72 * side, 0.62)).normalized()
        top_hint = V((1, 0.35 * side, 0.6))
    else:
        heel = V((ankle.x - 0.03 * s, ankle.y, ankle.z + 0.05 * s))
        toe_dir = V((-0.80, 0.10 * side, -0.59)).normalized()
        top_hint = V((0, 0, -1))
    length = 0.27 * s if sp.male else 0.235
    u, v = frame(toe_dir, top_hint)
    colour = PAL["shoe_m" if sp.male else "shoe_f"]
    rings = []
    raws = []
    n = 28
    for i in range(n + 1):
        t = i / n
        c = heel + toe_dir * (length * t - 0.03 * s)
        w = (0.032 + 0.017 * math.sin(min(1.0, t * 1.15) * math.pi * 0.62)) * s
        if t > 0.85:
            w *= math.sqrt(max(0.05, 1 - ((t - 0.85) / 0.15) ** 2))
        # Tall at the instep, low over the toes.
        top = (0.064 - 0.042 * smooth01((t - 0.18) / 0.72)) * s if sp.male else (0.050 - 0.038 * smooth01((t - 0.15) / 0.7))
        if t < 0.08:
            top *= math.sqrt(max(0.1, t / 0.08))
            w *= math.sqrt(max(0.3, t / 0.08))
        if t > 0.9:
            top *= math.sqrt(max(0.1, 1 - (t - 0.9) / 0.1))
        bottom = 0.016 * s
        rings.append((c, u, v, w, top, bottom, 3.0))
        raws.append(t)

    def paint(t, a, pt):
        tt = raws[min(n, round(t * n))]
        sa = math.sin(a)
        if sa < -0.55:
            return PAL["sole"], SHOE
        if sp.male and sa > 0.7 and 0.3 < tt < 0.6 and abs(math.cos(a)) < 0.4:
            return (PAL["lace"] if (tt * 22) % 1.0 < 0.38 else mix(colour, PAL["lace"], 0.5)), SHOE
        if sp.male and sa > 0.2 and 0.8 < tt and abs(math.cos(a)) < 0.9:
            return jitter(mix(colour, (0.3, 0.3, 0.3), 0.15), 0.04, rnd), SHOE  # toe cap shine
        return jitter(colour, 0.05, rnd), SHOE
    B.loft(rings, 32, paint)
    # Sole (a welt a little wider than the upper) and a heel block.
    sole_rings = []
    for i in range(n + 1):
        c, uu, vv, w, top, bottom, p = rings[i]
        t = raws[i]
        thick = (0.018 if t < 0.3 else 0.010) * s if sp.male else (0.014 if t < 0.28 else 0.007)
        sole_rings.append((c - vv * (bottom + thick * 0.2), uu, vv, w + 0.005 * s, thick * 0.5 + 0.002, thick * 0.5 + 0.002, 4.0))
    B.loft(sole_rings, 32, lambda t, a, pt: (jitter(PAL["sole"], 0.04, rnd), SHOE))


def build_skirt(B, sp):
    """Female: knee-length skirt over the hips and thighs, settled on the floor."""
    X = sp.X
    rnd = B.rnd
    rings = []
    raws = []
    for i in range(62):
        xr = 0.075 - i * 0.0075  # waist to just above the knee
        hw_t, f_t, b_t, p_t, zc_t = torso_ring(sp, max(xr, -0.07))
        t = smooth01((0.03 - xr) / 0.48)
        # The top edge tucks into the waist; below the hips the skirt spreads over the thighs.
        tuck = smooth01((xr - 0.05) / 0.025)
        ease = 0.008 - 0.012 * tuck
        hw = hw_t * (1 - t) + 0.215 * t + ease
        f = f_t * (1 - t) + 0.105 * t + ease
        b = b_t * (1 - t) + 0.05 * t + ease
        zc = (b_t if sp.up else f_t) * (1 - t) + (b if sp.up else f) * t
        rings.append((V((X(xr), 0, zc)), V((0, 1, 0)), V((0, 0, sp.s)), hw, f, b, 2.2 + 0.25 * t))
        raws.append(xr)

    def paint(t, a, pt):
        xr = raws[min(len(raws) - 1, round(t * (len(raws) - 1)))]
        if xr > 0.045:
            return PAL["skirt_dark"], CLOTH  # waistband
        if xr < raws[-1] + 0.012:
            return PAL["skirt_dark"], CLOTH  # hem
        return jitter(PAL["skirt"], 0.03, rnd), CLOTH
    B.loft(rings, 64, paint)


def build_parts(sex, pose):
    sp = Spec(sex, pose)
    B = Builder(hash((sex, pose)) & 0xffff)
    build_torso(B, sp)
    head = build_head(B, sp)
    build_neck(B, sp, head)
    if not sp.male:
        build_long_hair(B, sp, head)
        build_skirt(B, sp)
    # Face down: the right arm (y < 0) is raised beside the head, the left leg bent.
    build_arm(B, sp, 1, raised=False)
    build_arm(B, sp, -1, raised=not sp.up)
    build_leg(B, sp, 1, bent=not sp.up)
    build_leg(B, sp, -1, bent=False)
    fit_length(B, sp)
    return sp, B


def fit_length(B, sp):
    """Keeps the body inside VictimPlacer's box (half length = length / 2 + 0.06, with 1 cm to
    spare): centres it along x, and if the feet still reach past the box (face down, the feet lie
    flat behind the ankles), shortens the shins and feet a little."""
    hl = sp.L / 2 + 0.05
    xs = [v.x for v in B.verts]
    lo, hi = min(xs), max(xs)
    shift = min(max(-(lo + hi) / 2, -hl - lo), hl - hi)
    lo += shift
    x0 = sp.X(-0.45)  # knees
    k = 1.0 if lo >= -hl else (x0 + hl) / (x0 - lo)
    for v in B.verts:
        v.x += shift
        if v.x < x0:
            v.x = x0 + (v.x - x0) * k


# --------------------------------------------------------------------------- remesh, bake, export

def clear_scene_objects(prefix):
    for ob in list(bpy.data.objects):
        if ob.name.startswith(prefix):
            bpy.data.objects.remove(ob, do_unlink=True)


def make_high(sex, pose, voxel=0.003):
    name = f"victim_{sex}_{pose}"
    clear_scene_objects(name)
    sp, B = build_parts(sex, pose)
    src = B.to_object(name + "_src")
    hi = src.copy()
    hi.data = src.data.copy()
    hi.name = name + "_hi"
    bpy.context.scene.collection.objects.link(hi)
    mod = hi.modifiers.new("remesh", "REMESH")
    mod.mode = "VOXEL"
    mod.voxel_size = voxel
    mod.adaptivity = 0.0
    mod.use_smooth_shade = True
    bpy.context.view_layer.objects.active = hi
    for o in bpy.context.selected_objects:
        o.select_set(False)
    hi.select_set(True)
    bpy.ops.object.modifier_apply(modifier="remesh")
    # Soften the joins between parts.
    sm = hi.modifiers.new("smooth", "CORRECTIVE_SMOOTH")
    sm.factor = 0.5
    sm.iterations = 6
    sm.smooth_type = "SIMPLE"
    sm.use_only_smooth = True
    bpy.ops.object.modifier_apply(modifier="smooth")
    transfer_attributes(src, hi)
    wrinkle(hi, B.rnd)
    floor_contact(hi)
    src.hide_set(True)
    src.hide_render = True
    return sp, hi


def transfer_attributes(src, dst):
    """Colour and surface kind of each remeshed vertex from the nearest source vertex."""
    import numpy as np
    sm = src.data
    n = len(sm.vertices)
    sco = np.empty(n * 3, dtype=np.float32)
    sm.vertices.foreach_get("co", sco)
    kd = kdtree.KDTree(n)
    for i, c in enumerate(sco.reshape(-1, 3)):
        kd.insert(c, i)
    kd.balance()
    scol = np.empty(n * 4, dtype=np.float32)
    sm.color_attributes["Col"].data.foreach_get("color", scol)
    skind = np.empty(n, dtype=np.int32)
    sm.attributes["kind"].data.foreach_get("value", skind)
    dm = dst.data
    for name in [a.name for a in dm.color_attributes]:
        dm.color_attributes.remove(dm.color_attributes[name])
    if "kind" in dm.attributes:
        dm.attributes.remove(dm.attributes["kind"])
    m = len(dm.vertices)
    dco = np.empty(m * 3, dtype=np.float32)
    dm.vertices.foreach_get("co", dco)
    find = kd.find
    idx = np.fromiter((find(c)[1] for c in dco.reshape(-1, 3)), dtype=np.int64, count=m)
    dcol = dm.color_attributes.new("Col", "FLOAT_COLOR", "POINT")
    dcol.data.foreach_set("color", scol.reshape(-1, 4)[idx].ravel())
    dkind = dm.attributes.new("kind", "INT", "POINT")
    dkind.data.foreach_set("value", skind[idx])


def wrinkle(ob, rnd):
    """Fine folds on clothes (they show in the baked shading) and strands in the hair."""
    import numpy as np
    from mathutils import noise
    me = ob.data
    m = len(me.vertices)
    co = np.empty(m * 3, dtype=np.float32); me.vertices.foreach_get("co", co); co = co.reshape(-1, 3)
    nr = np.empty(m * 3, dtype=np.float32); me.vertices.foreach_get("normal", nr); nr = nr.reshape(-1, 3)
    kinds = np.empty(m, dtype=np.int32); me.attributes["kind"].data.foreach_get("value", kinds)
    h = np.zeros(m, dtype=np.float32)
    nz = noise.noise
    off = V((3, 1, 7))
    for i in np.nonzero(kinds == CLOTH)[0]:
        p = V(co[i]) * 9.0
        h[i] = nz(p) * 0.0022 + nz(p * 2.7 + off) * 0.0009
    for i in np.nonzero(kinds == HAIR)[0]:
        c = co[i]
        h[i] = nz(V((c[0] * 25, c[1] * 160, c[2] * 160))) * 0.0012
    co += nr * h[:, None]
    me.vertices.foreach_set("co", co.ravel())
    # Uneven skin tone: slightly redder over cheeks, knuckles and lips, a little pale elsewhere.
    col = np.empty(m * 4, dtype=np.float32)
    me.color_attributes["Col"].data.foreach_get("color", col)
    col = col.reshape(-1, 4)
    for i in np.nonzero(kinds == SKIN)[0]:
        c = co[i]
        f = nz(V((c[0] * 18, c[1] * 18, c[2] * 18)))
        col[i, 0] *= 1 + 0.06 * f
        col[i, 1] *= 1 + 0.03 * f
        col[i, 2] *= 1 + 0.02 * f
    me.color_attributes["Col"].data.foreach_set("color", col.ravel())
    me.update()


def floor_contact(ob):
    """Nothing below the floor, and a slightly flattened contact patch."""
    import numpy as np
    me = ob.data
    co = np.empty(len(me.vertices) * 3, dtype=np.float32); me.vertices.foreach_get("co", co); co = co.reshape(-1, 3)
    z = co[:, 2]
    low = z < 0.004
    z[low] = np.maximum(0.0, 0.004 - (0.004 - z[low]) * 0.35)
    me.vertices.foreach_set("co", co.ravel())
    me.update()


def make_low(hi, ratio):
    lo = hi.copy()
    lo.data = hi.data.copy()
    lo.name = hi.name.replace("_hi", "")
    bpy.context.scene.collection.objects.link(lo)
    for o in bpy.context.selected_objects:
        o.select_set(False)
    bpy.context.view_layer.objects.active = lo
    lo.select_set(True)
    # Keep more triangles where detail matters: the face and head, then hands and shoes.
    import numpy as np
    me = lo.data
    m = len(me.vertices)
    co = np.empty(m * 3, dtype=np.float32); me.vertices.foreach_get("co", co); co = co.reshape(-1, 3)
    kinds = np.empty(m, dtype=np.int32); me.attributes["kind"].data.foreach_get("value", kinds)
    head_x = co[:, 0].max() - 0.27
    w = np.where(co[:, 0] > head_x, 0.45, np.where(kinds == SKIN, 0.3, np.where(kinds == SHOE, 0.2, 0.0)))
    vg = lo.vertex_groups.new(name="detail")
    for value in (0.45, 0.3, 0.2):
        idx = np.nonzero(np.isclose(w, value))[0].tolist()
        if idx:
            vg.add(idx, value, "REPLACE")
    dec = lo.modifiers.new("dec", "DECIMATE")
    dec.ratio = ratio
    dec.use_collapse_triangulate = True
    dec.vertex_group = "detail"
    dec.invert_vertex_group = True
    dec.vertex_group_factor = 1.0
    bpy.ops.object.modifier_apply(modifier="dec")
    bpy.ops.object.shade_smooth()
    unwrap(lo)
    return lo


def unwrap(lo):
    """Smart UV projection with more texels where they are looked at: the head twice the density
    of the clothes, the hands 1.4 times."""
    me = lo.data
    import numpy as np
    head_x = max(v.co.x for v in me.vertices) - 0.27
    kinds = np.empty(len(me.vertices), dtype=np.int32)
    me.attributes["kind"].data.foreach_get("value", kinds)

    def group(p):
        c = p.center
        if c.x > head_x:
            return 0
        if all(kinds[v] == SKIN for v in p.vertices):
            return 1
        return 2
    groups = [group(p) for p in me.polygons]
    density = {0: 2.0, 1: 1.4, 2: 1.0}
    bpy.ops.object.mode_set(mode="EDIT")
    bm = bmesh.from_edit_mesh(me)
    bm.faces.ensure_lookup_table()
    uv = bm.loops.layers.uv.verify()
    for g in (0, 1, 2):
        for f in bm.faces:
            f.select_set(groups[f.index] == g)
        bmesh.update_edit_mesh(me)
        if not any(groups[f.index] == g for f in bm.faces):
            continue
        bpy.ops.uv.smart_project(angle_limit=math.radians(60), island_margin=0.002, area_weight=0.0, scale_to_bounds=False)
        bm = bmesh.from_edit_mesh(me)
        bm.faces.ensure_lookup_table()
        uv = bm.loops.layers.uv.verify()
        faces = [f for f in bm.faces if groups[f.index] == g]
        a3 = sum(f.calc_area() for f in faces)
        a2 = 0.0
        for f in faces:
            pts = [l[uv].uv for l in f.loops]
            a2 += abs(sum(pts[i].x * pts[i - 1].y - pts[i - 1].x * pts[i].y for i in range(len(pts)))) / 2
        k = math.sqrt(a3 / max(a2, 1e-9)) * density[g]
        for f in faces:
            for l in f.loops:
                l[uv].uv = l[uv].uv * k
    for f in bm.faces:
        f.select_set(True)
    bmesh.update_edit_mesh(me)
    bpy.ops.uv.pack_islands(rotate=True, margin=0.003)
    bpy.ops.object.mode_set(mode="OBJECT")


def emission_material(name):
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    attr = nt.nodes.new("ShaderNodeVertexColor")
    attr.layer_name = "Col"
    em = nt.nodes.new("ShaderNodeEmission")
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(attr.outputs["Color"], em.inputs["Color"])
    nt.links.new(em.outputs["Emission"], out.inputs["Surface"])
    return mat


def bake(hi, lo, size=1024, tmpdir=None):
    """Bakes colour (emission of the vertex colours) and AO from hi onto lo; returns the combined image."""
    scn = bpy.context.scene
    try:
        scn.render.engine = "CYCLES"
    except TypeError as e:
        raise RuntimeError(str(e))
    scn.cycles.samples = 64
    scn.cycles.device = "CPU"
    try:
        scn.cycles.use_denoising = False
    except AttributeError:
        pass
    # Floor for contact occlusion.
    floor = bpy.data.objects.get("fz_bake_floor")
    if floor is None:
        fm = bpy.data.meshes.new("fz_bake_floor")
        s = 3.0
        fm.from_pydata([(-s, -s, -0.0005), (s, -s, -0.0005), (s, s, -0.0005), (-s, s, -0.0005)], [], [(0, 1, 2, 3)])
        floor = bpy.data.objects.new("fz_bake_floor", fm)
        scn.collection.objects.link(floor)
    floor.hide_render = False
    floor.hide_set(False)
    # Only this body and the floor take part (other variants share the origin and would shadow it).
    for o in scn.objects:
        if o.type == "MESH" and o not in (hi, lo, floor):
            o.hide_render = True
    lo.hide_render = False
    lo.hide_set(False)
    # The low-poly target must not occlude the high-poly surface it is baked from.
    for attr in ("visible_diffuse", "visible_glossy", "visible_shadow", "visible_transmission", "visible_volume_scatter"):
        if hasattr(lo, attr):
            setattr(lo, attr, False)

    hi.data.materials.clear()
    hi.data.materials.append(emission_material("fz_victim_vcol"))
    imgs = {}
    for kind in ("col", "ao"):
        img = bpy.data.images.get(f"{lo.name}_{kind}")
        if img:
            bpy.data.images.remove(img)
        img = bpy.data.images.new(f"{lo.name}_{kind}", size, size, alpha=False, float_buffer=True)
        img.colorspace_settings.name = "Non-Color"
        imgs[kind] = img
    mat = bpy.data.materials.new(lo.name + "_bake")
    mat.use_nodes = True
    lo.data.materials.clear()
    lo.data.materials.append(mat)
    tex = mat.node_tree.nodes.new("ShaderNodeTexImage")
    mat.node_tree.nodes.active = tex

    for o in bpy.context.selected_objects:
        o.select_set(False)
    hi.hide_set(False); hi.hide_render = False
    hi.select_set(True)
    lo.select_set(True)
    bpy.context.view_layer.objects.active = lo
    bk = scn.render.bake
    bk.use_selected_to_active = True
    bk.cage_extrusion = 0.012
    bk.max_ray_distance = 0.03
    bk.margin = 6
    for kind, btype in (("col", "EMIT"), ("ao", "AO")):
        tex.image = imgs[kind]
        bpy.ops.object.bake(type=btype)
    # Combine: colour x (soft AO), back to sRGB, into an 8-bit PNG image.
    import numpy as np
    col = np.array(imgs["col"].pixels[:], dtype=np.float32).reshape(-1, 4)
    ao = np.array(imgs["ao"].pixels[:], dtype=np.float32).reshape(-1, 4)[:, :1]
    shade = 0.42 + 0.58 * np.clip(ao, 0, 1) ** 1.2
    rgb = np.clip(col[:, :3] * shade, 0, 1)
    out = bpy.data.images.get(lo.name + "_tex")
    if out:
        bpy.data.images.remove(out)
    out = bpy.data.images.new(lo.name + "_tex", size, size, alpha=False)
    px = np.ones((size * size, 4), dtype=np.float32)
    px[:, :3] = rgb  # vertex colours were authored in display (sRGB) values already
    out.pixels.foreach_set(px.ravel())  # (a new byte image is already sRGB; changing it here would reload the buffer)
    out.update()
    if tmpdir:
        out.filepath_raw = os.path.join(tmpdir, lo.name + ".png")
        out.file_format = "PNG"
        out.save()
    floor.hide_set(True); floor.hide_render = True
    for attr in ("visible_diffuse", "visible_glossy", "visible_shadow", "visible_transmission", "visible_volume_scatter"):
        if hasattr(lo, attr):
            setattr(lo, attr, True)
    return out


def final_material(lo, img):
    mat = bpy.data.materials.new(lo.name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = img
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.85
    lo.data.materials.clear()
    lo.data.materials.append(mat)
    # The texture already holds the colours: drop the vertex attributes from the export.
    for name in [a.name for a in lo.data.color_attributes]:
        lo.data.color_attributes.remove(lo.data.color_attributes[name])
    if "kind" in lo.data.attributes:
        lo.data.attributes.remove(lo.data.attributes["kind"])


def export(lo, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    for o in bpy.context.selected_objects:
        o.select_set(False)
    lo.hide_set(False)
    lo.select_set(True)
    bpy.context.view_layer.objects.active = lo
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True, export_yup=True,
                              export_apply=True, export_normals=True, export_texcoords=True,
                              export_materials="EXPORT", export_vertex_color="NONE", export_animations=False,
                              export_image_format="JPEG", export_jpeg_quality=90,
                              export_skins=False, export_morph=False, export_cameras=False, export_lights=False)


def build_one(repo, sex, pose, ratio=None, size=1024):
    sp, hi = make_high(sex, pose)
    faces = len(hi.data.polygons)
    target_tris = 12000
    ratio = ratio or min(1.0, target_tris / (faces * 2.0))
    lo = make_low(hi, ratio)
    tmp = os.path.join(repo, "art", "textures")
    img = bake(hi, lo, size=size, tmpdir=tmp)
    final_material(lo, img)
    hi.hide_set(True)
    hi.hide_render = True
    path = os.path.join(repo, "app", "src", "main", "assets", "models", "victim", f"victim_{sex}_{pose}.glb")
    export(lo, path)
    xs = [v.co for v in lo.data.vertices]
    bb = [min(c.x for c in xs), min(c.y for c in xs), min(c.z for c in xs), max(c.x for c in xs), max(c.y for c in xs), max(c.z for c in xs)]
    return {"name": lo.name, "hi_faces": faces, "lo_tris": sum(len(p.vertices) - 2 for p in lo.data.polygons), "bounds": [round(b, 3) for b in bb], "path": path}


def build_all(repo):
    out = []
    for sex in ("male", "female"):
        for pose in ("face_up", "face_down"):
            out.append(build_one(repo, sex, pose))
    tidy()
    return out


def tidy():
    """Drops the high-poly sources and bake buffers (rebuilt in about a minute by build_all),
    keeping the four exported low-poly bodies and their textures."""
    for ob in list(bpy.data.objects):
        if ob.name.startswith("victim_") and (ob.name.endswith("_hi") or "_src" in ob.name):
            me = ob.data
            bpy.data.objects.remove(ob, do_unlink=True)
            if me.users == 0:
                bpy.data.meshes.remove(me)
    for im in list(bpy.data.images):
        if im.name.startswith("victim_") and (im.name.endswith("_col") or im.name.endswith("_ao")):
            bpy.data.images.remove(im)
    for me in list(bpy.data.meshes):
        if me.users == 0:
            bpy.data.meshes.remove(me)
