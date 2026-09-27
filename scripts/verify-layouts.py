"""Run the installed adaptive-layout device test across real Android viewports.

First build/install app-debug.apk and app-debug-androidTest.apk. ADB must address
an emulator, never a user's physical device. Display settings are restored.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--serial', default='emulator-5554')
p.add_argument('--output', type=Path, default=ROOT/'build/layout-checks')
args = p.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('This runner only modifies emulator viewports.')
sdk = (ROOT/'local.properties').read_text().split('sdk.dir=', 1)[1].strip().replace('\\:', ':').replace('\\\\', '\\')
adb_path = Path(sdk)/'platform-tools'/('adb.exe' if os.name == 'nt' else 'adb')
args.output.mkdir(parents=True, exist_ok=True)

def adb(*parts):
    return subprocess.run([str(adb_path), '-s', args.serial, *parts], check=True, capture_output=True).stdout

def setting(name):
    return adb('shell', 'settings', 'get', 'system', name).decode().strip()

def tap_text(text):
    adb('shell', 'uiautomator', 'dump', '/sdcard/layout-check.xml')
    tree = ET.fromstring(adb('shell', 'cat', '/sdcard/layout-check.xml'))
    node = next(n for n in tree.iter('node') if n.get('text') == text)
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

saved = {k:setting(k) for k in ['user_rotation', 'accelerometer_rotation']}
for key in ['size', 'density']:
    match = re.search(r'Override (?:size|density): (\S+)', adb('shell','wm',key).decode())
    saved[key] = match.group(1) if match else 'reset'
matrix = [('compact-portrait','720x1280','320','0'),
          ('compact-landscape','720x1280','320','1'),
          ('phone','1080x1920','400','0'),
          ('tablet-portrait','1080x1920','240','0'),
          ('tablet-landscape','1080x1920','240','1')]
results=[]
try:
    adb('shell','settings','put','system','accelerometer_rotation','0')
    for name,size,density,rotation in matrix:
        adb('shell','wm','size',size)
        adb('shell','wm','density',density)
        adb('shell','settings','put','system','user_rotation',rotation)
        time.sleep(2)
        output=adb('shell','am','instrument','-w','-e','class',
                   'com.sblashkov.yesnorandomizer.AdaptiveLayoutTest',
                   'com.sblashkov.yesnorandomizer.test/androidx.test.runner.AndroidJUnitRunner').decode().replace('\r', '').strip() + '\n'
        (args.output/f'{name}.txt').write_text(output,encoding='utf-8')
        passed='OK (1 test)' in output
        results.append({'viewport':name,'size':size,'density':density,'rotation':rotation,'passed':passed})
        print(f'{name}: {"PASS" if passed else "FAIL"}',flush=True)
        if not passed:
            raise RuntimeError(output)
        adb('shell','am','start','-W','-n','com.sblashkov.yesnorandomizer/.MainActivity')
        time.sleep(1)
        tap_text('Decide!')
        time.sleep(3.5)
        (args.output/f'{name}.png').write_bytes(adb('exec-out','screencap','-p'))
finally:
    for key in ['size','density']:
        adb('shell','wm',key,saved[key])
    for key in ['user_rotation','accelerometer_rotation']:
        if saved[key]=='null':
            adb('shell','settings','delete','system',key)
        else:
            adb('shell','settings','put','system',key,saved[key])
    (args.output/'results.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
