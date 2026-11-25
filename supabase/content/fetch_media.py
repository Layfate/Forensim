"""Download Commons images for lessons, convert to WebP, record credits. Usage: fetch_media.py <assets_dir> <manifest.json>"""
import io, json, os, re, sys, time, urllib.parse, urllib.request, urllib.error
from PIL import Image, ImageDraw, ImageFont

sys.stdout.reconfigure(encoding="utf-8")
UA = {"User-Agent": "ForensimContentBuilder/1.0 (educational app; contact via project maintainer)"}
API = "https://commons.wikimedia.org/w/api.php"
OUT, MANIFEST = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)

ITEMS = {
    "photo_niepce": "View from the Window at Le Gras, Joseph Nicéphore Niépce.jpg",
    "photo_camera_obscura": "Camera obscura box.jpg",
    "photo_em_spectrum": "Electromagnetic spectrum -eng.svg",
    "photo_slr": "SLR cross section2.svg",
    "photo_chromatic": "Chromatic aberration lens diagram.svg",
    "photo_sd_cards": "SD Cards.svg",
    "pid_arch": "Fingerprint Arch.jpg",
    "pid_loop": "Fingerprint Loop.jpg",
    "pid_whorl": "Fingerprint Whorl.jpg",
    "pid_minutiae": "Fingerprints Minutiae Patterns Representation.jpg",
    "pid_bertillon_card": "Bertillon, Alphonse, fiche anthropométrique recto-verso.jpg",
    "pid_portrait_parle": "search:intitle:Tableau synoptic traits physionomiques MET",
    "pid_iris": "Iris-recognition-biometric-pipeline.svg",
    "chem_skull": "GHS-pictogram-skull.svg",
    "chem_flame": "GHS-pictogram-flamme.svg",
    "chem_acid": "GHS-pictogram-acid.svg",
    "chem_health": "GHS-pictogram-silhouette.svg",
    "chem_marquis": "Marquis Reagent.jpg",
    "chem_tlc": "Thin layer chromatography under UV lamp.jpg",
    "chem_paracelsus": "Paracelsus-03.jpg",
    "chem_evidence_bag": "search:intitle:Muhammed Adel Abdul Gafoor marks an evidence bag",
    "qde_typewriter": "Parts of a Corona No. 3 folding typewriter (front).jpg",
    "qde_signatures": "Van Meegeren signatures.jpg",
    "qde_stereo": "Optical stereo microscope nikon smz10.jpg",
    "qde_watermark": "50 Euro bill watermark.jpg",
    "poly_keeler": "Leonarde Keeler 1937.jpg",
    "poly_lafayette": "search:intitle:Lügendetektor Polygraph Lafayette Model 76056",
    "poly_ans": "The Autonomic Nervous System.jpg",
    "bal_ignition": "Cartridge ignition types Boxer Primer Berdan Primer Rimfire Pinfire Teatfire Cupfire cc BY-SA 4.0 by Grasyl.svg",
    "bal_fired_cases": "Fired rimfire and centerfire casings.jpg",
    "bal_bullets": "Firearm Bullet Types CC BY-SA 4.0 by Grasyl.svg",
    "bal_rifling_diagram": "Polygonal vs normal rifling.svg",
    "bal_rifling_bore": "Rifling inside a rifle barrel, view down the bore.jpg",
    "bal_comparison": "ComparisonMicroscope.png",
}
# Parts only used inside composites (not shipped on their own).
COMPOSITE_PARTS = {"pid_arch", "pid_loop", "pid_whorl", "chem_skull", "chem_flame", "chem_acid", "chem_health"}


def http(url):
    for attempt in range(8):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                data = r.read()
            time.sleep(6)
            return data
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            wait = int(e.headers.get("Retry-After") or 60) + 15
            print(f"  rate limited, waiting {wait}s", flush=True)
            time.sleep(wait)
    raise SystemExit("rate limited: " + url)


def api(params):
    return json.loads(http(API + "?" + urllib.parse.urlencode({"format": "json", **params})))


def strip(html):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", html or "")).strip()


def info(spec):
    q = {"prop": "imageinfo", "iiprop": "url|extmetadata|size", "iiurlwidth": 960}
    if spec.startswith("search:"):
        d = api({"action": "query", "generator": "search", "gsrsearch": spec[7:], "gsrnamespace": 6, "gsrlimit": 1, **q})
    else:
        d = api({"action": "query", "titles": "File:" + spec, **q})
    page = next(iter(d["query"]["pages"].values()))
    if "imageinfo" not in page:
        raise SystemExit("missing: " + spec)
    ii = page["imageinfo"][0]
    # Bots are throttled hard on original files; always ask for a standard-size thumbnail.
    if ii.get("thumburl", "").find("thumbnail_unscaled") >= 0 or ii.get("thumburl") == ii.get("url"):
        width = ii.get("width", 0)
        std = [w for w in (250, 330, 500, 960) if w < width]
        if std:
            d2 = api({"action": "query", "titles": page["title"], "prop": "imageinfo", "iiprop": "url|extmetadata|size", "iiurlwidth": std[-1]})
            ii = next(iter(d2["query"]["pages"].values()))["imageinfo"][0]
    return page["title"][5:], ii


def to_rgb(img):
    if img.mode in ("RGBA", "LA", "P"):
        img = img.convert("RGBA")
        bg = Image.new("RGB", img.size, "white")
        bg.paste(img, mask=img.split()[-1])
        return bg
    return img.convert("RGB")


def save(img, key):
    img = to_rgb(img)
    if img.width > 1000:
        img = img.resize((1000, round(img.height * 1000 / img.width)), Image.LANCZOS)
    path = os.path.join(OUT, key + ".webp")
    img.save(path, "WEBP", quality=82, method=6)
    return os.path.getsize(path)


def font(size):
    for f in ("arialbd.ttf", "arial.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def composite(keys, labels, cell, title=None):
    imgs = [to_rgb(Image.open(io.BytesIO(raw[k]))) for k in keys]
    pad, lab_h = 24, 56
    top = 70 if title else 0
    W = pad + len(imgs) * (cell + pad)
    H = top + pad + cell + lab_h + pad
    canvas = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(canvas)
    if title:
        d.text((W / 2, 38), title, fill=(20, 20, 20), font=font(34), anchor="mm")
    for i, (im, lab) in enumerate(zip(imgs, labels)):
        im.thumbnail((cell, cell), Image.LANCZOS)
        x = pad + i * (cell + pad)
        canvas.paste(im, (x + (cell - im.width) // 2, top + pad + (cell - im.height) // 2))
        d.text((x + cell / 2, top + pad + cell + lab_h / 2), lab, fill=(20, 20, 20), font=font(30), anchor="mm")
    return canvas


def dose_response():
    """Original diagram: sigmoid dose-response curve with the LD50 marked."""
    import math
    W, H = 1000, 640
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = 120, 560, 940, 80
    d.line([(x0, y0), (x1, y0)], fill="black", width=4)
    d.line([(x0, y0), (x0, y1)], fill="black", width=4)
    pts = []
    for i in range(0, 401):
        t = i / 400
        p = 1 / (1 + math.exp(-12 * (t - 0.5)))
        pts.append((x0 + t * (x1 - x0), y0 - p * (y0 - y1 - 20)))
    d.line(pts, fill=(200, 40, 40), width=6)
    mx, my = x0 + 0.5 * (x1 - x0), y0 - 0.5 * (y0 - y1 - 20)
    for (a, b) in [((x0, my), (mx, my)), ((mx, my), (mx, y0))]:
        d.line([a, b], fill=(90, 90, 90), width=3)
    d.ellipse([mx - 10, my - 10, mx + 10, my + 10], fill=(200, 40, 40))
    f, s = font(30), font(26)
    d.text((mx + 20, my - 40), "LD50: dose that kills 50%", fill=(200, 40, 40), font=f)
    d.text((x0 - 14, my), "50%", fill="black", font=s, anchor="rm")
    d.text((x0 - 14, y1 + 20), "100%", fill="black", font=s, anchor="rm")
    d.text((x0 - 14, y0), "0%", fill="black", font=s, anchor="rm")
    d.text(((x0 + x1) / 2, y0 + 50), "Dose  (increasing)  →", fill="black", font=f, anchor="mm")
    d.text((40, (y0 + y1) / 2), "Response", fill="black", font=f, anchor="mm")
    d.text((W / 2, 36), "Dose–response relationship", fill="black", font=font(34), anchor="mm")
    return img


CACHE = os.path.join(os.path.dirname(MANIFEST), "raw")
os.makedirs(CACHE, exist_ok=True)
def cartridge():
    """Original labelled cross-section of a center-fire cartridge."""
    W, H = 1000, 560
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)
    brass, copper, powder, dark = (214, 170, 72), (196, 110, 60), (90, 90, 90), (30, 30, 30)
    cy = 300
    # cartridge case (straight body + rim), horizontal, base on the left
    d.rectangle([150, cy - 70, 600, cy + 70], fill=brass, outline=dark, width=4)
    d.rectangle([130, cy - 82, 160, cy + 82], fill=brass, outline=dark, width=4)  # rim
    # propellant grains inside the case
    import random
    random.seed(4)
    for _ in range(170):
        x, y = random.randint(185, 585), random.randint(cy - 55, cy + 55)
        d.ellipse([x - 5, y - 4, x + 5, y + 4], fill=powder)
    # primer in the centre of the base
    d.rectangle([118, cy - 24, 150, cy + 24], fill=(170, 170, 175), outline=dark, width=4)
    # bullet seated in the mouth, with an ogive nose
    d.rectangle([560, cy - 64, 700, cy + 64], fill=copper, outline=dark, width=4)
    d.pieslice([600, cy - 64, 860, cy + 64], start=-90, end=90, fill=copper, outline=dark, width=4)
    d.line([(700, cy - 62), (700, cy + 62)], fill=copper, width=6)
    f, t = font(30), font(36)
    d.text((W / 2, 40), "Parts of a center-fire cartridge", fill=dark, font=t, anchor="mm")
    def label(xy, txt, to, anchor):
        d.line([xy, to], fill=dark, width=3)
        d.ellipse([to[0] - 6, to[1] - 6, to[0] + 6, to[1] + 6], fill=dark)
        d.text(xy, txt, fill=dark, font=f, anchor=anchor)
    label((760, 130), "1  Bullet", (760, cy - 30), "lm")
    label((380, 130), "2  Cartridge case", (420, cy - 70), "mm")
    label((380, 480), "3  Gunpowder (propellant)", (380, cy + 40), "mm")
    label((60, 470), "5  Primer", (134, cy + 10), "lm")
    label((60, 130), "4  Rim", (145, cy - 78), "lm")
    return img


raw, manifest = {}, {}
for key, spec in ITEMS.items():
    meta_path, bin_path = os.path.join(CACHE, key + ".json"), os.path.join(CACHE, key + ".bin")
    if os.path.exists(meta_path) and os.path.exists(bin_path):
        manifest[key] = json.load(open(meta_path, encoding="utf-8"))
        raw[key] = open(bin_path, "rb").read()
    else:
        title, ii = info(spec)
        md = ii.get("extmetadata", {})
        lic = strip(md.get("LicenseShortName", {}).get("value"))
        artist = strip(md.get("Artist", {}).get("value")) or "Unknown author"
        artist = re.sub(r"^(Unknown author)+", "Unknown author", artist)
        raw[key] = http(ii.get("thumburl") or ii["url"])
        manifest[key] = {"file": title, "credit": f"{artist}, {lic}, via Wikimedia Commons"}
        open(bin_path, "wb").write(raw[key])
        json.dump(manifest[key], open(meta_path, "w", encoding="utf-8"), ensure_ascii=False)
    credit = manifest[key]["credit"]
    title = manifest[key]["file"]
    if key not in COMPOSITE_PARTS:
        size = save(Image.open(io.BytesIO(raw[key])), key)
        manifest[key]["bytes"] = size
    print(f"{key}: {title} | {credit} | {manifest[key].get('bytes', 'part')}")

c = composite(["pid_arch", "pid_loop", "pid_whorl"], ["Arch", "Loop", "Whorl"], 280, "The three basic fingerprint patterns")
manifest["pid_patterns"] = {"credit": "NIST fingerprint images, public domain, via Wikimedia Commons", "bytes": save(c, "pid_patterns")}
c = composite(["chem_skull", "chem_health", "chem_acid", "chem_flame"], ["Acute toxicity", "Health hazard", "Corrosive", "Flammable"], 240, "GHS hazard pictograms")
manifest["chem_ghs"] = {"credit": "GHS pictograms, public domain, via Wikimedia Commons", "bytes": save(c, "chem_ghs")}
manifest["chem_dose_response"] = {"credit": "Forensim diagram", "bytes": save(dose_response(), "chem_dose_response")}
manifest["bal_cartridge"] = {"credit": "Forensim diagram", "bytes": save(cartridge(), "bal_cartridge")}
for k in COMPOSITE_PARTS:
    manifest.pop(k, None)
json.dump(manifest, open(MANIFEST, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("total KB:", sum(v.get("bytes", 0) for v in manifest.values()) // 1024)
