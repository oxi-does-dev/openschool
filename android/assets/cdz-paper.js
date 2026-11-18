(function () {
  var VERSION = 5;

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
    // Фирменный фиолетовый почти всегда декоративная рамка вокруг карточки.
    // В палитре он светло-сиреневый, и толстая рамка выглядела грязно.
    if (role === 'violet') return DARK.line;
    if (role === 'blue') return withAlpha(DARK.accent, 0.45);
    if (role) return withAlpha(roleColor(c, true), 0.45);
    if (c.l < 0.2) return null;
    return DARK.line;
  }

  // Тень — тоже цвет, и на тёмном фоне светлая тень выглядит как рамка
  // или ореол вокруг карточки. Перекрашиваем цвета внутри значения.
  function mapShadow(value, dark) {
    if (!dark || !value || value === 'none') return null;
    if (!/rgba?\(|#[0-9a-f]{3,8}\b/i.test(value)) return null;
    var inset = /inset/i.test(value);
    var changed = false;
    var out = value.replace(COLOR_TOKEN, function (tok) {
      var c = parseColor(tok);
      if (!c || c.a < 0.02) return tok;
      var role = roleOf(c);
      var rep;
      if (role && c.a >= 0.5 && c.l < 0.6) {
        // Насыщенная цветная тень — это фокус/подсветка, её оставляем цветной.
        rep = withAlpha(roleColor(c, true), Math.min(0.5, c.a));
      } else if (c.l >= 0.55) {
        // Светлая тень на тёмном фоне = ореол. Убираем в линию или в тень.
        rep = inset ? DARK.line : 'rgba(0,0,0,0.45)';
      } else {
        rep = 'rgba(0,0,0,' + Math.min(0.6, Math.max(0.15, c.a * 2)) + ')';
      }
      if (rep !== tok) changed = true;
      return rep;
    });
    return changed ? out : null;
  }

  // Границы, заданные ссылкой на токен: var(--MH-Color-School) — это цвет
  // школы, у неё бывает насыщенный фиолетовый. Как рамка он не нужен.
  function borderFromVar(value, dark) {
    if (!dark || !value || !/var\(/i.test(value)) return null;
    var m = /var\(\s*--(?:MH|NC|MF)-([A-Za-z0-9-]+)/.exec(value);
    if (!m) return null;
    var n = m[1].toLowerCase();
    if (/violet|school|grape|magenta/.test(n)) return DARK.line;
    return null;
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
    '-IconWrap': '#2A2721',
    '-IconWrap-Active': 'rgba(143,176,212,0.24)',
    '-Avatar': 'rgba(143,176,212,0.22)',
    '-Notify-Icon-Light': '#2A2721',
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
    '-Color-School': '#8FB0D4'
  };

  // --- дизайн-система ЦДЗ (Lemma, --LM-*) ----------------------------------
  // У неё собственный тёмный набор переменных, но включается он классом,
  // которого сайт при тёмной теме не ставит. Мы (1) находим этот класс в CSS
  // и включаем сами, (2) поверх кладём точную палитру приложения. Именно
  // --LM-* отвечают за карточки ответов, рамки и экран результата, поэтому
  // без них половина ЦДЗ оставалась светлой.

  var LM_BASE = {
    'layer-background': '#171613',
    'layer-1': '#201E1A',
    'layer-1-alt': '#201E1A',
    'layer-2': '#2A2721',
    'layer-2-alt': '#2A2721',
    'layer-inverted': '#EAE3D5',
    'layer-overlay': 'rgba(23,22,19,0.74)',
    'layer-darken': 'rgba(23,22,19,0.9)',
    'layer-on-solid': '#171613',
    'layer-on-solid-soft': 'rgba(23,22,19,0.16)',
    'layer-neutral-subtle': 'rgba(234,227,213,0.05)',
    'layer-neutral-soft': 'rgba(234,227,213,0.09)',
    'layer-neutral-solid': '#3E392F',
    'layer-accent-solid': '#8FB0D4',
    'layer-accent-subtle': 'rgba(143,176,212,0.10)',
    'layer-accent-soft': 'rgba(143,176,212,0.22)',
    'layer-negative-solid': '#D9827A',
    'layer-negative-subtle': 'rgba(217,130,122,0.10)',
    'layer-negative-soft': 'rgba(217,130,122,0.22)',
    'layer-warning-solid': '#D6A960',
    'layer-warning-subtle': 'rgba(214,169,96,0.10)',
    'layer-warning-soft': 'rgba(214,169,96,0.22)',
    'layer-positive-solid': '#85B394',
    'layer-positive-subtle': 'rgba(133,179,148,0.10)',
    'layer-positive-soft': 'rgba(133,179,148,0.22)',
    'content-primary': '#EAE3D5',
    'content-secondary': '#A79D8B',
    'content-tertiary': '#A79D8B',
    'content-accent': '#8FB0D4',
    'content-negative': '#D9827A',
    'content-warning': '#D6A960',
    'content-positive': '#85B394',
    'content-on-solid': '#171613',
    'content-accent-on-soft': '#8FB0D4',
    'content-negative-on-soft': '#D9827A',
    'content-warning-on-soft': '#D6A960',
    'content-positive-on-soft': '#85B394',
    'content-inverted': '#171613',
    'content-on-layer-on-solid-primary': '#171613',
    'content-on-layer-on-solid-secondary': 'rgba(23,22,19,0.7)',
    'border-on-layer-1': '#3E392F',
    'border-on-layer-2': '#3E392F',
    'border-control': '#3E392F',
    'border-on-solid': '#171613',
    'border-neutral-soft': '#35312A',
    'border-accent-solid': '#8FB0D4',
    'border-accent-soft': 'rgba(143,176,212,0.40)',
    'border-negative-solid': '#D9827A',
    'border-negative-soft': 'rgba(217,130,122,0.40)',
    'border-warning-solid': '#D6A960',
    'border-warning-soft': 'rgba(214,169,96,0.40)',
    'border-positive-solid': '#85B394',
    'border-positive-soft': 'rgba(133,179,148,0.40)',
    'icon-neutral-solid': '#A79D8B',
    'icon-neutral-soft': 'rgba(167,157,139,0.45)',
    'icon-accent-solid': '#8FB0D4',
    'icon-negative-solid': '#D9827A',
    'icon-warning-solid': '#D6A960',
    'icon-positive-solid': '#85B394',
    'toggle-track-off': 'rgba(234,227,213,0.16)',
    'toggle-track-off-disabled': 'rgba(234,227,213,0.08)',
    'toggle-track-on': '#85B394',
    'toggle-track-on-disabled': 'rgba(133,179,148,0.4)',
    'toggle-knob': '#EAE3D5',
    'toggle-knob-disabled': '#A79D8B',
    'switcher-selected-item-bg': '#2A2721',
    'slider-knob': '#EAE3D5',
    'slider-knob-border': '#3E392F',
    'slider-knob-disabled': '#3E392F',
    'slider-selected-range-disabled': '#3E392F',
    'gheader-b2c-bg': '#201E1A',
    'gheader-b2c-bg-hover': '#2A2721',
    'gheader-b2c-content-primary': '#EAE3D5',
    'gheader-b2c-content-secondary': '#A79D8B',
    'gheader-b2b-bg-color': '#201E1A',
    'gheader-b2b-bg-color-hover': '#2A2721',
    'gheader-b2b-bg-dark': '#171613',
    'gheader-b2b-bg-dark-hover': '#2A2721',
    'gheader-b2b-content-primary': '#EAE3D5',
    'gheader-b2b-content-secondary-on-color': '#A79D8B',
    'btn-on-sol-bg': '#201E1A',
    'btn-on-sol-accent-cont': '#8FB0D4',
    'btn-on-sol-accent-cont-hover': '#8FB0D4',
    'btn-on-sol-accent-cont-pressed': '#8FB0D4',
    'btn-on-sol-negative-cont': '#D9827A',
    'btn-on-sol-negative-cont-hover': '#D9827A',
    'btn-on-sol-negative-cont-pressed': '#D9827A',
    'btn-on-sol-positive-cont': '#85B394',
    'btn-on-sol-positive-cont-hover': '#85B394',
    'btn-on-sol-positive-cont-pressed': '#85B394',
    'btn-on-sol-warning-cont': '#D6A960',
    'btn-on-sol-warning-cont-hover': '#D6A960',
    'btn-on-sol-warning-cont-pressed': '#D6A960',
    'btn-on-sol-content-disabled': '#A79D8B'
  };

  // Hover/pressed/disabled тоже заданы токенами: если их не перекрыть,
  // кнопки «светятся» белым при нажатии.
  var LM_INTERACTIVE = {
    'interactive-layer-accent-solid-hover': '#8FB0D4',
    'interactive-layer-accent-solid-pressed': '#8FB0D4',
    'interactive-layer-negative-solid-hover': '#D9827A',
    'interactive-layer-negative-solid-pressed': '#D9827A',
    'interactive-layer-warning-solid-hover': '#D6A960',
    'interactive-layer-warning-solid-pressed': '#D6A960',
    'interactive-layer-positive-solid-hover': '#85B394',
    'interactive-layer-positive-solid-pressed': '#85B394',
    'interactive-layer-2-hover': '#2A2721',
    'interactive-layer-2-pressed': '#2A2721',
    'interactive-layer-accent-soft-hover': 'rgba(143,176,212,0.30)',
    'interactive-layer-accent-soft-pressed': 'rgba(143,176,212,0.38)',
    'interactive-layer-negative-soft-hover': 'rgba(217,130,122,0.30)',
    'interactive-layer-negative-soft-pressed': 'rgba(217,130,122,0.38)',
    'interactive-layer-warning-soft-hover': 'rgba(214,169,96,0.30)',
    'interactive-layer-warning-soft-pressed': 'rgba(214,169,96,0.38)',
    'interactive-layer-positive-soft-hover': 'rgba(133,179,148,0.30)',
    'interactive-layer-positive-soft-pressed': 'rgba(133,179,148,0.38)',
    'interactive-layer-neutral-soft-hover': 'rgba(234,227,213,0.12)',
    'interactive-layer-neutral-soft-pressed': 'rgba(234,227,213,0.16)',
    'interactive-layer-neutral-solid-hover': '#3E392F',
    'interactive-layer-neutral-solid-pressed': '#4A4438',
    'interactive-layer-on-solid-hover': 'rgba(23,22,19,0.12)',
    'interactive-layer-on-solid-pressed': 'rgba(23,22,19,0.18)',
    'interactive-layer-on-solid-soft-hover': 'rgba(23,22,19,0.16)',
    'interactive-layer-on-solid-soft-pressed': 'rgba(23,22,19,0.10)',
    'interactive-layer-darken-hover': 'rgba(23,22,19,0.9)',
    'interactive-layer-darken-pressed': 'rgba(23,22,19,0.95)',
    'interactive-layer-disabled': 'rgba(234,227,213,0.05)',
    'interactive-content-accent-hover': '#8FB0D4',
    'interactive-content-accent-pressed': '#8FB0D4',
    'interactive-content-negative-hover': '#D9827A',
    'interactive-content-negative-pressed': '#D9827A',
    'interactive-content-positive-hover': '#85B394',
    'interactive-content-positive-pressed': '#85B394',
    'interactive-content-warning-hover': '#D6A960',
    'interactive-content-warning-pressed': '#D6A960',
    'interactive-content-secondary-hover': '#EAE3D5',
    'interactive-content-secondary-pressed': '#A79D8B',
    'interactive-content-disabled': '#A79D8B',
    'interactive-content-on-solid-hover': 'rgba(23,22,19,0.8)',
    'interactive-content-on-solid-pressed': 'rgba(23,22,19,0.6)',
    'interactive-content-on-solid-disabled': 'rgba(23,22,19,0.6)',
    'interactive-border-disabled': '#35312A',
    'interactive-border-on-solid-disabled': 'rgba(23,22,19,0.6)'
  };

  var LM_SHADOWS = {
    'shadow-bottom-xs': '0 1px 3px rgba(0,0,0,0.45)',
    'shadow-bottom-s': '0 1px 4px rgba(0,0,0,0.45)',
    'shadow-bottom-m': '0 2px 8px rgba(0,0,0,0.45)',
    'shadow-bottom-l': '0 4px 12px rgba(0,0,0,0.45)',
    'shadow-bottom-xl': '0 6px 18px rgba(0,0,0,0.5)',
    'shadow-bottom-xxl': '0 8px 24px rgba(0,0,0,0.55)',
    'shadow-top-xs': '0 -1px 3px rgba(0,0,0,0.45)',
    'shadow-top-s': '0 -1px 4px rgba(0,0,0,0.45)',
    'shadow-top-m': '0 -2px 8px rgba(0,0,0,0.45)',
    'shadow-top-l': '0 -4px 12px rgba(0,0,0,0.45)',
    'shadow-top-xl': '0 -6px 18px rgba(0,0,0,0.5)',
    'shadow-top-xxl': '0 -8px 24px rgba(0,0,0,0.55)',
    'shadow-left-xs': '-1px 0 3px rgba(0,0,0,0.45)',
    'shadow-left-s': '-1px 0 4px rgba(0,0,0,0.45)',
    'shadow-left-m': '-2px 0 8px rgba(0,0,0,0.45)',
    'shadow-left-l': '-4px 0 12px rgba(0,0,0,0.45)',
    'shadow-left-xl': '-6px 0 18px rgba(0,0,0,0.5)',
    'shadow-left-xxl': '-8px 0 24px rgba(0,0,0,0.55)',
    'shadow-right-xs': '1px 0 3px rgba(0,0,0,0.45)',
    'shadow-right-s': '1px 0 4px rgba(0,0,0,0.45)',
    'shadow-right-m': '2px 0 8px rgba(0,0,0,0.45)',
    'shadow-right-l': '4px 0 12px rgba(0,0,0,0.45)',
    'shadow-right-xl': '6px 0 18px rgba(0,0,0,0.5)',
    'shadow-right-xxl': '8px 0 24px rgba(0,0,0,0.55)',
    'shadow-inner-xs': 'inset 0 1px 3px rgba(0,0,0,0.4)',
    'shadow-inner-s': 'inset 0 1px 4px rgba(0,0,0,0.45)',
    'shadow-inner-m': 'inset 0 2px 5px rgba(0,0,0,0.5)'
  };

  // Цветные темы дизайн-системы: red/orange/green/blue/violet/... — каждая
  // задаёт и заливку, и текст, и рамку. Сводим их к ролям палитры.
  var LM_THEME_ROLE = {
    neutral: '#A79D8B', asphalt: '#A79D8B',
    red: '#D9827A', magenta: '#D9827A',
    orange: '#D6A960', yellow: '#D6A960', 'yellow-alt': '#D6A960',
    green: '#85B394', lime: '#85B394', teal: '#7FB8B8', cian: '#7FB8B8',
    blue: '#8FB0D4', indigo: '#8FB0D4',
    violet: '#B79BC4', grape: '#B79BC4'
  };

  function lmTheme() {
    var out = {};
    for (var name in LM_THEME_ROLE) {
      if (!LM_THEME_ROLE.hasOwnProperty(name)) continue;
      var c = LM_THEME_ROLE[name];
      var soft = withAlpha(c, 0.20), subtler = withAlpha(c, 0.10);
      out['theme-' + name + '-layer-solid'] = c;
      out['theme-' + name + '-layer-solid-hover'] = c;
      out['theme-' + name + '-layer-solid-pressed'] = c;
      out['theme-' + name + '-layer-soft'] = soft;
      out['theme-' + name + '-layer-soft-hover'] = withAlpha(c, 0.28);
      out['theme-' + name + '-layer-soft-pressed'] = withAlpha(c, 0.34);
      out['theme-' + name + '-layer-subtle'] = subtler;
      out['theme-' + name + '-content'] = c;
      out['theme-' + name + '-content-hover'] = c;
      out['theme-' + name + '-content-pressed'] = c;
      out['theme-' + name + '-content-on-soft'] = c;
      out['theme-' + name + '-border-solid'] = withAlpha(c, 0.55);
      out['theme-' + name + '-border-soft'] = withAlpha(c, 0.35);
      out['theme-' + name + '-icon-solid'] = c;
    }
    return out;
  }

  function tokenDecls(prefix, map) {
    var out = [];
    for (var k in map) {
      if (map.hasOwnProperty(k)) out.push(prefix + '-' + k + ':' + map[k] + ' !important');
    }
    return out.join(';');
  }

  function dsCss() {
    var all = {};
    var add = function (map) {
      for (var k in map) if (map.hasOwnProperty(k)) all[k] = map[k];
    };
    add(LM_BASE);
    add(LM_INTERACTIVE);
    add(LM_SHADOWS);
    add(lmTheme());
    return 'html.sch-dark{' + tokenDecls('--LM', all) + '}';
  }

  // Токены шапки/меню (--MF-*): ещё одна семья, тоже светлая по умолчанию.
  var MF_TOKENS = {
    'Background': '#171613',
    'Background-dark': '#171613',
    'Background-blue': '#201E1A',
    'Border': '#3E392F',
    'Color': '#EAE3D5',
    'Color-dark': '#EAE3D5',
    'Color-Hover': '#8FB0D4',
    'Color-Active': '#8FB0D4',
    'Gray': '#A79D8B',
    'Gray-Dark': '#A79D8B',
    'Button-Hover': '#2A2721',
    'Button-Active': '#2A2721',
    'Border-Button': '#3E392F',
    'Fill-Button': 'transparent',
    'Color-Fill': '#201E1A',
    'Color-Fill-App': '#171613'
  };

  function mfCss() {
    return 'html.sch-dark{' + tokenDecls('--MF', MF_TOKENS) + '}';
  }

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
    // Логотип, найденный по расположению (шапка не всегда <header>).
    // Двойной атрибут поднимает специфичность выше правил сайта: наши
    // перекрашенные правила сайта идут позже базовых.
    'html.sch-dark [data-sch-hide][data-sch-hide]{display:none !important;}',
    // Плашка роли («УЧЕНИК», «УЧИТЕЛЬ»): на сайте её цвета зависят от
    // цвета школы, и после перекраски текст оказывался тёмным на сиреневом.
    'html.sch-dark [data-sch-role][data-sch-role]{',
    '  background-color:rgba(143,176,212,0.165) !important;',
    '  color:#8FB0D4 !important;border-color:#3E392F !important;}',
    // монохромные SVG-иконки: заливка чёрным по атрибуту
    'html.sch-dark svg [fill="#000"],html.sch-dark svg [fill="#000000"],',
    'html.sch-dark svg [fill="#212529"],html.sch-dark svg [fill="black"]{',
    '  fill:currentColor !important;}',
    'html.sch-dark svg [stroke="#000"],html.sch-dark svg [stroke="#000000"],',
    'html.sch-dark svg [stroke="#212529"],html.sch-dark svg [stroke="black"]{',
    '  stroke:currentColor !important;}'
  ].join('') + tokenCss() + mfCss() + dsCss();

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

    var borderFix = borderFromVar(borderRaw, true);
    if (borderFix) dark.push('border-color:' + borderFix + ' !important');

    var shadow = mapShadow(style.getPropertyValue('box-shadow'), true);
    if (shadow) dark.push('box-shadow:' + shadow + ' !important');

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
      if (/^--(MH|NC|MF)-/i.test(name)) continue;
      if (/shadow/i.test(name)) {
        var sm = mapShadow(style.getPropertyValue(name), true);
        if (sm) dark.push(name + ':' + sm + ' !important');
        continue;
      }
      if (/^--LM-/i.test(name)) continue;   // дизайн-система: свой блок DS_CSS
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
    var borderFix = borderFromVar(st.getPropertyValue('border-color') ||
        st.getPropertyValue('border') || st.getPropertyValue('border-top'), true);
    if (borderFix) dark.push('border-color:' + borderFix + ' !important');

    var shadow = mapShadow(st.getPropertyValue('box-shadow'), true);
    if (shadow) dark.push('box-shadow:' + shadow + ' !important');

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

  // --- шапка: словесный знак «мэш» -----------------------------------------
  // Сайт держит логотип не обязательно в <header>: это бывает <div> с
  // хешированными классами. Ищем текст «мэш» в верхней части экрана и
  // убираем его вместе с картинкой рядом. Картинки заданий не трогаем.
  var hiddenNodes = [];

  function unhideAll() {
    for (var i = 0; i < hiddenNodes.length; i++) {
      try { hiddenNodes[i].removeAttribute('data-sch-hide'); } catch (e) { }
    }
    hiddenNodes = [];
  }

  function hideNode(el) {
    if (!el || el.getAttribute('data-sch-hide') === '1') return;
    el.setAttribute('data-sch-hide', '1');
    hiddenNodes.push(el);
  }

  function hideWordmark() {
    var all;
    try { all = document.querySelectorAll('p,span,div,a,strong,b,img'); } catch (e) { return; }
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      var rect = null;
      try { rect = el.getBoundingClientRect(); } catch (e) { }
      var top = rect ? rect.top : 0;
      var w = rect ? rect.width : 0;
      var h = rect ? rect.height : 0;
      // jsdom и ещё не отрисованные узлы отдают нули — это не повод
      // отказываться от проверки, иначе тест ничего не увидит.
      var unknown = !rect || (w === 0 && h === 0);
      if (!unknown && (top > 170 || h > 150 || w > 460)) continue;

      if (el.tagName === 'IMG') {
        var src = el.getAttribute('src') || '';
        if (/logo|мэш|mesh/i.test(src)) hideNode(el);
        continue;
      }
      if (el.children.length) continue;
      if (!/^(мэш|меш)$/i.test((el.textContent || '').trim())) continue;
      hideNode(el);
      var parent = el.parentElement;
      if (!parent || parent === document.body) continue;
      var pics = parent.querySelectorAll('img');
      for (var j = 0; j < pics.length; j++) hideNode(pics[j]);
    }
  }

  // --- плашка роли ---------------------------------------------------------
  var ROLE_TEXT = /^(ученик|ученица|учитель|учительница|родитель|родительница|студент|студентка)$/i;

  function roleBadges() {
    var all;
    try { all = document.querySelectorAll('span,div,p,a,b,strong,li'); } catch (e) { return; }
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      var txt = '';
      try { txt = (el.textContent || '').trim(); } catch (e) { continue; }
      var match = !el.children.length && ROLE_TEXT.test(txt);
      if (match && isDarkOn) {
        if (el.getAttribute('data-sch-role') !== '1') el.setAttribute('data-sch-role', '1');
      } else if (el.hasAttribute && el.hasAttribute('data-sch-role')) {
        el.removeAttribute('data-sch-role');
      }
    }
  }

  // --- SVG, раскрашенные атрибутами fill/stroke ---------------------------
  // Иконки и «уголки» рамок в ЦДЗ часто нарисованы атрибутами, а не CSS:
  // такие заливки наш лист не перекрашивает и они остаются белыми.
  var svgSaved = [];

  function svgIsMono(svg) {
    var nodes = [svg].concat(Array.prototype.slice.call(
        svg.querySelectorAll('[fill],[stroke]')));
    var colored = false, count = 0;
    for (var i = 0; i < nodes.length; i++) {
      var attrs = ['fill', 'stroke'];
      for (var a = 0; a < attrs.length; a++) {
        var v = nodes[i].getAttribute && nodes[i].getAttribute(attrs[a]);
        if (!v || v === 'none' || /^currentcolor$/i.test(v) || /^url\(/i.test(v)) continue;
        var c = parseColor(v);
        if (!c || c.a < 0.05) continue;
        count++;
        if (c.s > 0.35) colored = true;
      }
    }
    return count > 0 && !colored;
  }

  function paintSvgs() {
    var list;
    try { list = document.querySelectorAll('svg'); } catch (e) { return; }
    for (var i = 0; i < list.length; i++) {
      var svg = list[i];
      if (!svgIsMono(svg)) continue;
      var nodes = [svg].concat(Array.prototype.slice.call(
          svg.querySelectorAll('[fill],[stroke]')));
      for (var n = 0; n < nodes.length; n++) {
        var el = nodes[n];
        for (var k = 0; k < 2; k++) {
          var attr = k === 0 ? 'fill' : 'stroke';
          var v = el.getAttribute(attr);
          if (!v || v === 'none' || /^currentcolor$/i.test(v) || /^url\(/i.test(v)) continue;
          var c = parseColor(v);
          if (!c || c.a < 0.05 || !el.style || !el.style.setProperty) continue;
          if (el.style.getPropertyValue(attr) === DARK.ink) continue;
          svgSaved.push({
            el: el, attr: attr, value: el.style.getPropertyValue(attr)
          });
          el.style.setProperty(attr, DARK.ink, 'important');
        }
      }
    }
  }

  function unpaintSvgs() {
    for (var i = 0; i < svgSaved.length; i++) {
      try {
        var it = svgSaved[i];
        if (it.value) it.el.style.setProperty(it.attr, it.value);
        else it.el.style.removeProperty(it.attr);
      } catch (e) { }
    }
    svgSaved = [];
  }

  // --- отчёт о теме ---------------------------------------------------------
  // Долгое нажатие на луну в окне ЦДЗ отдаёт список элементов, которые
  // остались светлыми. Без этого приходилось угадывать по скриншотам.
  function report() {
    var out = {
      version: VERSION,
      dark: isDarkOn,
      url: String(location.href || '').slice(0, 180),
      size: [window.innerWidth || 0, window.innerHeight || 0],
      ds: darkScope.slice(0, 6),
      items: []
    };
    try {
      var all = document.body.querySelectorAll('*');
      for (var i = 0; i < all.length && out.items.length < 80; i++) {
        var el = all[i];
        if (el.id === BASE_ID || el.id === SITE_ID || el.id === DYN_ID) continue;
        var st = null;
        try { st = window.getComputedStyle(el); } catch (e) { continue; }
        if (!st) continue;
        var sus = [];
        var bg = parseColor(st.backgroundColor);
        if (bg && bg.a > 0.4 && bg.l > 0.62) sus.push('bg');
        var fg = parseColor(st.color);
        if (bg && bg.a > 0.4 && bg.l < 0.32 && fg && fg.a > 0.4 && fg.l < 0.4) {
          sus.push('fg');
        }
        var bd = parseColor(st.borderTopColor);
        if (bd && bd.a > 0.2 && bd.l > 0.55 &&
            (parseFloat(st.borderTopWidth) || 0) > 0) sus.push('border');
        var sh = st.boxShadow;
        if (sh && sh !== 'none') {
          var sc = sh.match(/rgba?\([^)]*\)/g) || [];
          for (var j = 0; j < sc.length; j++) {
            var shc = parseColor(sc[j]);
            if (shc && shc.l > 0.55) { sus.push('shadow'); break; }
          }
        }
        if (st.backgroundImage && /url\(/i.test(st.backgroundImage)) sus.push('bgimg');
        if (el.tagName === 'IMG') sus.push('img');
        if (el.tagName === 'SVG' || el.ownerSVGElement) {
          var fv = el.getAttribute && el.getAttribute('fill');
          var fc = fv ? parseColor(fv) : null;
          if (fc && fc.l > 0.8) sus.push('svgfill');
        }
        if (!sus.length) continue;
        var r = null;
        try { r = el.getBoundingClientRect(); } catch (e) { }
        out.items.push({
          t: el.tagName.toLowerCase(),
          c: String(el.getAttribute('class') || '').slice(0, 70),
          r: r ? [Math.round(r.left), Math.round(r.top),
                  Math.round(r.width), Math.round(r.height)] : [0, 0, 0, 0],
          s: sus.join(','),
          st: String(el.getAttribute('style') || '').slice(0, 120),
          h: String(el.outerHTML || '').slice(0, 160)
        });
      }
    } catch (e) { }
    var text = JSON.stringify(out);
    return text.length > 30000 ? text.slice(0, 30000) : text;
  }

  // --- тёмный набор дизайн-системы ----------------------------------------
  // Ищем в таблицах стилей правило, где --LM-layer-background объявлен
  // тёмным, и запоминаем его класс: он включает весь тёмный набор ЦДЗ.
  var darkScope = [];

  function findDarkScope() {
    var scopes = [];
    var visit = function (rules) {
      if (!rules) return;
      for (var i = 0; i < rules.length; i++) {
        var r = rules[i];
        try {
          if (r.type === 1 && r.style && r.selectorText) {
            var v = r.style.getPropertyValue('--LM-layer-background');
            if (!v) continue;
            var c = parseColor(v);
            if (!c || c.l > 0.35) continue;
            var parts = String(r.selectorText).split(',');
            for (var p = 0; p < parts.length; p++) {
              var one = parts[p].trim();
              if (!/^\.[A-Za-z0-9_-]+$/.test(one)) continue;
              var cls = one.slice(1);
              if (scopes.indexOf(cls) < 0) scopes.push(cls);
            }
          } else if (r.type === 4 || r.type === 12) {
            visit(r.cssRules);
          }
        } catch (e) { }
      }
    };
    var list = document.styleSheets || [];
    for (var i = 0; i < list.length; i++) {
      try { if (list[i].cssRules) visit(list[i].cssRules); } catch (e) { }
    }
    return scopes;
  }

  function decorate() {
    if (isDarkOn) {
      hideWordmark();
      roleBadges();
      paintSvgs();
    } else {
      unhideAll();
      roleBadges();
      unpaintSvgs();
    }
  }

  // --- тема ----------------------------------------------------------------

  function applyClass(dark) {
    var de = document.documentElement;
    if (!de) return;
    if (dark) de.classList.add('sch-dark');
    else de.classList.remove('sch-dark');
    // Свой тёмный набор токенов дизайн-системы включается классом.
    if (!darkScope.length) darkScope = findDarkScope();
    for (var i = 0; i < darkScope.length && i < 4; i++) {
      try {
        if (dark) de.classList.add(darkScope[i]);
        else de.classList.remove(darkScope[i]);
      } catch (e) { }
    }
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
    decorate();
  }

  // --- наблюдатель ---------------------------------------------------------

  var rescanTimer = null;

  function scheduleRescan() {
    if (rescanTimer) return;
    rescanTimer = setTimeout(function () {
      rescanTimer = null;
      scanSheets(false);
      queueAll();
      decorate();
    }, 120);
  }

  function boot() {
    addBase();
    scanSheets(true);
    applyClass(isDarkOn);
    queueAll();
    decorate();
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
    var scopeTries = 0;
    setInterval(function () {
      if (!document.getElementById(BASE_ID)) addBase();
      if (!document.getElementById(SITE_ID)) rebuildSite();
      // Стили дизайн-системы могут подгрузиться позже: пробуем найти их
      // тёмный набор ещё несколько раз, но не бесконечно.
      if (isDarkOn && !darkScope.length && scopeTries++ < 20) applyClass(true);
      scanSheets(false);
      decorate();
    }, 2500);
  }

  window.__schPaper = {
    version: VERSION,
    set: set,
    isDark: function () { return isDarkOn; },
    rescan: function () { scanSheets(true); queueAll(); decorate(); },
    report: report
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
