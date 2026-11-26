package uz.cybergram;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Google Safe Browsing (v4 Lookup API) orqali havolalarni onlayn tekshirish.
 */
public final class SafeBrowsingClient {

    public interface Callback {
        /**
         * @param threats xavfli deb topilgan havolalar: normalize(url) -> tahdid turi
         * @param ok      onlayn tekshiruv muvaffaqiyatli bajarildimi
         */
        void onResult(Map<String, String> threats, boolean ok);
    }

    private static final String ENDPOINT = "https://safebrowsing.googleapis.com/v4/threatMatches:find?key=";
    private static final String NO_THREAT = "";

    /** Google talabi: ogohlantirishda manba va Safe Browsing Advisory havolasi ko'rsatiladi. */
    public static final String ADVISORY = "\n\nAdvisory provided by Google: https://developers.google.com/safe-browsing/v4/advisory";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, String> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 200;
                }
            });

    private SafeBrowsingClient() {
    }

    public static boolean isConfigured() {
        return !TextUtils.isEmpty(BuildConfig.SAFE_BROWSING_KEY);
    }

    /** Natija doim asosiy (UI) oqimda qaytadi. */
    public static void check(List<String> urls, final Callback callback) {
        final List<String> toCheck = new ArrayList<>();
        final Map<String, String> threats = new HashMap<>();
        for (String url : urls) {
            String n = normalize(url);
            if (n == null) {
                continue;
            }
            String cached = CACHE.get(n);
            if (cached == null) {
                if (!toCheck.contains(n)) {
                    toCheck.add(n);
                }
            } else if (!cached.isEmpty()) {
                threats.put(n, cached);
            }
        }
        if (toCheck.isEmpty() || !isConfigured()) {
            final boolean ok = toCheck.isEmpty();
            MAIN.post(() -> callback.onResult(threats, ok));
            return;
        }
        EXECUTOR.execute(() -> {
            boolean ok = false;
            try {
                Map<String, String> found = query(toCheck);
                for (String u : toCheck) {
                    String t = found.get(u);
                    CACHE.put(u, t != null ? t : NO_THREAT);
                }
                threats.putAll(found);
                ok = true;
            } catch (Exception ignore) {
                // Internet yo'q yoki kalit noto'g'ri — faqat oflayn natija ishlatiladi
            }
            final boolean finalOk = ok;
            MAIN.post(() -> callback.onResult(threats, finalOk));
        });
    }

    /** Faqat veb-havolalar tekshiriladi; sxemasiz manzilga http:// qo'shiladi. */
    public static String normalize(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim();
        String lower = u.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return u;
        }
        if (lower.contains("://") || lower.startsWith("tg:") || lower.startsWith("mailto:")
                || lower.startsWith("tel:") || lower.startsWith("intent:")) {
            return null;
        }
        return "http://" + u;
    }

    public static String describe(String threatType) {
        if (threatType == null) {
            return L.t("xavfli sayt", "опасный сайт");
        }
        switch (threatType) {
            case "MALWARE":
                return L.t("zararli dastur tarqatadi", "распространяет вредоносные программы");
            case "SOCIAL_ENGINEERING":
                return L.t("fishing — ma'lumotlaringizni o'g'irlashga urinadi", "фишинг — пытается украсть ваши данные");
            case "UNWANTED_SOFTWARE":
                return L.t("keraksiz yoki zararli dastur o'rnatadi", "устанавливает нежелательные или вредоносные программы");
            case "POTENTIALLY_HARMFUL_APPLICATION":
                return L.t("xavfli ilova tarqatadi", "распространяет опасные приложения");
            default:
                return threatType;
        }
    }

    private static Map<String, String> query(List<String> urls) throws Exception {
        JSONObject client = new JSONObject();
        client.put("clientId", "cybergram");
        client.put("clientVersion", "1.0");

        JSONArray entries = new JSONArray();
        for (String u : urls) {
            JSONObject e = new JSONObject();
            e.put("url", u);
            entries.put(e);
        }

        JSONObject info = new JSONObject();
        info.put("threatTypes", new JSONArray(Arrays.asList(
                "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION")));
        info.put("platformTypes", new JSONArray(Collections.singletonList("ANY_PLATFORM")));
        info.put("threatEntryTypes", new JSONArray(Collections.singletonList("URL")));
        info.put("threatEntries", entries);

        JSONObject body = new JSONObject();
        body.put("client", client);
        body.put("threatInfo", info);

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT + BuildConfig.SAFE_BROWSING_KEY).openConnection();
        try {
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(3500);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            OutputStream os = connection.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            if (connection.getResponseCode() != 200) {
                throw new Exception("HTTP " + connection.getResponseCode());
            }
            InputStream is = connection.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = is.read(buffer)) > 0) {
                bos.write(buffer, 0, n);
            }
            is.close();

            Map<String, String> out = new HashMap<>();
            JSONObject response = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
            JSONArray matches = response.optJSONArray("matches");
            if (matches != null) {
                for (int i = 0; i < matches.length(); i++) {
                    JSONObject m = matches.getJSONObject(i);
                    JSONObject threat = m.optJSONObject("threat");
                    if (threat != null) {
                        out.put(threat.optString("url"), m.optString("threatType", "MALWARE"));
                    }
                }
            }
            return out;
        } finally {
            connection.disconnect();
        }
    }
}
