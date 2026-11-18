package ru.sch.diary;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Клиент JSON API МЭШ — те же запросы, что делает sch на ПК. */
final class Api {

    static final String BASE = "https://school.mos.ru";
    static final String API = BASE + "/api/family/web/v1";
    static final String EJ = BASE + "/api/ej/attachments/v1";
    static final String RATING = BASE + "/api/ej/rating/v1";
    static final String PARTNERS = BASE + "/api/ej/partners/v1";
    static final String EJ_CORE = BASE + "/api/ej/core/family/v1";
    static final String UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/156.0.0.0 Mobile Safari/537.36";

    static final String NET_HINT =
            " Проверьте интернет и VPN: school.mos.ru пускает только с российских IP.";

    static {
        // Многие VPN на Android не выдают IPv6 — без этого соединение может
        // зависать на AAAA-записи school.mos.ru.
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    static class AuthException extends Exception {
        AuthException(String m) { super(m); }
    }

    static class ApiException extends Exception {
        /** HTTP-код ответа МЭШ, если он был. 0 — сеть или разбор ответа. */
        final int status;

        ApiException(String m) { this(0, m); }

        ApiException(int status, String m) {
            super(m);
            this.status = status;
        }
    }

    private final String token;
    private final String cookie;
    private String studentId;
    private String personId;

    // Выбранный ребёнок. Если не задан, берём active_student из cookies.
    private static String selectedStudentId;
    private static String selectedPersonId;

    // Родительский кабинет: в /profile лежит взрослый, ребёнок — в children.
    private static volatile boolean parentAccount;

    static void setParentAccount(boolean parent) {
        parentAccount = parent;
    }

    static boolean isParentAccount() {
        return parentAccount;
    }

    /** Имя роли из cookie aupd_current_role: «ученик:2», «родитель:3». */
    private static String roleName() {
        String role = Cookies.map().get("aupd_current_role");
        if (role == null) return "";
        try {
            role = URLDecoder.decode(role, "UTF-8");
        } catch (Exception ignored) {
        }
        int i = role.indexOf(':');
        String name = (i > 0 ? role.substring(0, i) : role).trim();
        return name.toLowerCase();
    }

    static boolean isParentRole() {
        String n = roleName();
        return n.contains("родител") || n.contains("parent");
    }

    static boolean isStudentRole() {
        String n = roleName();
        return n.contains("ученик") || n.contains("student");
    }

    static void selectStudent(String sid, String person) {
        selectedStudentId = sid == null || sid.trim().isEmpty() ? null : sid.trim();
        selectedPersonId = person == null || person.trim().isEmpty() ? null : person.trim();
    }

    private Api(String token, String cookie, String studentId) {
        this.token = token;
        this.cookie = cookie;
        this.studentId = studentId;
    }

    static Api fromCookies() throws AuthException {
        Map<String, String> m = Cookies.map();
        String token = m.get("aupd_token");
        if (!usable(token)) {
            throw new AuthException("Нет доступа МЭШ — нужно войти в школу.");
        }
        String sid = selectedStudentId;
        if (sid == null) {
            sid = m.get("active_student");
            if (sid != null) {
                sid = sid.trim();
                if (!sid.matches("\\d+")) sid = null;
            }
        }
        return new Api(token, Cookies.header(), sid);
    }

    static boolean usable(String t) {
        if (t == null || t.length() < 10) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < 0x21 || c > 0x7e) return false;
        }
        return true;
    }

    // ------------------------------------------------------------ HTTP

    private JSONObject get(String path, Map<String, String> params)
            throws ApiException, AuthException {
        try {
            return new JSONObject(getRaw(API + path, params, false, false));
        } catch (JSONException e) {
            throw new ApiException("ответ МЭШ не JSON: " + e.getMessage());
        }
    }

    private JSONArray getArray(String base, String path, Map<String, String> params)
            throws ApiException, AuthException {
        try {
            return new JSONArray(getRaw(base + path, params, true, false));
        } catch (JSONException e) {
            throw new ApiException("ответ МЭШ не JSON: " + e.getMessage());
        }
    }

    /** Общий GET с повтором при обрыве. body — объект или массив JSON. */
    private String getRaw(String url, Map<String, String> params, boolean ej)
            throws ApiException, AuthException {
        return getRaw(url, params, ej, false);
    }

    private String getRaw(String url, Map<String, String> params, boolean ej,
                          boolean allow3xx)
            throws ApiException, AuthException {
        StringBuilder u = new StringBuilder(url);
        if (params != null && !params.isEmpty()) {
            char sep = url.contains("?") ? '&' : '?';
            for (Map.Entry<String, String> e : params.entrySet()) {
                u.append(sep).append(enc(e.getKey())).append('=').append(enc(e.getValue()));
                sep = '&';
            }
        }
        IOException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u.toString()).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(25000);
                c.setRequestMethod("GET");
                if (allow3xx) c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept", "application/json, text/plain, */*");
                c.setRequestProperty("Accept-Language",
                        "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
                c.setRequestProperty("Referer", BASE + "/diary");
                c.setRequestProperty("X-Mes-RoleId",
                        ej || parentAccount ? roleId() : "1");
                c.setRequestProperty("X-mes-subsystem", "familyweb");
                c.setRequestProperty("Profile-Id", ej ? studentId() : "");
                c.setRequestProperty("Profile-Type", ej ? profileType() : "");
                c.setRequestProperty("Authorization", "Bearer " + token);
                if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);

                int code = c.getResponseCode();
                if (code == 401 || code == 403) {
                    throw new AuthException(
                            "МЭШ отклонил сессию — нужно войти заново.");
                }
                if (allow3xx && code >= 300 && code < 400) {
                    return readAll(c.getInputStream());
                }
                if (code >= 500 && attempt == 1) {
                    sleep(1000);
                    continue;
                }
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String body = readAll(in);
                if (code != 200) {
                    throw new ApiException("HTTP " + code + ": " + clip(body, 200));
                }
                return body;
            } catch (IOException e) {
                last = e;
                if (attempt == 1) {
                    sleep(1000);
                    continue;
                }
                throw new ApiException("сеть недоступна: " + e.getMessage() + NET_HINT);
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw new ApiException("не дозвонился до МЭШ: "
                + (last == null ? "?" : last.getMessage()) + NET_HINT);
    }

    // ------------------------------------------------------------ запросы

    JSONObject profile() throws ApiException, AuthException {
        return get("/profile", null);
    }

    String studentId() throws ApiException, AuthException {
        if (selectedStudentId != null) return selectedStudentId;
        if (studentId != null) return studentId;
        JSONObject p = profile();
        JSONObject prof = p.optJSONObject("profile");
        JSONArray kids = p.optJSONArray("children");

        // В родительском кабинете profile.id — это взрослый, а дневник
        // открывается по id ребёнка из children. У ученика всё наоборот:
        // профиль совпадает с ребёнком, поэтому порядок прежний.
        boolean kidFirst = isParentRole()
                || (kids != null && kids.length() > 1);
        String kid = kids == null || kids.length() == 0
                ? "" : s(kids.optJSONObject(0), "id");
        String sid = kidFirst ? kid : (prof == null ? "" : s(prof, "id"));
        if (sid.isEmpty()) sid = kidFirst
                ? (prof == null ? "" : s(prof, "id")) : kid;
        if (sid.isEmpty()) throw new ApiException("не могу определить student_id");
        studentId = sid;
        return sid;
    }

    private JSONObject scheduleDay(String date) throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("student_id", studentId());
        if (date != null && !date.isEmpty()) p.put("date", date);
        return get("/schedule", p);
    }

    private JSONArray scheduleShort(List<String> dates)
            throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("student_id", studentId());
        p.put("dates", String.join(",", dates));
        JSONObject o = get("/schedule/short", p);
        JSONArray a = o.optJSONArray("payload");
        return a == null ? new JSONArray() : a;
    }

    JSONArray homeworks(String from, String to) throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("student_id", studentId());
        p.put("from", from);
        p.put("to", to);
        JSONObject o = get("/homeworks", p);
        JSONArray a = o.optJSONArray("payload");
        return a == null ? new JSONArray() : a;
    }

    /** Домашние задания за диапазон, нормализованные и отсортированные по дате. */
    JSONArray homeworksRange(String from, String to)
            throws ApiException, AuthException {
        JSONArray payload = homeworks(from, to);

        // В самом ДЗ нет времени окончания урока — берём его из короткого
        // расписания на те же даты (один запрос на весь диапазон).
        Map<String, String> beginByDateEnd = new LinkedHashMap<>();
        // В диапазоне ДЗ время урока часто 00:00: подставляем конец урока
        // из расписания по дате и предмету.
        Map<String, String> endByDateSubject = new LinkedHashMap<>();
        Set<String> dates = new LinkedHashSet<>();
        for (int i = 0; i < payload.length(); i++) {
            JSONObject x = payload.optJSONObject(i);
            String d = x == null ? "" : s(x, "date");
            if (!d.isEmpty()) dates.add(d);
        }
        if (!dates.isEmpty()) {
            try {
                JSONArray shortPayload = scheduleShort(new ArrayList<>(dates));
                for (int i = 0; i < shortPayload.length(); i++) {
                    JSONObject day = shortPayload.optJSONObject(i);
                    if (day == null) continue;
                    String dd = s(day, "date");
                    JSONArray lessons = day.optJSONArray("lessons");
                    if (lessons == null) continue;
                    for (int j = 0; j < lessons.length(); j++) {
                        JSONObject l = lessons.optJSONObject(j);
                        if (l == null) continue;
                        String b = s(l, "begin_time");
                        String e = s(l, "end_time");
                        if (!dd.isEmpty() && !b.isEmpty() && !e.isEmpty()) {
                            beginByDateEnd.put(dd + "|" + e, b);
                        }
                        String subj = s(l, "subject");
                        if (subj.isEmpty()) subj = s(l, "subject_name").trim();
                        if (!dd.isEmpty() && !subj.isEmpty() && !e.isEmpty()) {
                            String key = dd + "|" + subj;
                            if (!endByDateSubject.containsKey(key)) {
                                endByDateSubject.put(key, e);
                            }
                        }
                    }
                }
            } catch (ApiException | AuthException ignored) {
                // время окончания не критично
            }
        }

        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < payload.length(); i++) {
            JSONObject x = payload.optJSONObject(i);
            if (x == null) continue;
            JSONObject h = normalizeHomework(x);
            String begin = beginByDateEnd.get(s(h, "date") + "|" + s(h, "lesson_time"));
            if (begin != null) put(h, "lesson_start", begin);
            String end = s(h, "lesson_time");
            if (end.isEmpty() || "00:00".equals(end)) {
                String fixed = endByDateSubject.get(s(h, "date") + "|"
                        + s(h, "subject").trim());
                if (fixed != null) put(h, "lesson_time", fixed);
            }
            list.add(h);
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                int c = s(a, "date").compareTo(s(b, "date"));
                if (c != 0) return c;
                return s(a, "lesson_time").compareTo(s(b, "lesson_time"));
            }
        });
        JSONArray out = new JSONArray();
        for (JSONObject h : list) out.put(h);
        return out;
    }

    JSONArray marks(String from, String to) throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("student_id", studentId());
        p.put("from", from);
        p.put("to", to);
        JSONObject o = get("/marks", p);
        JSONArray a = o.optJSONArray("payload");
        return a == null ? new JSONArray() : a;
    }

    /** Посещаемость: пропуски и опоздания за диапазон. */
    JSONObject attendance(String from, String to)
            throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("student_id", studentId());
        p.put("from", from);
        p.put("to", to);
        return get("/attendance", p);
    }

    /** Итоговые оценки по предмету: периоды и выставленные баллы. */
    JSONObject subjectMarks(String subjectId)
            throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("student_id", studentId());
        p.put("subject_id", subjectId);
        return get("/subject_marks/for_subject", p);
    }

    JSONArray academicYears() throws ApiException, AuthException {
        return getArray(EJ_CORE, "/academic_years", null);
    }

    /**
     * Ссылка запуска цифрового задания. МЭШ отвечает 302, а сама ссылка
     * лежит в теле ответа.
     */
    String homeworkLaunchUrl(String entryId, String materialId)
            throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("homework_entry_id", entryId);
        p.put("material_id", materialId);
        String body = getRaw(PARTNERS + "/homeworks/launch", p, true, true).trim();
        if (!body.startsWith("http")) {
            throw new ApiException("МЭШ не выдал ссылку на задание");
        }
        return body;
    }

    /** Рейтинг по предметам: место в классе, средний балл, динамика. */
    JSONArray subjectRatings(String date) throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("personId", personId());
        p.put("date", date);
        return getArray(RATING, "/rank/subjects", p);
    }

    /** Общее место в классе по среднему баллу (не по предмету). */
    JSONObject classRank(String date) throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("personId", personId());
        p.put("date", date);
        JSONArray all = getArray(RATING, "/rank/class", p);
        String me = personId();
        JSONObject out = new JSONObject();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            if (!me.equals(s(o, "personId"))) continue;
            JSONObject r = o.optJSONObject("rank");
            if (r == null) break;
            put(out, "place", r.opt("rankPlace"));
            put(out, "average", r.opt("averageMarkFive"));
            put(out, "place30", r.opt("rankPlace30"));
            put(out, "average30", r.opt("averageMarkFive30"));
            put(out, "trend", s(r, "trend"));
            break;
        }
        put(out, "total", all.length());
        return out;
    }

    /** Полная карточка оценки: учитель, критерии, распределение по классу. */
    /** Скачивание файла с теми же cookies, что и у API. */
    byte[] download(String url) throws ApiException, AuthException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Referer", BASE + "/diary");
            c.setRequestProperty("X-Mes-RoleId", "1");
            c.setRequestProperty("X-mes-subsystem", "familyweb");
            c.setRequestProperty("Authorization", "Bearer " + token);
            if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);

            int code = c.getResponseCode();
            if (code == 401 || code == 403) {
                throw new AuthException("МЭШ отклонил сессию — нужно войти заново.");
            }
            if (code != 200) throw new ApiException("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new ApiException("не удалось скачать файл: " + e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    JSONObject mark(String markId) throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("person_id", personId());
        return get("/marks/" + enc(markId), p);
    }

    JSONObject day(String date) throws ApiException, AuthException {
        JSONObject full = scheduleDay(date);
        String dayDate = s(full, "date");
        JSONArray shortPayload = new JSONArray();
        if (!dayDate.isEmpty()) {
            try {
                shortPayload = scheduleShort(Collections.singletonList(dayDate));
            } catch (ApiException | AuthException ignored) {
                shortPayload = new JSONArray();  // темы не критичны
            }
        }
        JSONArray hw = new JSONArray();
        if (!dayDate.isEmpty()) hw = homeworks(dayDate, dayDate);
        return normalizeDay(full, shortPayload, hw);
    }

    /**
     * Неделя целиком: темы и ДЗ качаются одним запросом на диапазон,
     * расписание каждого дня — отдельно, как в sch week.
     */
    JSONArray week(String start, int days) throws ApiException, AuthException {
        LocalDate d0 = LocalDate.parse(start);
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < days; i++) dates.add(d0.plusDays(i).toString());

        JSONArray shortPayload = new JSONArray();
        try {
            shortPayload = scheduleShort(dates);
        } catch (ApiException ignored) {
            // темы не критичны
        }

        JSONArray hw = new JSONArray();
        try {
            hw = homeworks(dates.get(0), dates.get(dates.size() - 1));
        } catch (ApiException ignored) {
            // ДЗ не критичны
        }

        JSONArray out = new JSONArray();
        for (String d : dates) {
            try {
                out.put(normalizeDay(scheduleDay(d), shortPayload, hw));
            } catch (ApiException e) {
                JSONObject row = new JSONObject();
                put(row, "date", d);
                put(row, "error", e.getMessage() == null ? "нет данных" : e.getMessage());
                out.put(row);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ прикрепление работы

    /** Полные данные урока: тема, ДЗ и student_work (прикреплённые сканы). */
    JSONObject lesson(String lessonId) throws ApiException, AuthException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("student_id", studentId());
        p.put("type", "LESSON");
        String person = personId();
        if (!person.isEmpty()) p.put("person_id", person);
        return get("/lesson_schedule_items/" + enc(lessonId), p);
    }

    String personId() throws ApiException, AuthException {
        if (selectedPersonId != null) return selectedPersonId;
        if (personId != null) return personId;
        String p = Cookies.map().get("student_person_id");
        if (p == null) p = "";
        p = p.trim();
        if (p.isEmpty()) {
            JSONObject prof = profile();
            JSONArray kids = prof.optJSONArray("children");
            JSONObject k = null;
            if (kids != null && kids.length() > 0) {
                for (int i = 0; i < kids.length() && selectedStudentId != null; i++) {
                    JSONObject cur = kids.optJSONObject(i);
                    if (cur != null
                            && selectedStudentId.equals(s(cur, "id"))) {
                        k = cur;
                        break;
                    }
                }
                if (k == null) k = kids.optJSONObject(0);
            }
            if (k != null) p = s(k, "contingent_guid").trim();
        }
        personId = p;
        return p;
    }

    /** Создаёт запись работы (если её ещё нет) и возвращает её id. */
    JSONObject createStudentWork(String lessonId, Integer variant)
            throws ApiException, AuthException {
        JSONObject body = new JSONObject();
        long lid;
        long sid;
        try {
            lid = Long.parseLong(lessonId);
            sid = Long.parseLong(studentId());
        } catch (NumberFormatException e) {
            throw new ApiException("некорректный id урока");
        }
        put(body, "lesson_schedule_item_id", lid);
        put(body, "student_profile_id", sid);
        put(body, "work_type", "INDEPENDENT_WORK");
        if (variant != null) put(body, "variant", variant);
        return sendJson("POST", EJ + "/student_work", body);
    }

    /**
     * id работы из объекта student_work. В МЭШ это student_work_id, но
     * на всякий случай смотрим и привычные id/studentWorkId.
     */
    static String workId(JSONObject work) {
        if (work == null) return "";
        String id = num(work, "student_work_id");
        if (id.isEmpty()) id = num(work, "studentWorkId");
        if (id.isEmpty()) id = num(work, "id");
        return id;
    }

    /** Перечитывает урок и отдаёт id уже созданной работы (ответ на 409). */
    String existingWorkId(String lessonId) throws ApiException, AuthException {
        for (int i = 0; i < 3; i++) {
            String id = workId(lesson(lessonId).optJSONObject("student_work"));
            if (!id.isEmpty()) return id;
            // Работа могла создаваться параллельно — дадим МЭШ секунду.
            sleep(700);
        }
        return "";
    }

    /** Ссылка и токен для загрузки одного файла в хранилище. */
    JSONObject storageAccess(String workId) throws ApiException, AuthException {
        return sendJson("POST", EJ + "/files/by_student_work/"
                + enc(workId) + "/storage_access", new JSONObject());
    }

    /** Регистрирует загруженные файлы у работы или удаляет их. */
    JSONObject upsertFiles(String workId, JSONArray files)
            throws ApiException, AuthException {
        JSONObject body = new JSONObject();
        put(body, "files", files);
        return sendJson("POST", EJ + "/files/by_student_work/" + enc(workId), body);
    }

    void updateVariant(String workId, int variant) throws ApiException, AuthException {
        JSONObject body = new JSONObject();
        put(body, "variant", variant);
        sendJson("PUT", EJ + "/student_work/" + enc(workId) + "/variant", body);
    }

    /** Заливает байты в хранилище по ссылке из storage_access. */
    void uploadFile(String link, String uploadToken, String fileName,
                    String mime, byte[] data) throws ApiException {
        String boundary = "----sch" + System.currentTimeMillis();
        String safeName = fileName.replace("\"", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            body.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"content\"; filename=\""
                    + safeName + "\"\r\n"
                    + "Content-Type: " + mime + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            body.write(data);
            body.write(("\r\n--" + boundary + "--\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException("не собрал файл: " + e.getMessage());
        }

        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(link).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("Authorization", "Bearer " + uploadToken);
            c.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            byte[] raw = body.toByteArray();
            c.setFixedLengthStreamingMode(raw.length);
            OutputStream out = c.getOutputStream();
            out.write(raw);
            out.close();

            int code = c.getResponseCode();
            String text = readAll(code >= 400
                    ? c.getErrorStream() : c.getInputStream());
            if (code < 200 || code >= 300) {
                throw new ApiException("хранилище ответило HTTP " + code
                        + ": " + clip(text, 200));
            }
        } catch (IOException e) {
            throw new ApiException("не загрузил файл: " + e.getMessage() + NET_HINT);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private JSONObject sendJson(String method, String url, JSONObject body)
            throws ApiException, AuthException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestMethod(method);
            c.setDoOutput(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("Accept-Language",
                    "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
            c.setRequestProperty("Referer", BASE + "/diary");
            c.setRequestProperty("X-Mes-RoleId", roleId());
            c.setRequestProperty("X-mes-subsystem", "familyweb");
            c.setRequestProperty("Profile-Id", studentId());
            c.setRequestProperty("Profile-Type", profileType());
            c.setRequestProperty("Authorization", "Bearer " + token);
            if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
            c.setRequestProperty("Content-Type", "application/json;charset=UTF-8");

            byte[] raw = body == null
                    ? new byte[0]
                    : body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(raw.length);
            OutputStream out = c.getOutputStream();
            out.write(raw);
            out.close();

            int code = c.getResponseCode();
            if (code == 401 || code == 403) {
                throw new AuthException("МЭШ отклонил сессию — нужно войти заново.");
            }
            String text = readAll(code >= 400
                    ? c.getErrorStream() : c.getInputStream());
            if (code < 200 || code >= 300) {
                throw new ApiException(code, "HTTP " + code + ": " + clip(text, 400));
            }
            if (text.trim().isEmpty()) return new JSONObject();
            return new JSONObject(text);
        } catch (IOException e) {
            throw new ApiException("сеть недоступна: " + e.getMessage() + NET_HINT);
        } catch (JSONException e) {
            throw new ApiException("ответ МЭШ не JSON: " + e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String roleId() {
        String role = Cookies.map().get("aupd_current_role");
        if (role != null && role.contains(":")) {
            String r = role.substring(role.indexOf(':') + 1).trim();
            if (r.matches("\\d+")) return r;
        }
        return "1";
    }

    /** Тип профиля для заголовка Profile-Type: у родителя — parent. */
    private static String profileType() {
        return isParentAccount() || isParentRole() ? "parent" : "student";
    }

    // ------------------------------------------------------------ нормализация

    static JSONObject normalizeDay(JSONObject full, JSONArray shortPayload,
                                   JSONArray hwPayload) {
        String dayDate = s(full, "date");

        Map<String, String> topicById = new LinkedHashMap<>();
        Map<String, Object> groupById = new LinkedHashMap<>();
        Map<String, String> groupByTime = new LinkedHashMap<>();

        for (int i = 0; i < shortPayload.length(); i++) {
            JSONObject day = shortPayload.optJSONObject(i);
            if (day == null) continue;
            JSONArray lessons = day.optJSONArray("lessons");
            if (lessons == null) continue;
            for (int j = 0; j < lessons.length(); j++) {
                JSONObject l = lessons.optJSONObject(j);
                if (l == null) continue;
                String key = s(l, "schedule_item_id");
                if (!key.isEmpty()) {
                    topicById.put(key, s(l, "lesson_name").trim());
                    groupById.put(key, l.opt("group_name"));
                }
                String gb = s(l, "group_name");
                if (gb.isEmpty()) gb = s(l, "subject_name");
                String begin = s(l, "begin_time");
                if (!gb.isEmpty() && !groupByTime.containsKey(begin)) {
                    groupByTime.put(begin, gb);
                }
            }
        }

        JSONArray lessons = new JSONArray();
        JSONArray extra = new JSONArray();
        JSONArray activities = full.optJSONArray("activities");
        if (activities != null) {
            for (int i = 0; i < activities.length(); i++) {
                JSONObject a = activities.optJSONObject(i);
                if (a == null) continue;
                if (!"LESSON".equals(s(a, "type"))) continue;
                JSONObject l = a.optJSONObject("lesson");
                if (l == null) continue;

                String itemId = s(l, "schedule_item_id");
                String edu = s(l, "lesson_education_type");
                String topic = topicById.containsKey(itemId)
                        ? topicById.get(itemId) : "";

                Object group = groupById.get(itemId);
                boolean groupOk = group != null && group != JSONObject.NULL
                        && !(group instanceof String && ((String) group).isEmpty());
                if (!groupOk) {
                    String g = groupByTime.get(s(a, "begin_time"));
                    group = g == null ? null : g;
                }

                JSONObject row = new JSONObject();
                put(row, "id", itemId);
                put(row, "edu", edu);
                put(row, "lesson_type", s(l, "lesson_type"));
                put(row, "begin", a.opt("begin_time"));
                put(row, "end", a.opt("end_time"));
                put(row, "subject", s(l, "subject_name"));
                put(row, "group", group);
                put(row, "topic", topic);
                put(row, "room", a.opt("room_number"));
                put(row, "room_name", a.opt("room_name"));
                put(row, "building", a.opt("building_name"));
                put(row, "teacher", teacher(l.optJSONObject("teacher")));

                JSONArray marks = new JSONArray();
                JSONArray lm = l.optJSONArray("marks");
                if (lm != null) {
                    for (int j = 0; j < lm.length(); j++) {
                        JSONObject m = lm.optJSONObject(j);
                        String v = m == null ? "" : str(m.opt("value"));
                        if (v.isEmpty() || "точка".equals(v)) continue;
                        marks.put(v);
                    }
                }
                put(row, "marks", marks);
                put(row, "homework", s(l, "homework").trim());
                put(row, "cancelled", b(l, "is_cancelled"));
                put(row, "replaced", b(l, "replaced"));
                put(row, "virtual", b(l, "is_virtual"));

                if (s(row, "subject").isEmpty() && group instanceof String) {
                    put(row, "subject", group);
                }

                boolean isExtra = "AE".equals(edu) || "EC".equals(edu)
                        || s(l, "subject_name").isEmpty();
                if (isExtra) {
                    put(row, "kind", "extra");
                    extra.put(row);
                } else {
                    put(row, "kind", "lesson");
                    put(row, "n", lessons.length() + 1);
                    lessons.put(row);
                }
            }
        }

        JSONArray homeworks = new JSONArray();
        Map<String, String> beginByEnd = new LinkedHashMap<>();
        Map<String, String> endBySubject = new LinkedHashMap<>();
        for (int i = 0; i < lessons.length(); i++) {
            JSONObject l = lessons.optJSONObject(i);
            if (l == null) continue;
            String b = s(l, "begin");
            String e = s(l, "end");
            if (!b.isEmpty() && !e.isEmpty()) beginByEnd.put(e, b);
            String subj = s(l, "subject").trim();
            if (!subj.isEmpty() && !e.isEmpty() && !endBySubject.containsKey(subj)) {
                endBySubject.put(subj, e);
            }
        }
        List<JSONObject> hwList = new ArrayList<>();
        for (int i = 0; i < hwPayload.length(); i++) {
            JSONObject x = hwPayload.optJSONObject(i);
            if (x == null) continue;
            if (!dayDate.equals(s(x, "date"))) continue;
            JSONObject h = normalizeHomework(x);
            String begin = beginByEnd.get(s(h, "lesson_time"));
            if (begin != null) put(h, "lesson_start", begin);
            // в диапазоне ДЗ время урока часто 00:00 — берём конец урока
            // из расписания этого дня по предмету
            String end = s(h, "lesson_time");
            if (end.isEmpty() || "00:00".equals(end)) {
                String fixed = endBySubject.get(s(h, "subject").trim());
                if (fixed != null) {
                    put(h, "lesson_time", fixed);
                    String b2 = beginByEnd.get(fixed);
                    if (b2 != null) put(h, "lesson_start", b2);
                }
            }
            hwList.add(h);
        }
        Collections.sort(hwList, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return s(a, "lesson_time").compareTo(s(b, "lesson_time"));
            }
        });
        for (JSONObject h : hwList) homeworks.put(h);

        JSONObject out = new JSONObject();
        put(out, "date", dayDate);
        put(out, "summary", s(full, "summary"));
        put(out, "lessons", lessons);
        put(out, "extra", extra);
        put(out, "homeworks", homeworks);
        return out;
    }

    /** Приводит одно ДЗ из ответа МЭШ к внутреннему виду. */
    static JSONObject normalizeHomework(JSONObject x) {
        String text = s(x, "description").trim();
        if (text.isEmpty()) text = s(x, "homework").trim();

        JSONArray files = filesFromHomework(x);
        String ldt = s(x, "lesson_date_time");
        String lessonTime = ldt.length() >= 16 ? ldt.substring(11, 16) : "";

        JSONObject h = new JSONObject();
        put(h, "date", s(x, "date"));
        put(h, "subject", s(x, "subject_name"));
        put(h, "text", text);
        put(h, "done", b(x, "is_done"));
        put(h, "has_teacher_answer", b(x, "has_teacher_answer"));
        JSONArray at = x.optJSONArray("attachments");
        JSONArray mt = x.optJSONArray("materials");
        put(h, "attachments", at == null ? 0 : at.length());
        put(h, "materials", mt == null ? 0 : mt.length());
        put(h, "files", files);
        put(h, "lesson_time", lessonTime);
        put(h, "type", x.opt("type"));
        put(h, "entry", x.opt("homework_entry_id"));

        JSONArray mats = new JSONArray();
        if (mt != null) {
            for (int k = 0; k < mt.length(); k++) {
                JSONObject m = mt.optJSONObject(k);
                if (m == null) continue;
                JSONObject row = new JSONObject();
                put(row, "uuid", m.opt("uuid"));
                put(row, "type", s(m, "type"));
                put(row, "title", s(m, "title"));
                put(row, "action", s(m, "action_name"));
                put(row, "action_id", m.opt("action_id"));
                String fileUrl = "";
                JSONArray urls = m.optJSONArray("urls");
                if (urls != null) {
                    for (int j = 0; j < urls.length(); j++) {
                        Object u = urls.opt(j);
                        if (u instanceof JSONObject) {
                            String t = s((JSONObject) u, "type");
                            if (t.isEmpty() || "file_link".equals(t)) {
                                fileUrl = s((JSONObject) u, "url");
                                if (!fileUrl.isEmpty()) break;
                            }
                        } else if (u instanceof String) {
                            fileUrl = (String) u;
                            break;
                        }
                    }
                }
                put(row, "url", fileUrl);
                mats.put(row);
            }
        }
        put(h, "materials_list", mats);
        return h;
    }

    static JSONArray filesFromHomework(JSONObject hw) {
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();

        JSONArray materials = hw.optJSONArray("materials");
        if (materials != null) {
            for (int i = 0; i < materials.length(); i++) {
                JSONObject m = materials.optJSONObject(i);
                if (m == null) continue;
                String kind = s(m, "type_name");
                if (kind.isEmpty()) kind = s(m, "type");
                JSONArray urls = m.optJSONArray("urls");
                if (urls == null) continue;
                for (int j = 0; j < urls.length(); j++) {
                    Object u = urls.opt(j);
                    if (u instanceof JSONObject) {
                        JSONObject uo = (JSONObject) u;
                        String type = s(uo, "type");
                        if (!type.isEmpty() && !"file_link".equals(type)) continue;
                        String title = s(m, "title");
                        if (title.isEmpty()) title = s(uo, "title");
                        addFile(out, seen, s(uo, "url"), title, kind);
                    } else if (u instanceof String) {
                        addFile(out, seen, (String) u, s(m, "title"), kind);
                    }
                }
            }
        }

        walkAttachments(hw.opt("attachments"), out, seen);
        return out;
    }

    private static void walkAttachments(Object node, JSONArray out, Set<String> seen) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            String url = s(o, "url");
            if (url.isEmpty()) url = s(o, "file_url");
            if (url.isEmpty()) url = s(o, "link");
            String title = s(o, "title");
            if (title.isEmpty()) title = s(o, "name");
            String kind = s(o, "type_name");
            if (kind.isEmpty()) kind = "attachment";
            addFile(out, seen, url, title, kind);
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                Object v = o.opt(it.next());
                if (v instanceof JSONObject || v instanceof JSONArray) {
                    walkAttachments(v, out, seen);
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                walkAttachments(a.opt(i), out, seen);
            }
        }
    }

    private static void addFile(JSONArray out, Set<String> seen, String url,
                                String title, String kind) {
        url = url == null ? "" : url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) return;
        if (!seen.add(url)) return;
        JSONObject f = new JSONObject();
        put(f, "title", title == null ? "" : title.trim());
        put(f, "url", url);
        put(f, "kind", kind == null ? "" : kind.trim());
        out.put(f);
    }

    static String teacher(JSONObject t) {
        if (t == null) return "";
        String last = s(t, "last_name");
        String first = firstChar(s(t, "first_name"));
        String middle = firstChar(s(t, "middle_name"));
        String initials = first;
        if (!middle.isEmpty()) initials += (initials.isEmpty() ? "" : ".") + middle;
        String out = last;
        if (!initials.isEmpty()) out += (out.isEmpty() ? "" : " ") + initials + ".";
        return out.trim();
    }

    // ------------------------------------------------------------ утилиты

    static String s(JSONObject o, String key) {
        if (o == null) return "";
        Object v = o.opt(key);
        return str(v);
    }

    /** Число как строка без хвоста «.0» — для id, которые уходят в URL. */
    static String num(JSONObject o, String key) {
        if (o == null) return "";
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof Number) return String.valueOf(((Number) v).longValue());
        String s = str(v).trim();
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    /** Число из JSON, даже если сервер отдал его строкой. */
    static long numLong(JSONObject o, String key) {
        if (o == null) return 0;
        Object v = o.opt(key);
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(str(v).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static String str(Object v) {
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof String) return (String) v;
        if (v instanceof Double || v instanceof Float) {
            return String.valueOf(((Number) v).doubleValue());
        }
        return String.valueOf(v);
    }

    static boolean b(JSONObject o, String key) {
        return o != null && o.optBoolean(key, false);
    }

    static void put(JSONObject o, String key, Object v) {
        try {
            o.put(key, v == null ? JSONObject.NULL : v);
        } catch (JSONException ignored) {
        }
    }

    static String firstChar(String s) {
        if (s == null || s.isEmpty()) return "";
        int end = s.offsetByCodePoints(0, 1);
        return s.substring(0, end);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // ------------------------------------------------------------ сеть и VPN

    /** Внешний IP и страна выхода — помогает понять, что видит МЭШ. */
    static JSONObject ipInfo() throws ApiException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(
                    "https://ipwho.is/?fields=success,ip,country,country_code")
                    .openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            String body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code != 200) throw new ApiException("HTTP " + code);
            return new JSONObject(body);
        } catch (IOException e) {
            throw new ApiException(e.getMessage() == null ? "нет связи" : e.getMessage());
        } catch (JSONException e) {
            throw new ApiException("ответ не JSON");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Проверка доступа к МЭШ: null — всё хорошо, иначе текст ошибки. */
    static String probe() {
        try {
            fromCookies().profile();
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
