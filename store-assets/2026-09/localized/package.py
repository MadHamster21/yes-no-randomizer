"""Validate the 18 upload sets and package them using only Python's standard library."""
import hashlib
import json
from pathlib import Path
import struct
import zipfile
import zlib

ROOT = Path(__file__).resolve().parent
locales = json.loads((ROOT / "i18n.json").read_text(encoding="utf-8"))
render = json.loads((ROOT / "render-validation.json").read_text(encoding="utf-8"))
tablet_render = json.loads((ROOT / "tablet-render-validation.json").read_text(encoding="utf-8"))
assert len(tablet_render) == 72 and all(row["textFits"] for row in tablet_render)
assert {(row["locale"], row["card"]) for row in tablet_render} == {
    (item["locale"], f"tablet-{number:02}") for item in locales for number in range(1, 5)
}
browser_data = json.loads((ROOT / "data.js").read_text(encoding="utf-8").removeprefix("window.storeLocales = ").strip().removesuffix(";"))

def add_workspace(bundle, items):
    for name in ("index.html", "gallery.js", "gallery.css", "README.md"):
        bundle.write(ROOT / name, name)
    bundle.writestr("data.js", "window.storeStandalone = true;\nwindow.storeLocales = " + json.dumps(items, ensure_ascii=False) + ";\n")

assert len(render) == 90 and all(row["textFits"] for row in render)
assert {(row["locale"], row["card"]) for row in render} == {
    (item["locale"], card) for item in locales for card in ("feature", "01", "02", "03", "04")
}
reports = []
all_files = []
(ROOT / "bundles").mkdir(exist_ok=True)
for item in locales:
    folder = ROOT / "locales" / item["locale"]
    report = {"locale": item["locale"], "copy": {}, "images": []}
    files = []
    for field, limit in (("title", 30), ("short-description", 80), ("full-description", 4000)):
        p = folder / (field + ".txt")
        text = p.read_text(encoding="utf-8").strip()
        assert 0 < len(text) <= limit, (p, len(text))
        assert "{decide}" not in text and "{float}" not in text and "{new}" not in text
        report["copy"][field] = {"characters": len(text), "limit": limit}
        files.append(p)
    files.append(folder / "alt-text.json")
    files.append(folder / "index.html")
    for name, size in [("feature-graphic.png", (1024, 500))] + [
        (f"phone/{number:02}.png", (1080, 1920)) for number in range(1, 5)
    ] + [
        (f"tablet-10-landscape/{number:02}.png", (1920, 1080)) for number in range(1, 5)
    ]:
        p = folder / name
        raw = p.read_bytes()
        assert raw[:8] == b"\x89PNG\r\n\x1a\n", p
        width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", raw[16:29])
        assert (width, height) == size and (depth, color, interlace) == (8, 2, 0), p
        assert len(raw) < 15_000_000, p
        offset, chunks = 8, []
        while offset < len(raw):
            length = struct.unpack_from(">I", raw, offset)[0]
            tag = raw[offset + 4:offset + 8]
            data = raw[offset + 8:offset + 8 + length]
            assert zlib.crc32(tag + data) == struct.unpack_from(">I", raw, offset + 8 + length)[0], p
            if tag == b"IDAT":
                chunks.append(data)
            offset += length + 12
        assert len(zlib.decompress(b"".join(chunks))) == height * (1 + width * 3), p
        report["images"].append({"file": name, "width": width, "height": height,
                                 "format": "RGB PNG", "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()})
        files.append(p)
    for capture in ("light", "dark", "floating", "languages"):
        assert (ROOT / "raw" / item["locale"] / (capture + ".png")).is_file()
        source = ROOT / "raw-tablet" / item["locale"] / (capture + ".png")
        width, height = struct.unpack(">II", source.read_bytes()[16:24])
        assert width > height or capture == "floating", source
    assert len(files) == 14
    with zipfile.ZipFile(ROOT / "bundles" / (item["locale"] + ".zip"), "w", zipfile.ZIP_DEFLATED) as bundle:
        for p in files:
            bundle.write(p, p.relative_to(ROOT).as_posix())
        add_workspace(bundle, [row for row in browser_data if row["locale"] == item["locale"]])
    with zipfile.ZipFile(ROOT / "bundles" / (item["locale"] + ".zip"), "r") as bundle:
        assert bundle.testzip() is None
    all_files.extend(files)
    reports.append(report)
with zipfile.ZipFile(ROOT / "all-locales.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
    for p in all_files:
        bundle.write(p, p.relative_to(ROOT).as_posix())
    add_workspace(bundle, browser_data)
with zipfile.ZipFile(ROOT / "all-locales.zip", "r") as bundle:
    assert bundle.testzip() is None
(ROOT / "validation.json").write_text(json.dumps({"locales": 18, "images": 162, "checks": "passed", "results": reports}, indent=2) + "\n", encoding="utf-8")
print("Validated 18 listings, 162 opaque PNGs, 144 source captures, text fitting and ZIP integrity.")
print(f"All-locales ZIP: {(ROOT / 'all-locales.zip').stat().st_size:,} bytes")
