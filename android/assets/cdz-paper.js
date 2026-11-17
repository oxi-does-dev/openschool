(function () {
  var VERSION = 2;
  var STYLE_ID = 'sch-paper-theme';
  var DARK_ID = 'sch-paper-dark';

  // Повторная инъекция на той же странице: просто переключаем тему.
  if (window.__schPaper && window.__schPaper.version === VERSION) {
    window.__schPaper.set(!!window.__schDark);
    return;
  }

  // Тёмная тема — инверсия готовой «бумаги»: так перекрашивается любой
  // чужой интерфейс ЦДЗ, включая то, что React рисует уже после нас.
  // Картинки и видео отменяют инверсию, чтобы сканы оставались нормальными.
  var DARK_CSS = [
    'html{filter:invert(1) hue-rotate(180deg) !important;',
    '  background:#F5EFE2 !important;}',
    'html img,html video,html canvas,html picture{',
    '  filter:invert(1) hue-rotate(180deg) !important;}',
    'html ::selection{background:#3E4A5C !important;color:#E9EDF3 !important;}'
  ].join('\n');

  function applyDark(dark) {
    var doc = document;
    var st = doc.getElementById(DARK_ID);
    if (dark) {
      if (!st) {
        st = doc.createElement('style');
        st.id = DARK_ID;
        (doc.head || doc.documentElement).appendChild(st);
      }
      if (st.textContent !== DARK_CSS) st.textContent = DARK_CSS;
    } else if (st && st.parentNode) {
      st.parentNode.removeChild(st);
    }
    window.__schDarkOn = !!dark;
  }

  var CSS = [
    ':root{',
    '  --sch-bg:#F5EFE2; --sch-surface:#FFFCF4; --sch-ink:#2B2A26;',
    '  --sch-muted:#7A7365; --sch-accent:#34577D; --sch-line:#D3C7AE;',
    '}',
    'html,body{background:#F5EFE2 !important;background-image:none !important;}',
    'body{color:#2B2A26 !important;',
    '  font-family:"PT Serif",Georgia,"Times New Roman",serif !important;}',
    'h1,h2,h3,h4,h5,h6{font-family:"PT Serif",Georgia,serif !important;}',
    'button,input,select,textarea{',
    '  font-family:"Golos Text",Arial,sans-serif !important;',
    '  border-radius:3px !important;box-shadow:none !important;}',
    'a{color:#34577D !important;}',
    'button,[role="button"]{border-radius:3px !important;box-shadow:none !important;}',
    '::selection{background:#D9CEB8;color:#2B2A26;}'
  ].join('\n');

  // Что мы сами записали в inline-стили: нужно, чтобы вернуть исходное
  // состояние, когда React меняет класс (например, выбранное задание).
  var orig = new WeakMap();

  function addStyle(doc) {
    if (!doc || !doc.documentElement) return;
    if (doc.getElementById(STYLE_ID)) return;
    var st = doc.createElement('style');
    st.id = STYLE_ID;
    st.textContent = CSS;
    (doc.head || doc.documentElement).appendChild(st);
  }

  function parseColor(str) {
    if (!str) return null;
    var m = str.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    var p = m[1].split(',').map(function (x) { return parseFloat(x); });
    if (p.length < 3) return null;
    var a = p.length > 3 ? p[3] : 1;
    var r = p[0] / 255, g = p[1] / 255, b = p[2] / 255;
    var max = Math.max(r, g, b), min = Math.min(r, g, b);
    var l = (max + min) / 2;
    var h = 0, s = 0;
    if (max !== min) {
      var d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      if (max === r) h = (g - b) / d + (g < b ? 6 : 0);
      else if (max === g) h = (b - r) / d + 2;
      else h = (r - g) / d + 4;
      h *= 60;
    }
    return { h: h, s: s, l: l, a: a };
  }

  // Светлый ли фактический фон под элементом (с учётом прозрачности).
  function bgIsLight(el) {
    var n = el, depth = 0;
    while (n && n.nodeType === 1 && depth < 10) {
      var st;
      try { st = getComputedStyle(n); } catch (e) { return false; }
      if (st.backgroundImage && st.backgroundImage.indexOf('gradient') >= 0) {
        return false;
      }
      var c = parseColor(st.backgroundColor);
      if (c && c.a > 0.5) return c.l > 0.85;
      n = n.parentElement;
      depth++;
    }
    return true;
  }

  function setProp(el, prop, value) {
    var rec = orig.get(el);
    if (!rec) {
      rec = {};
      orig.set(el, rec);
    }
    if (!rec[prop]) {
      rec[prop] = {
        v: el.style.getPropertyValue(prop),
        p: el.style.getPropertyPriority(prop)
      };
    }
    el.style.setProperty(prop, value, 'important');
    rec[prop].ours = el.style.getPropertyValue(prop);
  }

  function restore(el) {
    var rec = orig.get(el);
    if (!rec) return;
    orig.delete(el);
    for (var prop in rec) {
      var r = rec[prop];
      var cur = el.style.getPropertyValue(prop);
      // Пока мы держали значение, React мог записать своё — его не трогаем.
      if (r.ours !== undefined && cur !== r.ours) continue;
      if (r.v) el.style.setProperty(prop, r.v, r.p);
      else el.style.removeProperty(prop);
    }
  }

  // React поменял inline-стиль мимо нас?
  function styleChanged(el) {
    var rec = orig.get(el);
    if (!rec) return false;
    for (var prop in rec) {
      var r = rec[prop];
      if (r.ours === undefined) continue;
      if (el.style.getPropertyValue(prop) !== r.ours) return true;
    }
    return false;
  }

  function paper(el) {
    if (!el || el.nodeType !== 1) return;
    var tag = el.tagName;
    if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'SVG' ||
        tag === 'PATH' || tag === 'IMG' || tag === 'VIDEO' ||
        tag === 'CANVAS') return;

    var cs;
    try { cs = getComputedStyle(el); } catch (e) { return; }
    var r;
    try { r = el.getBoundingClientRect(); } catch (e) { return; }
    var big = r.width > 60 && r.height > 24;

    if (cs.backgroundImage && cs.backgroundImage.indexOf('gradient') >= 0) {
      setProp(el, 'background-image', 'none');
      setProp(el, 'background-color', '#34577D');
      if (r.height > 56) {
        setProp(el, 'color', '#FFFCF4');
      }
    }

    var c = parseColor(cs.backgroundColor);
    if (c && c.a > 0.4) {
      if (c.s > 0.25 && c.l > 0.2 && c.l < 0.8 && c.h >= 200 && c.h <= 285) {
        setProp(el, 'background-color', '#34577D');
        setProp(el, 'background-image', 'none');
      } else if (c.s > 0.12 && c.l > 0.85 && c.l <= 0.97 && c.h >= 200 && c.h <= 290) {
        setProp(el, 'background-color', '#EDE5D3');
        setProp(el, 'background-image', 'none');
      } else if (c.l > 0.93 && big) {
        setProp(el, 'background-color', '#FFFCF4');
        setProp(el, 'border', '1px solid #D3C7AE');
      }
    }

    if (big && parseFloat(cs.borderTopLeftRadius) > 6) {
      setProp(el, 'border-radius', '3px');
    }
    if (big && cs.boxShadow && cs.boxShadow !== 'none') {
      setProp(el, 'box-shadow', 'none');
    }

    // Светлый фон + белый текст (например, неактивная кнопка) — делаем
    // текст читаемым на бумаге.
    var fg = parseColor(cs.color);
    if (fg && fg.l > 0.8 && bgIsLight(el)) {
      setProp(el, 'color', '#7A7365');
    }
  }

  function paperTree(el) {
    if (!el || el.nodeType !== 1) return;
    restore(el);
    paper(el);
    var list;
    try { list = el.querySelectorAll('*'); } catch (e) { return; }
    for (var i = 0; i < list.length; i++) {
      restore(list[i]);
      paper(list[i]);
    }
  }

  var pending = [];
  var pendingTimer = null;

  function schedule(el) {
    if (pending.indexOf(el) < 0) pending.push(el);
    if (pendingTimer) return;
    pendingTimer = setTimeout(function () {
      pendingTimer = null;
      var items = pending;
      pending = [];
      for (var i = 0; i < items.length; i++) {
        var el = items[i];
        if (el && el.isConnected) paperTree(el);
      }
    }, 60);
  }

  function run(doc) {
    addStyle(doc);
    try {
      var all = doc.querySelectorAll('body *');
      for (var i = 0; i < all.length; i++) paper(all[i]);
    } catch (e) { }
    try {
      var ifr = doc.querySelectorAll('iframe');
      for (var j = 0; j < ifr.length; j++) {
        try { run(ifr[j].contentDocument); } catch (e) { }
      }
    } catch (e) { }
  }

  var runTimer = null;

  function scheduleRun() {
    if (runTimer) return;
    runTimer = setTimeout(function () {
      runTimer = null;
      run(document);
    }, 150);
  }

  function boot() {
    applyDark(!!window.__schDark);
    run(document);
    try {
      var mo = new MutationObserver(function (muts) {
        var structural = false;
        for (var i = 0; i < muts.length; i++) {
          var m = muts[i];
          if (m.type === 'childList') {
            structural = true;
            continue;
          }
          var el = m.target;
          if (!el || el.nodeType !== 1) continue;
          if (m.attributeName === 'style') {
            if (styleChanged(el)) schedule(el);
          } else {
            schedule(el);
          }
        }
        if (structural) scheduleRun();
      });
      mo.observe(document.documentElement, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['class', 'style', 'aria-selected', 'aria-checked',
          'aria-pressed', 'aria-current', 'data-selected']
      });
    } catch (e) { }
    setInterval(function () {
      addStyle(document);
      if (window.__schDarkOn && !document.getElementById(DARK_ID)) {
        applyDark(true);
      }
    }, 2000);
  }

  window.__schPaper = {
    version: VERSION,
    set: applyDark,
    isDark: function () { return !!window.__schDarkOn; }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
