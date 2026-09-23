"""Capture actual emulator pixels and inspect/tap Android accessibility nodes.

No screen contents or random results are synthesized. Run from the repo root.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe')
if not Path(ADB).exists():
    ADB = 'C:/Users/sblas/AppData/Local/Android/Sdk/platform-tools/adb.exe'
ROOT = Path(__file__).resolve().parents[1]

def adb(*args):
    result = subprocess.run([ADB, '-s', 'emulator-5554', *args], capture_output=True, check=True)
    return result.stdout

def nodes():
    for attempt in range(4):
        output = adb('shell', 'uiautomator', 'dump', '/sdcard/store-window.xml')
        if b'dumped to' in output:
            xml = adb('shell', 'cat', '/sdcard/store-window.xml')
            return ET.fromstring(xml).iter('node')
        time.sleep(1)
    raise RuntimeError('Could not capture the accessibility hierarchy')

def tap(label):
    for node in nodes():
        if node.get('text') == label or node.get('content-desc') == label:
            x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            return
    raise RuntimeError(f'No accessible element {label!r}')

def capture(name):
    dest = ROOT / 'raw' / f'{name}.png'
    dest.write_bytes(adb('exec-out', 'screencap', '-p'))
    print(dest)

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('action', choices=['capture', 'clean-capture', 'inspect', 'tap', 'question'])
    p.add_argument('value', nargs='?')
    args = p.parse_args()
    if args.action in ('capture', 'clean-capture'):
        if args.action == 'clean-capture':
            for extras in [('command','exit'), ('command','enter'), ('command','clock','hhmm','0941'), ('command','battery','level','100','plugged','false'), ('command','notifications','visible','false')]:
                flags = [v for pair in zip(extras[::2], extras[1::2]) for v in ('-e', *pair)]
                adb('shell','am','broadcast','-a','com.android.systemui.demo',*flags)
            time.sleep(.5)
        capture(args.value)
    elif args.action == 'inspect':
        print(json.dumps([{k:n.get(k) for k in ['text','content-desc','class','bounds','enabled']} for n in nodes() if n.get('text') or n.get('content-desc') or n.get('class') == 'android.widget.EditText'], ensure_ascii=True, indent=2))
    elif args.action == 'tap':
        tap(args.value)
    elif args.action == 'question':
        for n in nodes():
            if n.get('class') == 'android.widget.EditText':
                x1,y1,x2,y2 = map(int, re.findall(r'\d+', n.get('bounds')))
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
                if n.get('text'):
                    adb('shell', 'input', 'keyevent', *(['KEYCODE_DEL'] * len(n.get('text'))))
                break
        adb('shell', 'input', 'text', args.value.replace(' ', '%s'))
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(.5)
