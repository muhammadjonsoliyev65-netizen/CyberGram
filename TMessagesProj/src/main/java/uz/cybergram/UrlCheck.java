package uz.cybergram;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Saytni server orqali (AI'siz) tekshirish: domen yoshi, yo'naltirishlar, sahifadagi karta/parol/SMS formalari.
 * Sayt foydalanuvchi telefonidan emas, serverdan ochiladi. Serverga faqat havola (maxfiy qismlarisiz) yuboriladi.
 */
public final class UrlCheck {

    public interface Callback {
        /** @param info server javobi yoki null (xato/vaqt tugadi) */
        void onResult(JSONObject info);
    }

    /** Sekin internetda ham uzoq kutmaslik uchun: 5 soniyadan keyin oflayn natija bilan davom etiladi. */
    private static final long TIMEOUT_MS = 5000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // Takroriy havolalar uchun xotira: bir xil havola 6 soat ichida qayta so'ralmaydi
    private static final long MEM_TTL = 6L * 60 * 60 * 1000;
    private static final java.util.Map<String, Object[]> MEM = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, Object[]>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Object[]> e) {
                    return size() > 300;
                }
            });

    // Uzoq yillik, toza saytlar (masalan kun.uz) 7 kun davomida serverga umuman so'ralmaydi — darhol ochiladi
    private static final long EST_TTL = 7L * 24 * 60 * 60 * 1000;
    private static final String PREFS = "cybergram_sites";

    /** Bu sayt yaqinda "uzoq yillik va toza" deb topilganmi (serverga so'ramasdan). */
    public static boolean isKnownEstablished(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        try {
            long t = prefs().getLong("est_" + host.toLowerCase(java.util.Locale.ROOT), 0);
            return t > 0 && System.currentTimeMillis() - t < EST_TTL;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void rememberEstablished(String host) {
        if (host == null || host.isEmpty()) {
            return;
        }
        try {
            prefs().edit().putLong("est_" + host.toLowerCase(java.util.Locale.ROOT), System.currentTimeMillis()).apply();
        } catch (Throwable ignore) {
        }
    }

    private static android.content.SharedPreferences prefs() {
        return org.telegram.messenger.ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    private UrlCheck() {
    }

    /** Natija doim asosiy oqimda va faqat bir marta qaytadi (eng ko'pi 5 soniyada). */
    public static void check(String url, final Callback callback) {
        final String key = Redact.url(url);
        Object[] hit = MEM.get(key);
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < MEM_TTL) {
            final JSONObject cached = (JSONObject) hit[1];
            MAIN.post(() -> callback.onResult(cached));
            return;
        }
        final AtomicBoolean done = new AtomicBoolean(false);
        long timeout = TIMEOUT_MS;
        try {
            if (org.telegram.messenger.ApplicationLoader.isConnectionSlow()) {
                timeout = 3500; // 2G/juda sekin internet — kamroq kutamiz
            }
        } catch (Throwable ignore) {
        }
        MAIN.postDelayed(() -> {
            if (done.compareAndSet(false, true)) {
                callback.onResult(null);
            }
        }, timeout);
        CyberApiClient.post("/api/urlinfo", key, new CyberApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONObject good = response != null && response.optBoolean("ok") ? response : null;
                if (good != null) {
                    // kech kelgan javob ham keyingi safar uchun saqlanadi
                    MEM.put(key, new Object[]{System.currentTimeMillis(), good});
                }
                if (done.compareAndSet(false, true)) {
                    callback.onResult(good);
                }
            }

            @Override
            public void onError(String message) {
                if (done.compareAndSet(false, true)) {
                    callback.onResult(null);
                }
            }
        });
    }

    /**
     * Server natijasini tekshiruv natijasiga qo'shadi.
     * @return true bo'lsa, sayt uzoq vaqtdan beri mavjud va xavfli belgisi yo'q (notanish bo'lsa ham so'rash shart emas)
     */
    public static boolean apply(JSONObject info, LocalRules.Result result) {
        if (info == null) {
            return false;
        }
        final String host = info.optString("host", "");
        final boolean trusted = TrustedSites.isTrusted(host);
        int age = info.isNull("ageDays") ? -1 : info.optInt("ageDays", -1);
        String created = info.isNull("created") ? "" : info.optString("created", "");
        JSONObject page = info.optJSONObject("page");

        boolean card = false, password = false, sms = false, download = false, tgLogin = false, redirected = false;
        String brands = "";
        if (page != null && !page.has("error")) {
            card = page.optBoolean("card");
            password = page.optBoolean("password");
            sms = page.optBoolean("sms");
            download = page.optBoolean("download");
            tgLogin = page.optBoolean("telegramLogin");
            JSONArray b = page.optJSONArray("brands");
            if (b != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < b.length(); i++) {
                    String name = b.optString(i);
                    if ("telegram".equals(name)) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(name);
                }
                brands = sb.toString();
            }

            // Yo'naltirish: oxirgi sayt ham tekshiriladi
            String finalHost = page.optString("finalHost", host);
            if (!finalHost.isEmpty() && !finalHost.equals(host)) {
                redirected = true;
                result.note(L.t("Havola boshqa saytga yo'naltiradi: ", "Ссылка перенаправляет на другой сайт: ") + finalHost);
                LocalRules.Result fin = LocalRules.analyze("https://" + finalHost, null);
                if (fin.verdict != SafetyVerdict.SAFE) {
                    for (String reason : fin.reasons) {
                        result.add(fin.verdict, reason);
                    }
                }
            }
        }

        if (trusted) {
            return true;
        }

        boolean asksSecrets = card || password || sms;
        if (tgLogin) {
            result.add(SafetyVerdict.DANGEROUS, L.t(
                    "Sahifa Telegram'ga kirish oynasiga o'xshatilgan va telefon raqami yoki SMS kod so'raydi. Telegram hech qachon tashqi saytda kod so'ramaydi — bu akkauntni o'g'irlash usuli.",
                    "Страница похожа на вход в Telegram и просит номер телефона или SMS-код. Telegram никогда не просит код на сторонних сайтах — это способ угнать аккаунт."));
        }
        if (!brands.isEmpty() && asksSecrets) {
            result.add(SafetyVerdict.DANGEROUS, L.t(
                    "Sahifada \"" + brands + "\" nomi bor va karta, parol yoki SMS kod so'raladi, lekin bu ularning rasmiy sayti emas.",
                    "На странице есть название «" + brands + "» и запрашиваются карта, пароль или SMS-код, но это не их официальный сайт."));
        } else if (card) {
            result.add(SafetyVerdict.SUSPICIOUS, L.t("Sahifa karta ma'lumotlarini so'raydi. Faqat rasmiy do'kon va xizmatlarda kiriting.",
                    "Страница запрашивает данные карты. Вводите их только в официальных магазинах и сервисах."));
        } else if (sms && !tgLogin) {
            result.add(SafetyVerdict.SUSPICIOUS, L.t("Sahifa SMS kod so'raydi. SMS kodni hech kimga va hech qayerga kiritmang, agar o'zingiz so'ramagan bo'lsangiz.",
                    "Страница просит SMS-код. Не вводите код, если сами его не запрашивали."));
        }
        if (download) {
            result.add(SafetyVerdict.DANGEROUS, L.t("Havola dastur faylini yuklab beradi — notanish joydan dastur o'rnatmang.",
                    "Ссылка скачивает файл программы — не устанавливайте программы из неизвестных источников."));
        }
        if (age >= 0 && age < 30) {
            String when = age == 0 ? L.t("bugun", "сегодня") : age + L.t(" kun oldin", " дн. назад");
            boolean otherSigns = asksSecrets || !brands.isEmpty() || result.verdict != SafetyVerdict.SAFE;
            result.add(age < 7 && otherSigns ? SafetyVerdict.DANGEROUS : SafetyVerdict.SUSPICIOUS, "\"" + info.optString("domain", host) + "\" — "
                    + L.t("sayt atigi " + when + " ochilgan" + (created.isEmpty() ? "" : " (" + created + ")") + ". Firibgar saytlarning ko'pchiligi yangi ochiladi va tez yopiladi.",
                    "сайт создан всего " + when + (created.isEmpty() ? "" : " (" + created + ")") + ". Большинство мошеннических сайтов новые и быстро закрываются."));
        } else if (age >= 365 && !redirected) {
            result.note(L.t("Sayt " + (age / 365) + " yildan beri mavjud.", "Сайт существует " + (age / 365) + " г."));
        }
        return age >= 365 && !asksSecrets && brands.isEmpty() && !download && !tgLogin
                && result.verdict == SafetyVerdict.SAFE;
    }
}
