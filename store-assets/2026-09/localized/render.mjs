import {spawn} from 'node:child_process';
import {readFile, writeFile, mkdir, mkdtemp, access, unlink} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(root, '../../..');
const workspace = process.env.STORE_WORKSPACE ? path.resolve(process.env.STORE_WORKSPACE) : root;
const chrome = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const tablet = process.argv.includes('--tablet');
const screenshotsOnly = process.argv.includes('--screenshots-only');
const checkOnly = process.argv.includes('--check');
const selected = process.argv.slice(2).filter(arg => !['--tablet', '--check', '--screenshots-only'].includes(arg));
const catalog = JSON.parse(await readFile(path.join(root, 'i18n.json'), 'utf8'));
const locales = catalog.filter(item => !selected.length || selected.includes(item.locale));
const profile = await mkdtemp(path.join(repo, 'build/localized-chrome-'));
const browser = spawn(chrome, ['--headless=new', '--disable-gpu', '--hide-scrollbars',
  '--no-first-run', '--no-default-browser-check', '--allow-file-access-from-files',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], {windowsHide: true, stdio: 'ignore'});
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
let socket;
try {
  let port;
  for (let attempt = 0; attempt < 120 && !port; attempt++) {
    try { port = Number((await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]); }
    catch { await delay(250); }
  }
  if (!port) throw Error('Chrome did not start');
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  let sequence = 0;
  const pending = new Map();
  socket.onmessage = ({data}) => {
    const reply = JSON.parse(data);
    if (pending.has(reply.id)) {
      const {resolve, reject, timer} = pending.get(reply.id);
      clearTimeout(timer); pending.delete(reply.id);
      reply.error ? reject(Error(JSON.stringify(reply.error))) : resolve(reply.result);
    }
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(Error('Timed out: ' + method)); }, 30000);
    pending.set(id, {resolve, reject, timer}); socket.send(JSON.stringify({id, method, params}));
  });
  await send('Page.enable');
  await send('Runtime.enable');
  const results = [];
  for (const item of checkOnly ? [] : locales) {
    const cards = tablet ? ['tablet-01', 'tablet-02', 'tablet-03', 'tablet-04']
      : screenshotsOnly ? ['01', '02', '03', '04'] : ['feature', '01', '02', '03', '04'];
    for (const card of cards) {
      const [width, height] = tablet ? [1920, 1080] : card === 'feature' ? [1024, 500] : [1080, 1920];
      await send('Emulation.setDeviceMetricsOverride', {width, height, deviceScaleFactor: 1, mobile: false});
      const url = pathToFileURL(path.join(root, 'templates/artwork.html'));
      url.search = new URLSearchParams({locale: item.locale, card}).toString();
      await send('Page.navigate', {url: url.href});
      let ready;
      for (let attempt = 0; attempt < 80; attempt++) {
        const probe = await send('Runtime.evaluate', {expression: 'location.href', returnByValue: true});
        if (probe.result.value === url.href) {
          const loaded = await send('Runtime.evaluate', {expression: 'typeof window.artworkReady !== "undefined"', returnByValue: true});
          if (loaded.result.value) { ready = true; break; }
        }
        await delay(100);
      }
      if (!ready) throw Error('Artwork did not load: ' + url.href);
      const check = await send('Runtime.evaluate', {expression: 'window.artworkReady', awaitPromise: true, returnByValue: true});
      if (check.exceptionDetails) throw Error(`${item.locale} ${card}: ${JSON.stringify(check.exceptionDetails)}`);
      const number = Number(card.slice(-2));
      const filename = tablet ? `${String(number).padStart(2, '0')}-${item.locale}-tablet-10in-landscape-1920x1080.png`
        : card === 'feature' ? `00-${item.locale}-feature-1024x500.png`
          : `${String(number).padStart(2, '0')}-${item.locale}-phone-1080x1920.png`;
      const relativeOutput = tablet ? `tablet-10-landscape/${filename}` : card === 'feature' ? filename : `phone/${filename}`;
      const output = path.join(root, 'locales', item.locale, relativeOutput);
      await mkdir(path.dirname(output), {recursive: true});
      const shot = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: false, clip: {x: 0, y: 0, width, height, scale: 1}});
      await writeFile(output, Buffer.from(shot.data, 'base64'));
      const oldRelativeOutput = tablet ? `tablet-10-landscape/${String(number).padStart(2, '0')}.png`
        : card === 'feature' ? 'feature-graphic.png' : `phone/${card}.png`;
      if (oldRelativeOutput !== relativeOutput) {
        try { await unlink(path.join(root, 'locales', item.locale, oldRelativeOutput)); }
        catch (error) { if (error.code !== 'ENOENT') throw error; }
      }
      results.push(check.result.value);
    }
    const rendered = tablet ? 'four landscape tablet screenshots'
      : screenshotsOnly ? 'four phone screenshots' : 'feature graphic and four phone screenshots';
    console.log(`Rendered ${item.locale}: ${rendered}; text fits.`);
  }
  if (!checkOnly) {
    const reportName = `${tablet ? 'tablet-' : ''}render-validation${selected.length ? '-partial' : ''}.json`;
    if (!tablet && screenshotsOnly) {
      const reportPath = path.join(root, 'render-validation.json');
      const previous = JSON.parse(await readFile(reportPath, 'utf8'));
      const updatedLocales = new Set(results.map(row => row.locale));
      const preserved = previous.filter(row => row.card === 'feature' || !updatedLocales.has(row.locale));
      await writeFile(reportPath, JSON.stringify([...preserved, ...results], null, 2) + '\n');
    } else {
      await writeFile(path.join(root, reportName), JSON.stringify(results, null, 2) + '\n');
    }
  }
  if (!selected.length || checkOnly) {
    const gallery = pathToFileURL(path.join(workspace, 'index.html'));
    if (selected.length) gallery.hash = selected[0];
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    await send('Page.navigate', {url: gallery.href});
    const waitGallery = async () => {
      for (let attempt = 0; attempt < 100; attempt++) {
        const ready = await send('Runtime.evaluate', {expression: 'typeof window.galleryReady === "function"', returnByValue: true});
        if (ready.result.value) {
          const check = await send('Runtime.evaluate', {expression: 'window.galleryReady()', awaitPromise: true, returnByValue: true});
          if (check.exceptionDetails) throw Error(JSON.stringify(check.exceptionDetails));
          return check.result.value;
        }
        await delay(100);
      }
      throw Error('Gallery did not load');
    };
    await waitGallery();
    await send('Emulation.setFocusEmulationEnabled', {enabled: true});
    await send('Browser.grantPermissions', {permissions: ['clipboardReadWrite', 'clipboardSanitizedWrite']});
    const evaluate = async expression => {
      const result = await send('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true, userGesture: true});
      if (result.exceptionDetails) throw Error(JSON.stringify(result.exceptionDetails));
      return result.result.value;
    };
    const checks = [];
    const workspaceCodes = workspace === root ? catalog.map(item => item.locale) : await evaluate('window.storeLocales.map(item => item.locale)');
    const workspaceLocales = workspaceCodes.map(locale => catalog.find(item => item.locale === locale));
    for (const item of workspaceLocales) {
      await send('Runtime.evaluate', {expression: `window.showLocale(${JSON.stringify(item.locale)})`});
      const result = await waitGallery();
      const expectedCount = workspaceLocales.length;
      if (result.images !== 9 || result.locales !== expectedCount) throw Error('Incomplete gallery: ' + item.locale);
      const copy = await evaluate(`(async () => {
        const originalWrite = navigator.clipboard.writeText.bind(navigator.clipboard);
        let copiedText;
        navigator.clipboard.writeText = async text => { copiedText = text; await originalWrite(text); };
        const item = window.storeLocales.find(row => row.locale === ${JSON.stringify(item.locale)});
        for (const [id, expected] of [['title',item.title],['short',item.short],['full',item.full],['alt-feature',item.alt.feature]]) {
          const field = document.getElementById(id);
          if (field.value !== expected) throw Error('Wrong copy: ' + id);
          document.querySelector('[data-copy="' + id + '"]').click();
          if (!await window.copyComplete) throw Error('Copy action failed: ' + id);
          if (copiedText !== expected) throw Error('Wrong clipboard write request: ' + id);
        }
        const pathButton = document.querySelector('[data-path$="/phone/"]');
        pathButton.click();
        if (!await window.copyComplete) throw Error('Copy path action failed');
        if (copiedText !== filePath(pathButton.dataset.path)) throw Error('Folder path mismatch');
        let fallbackText;
        const captureCopy = () => { const active = document.activeElement; fallbackText = active.value.slice(active.selectionStart, active.selectionEnd); };
        document.addEventListener('copy', captureCopy);
        navigator.clipboard.writeText = async () => { throw Error('Exercise local-file fallback'); };
        document.querySelector('[data-copy="full"]').click();
        if (!await window.copyComplete || fallbackText !== item.full) throw Error('Clipboard fallback failed');
        document.removeEventListener('copy', captureCopy);
        navigator.clipboard.writeText = originalWrite;
        return {copyButtonPayloads: true, clipboardWritesResolved: true, clipboardFallback: true, folderPath: true};
      })()`);
      const links = await evaluate('[...document.querySelectorAll("a[href]")].filter(a => !a.hidden && a.protocol === "file:").map(a => a.href)');
      for (const link of links) await access(fileURLToPath(link));
      checks.push({locale: item.locale, ...result, ...copy, localLinks: links.length});
    }
    for (const item of workspaceLocales) {
      const pageUrl = pathToFileURL(path.join(workspace, 'locales', item.locale, 'index.html')).href;
      await send('Page.navigate', {url: pageUrl});
      await delay(200);
      await waitGallery();
      if (await evaluate('document.getElementById("locale").value') !== item.locale) throw Error('Wrong standalone language: ' + item.locale);
    }
    await send('Page.navigate', {url: gallery.href});
    await delay(200);
    await waitGallery();
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: false});
    if (await evaluate('document.documentElement.scrollWidth > innerWidth') ) throw Error('Mobile gallery overflows');
    await send('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false});
    const preview = await send('Page.captureScreenshot', {format: 'png'});
    await mkdir(path.join(root, 'review'), {recursive: true});
    await writeFile(path.join(root, 'review', 'upload-workspace.png'), Buffer.from(preview.data, 'base64'));
    for (const item of checks) {
      item.directLanguagePage = true;
      item.mobileLayout = true;
    }
    await mkdir(path.join(root, 'review'), {recursive: true});
    for (const start of selected.length ? [] : [0, 6, 12]) {
      gallery.search = `review=1&start=${start}${tablet ? '&tablet=1' : ''}`;
      await send('Page.navigate', {url: gallery.href});
      await delay(300);
      await waitGallery();
      const height = (await send('Runtime.evaluate', {expression: 'document.documentElement.scrollHeight', returnByValue: true})).result.value;
      const shot = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: true, clip: {x: 0, y: 0, width: 1440, height, scale: 1}});
      await writeFile(path.join(root, 'review', `${tablet ? 'tablet-' : ''}${1 + start / 6}.png`), Buffer.from(shot.data, 'base64'));
    }
    await writeFile(path.join(root, selected.length ? 'gallery-validation-partial.json' : 'gallery-validation.json'), JSON.stringify(checks, null, 2) + '\n');
    console.log(`Checked ${checks.length} language pages, copy buttons, image links and responsive layout.`);
  }
  await send('Browser.close').catch(() => {});
} finally {
  socket?.close();
  browser.kill();
}
