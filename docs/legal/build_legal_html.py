"""Builds privacy-policy.html and terms-of-use.html from the in-app text in LegalScreens.kt,
so the hosted pages (Play Console "Privacy policy URL") always match what the app shows.

Run: python docs/legal/build_legal_html.py   (from the project root)
"""
import html
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/com/forensim/app/feature/legal/LegalScreens.kt"
OUT = pathlib.Path(__file__).resolve().parent

src = SRC.read_text(encoding="utf-8")
info = dict(re.findall(r'const val (\w+) = "([^"]*)"', src))


def expand(s: str) -> str:
    s = s.replace("${LegalInfo.APP}", info["APP"]).replace("$e", info["CONTACT_EMAIL"])
    return s.replace('\\"', '"')


def sections(name: str):
    block = re.search(rf"private val {name} = listOf\((.*?)\n\)\n", src, re.S).group(1)
    out = []
    for m in re.finditer(r'LegalSection\("((?:[^"\\]|\\.)*)", listOf\((.*?)\)\),', block, re.S):
        paras = [expand(p) for p in re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(2))]
        out.append((expand(m.group(1)), paras))
    return out


PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title} | {app}</title>
<style>
:root{{color-scheme:light dark;--bg:#fff;--fg:#2a1a1f;--muted:#6b5a5f;--line:#ebe3e1}}
@media (prefers-color-scheme:dark){{:root{{--bg:#121212;--fg:#f4eeee;--muted:#bfb2b5;--line:#332d31}}}}
body{{margin:0;background:var(--bg);color:var(--fg);font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}}
main{{max-width:720px;margin:0 auto;padding:40px 16px 64px}}
h1{{font-size:28px;margin:0 0 4px}} h2{{font-size:18px;margin:28px 0 6px}}
p{{margin:6px 0;color:var(--muted)}} .date{{color:var(--muted);font-size:14px;border-bottom:1px solid var(--line);padding-bottom:16px}}
a{{color:inherit}}
</style></head><body><main>
<h1>{title}</h1><div class="date">{app} &middot; Effective {date}</div>
{body}
</main></body></html>
"""

for name, title, file in [("PRIVACY", "Privacy Policy", "privacy-policy.html"), ("TERMS", "Terms of Use", "terms-of-use.html")]:
    body = []
    for heading, paras in sections(name):
        body.append(f"<h2>{html.escape(heading)}</h2>")
        for p in paras:
            esc = html.escape(p).replace(info["CONTACT_EMAIL"], f'<a href="mailto:{info["CONTACT_EMAIL"]}">{info["CONTACT_EMAIL"]}</a>')
            body.append(f"<p>{esc}</p>")
    (OUT / file).write_text(PAGE.format(title=title, app=info["APP"], date=info["EFFECTIVE_DATE"], body="\n".join(body)), encoding="utf-8")
    print(file, len(body), "blocks")
