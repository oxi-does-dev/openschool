//! Доступ к МЭШ: cookies с этого компьютера, запросы и нормализация.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use chrono::{Datelike, Local, NaiveDate};
use serde_json::Value;

const BASE: &str = "https://school.mos.ru";
const API: &str = "https://school.mos.ru/api/family/web/v1";
const PARTNERS: &str = "https://school.mos.ru/api/ej/partners/v1";
const UA: &str = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 \
(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

// --------------------------------------------------------------- ошибки

#[derive(Debug, Clone)]
pub enum Error {
    Auth(String),
    Api(String),
}

impl Error {
    pub fn text(&self) -> &str {
        match self {
            Error::Auth(s) | Error::Api(s) => s,
        }
    }
}

impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.text())
    }
}

pub type Result<T> = std::result::Result<T, Error>;

// --------------------------------------------------------------- cookies

#[derive(Debug, Clone)]
pub struct CookieRec {
    pub name: String,
    pub value: String,
    pub host: String,
}

#[derive(Debug, Clone)]
pub struct CookieFile {
    pub cookies: Vec<CookieRec>,
    pub saved_at: Option<i64>,
    pub source: String,
    /// Файл читают другие пользователи системы — стоит сказать об этом.
    pub open_permissions: bool,
}

fn cookie_paths() -> Vec<PathBuf> {
    let mut out = Vec::new();
    if let Ok(p) = std::env::var("SCH_COOKIES") {
        if !p.trim().is_empty() {
            out.push(PathBuf::from(p));
        }
    }
    let home = std::env::var("HOME").unwrap_or_default();
    if !home.is_empty() {
        let h = PathBuf::from(&home);
        out.push(h.join(".cache/sch/cookies.json"));
        out.push(h.join(".config/sch/cookies.json"));
        out.push(h.join("Library/Application Support/sch/cookies.json"));
        out.push(h.join("Library/Caches/sch/cookies.json"));
    }
    out
}

impl CookieFile {
    /// Первый файл cookies, который удалось прочитать.
    pub fn load() -> Result<CookieFile> {
        let mut tried = Vec::new();
        for p in cookie_paths() {
            match std::fs::read_to_string(&p) {
                Ok(text) => match parse_cookie_file(&text) {
                    Ok(mut cf) => {
                        cf.source = p.display().to_string();
                        #[cfg(unix)]
                        {
                            use std::os::unix::fs::PermissionsExt;
                            if let Ok(meta) = std::fs::metadata(&p) {
                                cf.open_permissions =
                                    meta.permissions().mode() & 0o077 != 0;
                            }
                        }
                        return Ok(cf);
                    }
                    Err(e) => tried.push(format!("{}: {}", p.display(), e)),
                },
                Err(_) => tried.push(format!("{}: нет файла", p.display())),
            }
        }
        Err(Error::Auth(format!(
            "не нашёл cookies МЭШ. Сохраните сессию командой `sch login` \
             или положите cookies.json в ~/.config/sch/. Проверено: {}",
            tried.join("; ")
        )))
    }

    pub fn token(&self) -> Option<&str> {
        self.cookies
            .iter()
            .find(|c| c.name == "aupd_token" && c.host.ends_with(".mos.ru"))
            .or_else(|| self.cookies.iter().find(|c| c.name == "aupd_token"))
            .map(|c| c.value.as_str())
    }

    pub fn student_id_hint(&self) -> Option<String> {
        self.cookies
            .iter()
            .find(|c| {
                c.name == "active_student"
                    && c.host == "school.mos.ru"
                    && !c.value.is_empty()
                    && c.value.chars().all(|ch| ch.is_ascii_digit())
            })
            .map(|c| c.value.clone())
    }
}

fn parse_cookie_file(text: &str) -> std::result::Result<CookieFile, String> {
    let v: Value = serde_json::from_str(text).map_err(|e| e.to_string())?;
    let arr = v
        .get("cookies")
        .and_then(Value::as_array)
        .ok_or_else(|| "нет поля cookies".to_string())?;
    let mut cookies = Vec::new();
    for c in arr {
        let name = c.get("name").and_then(Value::as_str).unwrap_or("");
        let value = c.get("value").and_then(Value::as_str).unwrap_or("");
        let host = c.get("host").and_then(Value::as_str).unwrap_or("");
        if name.is_empty() {
            continue;
        }
        cookies.push(CookieRec {
            name: name.to_string(),
            value: value.to_string(),
            host: host.to_string(),
        });
    }
    if cookies.is_empty() {
        return Err("cookies пустые".into());
    }
    Ok(CookieFile {
        cookies,
        saved_at: v.get("saved_at").and_then(Value::as_i64),
        source: String::new(),
        open_permissions: false,
    })
}

// --------------------------------------------------------------- клиент

#[derive(Clone)]
pub struct Client {
    agent: ureq::Agent,
    token: String,
    cookie_header: String,
    student: Arc<Mutex<Option<String>>>,
    /// Роль в МЭШ из cookie aupd_current_role: «ученик:2» -> 2.
    role: String,
}

impl Client {
    pub fn from_local() -> Result<Client> {
        let cf = CookieFile::load()?;
        Client::new(&cf)
    }

    pub fn new(cf: &CookieFile) -> Result<Client> {
        let token = cf
            .token()
            .ok_or_else(|| {
                Error::Auth(
                    "в cookies нет aupd_token. Откройте school.mos.ru в браузере \
                     и сохраните сессию заново."
                        .into(),
                )
            })?
            .to_string();
        if token.chars().any(|c| c.is_control()) {
            return Err(Error::Auth(
                "токен aupd_token повреждён (двоичный мусор вместо текста). \
                 Сохраните сессию заново."
                    .into(),
            ));
        }
        // как браузер: одно имя — одно значение, последнее побеждает
        let mut by_name: BTreeMap<String, String> = BTreeMap::new();
        for c in &cf.cookies {
            // в cookies МЭШ иногда попадает мусор с управляющими символами;
            // такой заголовок нельзя отправить, поэтому печеньку пропускаем
            if c.value.chars().any(|ch| ch.is_control() || !ch.is_ascii()) {
                continue;
            }
            by_name.insert(c.name.clone(), c.value.clone());
        }
        let cookie_header = by_name
            .iter()
            .map(|(n, v)| format!("{}={}", n, v))
            .collect::<Vec<_>>()
            .join("; ");

        let agent = ureq::AgentBuilder::new()
            .timeout(Duration::from_secs(25))
            .user_agent(UA)
            .build();

        let role = by_name
            .get("aupd_current_role")
            .and_then(|v| v.rsplit(':').next())
            .map(|v| v.trim().to_string())
            .filter(|v| !v.is_empty() && v.chars().all(|c| c.is_ascii_digit()))
            .unwrap_or_else(|| "1".to_string());

        Ok(Client {
            agent,
            token,
            cookie_header,
            student: Arc::new(Mutex::new(cf.student_id_hint())),
            role,
        })
    }

    fn get(&self, path: &str, params: &[(&str, String)]) -> Result<Value> {
        let mut url = if path.starts_with("http") {
            path.to_string()
        } else {
            format!("{}{}", API, path)
        };
        if !params.is_empty() {
            url.push(if url.contains('?') { '&' } else { '?' });
            for (i, (k, v)) in params.iter().enumerate() {
                if i > 0 {
                    url.push('&');
                }
                url.push_str(&enc(k));
                url.push('=');
                url.push_str(&enc(v));
            }
        }

        let mut last = String::new();
        for attempt in 0..2 {
            let req = self
                .agent
                .get(&url)
                .set("Accept", "application/json, text/plain, */*")
                .set("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .set("Referer", &format!("{}/diary", BASE))
                .set("X-Mes-RoleId", "1")
                .set("X-mes-subsystem", "familyweb")
                .set("Profile-Id", "")
                .set("Profile-Type", "")
                .set("Authorization", &format!("Bearer {}", self.token))
                .set("Cookie", &self.cookie_header);
            match req.call() {
                Ok(r) => {
                    let body = r.into_string().map_err(|e| Error::Api(e.to_string()))?;
                    return serde_json::from_str(&body)
                        .map_err(|e| Error::Api(format!("ответ МЭШ не JSON: {}", e)));
                }
                Err(ureq::Error::Status(code, _r)) => {
                    if code == 401 || code == 403 {
                        return Err(Error::Auth(
                            "МЭШ отклонил сессию (истекла). Обновите cookies: `sch login`."
                                .into(),
                        ));
                    }
                    // тело ответа не показываем: там могут быть личные данные
                    last = format!("HTTP {}", code);
                    if code < 500 {
                        return Err(Error::Api(last));
                    }
                }
                Err(e) => last = format!("сеть: {}", short_text(&e.to_string(), 160)),
            }
            if attempt == 0 {
                std::thread::sleep(Duration::from_secs(1));
            }
        }
        Err(Error::Api(format!("не дозвонился до МЭШ: {}", last)))
    }

    pub fn profile(&self) -> Result<Profile> {
        let v = self.get("/profile", &[])?;
        let p = v.get("profile").cloned().unwrap_or(Value::Null);
        let child = v
            .get("children")
            .and_then(Value::as_array)
            .and_then(|a| a.first())
            .cloned()
            .unwrap_or(Value::Null);
        let id = s(&p, "id");
        if !id.is_empty() {
            if let Ok(mut g) = self.student.lock() {
                *g = Some(id.clone());
            }
        }
        let school = child.get("school").cloned().unwrap_or(Value::Null);
        let mut prof = Profile {
            name: [s(&p, "last_name"), s(&p, "first_name"), s(&p, "middle_name")]
                .into_iter()
                .filter(|x| !x.is_empty())
                .collect::<Vec<_>>()
                .join(" "),
            class_name: s(&child, "class_name"),
            school: {
                let short = s(&school, "short_name");
                if short.is_empty() {
                    s(&school, "name")
                } else {
                    short
                }
            },
            id: id.clone(),
            birth_date: s(&child, "birth_date"),
            enrollment_date: s(&child, "enrollment_date"),
            school_county: s(&school, "county"),
            school_phone: s(&school, "phone"),
            principal: s(&school, "principal"),
            academic_year: String::new(),
            representatives: array(child.get("representatives"))
                .iter()
                .map(|r| {
                    let name = [s(r, "last_name"), s(r, "first_name"), s(r, "middle_name")]
                        .into_iter()
                        .filter(|x| !x.is_empty())
                        .collect::<Vec<_>>()
                        .join(" ");
                    let kind = s(r, "type");
                    if kind.is_empty() {
                        name
                    } else if name.is_empty() {
                        kind
                    } else {
                        format!("{} — {}", name, kind)
                    }
                })
                .filter(|x| !x.is_empty())
                .collect(),
        };
        if !prof.id.is_empty() {
            if let Ok(v) = self.ej_get_as(&prof.id, "/ej/core/family/v1/academic_years", &[]) {
                for y in array(Some(&v)) {
                    if y.get("current_year").and_then(Value::as_bool).unwrap_or(false) {
                        prof.academic_year = s(&y, "name");
                        break;
                    }
                }
            }
        }
        Ok(prof)
    }

    /// Текущий учебный год (например, «2026 - 2027 год»).
    pub fn academic_year(&self) -> Result<String> {
        let sid = self.student_id()?;
        let v = self.ej_get_as(&sid, "/ej/core/family/v1/academic_years", &[])?;
        for y in array(Some(&v)) {
            if y.get("current_year").and_then(Value::as_bool).unwrap_or(false) {
                return Ok(s(&y, "name"));
            }
        }
        Ok(String::new())
    }

    pub fn student_id(&self) -> Result<String> {
        if let Ok(g) = self.student.lock() {
            if let Some(id) = g.as_ref() {
                if !id.is_empty() {
                    return Ok(id.clone());
                }
            }
        }
        let p = self.profile()?;
        if p.id.is_empty() {
            return Err(Error::Api("не удалось определить student_id".into()));
        }
        Ok(p.id)
    }

    pub fn schedule_day(&self, date: &str) -> Result<Value> {
        let sid = self.student_id()?;
        self.get("/schedule", &[("student_id", sid), ("date", date.into())])
    }

    pub fn schedule_short(&self, dates: &[String]) -> Result<Vec<Value>> {
        let sid = self.student_id()?;
        let v = self.get(
            "/schedule/short",
            &[("student_id", sid), ("dates", dates.join(","))],
        )?;
        Ok(array(v.get("payload")))
    }

    pub fn homeworks(&self, from: &str, to: &str) -> Result<Vec<Value>> {
        let sid = self.student_id()?;
        let v = self.get(
            "/homeworks",
            &[("student_id", sid), ("from", from.into()), ("to", to.into())],
        )?;
        Ok(array(v.get("payload")))
    }

    pub fn marks(&self, from: &str, to: &str) -> Result<Vec<Value>> {
        let sid = self.student_id()?;
        let v = self.get(
            "/marks",
            &[("student_id", sid), ("from", from.into()), ("to", to.into())],
        )?;
        Ok(array(v.get("payload")))
    }

    /// Прямая ссылка на цифровое задание: МЭШ отдаёт её редиректом.
    pub fn homework_launch_url(&self, entry: &str, uuid: &str) -> Result<String> {
        if entry.trim().is_empty() || uuid.trim().is_empty() {
            return Err(Error::Api(
                "у задания нет ссылки для запуска — откройте его в дневнике МЭШ".into(),
            ));
        }
        let sid = self.student_id()?;
        let url = format!(
            "{}/homeworks/launch?homework_entry_id={}&material_id={}",
            PARTNERS,
            enc(entry.trim()),
            enc(uuid.trim())
        );
        let agent = ureq::AgentBuilder::new()
            .timeout(Duration::from_secs(20))
            .redirects(0)
            .user_agent(UA)
            .build();

        let mut last = String::new();
        for attempt in 0..2 {
            let res = agent
                .get(&url)
                .set("Accept", "application/json, text/plain, */*")
                .set("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .set("Referer", &format!("{}/diary", BASE))
                .set("X-Mes-RoleId", &self.role)
                .set("X-mes-subsystem", "familyweb")
                .set("Profile-Id", &sid)
                .set("Profile-Type", "student")
                .set("Authorization", &format!("Bearer {}", self.token))
                .set("Cookie", &self.cookie_header)
                .call();
            match res {
                Ok(r) => {
                    let body = r.into_string().unwrap_or_default();
                    return pick_launch_url(&body, "");
                }
                Err(ureq::Error::Status(code, r)) => {
                    if code == 401 || code == 403 {
                        return Err(Error::Auth(
                            "МЭШ отклонил сессию (истекла). Обновите cookies: `sch login`."
                                .into(),
                        ));
                    }
                    if (300..400).contains(&code) {
                        let location = r.header("location").unwrap_or("").to_string();
                        let body = r.into_string().unwrap_or_default();
                        return pick_launch_url(&body, &location);
                    }
                    last = format!("HTTP {}", code);
                    if code < 500 {
                        break;
                    }
                }
                Err(e) => last = format!("сеть: {}", short_text(&e.to_string(), 120)),
            }
            if attempt == 0 {
                std::thread::sleep(Duration::from_secs(1));
            }
        }
        Err(Error::Api(format!(
            "МЭШ не выдал ссылку на задание ({})",
            last
        )))
    }

    /// Запросы подсистемы ЭЖ: нужны роль, профиль и тип профиля.
    fn ej_get(&self, path: &str, params: &[(&str, String)]) -> Result<Value> {
        let sid = self.student_id()?;
        self.ej_get_as(&sid, path, params)
    }

    fn ej_get_as(&self, sid: &str, path: &str, params: &[(&str, String)]) -> Result<Value> {
        let mut url = if path.starts_with("http") {
            path.to_string()
        } else {
            format!("https://school.mos.ru/api{}", path)
        };
        if !params.is_empty() {
            url.push(if url.contains('?') { '&' } else { '?' });
            for (i, (k, v)) in params.iter().enumerate() {
                if i > 0 {
                    url.push('&');
                }
                url.push_str(&enc(k));
                url.push('=');
                url.push_str(&enc(v));
            }
        }
        let res = self
            .agent
            .get(&url)
            .set("Accept", "application/json, text/plain, */*")
            .set("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .set("Referer", &format!("{}/diary", BASE))
            .set("X-Mes-RoleId", &self.role)
            .set("X-mes-subsystem", "familyweb")
            .set("Profile-Id", sid)
            .set("Profile-Type", "student")
            .set("Authorization", &format!("Bearer {}", self.token))
            .set("Cookie", &self.cookie_header)
            .call();
        match res {
            Ok(r) => {
                let body = r.into_string().map_err(|e| Error::Api(e.to_string()))?;
                serde_json::from_str(&body)
                    .map_err(|e| Error::Api(format!("ответ МЭШ не JSON: {}", e)))
            }
            Err(ureq::Error::Status(code, _)) => {
                if code == 401 || code == 403 {
                    Err(Error::Auth(
                        "МЭШ отклонил сессию (истекла). Обновите cookies: `sch login`."
                            .into(),
                    ))
                } else {
                    Err(Error::Api(format!("HTTP {}", code)))
                }
            }
            Err(e) => Err(Error::Api(format!(
                "сеть: {}",
                short_text(&e.to_string(), 120)
            ))),
        }
    }

    /// Место в классе и средний балл по каждому предмету.
    pub fn ranks(&self, date: &str) -> Result<(Vec<SubjectRank>, ClassRank)> {
        let person = self
            .cookie("student_person_id")
            .filter(|p| !p.is_empty())
            .unwrap_or_else(|| self.student_id().unwrap_or_default());
        let subjects = self.ej_get(
            "/ej/rating/v1/rank/subjects",
            &[("personId", person.clone()), ("date", date.into())],
        )?;
        let class = self.ej_get(
            "/ej/rating/v1/rank/class",
            &[("personId", person.clone()), ("date", date.into())],
        )?;

        let mut out = Vec::new();
        for x in array(Some(&subjects)) {
            let r = x.get("rank").cloned().unwrap_or(Value::Null);
            out.push(SubjectRank {
                subject_id: x.get("subjectId").and_then(Value::as_i64).unwrap_or(0),
                name: s(&x, "subjectName"),
                average: r.get("averageMarkFive").and_then(Value::as_f64),
                place: r.get("rankPlace").and_then(Value::as_i64),
                average30: r.get("averageMarkFive30").and_then(Value::as_f64),
                place30: r.get("rankPlace30").and_then(Value::as_i64),
                trend: s(&r, "trend"),
            });
        }
        out.sort_by(|a, b| a.name.cmp(&b.name));

        let rows = array(Some(&class));
        let mut me = ClassRank {
            total: rows.len(),
            ..Default::default()
        };
        for x in &rows {
            if s(x, "personId") != person {
                continue;
            }
            let r = x.get("rank").cloned().unwrap_or(Value::Null);
            me.place = r.get("rankPlace").and_then(Value::as_i64);
            me.average = r.get("averageMarkFive").and_then(Value::as_f64);
            me.place30 = r.get("rankPlace30").and_then(Value::as_i64);
            me.average30 = r.get("averageMarkFive30").and_then(Value::as_f64);
            me.trend = s(&r, "trend");
            break;
        }
        Ok((out, me))
    }

    /// Итоговые оценки по предмету: периоды, средний балл, целевая.
    pub fn subject_marks(&self, subject_id: i64) -> Result<SubjectMarks> {
        let sid = self.student_id()?;
        let v = self.get(
            "/subject_marks/for_subject",
            &[
                ("student_id", sid),
                ("subject_id", subject_id.to_string()),
            ],
        )?;
        let mut out = SubjectMarks {
            subject_id,
            name: s(&v, "subject_name"),
            average: s(&v, "average"),
            average_all: s(&v, "average_by_all"),
            dynamic: s(&v, "dynamic"),
            year_mark: s(&v, "year_mark"),
            periods: Vec::new(),
        };
        for p in array(v.get("periods")) {
            let target = p
                .get("target")
                .and_then(|t| t.get("value"))
                .map(|x| match x {
                    Value::String(t) => t.clone(),
                    other => other.to_string(),
                })
                .unwrap_or_default();
            out.periods.push(PeriodMarks {
                title: s(&p, "title"),
                start: s(&p, "start"),
                end: s(&p, "end"),
                value: s(&p, "value"),
                fixed: s(&p, "fixed_value"),
                target: target.trim_matches('"').to_string(),
                marks: normalize_marks(&array(p.get("marks"))),
            });
        }
        Ok(out)
    }

    /// Посещаемость: пропуски и опоздания по дням.
    pub fn attendance(&self, from: &str, to: &str) -> Result<Vec<AttendanceDay>> {
        let sid = self.student_id()?;
        let v = self.get(
            "/attendance",
            &[("student_id", sid), ("from", from.into()), ("to", to.into())],
        )?;
        let mut out = Vec::new();
        for d in array(v.get("attendance")) {
            let mut lessons = Vec::new();
            for l in array(d.get("lessons")) {
                lessons.push(AttendanceLesson {
                    subject: s(&l, "subject_name"),
                    reason: s(&l, "description"),
                    health: s(&l, "health_status"),
                });
            }
            out.push(AttendanceDay {
                date: s(&d, "date"),
                summary: s(&d, "summary"),
                description: s(&d, "description"),
                lessons,
            });
        }
        out.sort_by(|a, b| b.date.cmp(&a.date));
        Ok(out)
    }

    /// Подробности оценки: учитель, тема урока, распределение по классу.
    pub fn mark_details(&self, id: &str) -> Result<MarkDetails> {
        let sid = self.student_id()?;
        let v = self.get(&format!("/marks/{}", enc(id)), &[("student_id", sid)])?;
        let mut distribution = Vec::new();
        let mut total_students = 0usize;
        if let Some(cr) = v.get("class_results") {
            total_students = cr
                .get("total_students")
                .and_then(Value::as_u64)
                .unwrap_or(0) as usize;
            for d in array(cr.get("marks_distributions")) {
                let value = d
                    .get("mark_value")
                    .and_then(|v| v.get("five"))
                    .and_then(Value::as_f64)
                    .map(|v| {
                        if (v - v.round()).abs() < 0.005 {
                            format!("{}", v.round() as i64)
                        } else {
                            format!("{:.2}", v).replace('.', ",")
                        }
                    })
                    .unwrap_or_else(|| s(&d, "value"));
                let count = d
                    .get("number_of_students")
                    .and_then(Value::as_u64)
                    .or_else(|| d.get("count").and_then(Value::as_u64))
                    .or_else(|| d.get("students_count").and_then(Value::as_u64))
                    .unwrap_or(0) as usize;
                let percent = d
                    .get("percentage_of_students")
                    .and_then(Value::as_u64)
                    .unwrap_or(0) as usize;
                if !value.is_empty() && count > 0 {
                    distribution.push((value, count, percent));
                }
            }
        }
        distribution.sort_by(|a, b| {
            let av = a.0.replace(',', ".").parse::<f64>().unwrap_or(0.0);
            let bv = b.0.replace(',', ".").parse::<f64>().unwrap_or(0.0);
            bv.partial_cmp(&av).unwrap_or(std::cmp::Ordering::Equal)
        });
        Ok(MarkDetails {
            teacher: teacher(v.get("teacher")),
            topic: s(&v.get("activity").cloned().unwrap_or(Value::Null), "lesson_topic"),
            criteria: s(&v, "criteria"),
            total_students,
            distribution,
        })
    }

    fn cookie(&self, name: &str) -> Option<String> {
        self.cookie_header
            .split("; ")
            .find_map(|pair| pair.strip_prefix(&format!("{}=", name)))
            .map(|v| v.to_string())
    }

    pub fn day(&self, date: &str) -> Result<Day> {
        let full = self.schedule_day(date)?;
        let short = self.schedule_short(&[date.to_string()]).unwrap_or_default();
        let hw = self.homeworks(date, date).unwrap_or_default();
        Ok(normalize_day(&full, &short, &hw))
    }

    pub fn week(&self, start: NaiveDate, days: i64) -> Vec<Day> {
        let dates: Vec<String> = (0..days)
            .map(|i| iso(start + chrono::Duration::days(i)))
            .collect();
        let short = self.schedule_short(&dates).unwrap_or_default();
        let hw = self
            .homeworks(&dates[0], &dates[dates.len() - 1])
            .unwrap_or_default();

        let out: Arc<Mutex<Vec<Option<Day>>>> =
            Arc::new(Mutex::new((0..dates.len()).map(|_| None).collect()));
        let next = Arc::new(Mutex::new(0usize));
        let mut handles = Vec::new();
        let nthreads = dates.len().min(6);
        for _ in 0..nthreads {
            let me = self.clone();
            let dates = dates.clone();
            let short = short.clone();
            let hw = hw.clone();
            let next = next.clone();
            let out = out.clone();
            handles.push(std::thread::spawn(move || loop {
                let i = {
                    let mut g = match next.lock() {
                        Ok(g) => g,
                        Err(_) => return,
                    };
                    let i = *g;
                    *g += 1;
                    i
                };
                if i >= dates.len() {
                    return;
                }
                let day = match me.schedule_day(&dates[i]) {
                    Ok(full) => normalize_day(&full, &short, &hw),
                    Err(e) => Day {
                        date: dates[i].clone(),
                        error: Some(e.text().to_string()),
                        ..Default::default()
                    },
                };
                if let Ok(mut g) = out.lock() {
                    g[i] = Some(day);
                }
            }));
        }
        for h in handles {
            let _ = h.join();
        }

        let mut slots = match out.lock() {
            Ok(g) => g.clone(),
            Err(_) => vec![None; dates.len()],
        };
        slots
            .drain(..)
            .enumerate()
            .map(|(i, d)| {
                d.unwrap_or_else(|| Day {
                    date: dates[i].clone(),
                    error: Some("нет данных".into()),
                    ..Default::default()
                })
            })
            .collect()
    }
}

/// Цифровое задание: МЭШ отдаёт для него uuid и действие «Пройти».
fn is_digital_material(m: &Value) -> bool {
    let uuid = s(m, "uuid");
    if uuid.is_empty() {
        return false;
    }
    let kind = s(m, "type");
    let action = s(m, "action_name").to_lowercase();
    action.contains("пройти") || kind == "test_spec_binding" || kind == "basic_material"
}

// --------------------------------------------------------------- модель

#[derive(Debug, Clone, Default)]
pub struct Profile {
    pub name: String,
    pub class_name: String,
    pub school: String,
    pub id: String,
    pub birth_date: String,
    pub enrollment_date: String,
    pub school_county: String,
    pub school_phone: String,
    pub principal: String,
    pub academic_year: String,
    pub representatives: Vec<String>,
}

#[derive(Debug, Clone, Default)]
pub struct Day {
    pub date: String,
    pub summary: String,
    pub lessons: Vec<Lesson>,
    pub extra: Vec<Lesson>,
    pub homeworks: Vec<Homework>,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Default)]
pub struct Lesson {
    pub n: usize,
    pub begin: String,
    pub end: String,
    pub subject: String,
    pub group: String,
    pub topic: String,
    pub room: String,
    pub teacher: String,
    pub marks: Vec<String>,
    pub homework: String,
}

#[derive(Debug, Clone, Default)]
pub struct Homework {
    pub date: String,
    pub subject: String,
    pub text: String,
    pub done: bool,
    pub has_teacher_answer: bool,
    pub materials: Vec<Material>,
    pub files: Vec<FileRef>,
    pub lesson_time: String,
    pub lesson_start: String,
    pub kind: String,
    /// Номер записи ДЗ в МЭШ — нужен, чтобы открыть ЦДЗ.
    pub entry: String,
}

#[derive(Debug, Clone, Default)]
pub struct Material {
    pub title: String,
    pub kind: String,
    pub digital: bool,
    /// Идентификатор материала для запуска ЦДЗ.
    pub uuid: String,
    /// Что предлагает МЭШ: «Пройти», «Посмотреть» и т.п.
    pub action: String,
    pub urls: Vec<FileRef>,
}

#[derive(Debug, Clone, Default)]
pub struct FileRef {
    pub title: String,
    pub url: String,
    pub kind: String,
}

#[derive(Debug, Clone, Default)]
pub struct Mark {
    pub id: String,
    pub date: String,
    pub subject: String,
    pub subject_id: i64,
    pub value: String,
    pub weight: i64,
    pub comment: String,
    pub control_form: String,
    pub is_exam: bool,
    pub five: Option<f64>,
    pub ten: Option<f64>,
    pub hundred: Option<f64>,
}

// --------------------------------------------------------------- рейтинг и итоговые

#[derive(Debug, Clone, Default)]
pub struct SubjectRank {
    pub subject_id: i64,
    pub name: String,
    pub average: Option<f64>,
    pub place: Option<i64>,
    pub average30: Option<f64>,
    pub place30: Option<i64>,
    pub trend: String,
}

#[derive(Debug, Clone, Default)]
pub struct ClassRank {
    pub place: Option<i64>,
    pub average: Option<f64>,
    pub total: usize,
    pub place30: Option<i64>,
    pub average30: Option<f64>,
    pub trend: String,
}

#[derive(Debug, Clone, Default)]
pub struct PeriodMarks {
    pub title: String,
    pub start: String,
    pub end: String,
    pub value: String,
    pub fixed: String,
    pub target: String,
    pub marks: Vec<Mark>,
}

#[derive(Debug, Clone, Default)]
pub struct SubjectMarks {
    pub subject_id: i64,
    pub name: String,
    pub average: String,
    pub average_all: String,
    pub dynamic: String,
    pub year_mark: String,
    pub periods: Vec<PeriodMarks>,
}

#[derive(Debug, Clone, Default)]
pub struct AttendanceLesson {
    pub subject: String,
    pub reason: String,
    pub health: String,
}

#[derive(Debug, Clone, Default)]
pub struct AttendanceDay {
    pub date: String,
    pub summary: String,
    pub description: String,
    pub lessons: Vec<AttendanceLesson>,
}

#[derive(Debug, Clone, Default)]
pub struct MarkDetails {
    pub teacher: String,
    pub topic: String,
    pub criteria: String,
    pub total_students: usize,
    pub distribution: Vec<(String, usize, usize)>,
}

impl Homework {
    pub fn digital(&self) -> bool {
        self.materials.iter().any(|m| m.digital)
    }

    pub fn digital_titles(&self) -> Vec<&str> {
        self.materials
            .iter()
            .filter(|m| m.digital)
            .map(|m| m.title.as_str())
            .collect()
    }

    pub fn is_control(&self) -> bool {
        let t = self.text.to_lowercase();
        t.contains("контрольн")
            || t.contains("проверочн")
            || t.contains("самостоятельн")
            || t.contains("диктант")
            || t.contains("зачёт")
            || t.contains("зачет")
            || t.contains("тест")
    }
}

impl Lesson {
    pub fn is_control(&self) -> bool {
        let t = self.topic.to_lowercase();
        t.contains("контрольн")
            || t.contains("самостоятельн")
            || t.contains("проверочн")
            || t.contains("диктант")
            || t.contains("зачёт")
            || t.contains("зачет")
            || t.contains("тест")
    }
}

// --------------------------------------------------------------- разбор

fn s(v: &Value, key: &str) -> String {
    v.get(key)
        .map(|x| match x {
            Value::String(t) => t.clone(),
            Value::Null => String::new(),
            other => other.to_string(),
        })
        .unwrap_or_default()
        .trim()
        .to_string()
}

fn b(v: &Value, key: &str) -> bool {
    v.get(key).and_then(Value::as_bool).unwrap_or(false)
}

fn array(v: Option<&Value>) -> Vec<Value> {
    v.and_then(Value::as_array).cloned().unwrap_or_default()
}

fn teacher(v: Option<&Value>) -> String {
    let t = match v {
        Some(t) => t,
        None => return String::new(),
    };
    let last = s(t, "last_name");
    let first: String = s(t, "first_name").chars().take(1).collect();
    let middle: String = s(t, "middle_name").chars().take(1).collect();
    let initials = [first, middle]
        .into_iter()
        .filter(|x| !x.is_empty())
        .map(|x| format!("{}.", x))
        .collect::<Vec<_>>()
        .join("");
    if initials.is_empty() {
        last
    } else {
        format!("{} {}", last, initials)
    }
}

pub fn normalize_day(full: &Value, short_payload: &[Value], hw_payload: &[Value]) -> Day {
    let day_date = s(full, "date");

    let mut topic_by_id: HashMap<String, String> = HashMap::new();
    let mut group_by_id: HashMap<String, Value> = HashMap::new();
    let mut group_by_time: HashMap<String, String> = HashMap::new();
    for day in short_payload {
        for l in array(day.get("lessons")) {
            let key = s(&l, "schedule_item_id");
            if !key.is_empty() {
                topic_by_id.insert(key.clone(), s(&l, "lesson_name"));
                group_by_id.insert(key, l.get("group_name").cloned().unwrap_or(Value::Null));
            }
            let mut gb = s(&l, "group_name");
            if gb.is_empty() {
                gb = s(&l, "subject_name");
            }
            let begin = s(&l, "begin_time");
            if !gb.is_empty() {
                group_by_time.entry(begin).or_insert(gb);
            }
        }
    }

    let mut lessons = Vec::new();
    let mut extra = Vec::new();
    for a in array(full.get("activities")) {
        if s(&a, "type") != "LESSON" {
            continue;
        }
        let l = a.get("lesson").cloned().unwrap_or(Value::Null);
        let item_id = s(&l, "schedule_item_id");
        let edu = s(&l, "lesson_education_type");

        let mut subject = s(&l, "subject_name");
        let group = match group_by_id.get(&item_id) {
            Some(Value::String(t)) if !t.is_empty() => t.clone(),
            _ => group_by_time
                .get(&s(&a, "begin_time"))
                .cloned()
                .unwrap_or_default(),
        };
        if subject.is_empty() {
            subject = group.clone();
        }

        let marks: Vec<String> = array(l.get("marks"))
            .into_iter()
            .map(|m| s(&m, "value"))
            .filter(|v| !v.is_empty() && v != "точка")
            .collect();

        let row = Lesson {
            begin: s(&a, "begin_time"),
            end: s(&a, "end_time"),
            subject,
            group,
            topic: topic_by_id.get(&item_id).cloned().unwrap_or_default(),
            room: s(&a, "room_number"),
            teacher: teacher(l.get("teacher")),
            marks,
            homework: s(&l, "homework"),
            n: 0,
        };

        let no_subject = s(&l, "subject_name").is_empty();
        if edu == "AE" || edu == "EC" || no_subject {
            extra.push(row);
        } else {
            lessons.push(row);
        }
    }
    for (i, l) in lessons.iter_mut().enumerate() {
        l.n = i + 1;
    }

    let mut end_by_subject: HashMap<String, String> = HashMap::new();
    for l in &lessons {
        if !l.subject.is_empty() && !l.end.is_empty() {
            end_by_subject
                .entry(l.subject.clone())
                .or_insert_with(|| l.end.clone());
        }
    }

    let mut homeworks = Vec::new();
    for x in hw_payload {
        if s(x, "date") != day_date {
            continue;
        }
        let text = {
            let d = s(x, "description");
            if d.is_empty() {
                s(x, "homework")
            } else {
                d
            }
        };

        let mut mats = Vec::new();
        for m in array(x.get("materials")) {
            let kind = {
                let t = s(&m, "type_name");
                if t.is_empty() {
                    s(&m, "type")
                } else {
                    t
                }
            };
            let mut urls = Vec::new();
            for u in array(m.get("urls")) {
                let url = s(&u, "url");
                if url.is_empty() {
                    continue;
                }
                let ty = s(&u, "type");
                if !ty.is_empty() && ty != "file_link" {
                    continue;
                }
                let mut title = s(&m, "title");
                if title.is_empty() {
                    title = s(&u, "title");
                }
                urls.push(FileRef {
                    title,
                    url,
                    kind: kind.clone(),
                });
            }
            mats.push(Material {
                title: s(&m, "title"),
                kind,
                digital: is_digital_material(&m),
                uuid: s(&m, "uuid"),
                action: s(&m, "action_name"),
                urls,
            });
        }

        let mut files = Vec::new();
        for m in &mats {
            files.extend(m.urls.iter().cloned());
        }

        let mut lesson_time = {
            let ldt = s(x, "lesson_date_time");
            if ldt.len() >= 16 {
                ldt[11..16].to_string()
            } else {
                String::new()
            }
        };
        let mut lesson_start = String::new();
        if lesson_time.is_empty() || lesson_time == "00:00" {
            if let Some(end) = end_by_subject.get(s(x, "subject_name").trim()) {
                lesson_time = end.clone();
            }
        }
        if let Some(l) = lessons.iter().find(|l| l.end == lesson_time) {
            lesson_start = l.begin.clone();
        }

        homeworks.push(Homework {
            date: day_date.clone(),
            subject: s(x, "subject_name"),
            text,
            done: b(x, "is_done"),
            has_teacher_answer: b(x, "has_teacher_answer"),
            materials: mats,
            files,
            lesson_time,
            lesson_start,
            kind: s(x, "type"),
            entry: s(x, "homework_entry_id"),
        });
    }
    homeworks.sort_by(|a, b| a.lesson_time.cmp(&b.lesson_time));

    Day {
        date: day_date,
        summary: s(full, "summary"),
        lessons,
        extra,
        homeworks,
        error: None,
    }
}

pub fn normalize_homeworks(
    client: &Client,
    payload: &[Value],
    from: &str,
    to: &str,
) -> Vec<Homework> {
    // Для списка ДЗ времени урока в ответе нет: подтягиваем расписание.
    let mut dates: Vec<String> = payload.iter().map(|x| s(x, "date")).collect();
    dates.retain(|d| !d.is_empty());
    dates.sort();
    dates.dedup();

    let mut end_by_date_subject: HashMap<String, String> = HashMap::new();
    let mut begin_by_date_end: HashMap<String, String> = HashMap::new();
    {
        if let Ok(short) = client.schedule_short(&dates) {
            for day in short {
                let dd = s(&day, "date");
                for l in array(day.get("lessons")) {
                    let mut subj = s(&l, "subject_name");
                    if subj.is_empty() {
                        subj = s(&l, "group_name");
                    }
                    let e = s(&l, "end_time");
                    let bgn = s(&l, "begin_time");
                    if !dd.is_empty() && !subj.is_empty() && !e.is_empty() {
                        end_by_date_subject
                            .entry(format!("{}|{}", dd, subj))
                            .or_insert_with(|| e.clone());
                    }
                    if !dd.is_empty() && !bgn.is_empty() && !e.is_empty() {
                        begin_by_date_end.insert(format!("{}|{}", dd, e), bgn);
                    }
                }
            }
        }
    }

    let mut out = Vec::new();
    for x in payload {
        let date = s(x, "date");
        if !date.is_empty() && (date.as_str() < from || date.as_str() > to) {
            continue;
        }
        let subject = s(x, "subject_name");
        let mut lesson_time = {
            let ldt = s(x, "lesson_date_time");
            if ldt.len() >= 16 {
                ldt[11..16].to_string()
            } else {
                String::new()
            }
        };
        if lesson_time.is_empty() || lesson_time == "00:00" {
            if let Some(e) = end_by_date_subject.get(&format!("{}|{}", date, subject)) {
                lesson_time = e.clone();
            }
        }
        let lesson_start = begin_by_date_end
            .get(&format!("{}|{}", date, lesson_time))
            .cloned()
            .unwrap_or_default();

        let text = {
            let d = s(x, "description");
            if d.is_empty() {
                s(x, "homework")
            } else {
                d
            }
        };

        let mut mats = Vec::new();
        for m in array(x.get("materials")) {
            let kind = {
                let t = s(&m, "type_name");
                if t.is_empty() {
                    s(&m, "type")
                } else {
                    t
                }
            };
            let mut urls = Vec::new();
            for u in array(m.get("urls")) {
                let url = s(&u, "url");
                if url.is_empty() {
                    continue;
                }
                let ty = s(&u, "type");
                if !ty.is_empty() && ty != "file_link" {
                    continue;
                }
                let mut title = s(&m, "title");
                if title.is_empty() {
                    title = s(&u, "title");
                }
                urls.push(FileRef {
                    title,
                    url,
                    kind: kind.clone(),
                });
            }
            mats.push(Material {
                title: s(&m, "title"),
                kind,
                digital: is_digital_material(&m),
                uuid: s(&m, "uuid"),
                action: s(&m, "action_name"),
                urls,
            });
        }
        let mut files = Vec::new();
        for m in &mats {
            files.extend(m.urls.iter().cloned());
        }

        out.push(Homework {
            date,
            subject,
            text,
            done: b(x, "is_done"),
            has_teacher_answer: b(x, "has_teacher_answer"),
            materials: mats,
            files,
            lesson_time,
            lesson_start,
            kind: s(x, "type"),
            entry: s(x, "homework_entry_id"),
        });
    }
    out.sort_by(|a, b| (a.date.clone(), a.lesson_time.clone()).cmp(&(b.date.clone(), b.lesson_time.clone())));
    out
}

pub fn normalize_marks(payload: &[Value]) -> Vec<Mark> {
    let mut out = Vec::new();
    for m in payload {
        let id = s(m, "id");
        let value = s(m, "value");
        if value.is_empty() {
            continue;
        }
        let (mut five, mut ten, mut hundred) = (None, None, None);
        if let Some(g) = array(m.get("values"))
            .first()
            .and_then(|v| v.get("grade"))
        {
            five = g.get("five").and_then(Value::as_f64);
            ten = g.get("ten").and_then(Value::as_f64);
            hundred = g.get("hundred").and_then(Value::as_f64);
        }
        out.push(Mark {
            id,
            date: s(m, "date"),
            subject: s(m, "subject_name"),
            subject_id: m.get("subject_id").and_then(Value::as_i64).unwrap_or(0),
            value,
            weight: m.get("weight").and_then(Value::as_i64).unwrap_or(1),
            comment: s(m, "comment"),
            control_form: s(m, "control_form_name"),
            is_exam: b(m, "is_exam"),
            five,
            ten,
            hundred,
        });
    }
    out.sort_by(|a, b| b.date.cmp(&a.date));
    out
}

// --------------------------------------------------------------- даты

pub fn today() -> NaiveDate {
    Local::now().date_naive()
}

pub fn iso(d: NaiveDate) -> String {
    d.format("%Y-%m-%d").to_string()
}

pub fn parse_iso(s: &str) -> Option<NaiveDate> {
    NaiveDate::parse_from_str(s, "%Y-%m-%d").ok()
}

pub fn shift(d: NaiveDate, days: i64) -> NaiveDate {
    d + chrono::Duration::days(days)
}

pub const WEEKDAYS_SHORT: [&str; 7] = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
pub const WEEKDAYS_FULL: [&str; 7] = [
    "понедельник",
    "вторник",
    "среда",
    "четверг",
    "пятница",
    "суббота",
    "воскресенье",
];
pub const MONTHS: [&str; 12] = [
    "января",
    "февраля",
    "марта",
    "апреля",
    "мая",
    "июня",
    "июля",
    "августа",
    "сентября",
    "октября",
    "ноября",
    "декабря",
];

pub fn dow_index(d: NaiveDate) -> usize {
    d.weekday().num_days_from_monday() as usize
}

pub fn human_date(d: NaiveDate) -> String {
    format!("{} {}", d.day(), MONTHS[(d.month0()) as usize])
}

pub fn human_date_short(d: NaiveDate) -> String {
    format!("{} · {} {}", WEEKDAYS_SHORT[dow_index(d)], d.day(), MONTHS[(d.month0()) as usize])
}

/// Понедельник недели, в которую попадает дата.
pub fn week_start(d: NaiveDate) -> NaiveDate {
    d - chrono::Duration::days(dow_index(d) as i64)
}

pub fn plural(n: usize, one: &str, few: &str, many: &str) -> String {
    let n10 = n % 10;
    let n100 = n % 100;
    let word = if n10 == 1 && n100 != 11 {
        one
    } else if (2..=4).contains(&n10) && !(12..=14).contains(&n100) {
        few
    } else {
        many
    };
    format!("{} {}", n, word)
}

/// Открываем только ссылки МЭШ: остальное может увести не туда.
pub fn allowed_url(url: &str) -> bool {
    let rest = match url.strip_prefix("https://") {
        Some(r) => r,
        None => return false,
    };
    let host = rest.split(['/', '?', '#']).next().unwrap_or("").to_lowercase();
    host == "mos.ru" || host.ends_with(".mos.ru")
}

/// В ответе на запуск ЦДЗ приходит либо адрес в теле, либо Location.
fn pick_launch_url(body: &str, location: &str) -> Result<String> {
    let body = body.trim();
    let url = if body.starts_with("http") {
        body.lines().next().unwrap_or("").trim().to_string()
    } else {
        location.trim().to_string()
    };
    if url.is_empty() {
        return Err(Error::Api(
            "МЭШ не выдал ссылку на задание — откройте его в дневнике МЭШ".into(),
        ));
    }
    if !allowed_url(&url) {
        return Err(Error::Api(format!(
            "МЭШ предложил ссылку вне mos.ru ({}), не открываю",
            url_host(&url)
        )));
    }
    Ok(url)
}

pub fn url_host(url: &str) -> String {
    url.split("://")
        .nth(1)
        .unwrap_or(url)
        .split(['/', '?', '#'])
        .next()
        .unwrap_or("")
        .to_string()
}

/// Короткий текст без управляющих символов — для сообщений об ошибках.
pub fn short_text(text: &str, max: usize) -> String {
    let flat: String = text
        .chars()
        .map(|ch| if ch.is_control() { ' ' } else { ch })
        .collect();
    let flat = flat.split_whitespace().collect::<Vec<_>>().join(" ");
    if flat.chars().count() <= max {
        flat
    } else {
        let cut: String = flat.chars().take(max).collect();
        format!("{}…", cut.trim_end())
    }
}

pub fn enc(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char)
            }
            _ => out.push_str(&format!("%{:02X}", b)),
        }
    }
    out
}

// --------------------------------------------------------------- поиск

pub struct SearchHit {
    pub title: String,
    pub subtitle: String,
    pub hw: Option<Homework>,
    pub mark: Option<Mark>,
}

pub fn search(
    homeworks: &[Homework],
    marks: &[Mark],
    query: &str,
) -> Vec<SearchHit> {
    let q = query.trim().to_lowercase();
    if q.is_empty() {
        return Vec::new();
    }
    let mut out = Vec::new();
    for h in homeworks {
        let hay = format!("{} {} {}", h.subject, h.text, h.kind).to_lowercase();
        if hay.contains(&q) {
            out.push(SearchHit {
                title: if h.subject.is_empty() {
                    "Задание".into()
                } else {
                    h.subject.clone()
                },
                subtitle: format!(
                    "{} · {}",
                    parse_iso(&h.date).map(human_date).unwrap_or_else(|| h.date.clone()),
                    first_line(&h.text, 90)
                ),
                hw: Some(h.clone()),
                mark: None,
            });
        }
    }
    for m in marks {
        let hay = format!("{} {} {}", m.subject, m.comment, m.control_form).to_lowercase();
        if hay.contains(&q) {
            out.push(SearchHit {
                title: format!("{} · {}", m.subject, m.value),
                subtitle: format!(
                    "{}{}",
                    parse_iso(&m.date).map(human_date).unwrap_or_else(|| m.date.clone()),
                    if m.comment.is_empty() {
                        String::new()
                    } else {
                        format!(" · {}", first_line(&m.comment, 80))
                    }
                ),
                hw: None,
                mark: Some(m.clone()),
            });
        }
    }
    out.sort_by(|a, b| b.subtitle.cmp(&a.subtitle));
    out.truncate(120);
    out
}

pub fn first_line(text: &str, max: usize) -> String {
    let flat = text.replace('\n', " ").replace('\r', " ");
    let flat = flat.split_whitespace().collect::<Vec<_>>().join(" ");
    if flat.chars().count() <= max {
        flat
    } else {
        let cut: String = flat.chars().take(max).collect();
        format!("{}…", cut.trim_end())
    }
}

// Неиспользуемые, но полезные мелочи держим рядом с разбором.
pub fn _dedupe_subjects(mut v: Vec<String>) -> Vec<String> {
    v.sort();
    v.dedup();
    v
}

#[allow(dead_code)]
fn _unused(_: HashSet<String>) {}
