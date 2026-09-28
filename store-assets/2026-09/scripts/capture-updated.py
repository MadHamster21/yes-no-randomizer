"""Refresh the store inputs using normal interactions on emulator-5554.

Run after installing the latest debug APK. Output is versioned under updated/.
Display overrides and system theme are restored; no answers are synthesized.
"""
import importlib.util
from pathlib import Path
import re
import subprocess
import struct
import time

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('capture', Path(__file__).with_name('capture.py'))
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)
OUT = ROOT / 'updated' / 'raw'
OUT.mkdir(parents=True, exist_ok=True)
saved = {}
for key in ['size', 'density']:
    match = re.search(r'Override (?:size|density): (\S+)', c.adb('shell', 'wm', key).decode())
    saved[key] = match[1] if match else 'reset'
saved['night'] = c.adb('shell', 'cmd', 'uimode', 'night').decode().strip().split()[-1]
for key in ['accelerometer_rotation', 'user_rotation']:
    saved[key] = c.adb('shell', 'settings', 'get', 'system', key).decode().strip()

def capture(name):
    (OUT / f'{name}.png').write_bytes(c.adb('exec-out', 'screencap', '-p'))
    print(name, flush=True)

def question(text):
    subprocess.run(['python', str(Path(__file__).with_name('capture.py')), 'question', text], check=True)

def roll_until(answer, button='Decide!'):
    for _ in range(30):
        c.tap(button)
        time.sleep(3.5)
        # Android's XML dumper omits Compose stateDescription. Inspect the actual
        # GL surface colors instead; this reads pixels without altering them.
        surface = next(n for n in c.nodes() if n.get('class') == 'android.view.TextureView')
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', surface.get('bounds')))
        pixels = c.adb('exec-out', 'screencap')
        width, height, pixel_format = struct.unpack('<III', pixels[:12])
        assert pixel_format == 1, 'Expected RGBA_8888 screenshot'
        offset = len(pixels) - width * height * 4
        assert offset in (12, 16), 'Unexpected screencap header'
        blue = red = 0
        for y in range(y1, y2, 4):
            for x in range(x1, x2, 4):
                index = offset + (y * width + x) * 4
                r, g, b = pixels[index:index + 3]
                blue += b > 128 and b > r + 20
                red += r > 128 and r > b + 20
        is_yes = blue > red
        if is_yes == (answer in ('Yes', 'Sí')):
            return
    raise RuntimeError(f'No {answer} outcome after 30 normal random rolls')

try:
    c.adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    c.adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    c.adb('shell', 'wm', 'size', '1080x1920')
    c.adb('shell', 'wm', 'density', '400')
    c.adb('shell', 'cmd', 'uimode', 'night', 'no')
    c.adb('shell', 'am', 'start', '-W', '-n', 'com.sblashkov.yesnorandomizer/.MainActivity')
    time.sleep(2)
    question('Pizza tonight?')
    roll_until('Yes')
    capture('phone-light-roll-2')
    c.adb('shell', 'cmd', 'uimode', 'night', 'yes')
    time.sleep(2)
    roll_until('No')
    capture('phone-dark-rolled')
    c.tap('Float answer')
    time.sleep(3)
    capture('phone-pip-home')
    c.adb('shell', 'am', 'start', '-W', '-n', 'com.sblashkov.yesnorandomizer/.MainActivity')
    time.sleep(2)
    c.adb('shell', 'cmd', 'uimode', 'night', 'no')
    time.sleep(2)
    c.tap('🇺🇸 English (US)')
    time.sleep(1)
    capture('phone-language-picker')
    c.tap('🇪🇸 Español')
    time.sleep(2)
    question('Pizza hoy?')
    roll_until('Sí', '¡Decidir!')
    capture('phone-spanish')
    c.tap('🇪🇸 Español')
    time.sleep(1)
    # Scroll toward the first row because the dialog starts at the selected item.
    c.adb('shell', 'input', 'swipe', '600', '800', '600', '1300', '1000')
    time.sleep(1)
    c.tap('🇺🇸 English (US)')
    time.sleep(1)
finally:
    for key in ['size', 'density']:
        c.adb('shell', 'wm', key, saved[key])
    c.adb('shell', 'cmd', 'uimode', 'night', saved['night'])
    for key in ['accelerometer_rotation', 'user_rotation']:
        if saved[key] == 'null':
            c.adb('shell', 'settings', 'delete', 'system', key)
        else:
            c.adb('shell', 'settings', 'put', 'system', key, saved[key])
