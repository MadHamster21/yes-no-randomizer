import {readFile, writeFile, mkdir} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const res = path.resolve(root, '../../../app/src/main/res');
const input = JSON.parse(await readFile(path.join(root, 'i18n.json'), 'utf8'));
const expected = ['en-US', 'es-ES', 'fr-FR', 'ar', 'es-419', 'de-DE', 'hi-IN', 'id', 'it-IT',
  'ja-JP', 'ko-KR', 'pl-PL', 'pt-PT', 'ru-RU', 'th', 'tr-TR', 'vi', 'zh-CN'];
if (JSON.stringify(input.map(item => item.locale)) !== JSON.stringify(expected)) throw Error('Locale inventory mismatch');
function decode(text) {
  return text.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/\\'/g, "'")
    .replace(/\\u([0-9a-f]{4})/gi, (_, n) => String.fromCharCode(parseInt(n, 16)));
}
async function strings(folder) {
  const xml = await readFile(path.join(res, folder, 'strings.xml'), 'utf8');
  return Object.fromEntries([...xml.matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)]
    .map(([, key, value]) => [key, decode(value)]));
}
const base = await strings('values');
const report = [];
const data = [];
const page = await readFile(path.join(root, 'index.html'), 'utf8');
for (const item of input) {
  const ui = {...base, ...await strings(item.resource)};
  const tokens = {decide: ui.decide_button_text, float: ui.pip_button_text, new: ui.pip_new_answer};
  const full = item.paragraphs.join('\n\n').replace(/\{(decide|float|new)\}/g, (_, key) => tokens[key])
    .replace(/!\./g, '!');
  const folder = path.join(root, 'locales', item.locale);
  await mkdir(path.join(folder, 'phone'), {recursive: true});
  const counts = {};
  for (const [field, text, limit] of [['title', item.title, 30], ['short-description', item.short, 80], ['full-description', full, 4000]]) {
    const length = Array.from(text).length;
    if (!length || length > limit || /\{(?:decide|float|new)\}/.test(text)) throw Error(`${item.locale} ${field}: ${length}/${limit}`);
    counts[field] = {characters: length, limit};
    await writeFile(path.join(folder, `${field}.txt`), text + '\n', 'utf8');
  }
  const alt = {feature: `${item.title}. ${item.headlines[0]}`,
    screenshots: item.headlines.map((headline, index) => `${headline} ${item.subtitles[index]}`)};
  if ([alt.feature, ...alt.screenshots].some(text => Array.from(text).length > 140)) throw Error('Alt text exceeds 140 characters');
  await writeFile(path.join(folder, 'alt-text.json'), JSON.stringify(alt, null, 2) + '\n', 'utf8');
  report.push({locale: item.locale, ...counts});
  data.push({...item, ui, alt, full});
  await writeFile(path.join(folder, 'index.html'), page
    .replace('<body>', `<body data-locale="${item.locale}">`)
    .replaceAll('href="gallery.css"', 'href="../../gallery.css"')
    .replaceAll('src="data.js"', 'src="../../data.js"')
    .replaceAll('src="gallery.js"', 'src="../../gallery.js"'), 'utf8');
}
await writeFile(path.join(root, 'data.js'), 'window.storeLocales = ' + JSON.stringify(data, null, 2) + ';\n', 'utf8');
await writeFile(path.join(root, 'copy-validation.json'), JSON.stringify(report, null, 2) + '\n', 'utf8');
console.log(`Prepared ${data.length} localized listings; all text limits and alt text passed.`);
