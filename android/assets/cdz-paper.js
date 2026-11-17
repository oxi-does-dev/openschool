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
    accentBg: '#34577D', onAccent: '#FFFFFF',
    accentSoft: 'rgba(52,87,125,0.10)',
    line: '#D3C7AE', divider: '#D9CEB8', rule: '#C9BDA6',
    current: '#FFF8E6', green: '#2F6B4F', amber: '#9A6A22',
    red: '#B0403A', teal: '#2F6B6B', violet: '#6F4A75',
    errBg: '#F7E7E2', errStroke: '#D9B3A8', mark: '#4E7A3A'
  };
  // Ровно applyPalette() приложения. Никаких «похожих» оттенков.
  var DARK = {
    bg: '#171613', surface: '#201E1A', surface2: '#2A2721',
    ink: '#EAE3D5', muted: '#A79D8B', accent: '#8FB0D4',
    accentBg: '#8FB0D4', onAccent: '#171613',
    accentSoft: 'rgba(143,176,212,0.165)',
    line: '#3E392F', divider: '#35312A', rule: '#4A4438',
    current: '#2C2A20', green: '#85B394', amber: '#D6A960',
    red: '#D9827A', teal: '#7FB8B8', violet: '#B79BC4',
    errBg: '#33211D', errStroke: '#5A3A31', mark: '#A8C98A'
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

  function isAccentHue(c) { return c.h >= 195 && c.h <= 335; }

  function isInlineTag(tag) {
    return tag === 'SPAN' || tag === 'A' || tag === 'CODE' || tag === 'MARK' ||
        tag === 'KBD' || tag === 'SAMP' || tag === 'SMALL' || tag === 'LABEL' ||
        tag === 'STRONG' || tag === 'EM' || tag === 'B' || tag === 'I' ||
        tag === 'U' || tag === 'S' || tag === 'CITE' || tag === 'ABBR';
  }

  // --- что во что превращается ----------------------------------------------

  // Разбираем цвет на точные r/g/b, без пересчёта через HSL: иначе
  // #8FB0D4 после round-trip превращается в «почти такой же».
  function rgbParts(str) {
    if (!str) return null;
    str = String(str).trim().toLowerCase();
    if (NAMES[str]) str = NAMES[str];
    var hex = str.match(/^#([0-9a-f]{3,8})$/);
    if (hex) {
      var h = hex[1];
      if (h.length === 3 || h.length === 4) {
        h = h.split('').map(function (c) { return c + c; }).join('');
      }
      if (h.length !== 6 && h.length !== 8) return null;
      return {
        r: parseInt(h.substr(0, 2), 16),
        g: parseInt(h.substr(2, 2), 16),
        b: parseInt(h.substr(4, 2), 16),
        a: h.length === 8 ? parseInt(h.substr(6, 2), 16) / 255 : 1
      };
    }
    var m = str.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    var p = m[1].split(',').map(function (x) { return parseFloat(x); });
    if (p.length < 3) return null;
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  }

  // Точная палитра приложения: цвет + альфа, как 0xAARRGGBB в applyPalette().
  function withAlpha(color, a) {
    var c = rgbParts(color);
    if (!c) return color;
    a = Math.max(0, Math.min(1, a));
    return 'rgba(' + Math.round(c.r) + ', ' + Math.round(c.g) + ', ' +
        Math.round(c.b) + ', ' + Math.round(a * 1000) / 1000 + ')';
  }

  // Мягкая подложка метки — ровно soft() из приложения: цвет + 0x22.
  function softOf(color) { return withAlpha(color, 0x22 / 255); }

  // Цветовой тон → роль в палитре приложения.
  function roleOf(c) {
    if (!c || c.s < 0.22) return null;
    var h = c.h;
    if (h < 20 || h >= 340) return 'red';
    if (h < 70) return 'amber';
    if (h < 175) return 'green';
    if (h < 200) return 'teal';
    if (h < 250) return 'blue';
    return 'violet';
  }

  function roleColor(c, dark) {
    var role = roleOf(c);
    if (!role) return null;
    var p = dark ? DARK : LIGHT;
    return role === 'blue' ? p.accent : p[role];
  }

  function mapBgSolid(c, dark, inline) {
    var p = dark ? DARK : LIGHT;
    if (c.l < 0.12) return null;                        // уже тёмный
    var role = roleOf(c);

    if (!dark) {
      // Светлая бумага: синие заливки ведём в акцент, остальные не трогаем.
      if (role === 'blue' && c.l > 0.18 && c.l < 0.85) return LIGHT.accentBg;
      if (role) return null;
      if (c.l >= 0.97) return LIGHT.surface;
      if (c.l >= 0.9) return LIGHT.surface2;
      return null;
    }

    if (role) {
      if (role === 'blue') {
        if (c.l < 0.3) return null;                     // тёмно-синие бары
        if (c.l > 0.78) return p.accentSoft;            // бледная голубая заливка
        return p.accent;                                // плотная синяя кнопка
      }
      return softOf(roleColor(c, true));                // цветная метка
    }

    if (c.l >= 0.97) return inline ? p.surface2 : p.surface;
    if (c.l >= 0.88) return p.bg;
    if (c.l > 0.55) return p.surface2;
    return null;
  }

  // Фон. inline — плашка внутри текста, ей нужен второй уровень поверхности.
  function mapBg(c, dark, inline) {
    if (!c) return null;
    if (c.a < 0.04) return null;
    if (c.a < 0.95) {
      if (!dark) {
        if (c.a < 0.4) return null;                     // на бумаге плёнки родные
        var lb = mapBgSolid({ h: c.h, s: c.s, l: c.l, a: 1 }, false, inline);
        return lb ? withAlpha(lb, Math.max(c.a, 0.35)) : null;
      }
      // Полупрозрачные заливки: цветная — мягкая подложка роли (soft()),
      // светлая / тёмная — едва заметная плёнка из палитры.
      var role = roleOf(c);
      if (role === 'blue') return DARK.accentSoft;      // 0x2A8FB0D4
      if (role) return softOf(roleColor(c, true));      // как soft() в приложении
      if (c.l > 0.6) return withAlpha(DARK.ink, 0.06);
      return fromDarkFilm(c);
    }
    return mapBgSolid({ h: c.h, s: c.s, l: c.l, a: 1 }, dark, inline);
  }

  function fromDarkFilm(c) {
    if (c.l < 0.12) return null;                        // тёмная плёнка не нужна
    return withAlpha(DARK.ink, Math.min(0.12, Math.max(0.04, c.l / 4)));
  }

  var COLOR_TOKEN = new RegExp(
      '#[0-9a-fA-F]{3,8}\\b|rgba?\\([^)]*\\)|\\b(?:' +
      Object.keys(NAMES).join('|') + ')\\b', 'gi');

  // Градиенты — половина фирменных плашек МЭШ. Сводим каждый стоп к роли
  // палитры и оставляем полупрозрачной плёнкой: форма градиента сохраняется,
  // но цвет больше не выпадает из темы.
  function tokenTint(name) {
    if (/violet|school|purple/i.test(name)) return withAlpha(DARK.violet, 0.24);
    if (/red|danger|error/i.test(name)) return withAlpha(DARK.red, 0.22);
    if (/green|success/i.test(name)) return withAlpha(DARK.green, 0.22);
    if (/yellow|amber|warning/i.test(name)) return withAlpha(DARK.amber, 0.22);
    if (/blue|userrole|button|mobile|push|avatar/i.test(name)) {
      return withAlpha(DARK.accent, 0.26);
    }
    if (/background|light|neutrals|black|hat|icon/i.test(name)) {
      return withAlpha(DARK.ink, 0.07);
    }
    return null;
  }

  function mapGradient(value, dark) {
    if (!dark || !/gradient\(/i.test(value)) return null;
    value = value.replace(
        /var\(\s*--(?:MH|NC)-([A-Za-z0-9-]+)\s*\)/gi,
        function (m, name) { return tokenTint(name) || m; });
    return value.replace(COLOR_TOKEN, function (tok) {
      var c = parseColor(tok);
      if (!c || c.a < 0.15) return tok;
      var role = roleOf(c);
      if (role) {
        var a = role === 'blue' ? 0.28 : 0.24;
        return withAlpha(roleColor(c, true), a);
      }
      if (c.l > 0.6) return withAlpha(DARK.ink, 0.07);
      return withAlpha(DARK.bg, 0.35);
    });
  }

  function mapFg(c, dark) {
    if (!c || !dark) return null;
    var solid;
    var role = roleOf(c);
    if (role) solid = roleColor(c, true);
    else if (c.l < 0.34 || c.l >= 0.9) solid = DARK.ink;
    else if (c.l < 0.7) solid = DARK.muted;
    else return null;                                   // уже светлый
    return c.a < 0.95 ? withAlpha(solid, Math.max(c.a, 0.55)) : solid;
  }

  function mapBorder(c, dark) {
    if (!c || c.a < 0.15) return null;
    if (!dark) return c.l > 0.85 ? LIGHT.line : null;
    var role = roleOf(c);
    if (role) return withAlpha(roleColor(c, true), 0.55);
    if (c.l < 0.2) return null;
    return DARK.line;
  }

  function mapInk(c, dark) {
    if (!c || !dark) return null;
    var role = roleOf(c);
    if (role) return roleColor(c, true);
    if (c.l < 0.2) return DARK.ink;
    if (c.l < 0.55) return DARK.muted;
    return null;
  }

  // --- токены дизайн-системы МЭШ -------------------------------------------
  // ЦДЗ (uchebnik.mos.ru) собран на CSS-переменных --MH-* / --NC-*.
  // Оверрайдим их разом, а не отдельные элементы: тогда любые компоненты,
  // включая те, что подгрузятся позже, сразу получают тёмные значения.

  var TOKENS = {
    '-Background-Light': '#201E1A',
    '-Border-Light': '#3E392F',
    '-Background-Blue': '#8FB0D4',
    '-Background-Blue-Light': '#8FB0D4',
    '-Color-Gray': '#A79D8B',
    '-Color-Red': '#D9827A',
    '-Color-Yellow': '#D6A960',
    '-Background-Dark': '#201E1A',
    '-Color-Black': '#EAE3D5',
    '-Color-Neutrals': '#2A2721',
    '-Color-Light': '#201E1A',
    '-Color-Violet': '#B79BC4',
    '-Color-Hat': '#201E1A',
    '-BorderColor': '#3E392F',
    '-UserRole-Gray': '#8FB0D4',
    '-UserRole-Blue': '#8FB0D4',
    '-UserBar-Active-Light': '#2A2721',
    '-UserBar-Active-Blue': '#201E1A',
    '-UserBar-Active-Dark': '#171613',
    '-UserBar-Active': 'rgba(143,176,212,0.165)',
    '-IconUser': '#A79D8B',
    '-Push': '#D9827A',
    '-MenuItem-Black': '#EAE3D5',
    '-MenuItem-Gray': '#A79D8B',
    '-Notify-Icon-Black': '#A79D8B',
    '-Button-Background-Light': 'rgba(143,176,212,0.165)',
    '-Button-Background-Dark': 'rgba(143,176,212,0.165)',
    '-Button-Background-Hover': '#8FB0D4',
    '-Button-Color': '#8FB0D4',
    '-Mobile-Active-Menu': '#8FB0D4',
    '-Background-Blue-RGBA': 'rgba(143,176,212,0.165)',
    '-Box-Shadow': '0 4px 6px -2px rgba(0,0,0,0.25),0 10px 15px -3px rgba(0,0,0,0.35)'
  };

  var TOKEN_ALIASES = {
    '-Background': '#171613',
    '-Color': '#EAE3D5',
    '-UserRole': '#8FB0D4',
    '-UserBar': '#201E1A',
    '-UserBar-Active': 'rgba(143,176,212,0.165)',
    '-MenuItem': '#EAE3D5',
    '-Notify-Icon': '#A79D8B',
    '-Button-Background': 'rgba(143,176,212,0.165)',
    '-Button-Color': '#8FB0D4',
    '-Button-Background-Hover': '#8FB0D4',
    '-Color-School': '#B79BC4'
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
    'html.sch-dark ::selection{background:#8FB0D4;color:#171613;}',
    'html.sch-dark input,html.sch-dark textarea,html.sch-dark select{',
    '  background:#201E1A !important;color:#EAE3D5 !important;',
    '  border-color:#3E392F !important;}',
    'html.sch-dark input::placeholder,html.sch-dark textarea::placeholder{',
    '  color:#A79D8B !important;}',
    'html.sch-dark ::-webkit-scrollbar{width:10px;height:10px;background:#171613;}',
    'html.sch-dark ::-webkit-scrollbar-thumb{background:#3E392F;border-radius:6px;}',
    'html.sch-dark ::-webkit-scrollbar-thumb:hover{background:#4A4438;}',
    'html.sch-dark ::-webkit-scrollbar-corner{background:#171613;}',
    // Тёмный логотип МЭШ на тёмной шапке не видно — просто убираем его.
    // Только в шапке: логотипы школ и картинки в заданиях не трогаем.
    'html.sch-dark header img[src*="logo" i],',
    'html.sch-dark header [class*="logo" i],',
    'html.sch-dark [class*="hat" i] img[src*="logo" i],',
    'html.sch-dark [class*="hat" i] [class*="logo" i],',
    'html.sch-dark [class*="userbar" i] img[src*="logo" i],',
    'html.sch-dark [class*="userbar" i] [class*="logo" i]{',
    '  display:none !important;}',
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

    var darkBg = null;
    if (!rootSelector(sel)) {
      var bgRaw = style.getPropertyValue('background-color') ||
          shorthandColor(style.getPropertyValue('background'));
      var bg = parseColor(bgRaw);
      if (bg) {
        darkBg = mapBg(bg, true, false);
        push('background-color', mapBg(bg, false, false), darkBg);
      }
    }

    var rawBg = style.getPropertyValue('background-color') ||
        style.getPropertyValue('background');
    var rawImg = style.getPropertyValue('background-image');
    var grad = mapGradient(rawImg, true);
    if (grad) dark.push('background-image:' + grad + ' !important');
    var fg = parseColor(style.getPropertyValue('color'));
    if (fg) push('color', mapFg(fg, false), mapFg(fg, true));
    // На плотной акцентной заливке текст — ON_ACCENT приложения.
    if (darkBg === DARK.accentBg ||
        /var\(\s*--(?:MH|NC)-(?:UserRole|UserRole-Blue|UserRole-Gray|Background-Blue|Background-Blue-Light|Mobile-Active-Menu)\b/
            .test(rawBg)) {
      dark.push('color:' + DARK.onAccent + ' !important');
    }

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
          p === 'background-image' || p === 'fill' || p === 'stroke' ||
          p.indexOf('border') === 0) return true;
    }
    return false;
  }

  function evalInline(el) {
    if (!el || el.nodeType !== 1 || !el.isConnected) return;
    var st = el.style;
    if (!st || !st.length || !colorProps(st)) return;

    var tag = el.tagName.toUpperCase();
    var light = [], dark = [];

    var dbg = null;
    var bg = parseColor(st.getPropertyValue('background-color') ||
        shorthandColor(st.getPropertyValue('background')));
    if (bg) {
      var lb = mapBg(bg, false, isInlineTag(tag));
      dbg = mapBg(bg, true, isInlineTag(tag));
      if (lb) light.push('background-color:' + lb + ' !important');
      if (dbg) dark.push('background-color:' + dbg + ' !important');
    }

    var bgImg = st.getPropertyValue('background-image');
    var dgrad = mapGradient(bgImg, true);
    if (dgrad) dark.push('background-image:' + dgrad + ' !important');

    var fg = parseColor(st.getPropertyValue('color'));
    if (fg) {
      var df = mapFg(fg, true);
      if (df) dark.push('color:' + df + ' !important');
    }
    if (dbg === DARK.accentBg) dark.push('color:' + DARK.onAccent + ' !important');

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
