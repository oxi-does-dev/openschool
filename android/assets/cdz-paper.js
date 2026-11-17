(function () {
  var VERSION = 4;

  var BASE_ID = 'sch-paper-theme';   // шрифты, фон, ссылки, контролы, скроллбар
  var SITE_ID = 'sch-paper-site';    // перекрашенные правила самого ЦДЗ
  var DYN_ID = 'sch-paper-dyn';      // правила для inline-стилей

  // Тёмная палитра совпадает с applyPalette() приложения, чтобы страница
  // и рамка окна выглядели одним целым.
  var LIGHT = {
    bg: '#F5EFE2', surface: '#FFFCF4', surface2: '#EDE5D3',
    ink: '#2B2A26', muted: '#7A7365', accent: '#34577D',
    accentBg: '#34577D', onAccent: '#FFFCF4', soft: '#EDE5D3',
    line: '#D3C7AE'
  };
  var DARK = {
    bg: '#171613', surface: '#1E1C18', surface2: '#2A2721',
    ink: '#EAE3D5', muted: '#A79D8B', accent: '#8FB0D4',
    accentBg: '#34577D', onAccent: '#F3EDE1', soft: '#26333F',
    line: '#3E392F'
  };

  var isDarkOn = !!window.__schDark;

  if (window.__schPaper && window.__schPaper.version === VERSION) {
    if (window.__schDark !== undefined) window.__schPaper.set(!!window.__schDark);
    return;
  }

  // --- цветовые утилиты -----------------------------------------------------

  var NAMES = {
    transparent: 'rgba(0,0,0,0)', white: '#ffffff', black: '#000000',
    gray: '#808080', grey: '#808080', silver: '#c0c0c0', gainsboro: '#dcdcdc',
    whitesmoke: '#f5f5f5', snow: '#fffafa', ivory: '#fffff0',
    red: '#ff0000', green: '#008000', blue: '#0000ff', navy: '#000080',
    teal: '#008080', olive: '#808000', maroon: '#800000', purple: '#800080',
    orange: '#ffa500', yellow: '#ffff00', pink: '#ffc0cb', lime: '#00ff00',
    aqua: '#00ffff', cyan: '#00ffff', magenta: '#ff00ff', fuchsia: '#ff00ff'
  };

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

  function parseColor(str) {
    if (!str) return null;
    str = String(str).trim().toLowerCase();
    if (str.indexOf('currentcolor') === 0) return null;
    if (NAMES[str]) str = NAMES[str];
    var hex = str.match(/^#([0-9a-f]{3,8})$/);
    if (hex) {
      var h = hex[1];
      if (h.length === 3 || h.length === 4) {
        h = h.split('').map(function (c) { return c + c; }).join('');
      }
      if (h.length !== 6 && h.length !== 8) return null;
      return hslOf(
        parseInt(h.substr(0, 2), 16) / 255,
        parseInt(h.substr(2, 2), 16) / 255,
        parseInt(h.substr(4, 2), 16) / 255,
        h.length === 8 ? parseInt(h.substr(6, 2), 16) / 255 : 1);
    }
    var m = str.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    var p = m[1].split(',').map(function (x) { return parseFloat(x); });
    if (p.length < 3) return null;
    return hslOf(p[0] / 255, p[1] / 255, p[2] / 255, p.length > 3 ? p[3] : 1);
  }

  // Цвет внутри shorthand («1px solid #ddd»): без картинок и градиентов.
  function shorthandColor(value) {
    if (!value || /url\(|gradient\(/i.test(value)) return null;
    var toks = String(value).split(/[\s,]+/);
    for (var i = 0; i < toks.length; i++) {
      if (toks[i] && parseColor(toks[i])) return toks[i];
    }
    return null;
  }

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
    return '#' + [r, g, b].map(function (v) {
      var n = Math.round((v + m) * 255);
      n = n < 0 ? 0 : n > 255 ? 255 : n;
      return ('0' + n.toString(16)).slice(-2);
    }).join('');
  }

  function isAccentHue(c) { return c.h >= 195 && c.h <= 335; }

  function isInlineTag(tag) {
    return tag === 'SPAN' || tag === 'A' || tag === 'CODE' || tag === 'MARK' ||
        tag === 'KBD' || tag === 'SAMP' || tag === 'SMALL' || tag === 'LABEL' ||
        tag === 'STRONG' || tag === 'EM' || tag === 'B' || tag === 'I' ||
        tag === 'U' || tag === 'S' || tag === 'CITE' || tag === 'ABBR';
  }

  // --- что во что превращается ----------------------------------------------

  // Полупрозрачные цвета: сохраняем альфу, иначе исчезнут плёнки и тени.
  function withAlpha(color, a) {
    var c = parseColor(color);
    if (!c) return color;
    var hex = hslHex(c.h, c.s * 100, c.l * 100);
    var r = parseInt(hex.substr(1, 2), 16);
    var g = parseInt(hex.substr(3, 2), 16);
    var b = parseInt(hex.substr(5, 2), 16);
    a = Math.max(0, Math.min(1, a));
    return 'rgba(' + r + ', ' + g + ', ' + b + ', ' + Math.round(a * 100) / 100 + ')';
  }

  function mapBgSolid(c, dark, inline) {
    if (!dark) {
      // Цветные плашки в светлой теме не трогаем — они и так родные.
      if (c.s > 0.15) {
        if (c.s > 0.25 && c.l > 0.2 && c.l < 0.8 && isAccentHue(c)) return LIGHT.accentBg;
        if (c.s > 0.12 && c.l > 0.85 && c.l <= 0.97 && isAccentHue(c)) return LIGHT.soft;
        return null;
      }
      if (c.l >= 0.97) return LIGHT.surface;
      if (c.l >= 0.9) return LIGHT.surface2;
      return null;
    }
    if (c.l < 0.14) return null;                        // уже тёмный
    if (c.s > 0.26 && c.l > 0.16 && c.l < 0.74) {
      // Синий остаётся синим, фиолетовый бренд — фиолетовым, остальные
      // цветные плашки просто приглушаются, сохраняя свой оттенок.
      if (isAccentHue(c)) {
        if (c.h <= 250) return DARK.accentBg;
        return hslHex(c.h, 32, 27);
      }
      return hslHex(c.h, 34, 24);
    }
    if (c.s > 0.18 && c.l >= 0.74) return hslHex(c.h, 26, 17);
    if (c.l >= 0.97) return inline ? DARK.surface2 : DARK.surface;
    if (c.l >= 0.9) return DARK.bg;
    if (c.l > 0.62) return DARK.surface2;
    return null;
  }

  // Фон. inline — плашка внутри текста, ей нужен второй уровень поверхности.
  function mapBg(c, dark, inline) {
    if (!c) return null;
    if (c.a < 0.04) return null;
    if (c.a < 0.4) {
      if (!dark) return null;                           // на бумаге плёнки родные
      // Полупрозрачные заливки: синие акценты и белые подсветки должны
      // остаться видимыми на тёмном, тёмные — не мешать.
      var base = mapBgSolid({ h: c.h, s: c.s, l: c.l, a: 1 }, true, inline);
      if (c.s > 0.3 && base) return withAlpha(base, Math.max(c.a * 1.6, 0.16));
      if (c.l > 0.62) return withAlpha('#ffffff', Math.min(0.2, Math.max(0.06, c.a * 1.3)));
      if (c.l < 0.35) return withAlpha('#000000', Math.min(c.a, 0.6));
      return base ? withAlpha(base, Math.max(c.a, 0.1)) : null;
    }
    var solid = mapBgSolid({ h: c.h, s: c.s, l: c.l, a: 1 }, dark, inline);
    if (solid && c.a < 0.95) return withAlpha(solid, Math.max(c.a, 0.35));
    return solid;
  }

  function mapFg(c, dark) {
    if (!c) return null;
    if (!dark) return null;
    if (c.l > 0.66) return null;                        // уже светлый
    var solid;
    if (c.s > 0.3 && c.l < 0.66) solid = hslHex(c.h, 44, 78);
    else if (c.l < 0.34) solid = DARK.ink;
    else solid = DARK.muted;
    return c.a < 0.95 ? withAlpha(solid, Math.max(c.a, 0.55)) : solid;
  }

  function mapBorder(c, dark) {
    if (!c || c.a < 0.15) return null;
    var solid = null;
    if (dark) {
      if (c.l > 0.5) solid = DARK.line;
      else if (c.l > 0.25) solid = '#2C2821';
    } else if (c.l > 0.85) {
      solid = LIGHT.line;
    }
    if (solid && c.a < 0.9) return withAlpha(solid, Math.max(c.a, 0.4));
    return solid;
  }

  function mapInk(c, dark) {
    if (!c || !dark) return null;
    if (c.l < 0.2) return DARK.ink;
    if (c.l < 0.55) return hslHex(c.h, 24, 82);
    return null;
  }

  // --- токены дизайн-системы МЭШ -------------------------------------------
  // ЦДЗ (uchebnik.mos.ru) собран на CSS-переменных --MH-* / --NC-*.
  // Оверрайдим их разом, а не отдельные элементы: тогда любые компоненты,
  // включая те, что подгрузятся позже, сразу получают тёмные значения.

  var TOKENS = {
    '-Background-Light': '#1E1C18',
    '-Border-Light': '#3E392F',
    '-Background-Blue': '#2A4664',
    '-Background-Blue-Light': '#34577D',
    '-Color-Gray': '#A79D8B',
    '-Color-Red': '#E3908C',
    '-Color-Yellow': '#DFB57F',
    '-Background-Dark': '#232331',
    '-Color-Black': '#EAE3D5',
    '-Color-Neutrals': '#2A2721',
    '-Color-Light': '#1E1C18',
    '-Color-Violet': '#9C86E0',
    '-Color-Hat': '#1E1C18',
    '-BorderColor': '#3E392F',
    '-UserRole-Gray': '#3E392F',
    '-UserRole-Blue': '#34577D',
    '-UserBar-Active-Light': '#2A2721',
    '-UserBar-Active-Blue': '#243447',
    '-UserBar-Active-Dark': '#20232C',
    '-UserBar-Active': 'rgba(255,255,255,0.08)',
    '-IconUser': '#A79D8B',
    '-Push': '#E0713A',
    '-MenuItem-Black': '#C9C0B0',
    '-MenuItem-Gray': 'rgba(234,227,213,0.8)',
    '-Notify-Icon-Black': '#A79D8B',
    '-Button-Background-Light': 'rgba(143,176,212,0.18)',
    '-Button-Background-Dark': 'rgba(255,255,255,0.12)',
    '-Button-Background-Hover': '#34577D',
    '-Button-Color': '#8FB0D4',
    '-Mobile-Active-Menu': '#34577D',
    '-Background-Blue-RGBA': 'rgba(143,176,212,0.12)',
    '-Box-Shadow': '0 4px 6px -2px rgba(0,0,0,0.25),0 10px 15px -3px rgba(0,0,0,0.35)'
  };

  var TOKEN_ALIASES = {
    '-Background': '#171613',
    '-Color': '#EAE3D5',
    '-UserRole': '#34577D',
    '-UserBar': '#243447',
    '-UserBar-Active': 'rgba(255,255,255,0.08)',
    '-MenuItem': 'rgba(234,227,213,0.8)',
    '-Notify-Icon': '#A79D8B',
    '-Button-Background': 'rgba(143,176,212,0.18)',
    '-Button-Color': '#8FB0D4',
    '-Button-Background-Hover': '#34577D',
    '-Color-School': '#9C86E0'
  };

  function tokenCss() {
    var decls = [];
    var prefixes = ['--MH', '--NC'];
    for (var p = 0; p < prefixes.length; p++) {
      for (var k in TOKENS) {
        if (TOKENS.hasOwnProperty(k)) decls.push(prefixes[p] + k + ':' + TOKENS[k]);
      }
      for (var a in TOKEN_ALIASES) {
        if (TOKEN_ALIASES.hasOwnProperty(a)) decls.push(prefixes[p] + a + ':' + TOKEN_ALIASES[a]);
      }
    }
    return 'html.sch-dark{' + decls.join('!important;') + '!important;}';
  }

  // --- статическая база (обе темы в одном листе, переключение — класс) ------

  var BASE_CSS = [
    'html:not(.sch-dark){color-scheme:light;}',
    'html.sch-dark{color-scheme:dark;}',
    'html:not(.sch-dark) body{background:#F5EFE2 !important;',
    '  background-image:none !important;color:#2B2A26 !important;}',
    'html.sch-dark body{background:#171613 !important;',
    '  background-image:none !important;color:#EAE3D5 !important;}',
    // Светлая тема — книжная, с засечками. Тёмная — чистая типографика
    // без засечек: на тёмном фоне они выглядят грязно.
    'html:not(.sch-dark) body{font-family:"PT Serif",Georgia,"Times New Roman",serif !important;}',
    'html:not(.sch-dark) h1,html:not(.sch-dark) h2,html:not(.sch-dark) h3,',
    'html:not(.sch-dark) h4,html:not(.sch-dark) h5,html:not(.sch-dark) h6{',
    '  font-family:"PT Serif",Georgia,serif !important;}',
    'html.sch-dark body,html.sch-dark h1,html.sch-dark h2,html.sch-dark h3,',
    'html.sch-dark h4,html.sch-dark h5,html.sch-dark h6{',
    '  font-family:"Golos Text",Roboto,"Helvetica Neue",Arial,sans-serif !important;',
    '  -webkit-font-smoothing:antialiased;}',
    'button,input,select,textarea{font-family:"Golos Text",Roboto,Arial,sans-serif !important;',
    '  border-radius:3px !important;}',
    'button,[role="button"]{border-radius:3px !important;}',
    'html:not(.sch-dark) a{color:#34577D !important;}',
    'html.sch-dark a{color:#8FB0D4 !important;}',
    'html:not(.sch-dark) ::selection{background:#D9CEB8;color:#2B2A26;}',
    'html.sch-dark ::selection{background:#34577D;color:#F3EDE1;}',
    'html.sch-dark input,html.sch-dark textarea,html.sch-dark select{',
    '  background:#1E1C18 !important;color:#EAE3D5 !important;',
    '  border-color:#3E392F !important;}',
    'html.sch-dark input::placeholder,html.sch-dark textarea::placeholder{',
    '  color:#7D7466 !important;}',
    'html.sch-dark ::-webkit-scrollbar{width:10px;height:10px;background:#171613;}',
    'html.sch-dark ::-webkit-scrollbar-thumb{background:#3E392F;border-radius:6px;}',
    'html.sch-dark ::-webkit-scrollbar-thumb:hover{background:#4A4438;}',
    'html.sch-dark ::-webkit-scrollbar-corner{background:#171613;}',
    // монохромные SVG-иконки: заливка чёрным по атрибуту
    'html.sch-dark svg [fill="#000"],html.sch-dark svg [fill="#000000"],',
    'html.sch-dark svg [fill="#212529"],html.sch-dark svg [fill="black"]{',
    '  fill:currentColor !important;}',
    'html.sch-dark svg [stroke="#000"],html.sch-dark svg [stroke="#000000"],',
    'html.sch-dark svg [stroke="#212529"],html.sch-dark svg [stroke="black"]{',
    '  stroke:currentColor !important;}'
  ].join('') + tokenCss();

  function addBase() {
    var doc = document;
    if (!doc.documentElement) return;
    var st = doc.getElementById(BASE_ID);
    if (!st) {
      st = doc.createElement('style');
      st.id = BASE_ID;
      (doc.head || doc.documentElement).appendChild(st);
    }
    if (st.textContent !== BASE_CSS) st.textContent = BASE_CSS;
  }

  // --- перекраска CSS самого ЦДЗ -------------------------------------------

  // Из правила сайта делаем своё: тот же селектор + наша тема и !important.
  // Стили страницы при этом не трогаются, React ничего не «возвращает»,
  // поэтому ничего не мигает; переключение темы — один класс на <html>.
  function rootSelector(sel) {
    return /(^|[\s,>+~])(html|body)\b/i.test(sel);
  }

  function scopeSelectors(sel, dark) {
    var root = dark ? 'html.sch-dark' : 'html:not(.sch-dark)';
    var parts = String(sel).split(',');
    var out = [];
    for (var i = 0; i < parts.length; i++) {
      var one = parts[i].trim();
      if (!one) continue;
      if (one.indexOf(':root') === 0) out.push(one.replace(':root', root));
      else if (/^html\b/i.test(one)) out.push(one.replace(/^html/i, root));
      else out.push(root + ' ' + one);
    }
    return out.join(',');
  }

  // По имени переменной понять, фон это или текст.
  function customKind(name) {
    var n = name.toLowerCase();
    if (/(text|color|fg|ink|font|label|link|icon)/.test(n)) return 'fg';
    if (/(bg|background|surface|card|paper|panel|fill|border|line|stroke|shadow)/.test(n)) return 'bg';
    return null;
  }

  function ruleDecls(style, sel) {
    var light = [], dark = [];
    var push = function (prop, lv, dv) {
      if (lv) light.push(prop + ':' + lv + ' !important');
      if (dv) dark.push(prop + ':' + dv + ' !important');
    };

    if (!rootSelector(sel)) {
      var bgRaw = style.getPropertyValue('background-color') ||
          shorthandColor(style.getPropertyValue('background'));
      var bg = parseColor(bgRaw);
      if (bg) push('background-color', mapBg(bg, false, false), mapBg(bg, true, false));
    }

    var fg = parseColor(style.getPropertyValue('color'));
    if (fg) push('color', mapFg(fg, false), mapFg(fg, true));

    var borderRaw = style.getPropertyValue('border-color') ||
        style.getPropertyValue('border-top-color') ||
        shorthandColor(style.getPropertyValue('border')) ||
        shorthandColor(style.getPropertyValue('border-top'));
    var border = parseColor(borderRaw);
    if (border) push('border-color', mapBorder(border, false), mapBorder(border, true));

    var fill = parseColor(style.getPropertyValue('fill'));
    if (fill) push('fill', mapInk(fill, false), mapInk(fill, true));

    var stroke = parseColor(style.getPropertyValue('stroke'));
    if (stroke) push('stroke', mapInk(stroke, false), mapInk(stroke, true));

    // CSS-переменные: сайт часто пишет --bg: #fff, а элементы используют
    // var(--bg). Перекрашиваем саму переменную, иначе всё останется белым.
    for (var i = 0; i < style.length; i++) {
      var name = style[i];
      if (name.indexOf('--') !== 0) continue;
      // Токены дизайн-системы МЭШ красим целиком своим блоком (TOKENS):
      // иначе их значения зависят от того, в каком порядке сайт объявил
      // переменные, и половина интерфейса остаётся светлой.
      if (/^--(MH|NC)-/i.test(name)) continue;
      var c = parseColor(style.getPropertyValue(name));
      if (!c) continue;
      var kind = customKind(name);
      if (!kind) kind = c.l > 0.7 ? 'bg' : 'fg';
      var v = kind === 'bg' ? mapBg(c, true, false) : mapFg(c, true);
      if (v) dark.push(name + ':' + v + ' !important');
    }

    return (light.length || dark.length) ? { light: light, dark: dark } : null;
  }

  var scanned = [];        // [{sheet, count, index}]
  var siteEntries = [];    // [{wrappers, sel, light[], dark[]}]

  function readGroup(rules, wrappers, out) {
    if (!rules) return;
    for (var i = 0; i < rules.length; i++) {
      var rule = rules[i];
      try {
        if (rule.type === 1) {                       // CSSStyleRule
          var d = ruleDecls(rule.style, rule.selectorText || '');
          if (d) {
            out.push({
              wrappers: wrappers, sel: rule.selectorText,
              light: d.light, dark: d.dark
            });
          }
        } else if (rule.type === 4) {                // @media
          readGroup(rule.cssRules, wrappers.concat('@media ' + rule.conditionText), out);
        } else if (rule.type === 12) {               // @supports
          readGroup(rule.cssRules, wrappers.concat('@supports ' + rule.conditionText), out);
        } else if (rule.type === 3 && rule.styleSheet) {  // @import
          readGroup(rule.styleSheet.cssRules, wrappers, out);
        }
      } catch (e) { }
    }
  }

  function siteCss() {
    var css = [];
    for (var i = 0; i < siteEntries.length; i++) {
      var batch = siteEntries[i];
      for (var j = 0; j < batch.length; j++) {
        var e = batch[j];
        var parts = [];
        if (e.light.length) {
          parts.push(scopeSelectors(e.sel, false) + '{' + e.light.join(';') + '}');
        }
        if (e.dark.length) {
          parts.push(scopeSelectors(e.sel, true) + '{' + e.dark.join(';') + '}');
        }
        if (!parts.length) continue;
        var open = '', close = '';
        for (var k = 0; k < e.wrappers.length; k++) {
          open += e.wrappers[k] + '{';
          close += '}';
        }
        css.push(open + parts.join('') + close);
      }
    }
    return css.join('');
  }

  function rebuildSite() {
    var doc = document;
    if (!doc.documentElement) return;
    var st = doc.getElementById(SITE_ID);
    if (!st) {
      st = doc.createElement('style');
      st.id = SITE_ID;
      (doc.head || doc.documentElement).appendChild(st);
    }
    var css = siteCss();
    if (st.textContent !== css) st.textContent = css;
  }

  // Пересобираем только таблицы, которые появились или выросли: CSS-in-JS
  // (styled-components и подобное) дописывает правила в уже существующий <style>.
  function ours(sheet) {
    var node = sheet && sheet.ownerNode;
    var id = node && node.id;
    return id === BASE_ID || id === SITE_ID || id === DYN_ID;
  }

  function scanSheets(force) {
    var list = document.styleSheets || [];
    var fresh = false;
    for (var i = 0; i < list.length; i++) {
      var sheet = list[i];
      if (!sheet || ours(sheet)) continue;
      var count;
      try {
        if (!sheet.cssRules) continue;
        count = sheet.cssRules.length;
      } catch (e) { continue; }
      var known = null;
      for (var j = 0; j < scanned.length; j++) {
        if (scanned[j].sheet === sheet) { known = scanned[j]; break; }
      }
      if (known && known.count === count) continue;
      var out = [];
      try { readGroup(sheet.cssRules, [], out); } catch (e) { }
      if (known) {
        siteEntries[known.index] = out;
        known.count = count;
      } else {
        siteEntries.push(out);
        scanned.push({ sheet: sheet, count: count, index: siteEntries.length - 1 });
      }
      fresh = true;
    }
    if (fresh || force) rebuildSite();
  }

  // --- inline-стили: свои правила по data-атрибуту --------------------------
  // Атрибут React не перезаписывает (в отличие от style), значение меняется
  // только когда меняется сам цвет, — мигать нечему.

  var dynSheet = null;
  var dynCache = {};
  var dynSeq = 0;

  function dynStyle() {
    if (dynSheet && dynSheet.ownerNode && dynSheet.ownerNode.isConnected) return dynSheet;
    var st = document.getElementById(DYN_ID);
    if (!st) {
      st = document.createElement('style');
      st.id = DYN_ID;
      (document.head || document.documentElement).appendChild(st);
    }
    dynSheet = st.sheet;
    return dynSheet;
  }

  function dynId(attr, css) {
    var key = attr + '|' + css;
    if (dynCache[key]) return dynCache[key];
    var id = 'k' + (++dynSeq).toString(36);
    var root = attr === 'data-sch-d' ? 'html.sch-dark ' : 'html:not(.sch-dark) ';
    try {
      var sheet = dynStyle();
      sheet.insertRule(root + '[' + attr + '="' + id + '"]{' + css + '}',
          sheet.cssRules.length);
    } catch (e) { }
    dynCache[key] = id;
    return id;
  }

  function applyDecls(el, attr, decls) {
    var css = decls.join(';');
    if (!css) {
      if (el.hasAttribute(attr)) el.removeAttribute(attr);
      return;
    }
    var id = dynId(attr, css);
    if (el.getAttribute(attr) !== id) el.setAttribute(attr, id);
  }

  function colorProps(style) {
    for (var i = 0; i < style.length; i++) {
      var p = style[i];
      if (p === 'color' || p === 'background' || p === 'background-color' ||
          p === 'fill' || p === 'stroke' || p.indexOf('border') === 0) return true;
    }
    return false;
  }

  function evalInline(el) {
    if (!el || el.nodeType !== 1 || !el.isConnected) return;
    var st = el.style;
    if (!st || !st.length || !colorProps(st)) return;

    var tag = el.tagName.toUpperCase();
    var light = [], dark = [];

    var bg = parseColor(st.getPropertyValue('background-color') ||
        shorthandColor(st.getPropertyValue('background')));
    if (bg) {
      var lb = mapBg(bg, false, isInlineTag(tag));
      var db = mapBg(bg, true, isInlineTag(tag));
      if (lb) light.push('background-color:' + lb + ' !important');
      if (db) dark.push('background-color:' + db + ' !important');
    }

    var fg = parseColor(st.getPropertyValue('color'));
    if (fg) {
      var df = mapFg(fg, true);
      if (df) dark.push('color:' + df + ' !important');
    }

    var border = parseColor(st.getPropertyValue('border-color') ||
        shorthandColor(st.getPropertyValue('border')) ||
        shorthandColor(st.getPropertyValue('border-top')));
    if (border) {
      var lbd = mapBorder(border, false), dbd = mapBorder(border, true);
      if (lbd) light.push('border-color:' + lbd + ' !important');
      if (dbd) dark.push('border-color:' + dbd + ' !important');
    }

    var fill = parseColor(st.getPropertyValue('fill'));
    if (fill) {
      var dfl = mapInk(fill, true);
      if (dfl) dark.push('fill:' + dfl + ' !important');
    }
    var stroke = parseColor(st.getPropertyValue('stroke'));
    if (stroke) {
      var dst = mapInk(stroke, true);
      if (dst) dark.push('stroke:' + dst + ' !important');
    }

    applyDecls(el, 'data-sch-l', light);
    applyDecls(el, 'data-sch-d', dark);
  }

  var queue = [];
  var queued = new Set();
  var queueTimer = null;

  function queueEl(el) {
    if (!el || el.nodeType !== 1 || !el.isConnected) return;
    if (!el.style || !el.style.length || !colorProps(el.style)) return;
    if (queued.has(el)) return;
    queued.add(el);
    queue.push(el);
  }

  function queueTree(root) {
    if (!root || root.nodeType !== 1) return;
    queueEl(root);
    var list;
    try { list = root.querySelectorAll('[style]'); } catch (e) { return; }
    for (var i = 0; i < list.length; i++) queueEl(list[i]);
  }

  function flush() {
    queueTimer = null;
    var items = queue;
    queue = [];
    queued = new Set();
    for (var i = 0; i < items.length; i++) {
      try { evalInline(items[i]); } catch (e) { }
    }
  }

  function schedule() {
    if (queueTimer) return;
    queueTimer = setTimeout(flush, 80);
  }

  function queueAll() {
    var list;
    try { list = document.querySelectorAll('[style]'); } catch (e) { return; }
    for (var i = 0; i < list.length; i++) queueEl(list[i]);
    flush();
  }

  // --- тема ----------------------------------------------------------------

  function applyClass(dark) {
    var de = document.documentElement;
    if (!de) return;
    if (dark) de.classList.add('sch-dark');
    else de.classList.remove('sch-dark');
  }

  function set(dark) {
    dark = !!dark;
    var de = document.documentElement;
    var has = !!(de && de.classList.contains('sch-dark'));
    if (dark === isDarkOn && has === dark &&
        document.getElementById(SITE_ID) && document.getElementById(BASE_ID)) {
      return;
    }
    isDarkOn = dark;
    window.__schDarkOn = dark;
    addBase();
    scanSheets(false);
    applyClass(dark);
    queueAll();
  }

  // --- наблюдатель ---------------------------------------------------------

  var rescanTimer = null;

  function scheduleRescan() {
    if (rescanTimer) return;
    rescanTimer = setTimeout(function () {
      rescanTimer = null;
      scanSheets(false);
      queueAll();
    }, 120);
  }

  function boot() {
    addBase();
    scanSheets(true);
    applyClass(isDarkOn);
    queueAll();
    dynStyle();
    try {
      var mo = new MutationObserver(function (muts) {
        var needScan = false;
        for (var i = 0; i < muts.length; i++) {
          var m = muts[i];
          if (m.type === 'attributes') {
            queueEl(m.target);
            continue;
          }
          if (m.type !== 'childList') continue;
          for (var j = 0; j < m.addedNodes.length; j++) {
            var n = m.addedNodes[j];
            if (!n || n.nodeType !== 1) continue;
            var t = n.tagName.toUpperCase();
            if (t === 'STYLE' || t === 'LINK') needScan = true;
            if (n.style && n.style.length) queueEl(n);
            if (n.querySelector && n.querySelector('[style]')) queueTree(n);
            if (t === 'STYLE' && n.textContent && n.textContent.length) needScan = true;
          }
        }
        if (needScan) scheduleRescan();
        if (queue.length) schedule();
      });
      mo.observe(document.documentElement, {
        childList: true, subtree: true,
        attributes: true, attributeFilter: ['style']
      });
    } catch (e) { }
    // Страховка: сайт иногда меняет <head> целиком или дописывает правила
    // в уже существующий <style> (CSS-in-JS).
    setTimeout(function () { scanSheets(false); queueAll(); }, 600);
    setInterval(function () {
      if (!document.getElementById(BASE_ID)) addBase();
      if (!document.getElementById(SITE_ID)) rebuildSite();
      scanSheets(false);
    }, 2500);
  }

  window.__schPaper = {
    version: VERSION,
    set: set,
    isDark: function () { return isDarkOn; },
    rescan: function () { scanSheets(true); queueAll(); }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
