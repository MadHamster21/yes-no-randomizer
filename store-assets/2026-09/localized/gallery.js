const assetRoot = new URL('.', document.querySelector('script[src$="gallery.js"]').src);
const esc = text => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
const url = relative => new URL(relative, assetRoot).href;
const filePath = relative => {
  const target = new URL(relative, assetRoot);
  return target.protocol === 'file:' ? decodeURIComponent(target.pathname).replace(/^\/(?=[A-Za-z]:)/, '').replaceAll('/', '\\') : target.href;
};
const select = document.getElementById('locale');
select.innerHTML = window.storeLocales.map(item => `<option value="${item.locale}">${esc(item.nativeName)} · ${item.locale}</option>`).join('');
document.getElementById('all-bundles').href = url('all-locales.zip');
if (window.storeStandalone) {
  document.getElementById('all-bundles').hidden = true;
  document.getElementById('bundle').hidden = true;
}
const field = (id, label, value, limit, dir) => `<div class="field"><div class="field-heading"><label for="${id}"><strong>${label}</strong></label><span>${Array.from(value).length} / ${limit}</span><button data-copy="${id}">Copy ${label.toLowerCase()}</button></div><textarea id="${id}" dir="${dir}" readonly class="${id === 'full' ? 'full' : ''}" rows="${id === 'full' ? 12 : 2}">${esc(value)}</textarea></div>`;
function show(locale) {
  const item = window.storeLocales.find(row => row.locale === locale) || window.storeLocales[0];
  select.value = item.locale;
  const base = `locales/${item.locale}`;
  const dir = item.dir || 'ltr';
  document.title = `${item.nativeName} — Play Store upload workspace`;
  document.getElementById('locale-page').href = url(`${base}/index.html`);
  document.getElementById('bundle').href = url(`bundles/${item.locale}.zip`);
  const folder = (name, relative) => `<div class="actions"><a href="${url(relative + '/')}" target="_blank" rel="noopener">Open ${name} folder</a><button data-path="${relative}/">Copy folder path</button></div><p class="path">${esc(filePath(relative + '/'))}</p>`;
  const asset = (name, relative, alt, id) => `<article class="asset"><a href="${url(relative)}" target="_blank" rel="noopener"><img src="${url(relative)}" alt="${esc(alt)}"></a><strong>${name}</strong><div class="actions"><a href="${url(relative)}" target="_blank" rel="noopener">Open image</a><a href="${url(relative)}" download>Save image</a><button data-path="${relative}">Copy file path</button></div><label for="${id}">Alt text</label><textarea id="${id}" dir="${dir}" readonly rows="2">${esc(alt)}</textarea><button data-copy="${id}">Copy alt text</button></article>`;
  document.getElementById('listing').innerHTML = `<h2>${esc(item.nativeName)} · ${item.locale}</h2>
    ${folder('language', base)}<p>Paste the copied folder path into the Play Console file picker’s address bar to select several images at once. Folder links open the browser’s local folder view.</p>
    ${field('title', 'App name', item.title, 30, dir)}${field('short', 'Short description', item.short, 80, dir)}${field('full', 'Full description', item.full, 4000, dir)}
    <h2>Feature graphic · 1024 × 500</h2><div class="feature">${asset('Feature graphic', `${base}/feature-graphic.png`, item.alt.feature, 'alt-feature')}</div>
    <h2 id="phone">Phone screenshots · 1080 × 1920</h2>${folder('phone images', `${base}/phone`)}
    <div class="cards">${item.headlines.map((headline, i) => asset(`${i + 1}. ${esc(headline)}`, `${base}/phone/0${i + 1}.png`, item.alt.screenshots[i], `alt-phone-${i}`)).join('')}</div>
    <h2 id="tablet">10-inch tablet screenshots · landscape · 1920 × 1080</h2>${folder('tablet images', `${base}/tablet-10-landscape`)}
    <div class="cards tablet">${item.headlines.map((headline, i) => asset(`${i + 1}. ${esc(headline)}`, `${base}/tablet-10-landscape/0${i + 1}.png`, item.alt.screenshots[i], `alt-tablet-${i}`)).join('')}</div>`;
  document.getElementById('status').textContent = '';
  history.replaceState(null, '', '#' + item.locale);
}
async function copyText(text, source) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const previousFocus = document.activeElement;
    const helper = document.createElement('textarea');
    helper.style.cssText = 'position:fixed;left:-9999px;top:0;opacity:0';
    helper.tabIndex = -1;
    helper.value = text;
    document.body.append(helper);
    helper.select();
    const copied = document.execCommand('copy');
    helper.remove();
    previousFocus?.focus({preventScroll: true});
    if (!copied) {
      source?.focus(); source?.select();
      document.getElementById('status').textContent = 'Select the text and press Ctrl+C to copy.';
      return false;
    }
  }
  document.getElementById('status').textContent = 'Copied to clipboard.';
  return true;
}
document.addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button) return;
  if (button.dataset.copy) { const source = document.getElementById(button.dataset.copy); window.copyComplete = copyText(source.value, source); }
  if (button.dataset.path) window.copyComplete = copyText(filePath(button.dataset.path));
});
select.addEventListener('change', () => show(select.value));
window.addEventListener('hashchange', () => { if (window.storeLocales.some(row => row.locale === location.hash.slice(1))) show(location.hash.slice(1)); });
for (const id of ['phone', 'tablet']) document.getElementById('jump-' + id).addEventListener('click', event => { event.preventDefault(); document.getElementById(id).scrollIntoView({behavior: 'smooth'}); });
window.showLocale = show;
show(location.hash.slice(1) || document.body.dataset.locale || 'en-US');
const params = new URLSearchParams(location.search);
if (params.has('review')) {
  const start = Number(params.get('start') || 0);
  document.querySelector('nav').hidden = true;
  document.getElementById('listing').innerHTML = window.storeLocales.slice(start, start + 6).map(item => {
    const base = `locales/${item.locale}`;
    const tablet = params.has('tablet');
    return `<div class="review-row"><div><h2>${item.locale} · ${esc(item.nativeName)}</h2><img src="${url(`${base}/feature-graphic.png`)}" alt=""></div>${[1, 2, 3, 4].map(i => `<img src="${url(`${base}/${tablet ? 'tablet-10-landscape' : 'phone'}/0${i}.png`)}" alt="">`).join('')}</div>`;
  }).join('');
}
window.galleryReady = async () => {
  await document.fonts.ready;
  await Promise.all([...document.images].map(img => img.decode()));
  return {images: document.images.length, locales: select.options.length};
};
