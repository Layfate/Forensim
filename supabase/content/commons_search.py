"""Search Wikimedia Commons for freely licensed images. Usage: commons_search.py "query1" "query2" ..."""
import json, re, sys, urllib.parse, urllib.request
sys.stdout.reconfigure(encoding="utf-8")

UA = {"User-Agent": "ForensimContentBuilder/1.0 (educational app; contact via project maintainer)"}
API = "https://commons.wikimedia.org/w/api.php"
FREE = re.compile(r"^(CC0|CC BY|CC-BY|Public domain|PD|GFDL)", re.I)


def get(params):
    import time, urllib.error
    url = API + "?" + urllib.parse.urlencode(params)
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                time.sleep(1.5)
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code != 429: raise
            time.sleep(10 * (attempt + 1))
    raise SystemExit("rate limited")


def strip(html):
    return re.sub(r"<[^>]+>", "", html or "").strip()


for q in sys.argv[1:]:
    data = get({"action": "query", "format": "json", "generator": "search", "gsrsearch": q,
                "gsrnamespace": 6, "gsrlimit": 8, "prop": "imageinfo", "iiprop": "url|extmetadata|size",
                "iiurlwidth": 1000})
    print(f"### {q}")
    pages = sorted((data.get("query") or {}).get("pages", {}).values(), key=lambda p: p.get("index", 0))
    for p in pages:
        ii = p["imageinfo"][0]
        md = ii.get("extmetadata", {})
        lic = strip(md.get("LicenseShortName", {}).get("value"))
        if not FREE.match(lic):
            continue
        artist = strip(md.get("Artist", {}).get("value"))[:60]
        desc = strip(md.get("ImageDescription", {}).get("value"))[:90].replace("\n", " ")
        print(f"- {p['title'][5:]} | {ii['width']}x{ii['height']} | {lic} | {artist} | {desc}")
