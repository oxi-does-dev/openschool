package ru.sch.diary;

import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cookies живут в CookieManager этого приложения (WebView), не в Chromium. */
final class Cookies {

    private Cookies() {}

    static Map<String, String> map() {
        Map<String, String> out = new LinkedHashMap<>();
        String raw = CookieManager.getInstance().getCookie("https://school.mos.ru");
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return out;
    }

    static String token() { return map().get("aupd_token"); }

    static String header() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map().entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** Формат ~/.cache/sch/cookies.json (host/name/value/path/secure). */
    static int importJson(String text) throws Exception {
        JSONObject root = new JSONObject(text);
        JSONArray arr = root.optJSONArray("cookies");
        if (arr == null) throw new Exception("в файле нет массива cookies");
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        int n = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            String host = c.optString("host", "");
            String name = c.optString("name", "");
            String value = c.optString("value", "");
            String path = c.optString("path", "/");
            boolean secure = c.optBoolean("secure", true);
            if (host.isEmpty() || name.isEmpty()) continue;
            if (path.isEmpty()) path = "/";
            String bare = host.startsWith(".") ? host.substring(1) : host;
            String url = "https://" + bare + path;
            StringBuilder sb = new StringBuilder();
            sb.append(name).append('=').append(value)
              .append("; Domain=").append(host)
              .append("; Path=").append(path);
            if (secure) sb.append("; Secure");
            cm.setCookie(url, sb.toString());
            n++;
        }
        cm.flush();
        return n;
    }
}
