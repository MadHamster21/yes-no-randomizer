import {spawn} from 'node:child_process';
import {readFile, writeFile, mkdir, mkdtemp} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(root, '../../..');
const chrome = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const selected = process.argv.slice(2);
const locales = JSON.parse(await readFile(path.join(root, 'i18n.json'), 'utf8'))
  .filter(item => !selected.length || selected.includes(item.locale));
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
  for (const item of locales) {
    for (const card of ['feature', '01', '02', '03', '04']) {
      const [width, height] = card === 'feature' ? [1024, 500] : [1080, 1920];
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
      const output = path.join(root, 'locales', item.locale, card === 'feature' ? 'feature-graphic.png' : `phone/${card}.png`);
      await mkdir(path.dirname(output), {recursive: true});
      const shot = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: false, clip: {x: 0, y: 0, width, height, scale: 1}});
      await writeFile(output, Buffer.from(shot.data, 'base64'));
      results.push(check.result.value);
    }
    console.log(`Rendered ${item.locale}: feature graphic and four screenshots; text fits.`);
  }
  await writeFile(path.join(root, selected.length ? 'render-validation-partial.json' : 'render-validation.json'), JSON.stringify(results, null, 2) + '\n');
  if (!selected.length) {
    const gallery = pathToFileURL(path.join(root, 'index.html'));
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
    const checks = [];
    for (const item of locales) {
      await send('Runtime.evaluate', {expression: `window.showLocale(${JSON.stringify(item.locale)})`});
      const result = await waitGallery();
      if (result.images !== 5 || result.locales !== 18) throw Error('Incomplete gallery: ' + item.locale);
      checks.push({locale: item.locale, ...result});
    }
    await mkdir(path.join(root, 'review'), {recursive: true});
    for (const start of [0, 6, 12]) {
      gallery.search = `review=1&start=${start}`;
      await send('Page.navigate', {url: gallery.href});
      await delay(300);
      await waitGallery();
      const height = (await send('Runtime.evaluate', {expression: 'document.documentElement.scrollHeight', returnByValue: true})).result.value;
      const shot = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: true, clip: {x: 0, y: 0, width: 1440, height, scale: 1}});
      await writeFile(path.join(root, 'review', `${1 + start / 6}.png`), Buffer.from(shot.data, 'base64'));
    }
    await writeFile(path.join(root, 'gallery-validation.json'), JSON.stringify(checks, null, 2) + '\n');
    console.log('Checked all 18 gallery selections and exported three review sheets.');
  }
  await send('Browser.close').catch(() => {});
} finally {
  socket?.close();
  browser.kill();
}
