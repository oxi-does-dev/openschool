(function () {
  var VERSION = 3;
  var BASE_ID = 'sch-paper-theme';
  var THEME_ID = 'sch-paper-colors';

  // Палитра тёмной темы повторяет applyPalette() из MainActivity, чтобы
  // страница ЦДЗ и рамка приложения выглядели одним целым.
  var P_LIGHT = {
    bg: '#F5EFE2', surface: '#FFFCF4', surface2: '#EDE5D3',
    ink: '#2B2A26', muted: '#7A7365', accent: '#34577D',
    accentBg: '#34577D', onAccent: '#FFFCF4', soft: '#EDE5D3',
    line: '#D3C7AE'
  };
  var P_DARK = {
    bg: '#171613', surface: '#201E1A', surface2: '#2A2721',
    ink: '#EAE3D5', muted: '#A79D8B', accent: '#8FB0D4',
    accentBg: '#34577D', onAccent: '#F6F0E4', soft: '#26333F',
    line: '#3E392F'
  };

  var isDarkOn = false;
  var P = P_LIGHT;

  // Повторная инъекция на той же странице: просто переключаем тему.
  if (window.__schPaper && window.__schPaper.version === VERSION) {
    if (window.__schDark !== undefined) window.__schPaper.set(!!window.__schDark);
    return;
  }

  // Что мы сами записали в inline-стили: нужно, чтобы вернуть исходное
  // состояние при смене темы или когда React меняет класс задания.
  var orig = new WeakMap();

  function hslHex(h, s, l) {
    s = Math.max(0, Math.min(100, s)) / 100;
    l = Math.max(0, Math.min(100, l)) / 100;
    var c = (1 - Math.abs(2 * l - 1)) * s;
    var hp = (((h % 360) + 360) % 360) / 60;
    var x = c * (1 - Math.abs((hp % 2) - 1));
    var r = 0, g = 0, b = 0;
    if (hp < 1) { r = c; g = x; }
    else if (hp < 2) { r = x; g = c; }
    else if (hp < 3) { g = c; b = x; }
    else if (hp < 4) { g = x; b = c; }
    else if (hp < 5) { r = x; b = c; }
    else { r = c; b = x; }
    var m = l - c / 2;
    var out = '#' + [r, g, b].map(function (v) {
      var n = Math.round((v + m) * 255);
      n = n < 0 ? 0 : n > 255 ? 255 : n;
      return ('0' + n.toString(16)).slice(-2);
    }).join('');
    return out;
  }

  function parseColor(str) {
    if (!str) return null;
    str = String(str).trim();
    var hex = str.match(/^#([0-9a-f]{3,8})$/i);
    if (hex) {
      var h = hex[1];
      if (h.length === 3 || h.length === 4) {
        h = h.split('').map(function (ch) { return ch + ch; }).join('');
      }
      if (h.length !== 6 && h.length !== 8) return null;
      var r = parseInt(h.substr(0, 2), 16) / 255;
      var g = parseInt(h.substr(2, 2), 16) / 255;
      var b = parseInt(h.substr(4, 2), 16) / 255;
      var a = h.length === 8 ? parseInt(h.substr(6, 2), 16) / 255 : 1;
      return hslOf(r, g, b, a);
    }
    var m = str.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    var p = m[1].split(',').map(function (x) { return parseFloat(x); });
    if (p.length < 3) return null;
    var alpha = p.length > 3 ? p[3] : 1;
    return hslOf(p[0] / 255, p[1] / 255, p[2] / 255, alpha);
  }

  function hslOf(r, g, b, a) {
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

  // --- базовый слой: шрифты и форма, не зависит от темы --------------------

  var BASE_CSS = [
    'body{font-family:"PT Serif",Georgia,"Times New Roman",serif !important;}',
    'h1,h2,h3,h4,h5,h6{font-family:"PT Serif",Georgia,serif !important;}',
    'button,input,select,textarea{',
    '  font-family:"Golos Text",Arial,sans-serif !important;',
    '  border-radius:3px !important;box-shadow:none !important;}',
    'button,[role="button"]{border-radius:3px !important;box-shadow:none !important;}'
  ].join('\n');

  function themeCss(dk) {
    if (!dk) {
      return [
        'html,body{background:#F5EFE2 !important;background-image:none !important;}',
        'body{color:#2B2A26 !important;}',
        'a{color:#34577D !important;}',
        '::selection{background:#D9CEB8;color:#2B2A26;}'
      ].join('\n');
    }
    return [
      'html{color-scheme:dark;}',
      'html,body{background:#171613 !important;background-image:none !important;}',
      'body{color:#EAE3D5 !important;}',
      'a{color:#8FB0D4 !important;}',
      '::selection{background:#34577D;color:#F6F0E4;}',
      'input,textarea,select{background:#201E1A !important;color:#EAE3D5 !important;',
      '  border-color:#3E392F !important;}',
      'input::placeholder,textarea::placeholder{color:#7D7466 !important;}',
      '::-webkit-scrollbar{width:10px;height:10px;background:#171613;}',
      '::-webkit-scrollbar-thumb{background:#3E392F;border-radius:6px;}',
      '::-webkit-scrollbar-thumb:hover{background:#4A4438;}',
      '::-webkit-scrollbar-corner{background:#171613;}'
    ].join('\n');
  }

  function ensureStyle(doc, id, css) {
    var st = doc.getElementById(id);
    if (!st) {
      st = doc.createElement('style');
      st.id = id;
      (doc.head || doc.documentElement).appendChild(st);
    }
    if (st.textContent !== css) st.textContent = css;
    return st;
  }

  function addStyles(doc) {
    if (!doc || !doc.documentElement) return;
    ensureStyle(doc, BASE_ID, BASE_CSS);
    ensureStyle(doc, THEME_ID, themeCss(isDarkOn));
  }

  // --- правка inline-стилей -------------------------------------------------

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

  // Ближайший непрозрачный фон над элементом. l === null — под элементом
  // картинка, такой текст не перекрашиваем, иначе он потеряется на фото.
  function backdrop(el) {
    var n = el, depth = 0;
    while (n && n.nodeType === 1 && depth < 12) {
      var st;
      try { st = getComputedStyle(n); } catch (e) { return { l: null, img: true }; }
      var bgImg = st.backgroundImage;
      if (bgImg && bgImg !== 'none' && bgImg.indexOf('gradient') < 0) {
        return { l: null, img: true };
      }
      var c = parseColor(st.backgroundColor);
      if (c && c.a > 0.5) return { l: c.l, img: false };
      n = n.parentElement;
      depth++;
    }
    return { l: 1, img: false };
  }

  function isBlue(c) {
    return c.h >= 195 && c.h <= 300;
  }

  function icon(el) {
    if (!isDarkOn) return;
    var r;
    try { r = el.getBoundingClientRect(); } catch (e) { return; }
    // Крупные SVG — иллюстрации, их не трогаем.
    if (r.width > 56 || r.height > 56) return;
    var nodes = el.querySelectorAll('path,circle,rect,polygon,line,polyline,ellipse');
    var changed = false;
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i], st;
      try { st = getComputedStyle(n); } catch (e) { continue; }
      var f = parseColor(st.fill || n.getAttribute('fill'));
      if (f && f.a > 0.3 && f.l < 0.55) {
        setProp(n, 'fill', 'currentColor');
        changed = true;
      }
      var s = parseColor(st.stroke || n.getAttribute('stroke'));
      if (s && s.a > 0.3 && s.l < 0.55) {
        setProp(n, 'stroke', 'currentColor');
        changed = true;
      }
    }
    if (changed) {
      var cc = parseColor(getComputedStyle(el).color);
      if (!cc || cc.l < 0.6) setProp(el, 'color', P.ink);
    }
  }

  function paint(el) {
    if (!el || el.nodeType !== 1) return;
    var tag = el.tagName.toUpperCase();
    if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'IMG' ||
        tag === 'VIDEO' || tag === 'CANVAS' || tag === 'PICTURE' ||
        tag === 'IFRAME') return;
    if (tag === 'SVG') { icon(el); return; }

    var cs;
    try { cs = getComputedStyle(el); } catch (e) { return; }
    var r;
    try { r = el.getBoundingClientRect(); } catch (e) { return; }
    var big = r.width > 60 && r.height > 24;

    var gradient = cs.backgroundImage && cs.backgroundImage.indexOf('gradient') >= 0;
    if (gradient) {
      setProp(el, 'background-image', 'none');
      setProp(el, 'background-color', isDarkOn ? P.surface2 : P.accentBg);
      if (r.height > 56) setProp(el, 'color', isDarkOn ? P.ink : P.onAccent);
    }

    var c = gradient ? null : parseColor(cs.backgroundColor);
    if (c && c.a > 0.4) {
      if (!isDarkOn) {
        if (c.s > 0.25 && c.l > 0.2 && c.l < 0.8 && isBlue(c)) {
          setProp(el, 'background-color', P.accentBg);
          setProp(el, 'background-image', 'none');
        } else if (c.s > 0.12 && c.l > 0.85 && c.l <= 0.97 && isBlue(c)) {
          setProp(el, 'background-color', P.soft);
          setProp(el, 'background-image', 'none');
        } else if (c.l > 0.93 && big) {
          setProp(el, 'background-color', P.surface);
          setProp(el, 'border', '1px solid ' + P.line);
        }
      } else {
        if (c.s > 0.28 && c.l > 0.18 && c.l < 0.72) {
          // Цветная плашка: сохраняем оттенок, иначе красное станет зелёным.
          setProp(el, 'background-color',
                  isBlue(c) ? P.accentBg : hslHex(c.h, 34, 24));
        } else if (c.l >= 0.78 && c.s > 0.18) {
          setProp(el, 'background-color',
                  isBlue(c) ? P.soft : hslHex(c.h, 26, 20));
        } else if (c.l > 0.9) {
          setProp(el, 'background-color', big ? P.surface : P.surface2);
        }
      }
    }

    if (big && parseFloat(cs.borderTopLeftRadius) > 6) {
      setProp(el, 'border-radius', '3px');
    }
    if (big && cs.boxShadow && cs.boxShadow !== 'none') {
      setProp(el, 'box-shadow', 'none');
    }

    var bd = backdrop(el);
    var fg = parseColor(cs.color);
    if (fg && !bd.img) {
      if (!isDarkOn) {
        // Светлый фон + белый текст (неактивная кнопка) — читаемый серый.
        if (fg.l > 0.8 && bd.l !== null && bd.l > 0.85) {
          setProp(el, 'color', P.muted);
        }
      } else if (bd.l !== null && bd.l < 0.4) {
        if (fg.s > 0.3 && fg.l < 0.66) {
          setProp(el, 'color', hslHex(fg.h, 58, 74));
        } else if (fg.l < 0.3) {
          setProp(el, 'color', P.ink);
        } else if (fg.l < 0.66) {
          setProp(el, 'color', P.muted);
        }
      }
    }

    if (isDarkOn) {
      var sides = [
        ['border-top-width', 'border-top-color'],
        ['border-right-width', 'border-right-color'],
        ['border-bottom-width', 'border-bottom-color'],
        ['border-left-width', 'border-left-color']
      ];
      for (var i = 0; i < sides.length; i++) {
        if (!(parseFloat(cs.getPropertyValue(sides[i][0])) > 0)) continue;
        var bc = parseColor(cs.getPropertyValue(sides[i][1]));
        if (bc && bc.a > 0.25 && bc.l > 0.55) {
          setProp(el, sides[i][1], P.line);
        }
      }
    }
  }

  function paintTree(el) {
    if (!el || el.nodeType !== 1) return;
    var list;
    try { list = el.querySelectorAll('*'); } catch (e) { list = []; }
    // Сначала снимаем свои прошлые правки, потом красим в порядке документа:
    // иначе restore() потомка затрёт то, что иконка записала в path.
    restore(el);
    for (var i = 0; i < list.length; i++) restore(list[i]);
    paint(el);
    for (var j = 0; j < list.length; j++) paint(list[j]);
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
        var it = items[i];
        if (it && it.isConnected) paintTree(it);
      }
    }, 60);
  }

  function run(doc) {
    if (!doc || !doc.documentElement) return;
    addStyles(doc);
    try {
      var all = doc.querySelectorAll('body *');
      for (var i = 0; i < all.length; i++) restore(all[i]);
      for (var k = 0; k < all.length; k++) paint(all[k]);
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

  function applyDark(dark) {
    dark = !!dark;
    if (dark === isDarkOn && document.getElementById(THEME_ID)) return;
    isDarkOn = dark;
    P = dark ? P_DARK : P_LIGHT;
    window.__schDarkOn = dark;
    // Полный проход с restore(): цвета другой темы не должны остаться.
    run(document);
  }

  function boot() {
    P = window.__schDark ? P_DARK : P_LIGHT;
    applyDark(!!window.__schDark);
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
      addStyles(document);
      if (!document.getElementById(THEME_ID)) applyDark(isDarkOn);
    }, 2000);
  }

  window.__schPaper = {
    version: VERSION,
    set: applyDark,
    isDark: function () { return isDarkOn; }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
