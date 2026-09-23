"""Validate deliverable dimensions, PNG format, listing limits and local links."""
import json
from pathlib import Path
import re
import struct
from urllib.parse import unquote, urlsplit

root = Path(__file__).resolve().parents[1]
report = {'images': [], 'copy': {}, 'missing_links': []}
for group in ('phone/real', 'phone/generated', 'tablet'):
    for number in range(1, 5):
        p = root / group / f'{number:02}.png'
        data = p.read_bytes()
        assert data[:8] == b'\x89PNG\r\n\x1a\n', p
        width, height, depth, color = struct.unpack('>IIBB', data[16:26])
        assert (width, height, depth, color) == (1080, 1920, 8, 2), (p, width, height, depth, color)
        report['images'].append({'file': p.relative_to(root).as_posix(), 'size': [width, height], 'format': 'RGB PNG', 'bytes': len(data)})
for field, limit in [('title', 30), ('short-description', 80), ('full-description', 4000)]:
    content = (root / 'copy' / (field + '.txt')).read_text(encoding='utf-8').strip()
    assert 0 < len(content) <= limit, (field, len(content))
    report['copy'][field] = {'characters': len(content), 'limit': limit}
for html in [root/'index.html', root/'review/contact-sheet.html']:
    for attr in re.findall(r'(?:src|href)="([^"]+)"', html.read_text(encoding='utf-8')):
        if '${' in attr or urlsplit(attr).scheme or attr.startswith('#'):
            continue
        target = html.parent / unquote(urlsplit(attr).path)
        if not target.exists():
            report['missing_links'].append(str(target))
assert not report['missing_links'], report['missing_links']
(root/'validation.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
print(f"Verified {len(report['images'])} opaque 1080 x 1920 RGB PNGs, 3 listing fields, and static gallery links.")
print(json.dumps(report['copy']))
