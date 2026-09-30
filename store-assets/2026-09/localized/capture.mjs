import {spawn} from 'node:child_process';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(root, '../../..');
const serial = process.env.ANDROID_SERIAL || 'emulator-5554';
const adb = process.env.ADB || 'C:/Users/sblas/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const tablet = process.argv.includes('--tablet');
const selected = process.argv.slice(2).filter(arg => arg !== '--tablet');
const rawFolder = tablet ? 'raw-tablet' : 'raw';
const locales = JSON.parse(await readFile(path.join(root, 'i18n.json'), 'utf8'))
  .filter(item => !selected.length || selected.includes(item.locale));
if (!locales.length) throw Error('No matching locales');
async function run(args, live = false) {
  return new Promise((resolve, reject) => {
    const child = spawn(adb, ['-s', serial, ...args], {cwd: repo, windowsHide: true});
    let output = '';
    child.stdout.on('data', chunk => { output += chunk; if (live) process.stdout.write(chunk); });
    child.stderr.on('data', chunk => { output += chunk; if (live) process.stderr.write(chunk); });
    child.on('error', reject);
    child.on('exit', code => code === 0 ? resolve(output) : reject(Error(output)));
  });
}
const original = {};
original.windowRotation = (await run(['shell', 'wm', 'user-rotation'])).trim().split(/\s+/);
for (const field of ['size', 'density']) {
  original[field] = (await run(['shell', 'wm', field])).match(/Override (?:size|density): (\S+)/)?.[1] || 'reset';
}
for (const field of ['accelerometer_rotation', 'user_rotation']) {
  original[field] = (await run(['shell', 'settings', 'get', 'system', field])).trim();
}
await mkdir(path.join(root, rawFolder), {recursive: true});
try {
  await run(['install', '-r', 'app/build/outputs/apk/debug/app-debug.apk']);
  await run(['install', '-r', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']);
  await run(['shell', 'wm', 'size', tablet ? '2560x1600' : '1080x1920']);
  await run(['shell', 'wm', 'density', tablet ? '320' : '400']);
  await run(['shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0']);
  await run(['shell', 'settings', 'put', 'system', 'user_rotation', '0']);
  await run(['shell', 'wm', 'user-rotation', 'lock', '0']);
  for (const item of locales) {
    // Isolate each ActivityScenario so the previous PiP task cannot retain
    // another locale's configuration. Pull each successful locale immediately.
    const {locale, appLocale, label, question} = item;
    const input = Buffer.from(JSON.stringify([{locale, appLocale, label, question}])).toString('base64');
    const result = await run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'storeLocales', input,
      '-e', 'storeTablet', String(tablet),
      '-e', 'class', 'com.sblashkov.yesnorandomizer.StoreListingCapture',
      'com.sblashkov.yesnorandomizer.test/androidx.test.runner.AndroidJUnitRunner'], true);
    await writeFile(path.join(repo, `build/localized-capture-${tablet ? 'tablet-' : ''}${locale}.txt`), result);
    if (!result.includes('OK (1 test)')) throw Error(`Capture instrumentation failed: ${locale}`);
    await run(['pull', '/sdcard/Android/data/com.sblashkov.yesnorandomizer/files/store-localized/' + item.locale,
      path.join(root, rawFolder)]);
  }
} finally {
  for (const field of ['size', 'density']) await run(['shell', 'wm', field, original[field]]);
  for (const field of ['accelerometer_rotation', 'user_rotation']) {
    await run(['shell', 'settings', original[field] === 'null' ? 'delete' : 'put', 'system', field,
      ...(original[field] === 'null' ? [] : [original[field]])]);
  }
  await run(['shell', 'wm', 'user-rotation', ...original.windowRotation]);
}
console.log(`Captured ${locales.length} locales on ${serial}; display settings restored.`);
