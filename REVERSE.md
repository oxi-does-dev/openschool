# OpenSchool — разбор

Источник: `https://github.com/oxi-does-dev/openschool` (ветка `main`, коммит
`26c48ef`, запушен 2026-11-12 19:48). APK в `Download/sch*.apk` собраны из
этого же дерева.

## Что это

- пакет `ru.sch.diary`, label «Дневник», minSdk 26, targetSdk 34;
- в `Download` лежат сборки 1.4 (versionCode 5), в репозитории — 1.5 (6);
- ассеты совпадают побайтово (`assets/cdz-paper.js`, md5
  `fb5f7d01947d5337909aee9cf98d8d6e`) — значит APK действительно из этого
  репозитория;
- внутри только `classes.dex` (Java, без нативных библиотек), шрифты и один JS.

## Авторизация

Приложение не логинится по логину/паролю — оно забирает cookies школьного
сайта:

- вход через WebView на `https://school.mos.ru/` (страница логина mos.ru);
- либо импорт `cookies.json` с ПК: `Android/data/ru.sch.diary/files/`;
- cookies хранятся в `CookieManager` приложения, на ПК — в
  `~/.cache/sch/cookies.json`.

Ключевые cookies:

| cookie | смысл |
|---|---|
| `aupd_token` | bearer-токен МЭШ (главное) |
| `active_student` | id ученика (число) |
| `student_person_id` | `contingent_guid` для EJ-запросов |
| `aupd_current_role` | id роли для `X-Mes-RoleId` |

Заголовки каждого запроса:

```
Authorization: Bearer <aupd_token>
Cookie: <все cookies>
User-Agent: Mozilla/5.0 (Linux; Android 14) ... Chrome/156 ...
Accept: application/json, text/plain, */*
Referer: https://school.mos.ru/diary
X-Mes-RoleId: 1            (для EJ — id роли)
X-mes-subsystem: familyweb
Profile-Id: <student_id>   (только EJ)
Profile-Type: student      (только EJ)
```

401/403 → сессия протухла, нужен повторный вход. Запросы повторяются один
раз при 5xx и при обрыве сети. `java.net.preferIPv4Stack=true` — иначе
Android+VPN зависает на AAAA-записи `school.mos.ru`.

## API

Базы:

- `https://school.mos.ru/api/family/web/v1` — дневник;
- `https://school.mos.ru/api/ej/attachments/v1` — файлы/работы;
- `https://school.mos.ru/api/ej/rating/v1` — рейтинг;
- `https://school.mos.ru/api/ej/partners/v1` — запуск ЦДЗ.

| метод | путь | параметры |
|---|---|---|
| GET | `/profile` | — (id ученика, дети, `contingent_guid`) |
| GET | `/schedule` | `student_id`, `date` |
| GET | `/schedule/short` | `student_id`, `dates=a,b,c` (темы) |
| GET | `/homeworks` | `student_id`, `from`, `to` |
| GET | `/marks` | `student_id`, `from`, `to` |
| GET | `/marks/{id}` | `person_id` |
| GET | `/lesson_schedule_items/{id}` | `student_id`, `type=LESSON`, `person_id` |
| GET | `rating /rank/subjects` | `personId`, `date` |
| GET | `rating /rank/class` | `personId`, `date` |
| GET | `partners /homeworks/launch` | `homework_entry_id`, `material_id` (302, ссылка в теле) |
| POST | `ej /student_work` | `lesson_schedule_item_id`, `student_profile_id`, `work_type:"INDEPENDENT_WORK"`, `variant` |
| PUT | `ej /student_work/{workId}/variant` | `{variant:N}` (400 — дедлайн) |
| POST | `ej /files/by_student_work/{workId}/storage_access` | `{}` → `{file_uuid,file_link,token}` (400 — дедлайн) |
| POST | `ej /files/by_student_work/{workId}` | `{files:[...]}` (attach/delete) |

Ответ на создание — `StudentWork`: `{student_work_id, variant, files[]}`
(409 — работа уже есть; тогда id берём из повторного `GET lesson`).
Элемент `files[]` (`StudentWorkFile`) — `id` (uuid), `ordinal`, `type`
(расширение), `size` (строка, байты), `file_url`, `file_name`, `uploaded_by`
(`teacher`/`student`).

Протокол прикрепления (сверено с `ru.mes.dnevnik` 3.93.3, Hermes-бандл):

1. `storage_access` → `file_uuid` + `file_link` + `token`;
2. `multipart/form-data` POST на `file_link`, поле `content`, заголовки
   `Authorization: Bearer <token>` и `Content-Type: multipart/form-data`
   (отдельный HTTP-клиент, без `Profile-Id`);
3. `POST /files/by_student_work/{id}` с `{files:[…]}` — **только новые файлы**:
   - только что загруженный: `{file_uuid, file_name, size, order_number, extension}`;
   - уже прикреплённый: `{file_uuid, order_number}`;
   - удаление: `{file_uuid, deleted:true}`.

Все четыре EJ-запроса (create/variant/storage/attach) идут с `X-Mes-Subsystem`,
`Authorization`, `Profile-Id`; `Profile-Type` у МЭШ равен `parent` для
родительского профиля и `student` для детского.
В SDK имена операций содержат `…Family…` (`postApiEjAttachmentsFamilyV1StudentWork`),
но путь при этом обычный `/v1/student_work` — `/family/` в URL добавлять нельзя.


Скачивание файлов — тот же набор заголовков, `Accept: */*`, таймаут 60 с.

## Сборка в Termux (проверено)

В репозитории скрипт рассчитан на build-tools 35. Android SDK здесь есть,
но `aapt2` из SDK не запускается (конфликт protobuf), поэтому:

- `aapt2` — из Termux (`/usr/bin/aapt2`, 2.20);
- `d8` из build-tools 34 падает на классах от javac 21 → взят `r8-9.5.32.jar`
  с `dl.google.com/dl/android/maven2/com/android/tools/r8/9.5.32/`;
- `javac --release 11`, `zipalign`, `apksigner`, `keytool` — штатные.

Готовый скрипт: `android/build-termux.sh` → `android/build-local/sch-local.apk`
(795 КБ). Ключ подписи лежит в `android/.keys/` и переживает пересборку,
поэтому новые сборки встают поверх друг друга. Поверх *чужой* установленной
версии (`CN=sch diary` или оригинальной `CN=OpenSchool`) APK не встанет —
нужна переустановка с удалением.

Терминальная версия собирается как есть:

```bash
pkg install nlohmann-json libcurl sqlite openssl
cd cpp && make -j8        # -> build/sch
```

Использование: `sch today`, `sch marks 30`, `sch serve` (JSON API на
`127.0.0.1:8787`: `/day`, `/lessons`, `/hw`, `/homeworks`, `/marks`,
`/schedule`, `/schedule/short`, `/profile`, `/health`).

Локальная сессия: `~/.cache/sch/cookies.json`; `sch login` умеет вытащить
cookies из профиля Chromium (на Android Chromium недоступен — только
импорт файла).

## Что осталось

- cookies МЭШ на этом устройстве отсутствуют (`~/.cache/sch` пуст), поэтому
  `sch doctor` не проходит дальше поиска сессии;
- ключ подписи исходных APK (`CN=OpenSchool`, alias `sch`) в репозиторий не
  входит — для обновления «поверх» нужен он.

## Локальные доработки (ветка `local`)

- **родительский кабинет**: роль берётся из `aupd_current_role`
  (`ученик:2`, `родитель:3`, значение может быть url-encoded); для родителя
  `student_id` — это id ребёнка из `/profile.children`, а не `profile.id`;
  `X-Mes-RoleId` для family-запросов тоже равен роли из cookie; в шапке
  показывается ребёнок, взрослый подписан «родитель: …»; переключатель детей
  виден и при одном ребёнке;
- **прикрепление фото работы (ЦДЗ)**: окно урока → «Прикрепить фото работы»,
  до 10 файлов, фото жмутся в JPEG ≤2400 px; отправка создаёт работу
  (409 → переиспользует существующую), заливает файлы и прикрепляет их.
  Ошибки показывают HTTP-код и тело ответа МЭШ.
- **тёмная тема ЦДЗ**: кнопка в шапке окна задания (луна/солнце), состояние в
  `SharedPreferences` (`cdz_theme`: auto/light/dark, auto = тема приложения).
  `assets/cdz-paper.js` (v4) не трогает DOM и не воюет с React: он читает
  правила самого ЦДЗ через `document.styleSheets` и генерирует свои копии тех
  же селекторов со `!important` и префиксом `html.sch-dark` /
  `html:not(.sch-dark)`. Переключение темы — один класс на `<html>`, поэтому
  ничего не мигает и не перерисовывается заново. Inline-стили (их React может
  переписать) живут в `data-sch-d` / `data-sch-l` + сгенерированные правила,
  а не в `style`. Палитра берётся из `applyPalette()` один в один, без
  «похожих» оттенков: BG `#171613`, SURFACE `#201E1A`, SURFACE2 `#2A2721`,
  текст `#EAE3D5`, приглушённый `#A79D8B`, акцент `#8FB0D4`, ON_ACCENT
  `#171613`, STROKE `#3E392F`, DIVIDER `#35312A`, RULE `#4A4438`,
  CURRENT `#2C2A20`, RED `#D9827A`, GREEN `#85B394`, AMBER `#D6A960`,
  фиолетовый `#B79BC4`. Цветные плашки — `soft()` приложения (цвет + 0x22),
  кнопки — `ACCENT_SOFT` (0x2A8FB0D4) с текстом `ACCENT`, плотная акцентная
  заливка — `ACCENT` с текстом `ON_ACCENT`.
  Окно задания — во весь экран, вместо внутреннего загрузчика ЦДЗ показывается
  своя тёмная заставка со спиннером, которая плавно уступает место странице.
  Градиенты (`linear-gradient`, в том числе inline и на `var(--MH-*)`)
  перекрашиваются по стопам в полупрозрачные тона палитры — иначе фирменные
  фиолетовые баннеры и пилюли выпадали из темы. Тёмный логотип МЭШ в шапке
  скрывается (на тёмном фоне его не видно), остальные картинки не трогаются.
  Новые/дописанные таблицы стилей подхватываются (`scanSheets`, CSS-in-JS).

### Адаптивный нативный UI (2.3)

  `tight()` = ширина экрана < 340dp или `fontScale >= 1.3`. В этом режиме:

  - колонки времени (`08:30` / `до 09:15`) считаются по реальной ширине
    текста, а не фиксируются в 42/64dp;
  - оценки урока уходят под тему (`chipFlow`) вместо того, чтобы выжимать
    название предмета; ряд из пяти оценок переносится на вторую строку;
  - высота чипа оценки следует масштабу шрифта (`minHeight`, а не `dp(32)`);
  - пары кнопок (`buttonPair`) складываются в столбик, только если текст
    реально не помещается по ширине;
  - полосы чипов (ДЗ, оценки) прокручиваются по горизонтали (`chipStrip`);
  - диалог «Сеть и VPN» прокручивается и не теряет кнопки по высоте;
  - заголовки (шапка, месяца, недели, нижняя навигация) не переносятся
    бесконечно, а обрезаются с многоточием.

### 2.4: поведение и информация

  - **Назад** возвращает на «Дневник», а с другого дня — на сегодня;
    выходит из приложения только с сегодняшнего дня.
  - При возврате в приложение после 3 минут простоя открытая вкладка
    обновляется; если приложение простояло ночь, «сегодня» переезжает
    на новый день (и сохранённая дата тоже).
  - Если сеть отвалилась, но есть кэш, показывается плашка
    «Нет связи — данные от HH:MM» с кнопкой «Обновить»
    (день, неделя, ДЗ, оценки, итоговые, рейтинг).
  - `cachePut` пишет `key#at` — время фактического ответа сервера;
    «Очистить кэш» убирает и его, и `week3:`, `final2:`, `profile`.
  - В карточке ДЗ: «Копировать» и «Поделиться» (предмет, дата, урок,
    текст, материалы простым текстом).
  - В окне ЦДЗ — кнопка перезагрузки страницы.
  - «Ещё → Размер текста»: множитель 1.15 / 1.35 к системному шрифту
    через `attachBaseContext` + `Configuration.fontScale`; Activity
    пересоздаётся, `tight()` автоматически включает адаптивную вёрстку.
  - Для родителя в шапке дня показывается имя ребёнка, а не только класс.
  - В карточке дня ДЗ стало интерактивным: нажатие открывает подробности
    (файлы, материалы), а цифровое задание запускается кнопкой «Пройти ЦДЗ»
    прямо из дня — раньше это было только на вкладке ДЗ.
  - Сборка (`build-termux.sh`) теперь падает при ошибке `javac`: раньше
    вывод шёл через `grep -v`, и ошибки компиляции могли не остановить
    сборку.

### 2.5: тёмная тема ЦДЗ и дизайн-система

Экран ЦДЗ собран не только на токенах МЭШ (`--MH-*`/`--NC-*`), но и на
дизайн-системе Lemma (`--LM-*`, файл `7.383273e8.chunk.css` у `uchebnik`).
Раньше перекрашивались только токены МЭШ, поэтому шапка и кнопки были
тёмными, а карточки ответов, рамки, экран результата — светлыми.

  - `--LM-*` перекрываются точной палитрой приложения: `layer-background`
    (#171613), `layer-1` (#201E1A), `layer-2` (#2A2721), `content-primary`
    (#EAE3D5), `content-secondary` (#A79D8B), `border-*` (#3E392F),
    `shadow-*` (чёрные), `theme-<цвет>-*` — по ролям палитры.
  - У дизайн-системы есть собственный тёмный набор токенов, он включается
    классом на `<html>`. Класс включён с версии 7.383273e8 — версии нет,
    поэтому тёмный набор ищется в таблицах стилей по
    `--LM-layer-background` с тёмным значением и его класс добавляется/снимается
    вместе с темой (до 20 попыток, если CSS подгрузился позже).
  - `box-shadow`/`--*-shadow-*` перекрашиваются: светлая тень на тёмном фоне
    выглядела ореолом вокруг карточек; inset-светлые кольца → STROKE.
  - Рамки, заданные ссылкой на токен (`border:3px solid var(--MH-Color-School)`),
    сводятся к STROKE: у школы бывает насыщенный фиолетовый, и карточка
    результата получала толстую сиреневую рамку.
  - `--MH-Color-School` → ACCENT: плашка роли («УЧЕНИК») больше не сиреневая.
  - Плашка роли дополнительно помечается `data-sch-role`: ACCENT_SOFT +
    ACCENT, независимо от того, какие токены использовал сайт.
  - SVG, раскрашенные атрибутами `fill`/`stroke` (иконки, «уголки» рамок),
    перекрашиваются в TEXT — но только монохромные: цветные иллюстрации
    заданий не трогаются.
  - Логотип МЭШ ищется не только внутри `<header>`, но и по словесному знаку
    «мэш» в верхней части экрана (шапка бывает `<div>` с хешем).
  - Долгое нажатие на луну в окне ЦДЗ → «Отчёт о теме»: JSON со списком
    элементов, у которых остались светлые фон/текст/рамка/тень/картинка.
    Его можно скопировать или отправить.
  - `report()` доступен как `window.__schPaper.report()`. CSS-переменные (`--bg: #fff` + `var(--bg)`)
  тоже перекрашиваются, иначе половина сайта осталась бы белой. API:
  `window.__schPaper.set(true)`,
  `.isDark()`, `.rescan()`.
