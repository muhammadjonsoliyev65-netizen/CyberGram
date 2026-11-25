package uz.cybergram;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "📢 Rasmiy kanallar" ro'yxati. Ro'yxat serverdan olinadi (/api/channels) va telefonda saqlanadi,
 * shuning uchun uni ilovani yangilamasdan o'zgartirish mumkin. Internet bo'lmasa — saqlangan yoki ichki ro'yxat.
 */
public final class OfficialChannels {

    private static final String PREFS = "cybergram";
    private static final String KEY_JSON = "channels_json";
    private static final String KEY_TIME = "channels_time";
    private static final long REFRESH_MS = 6L * 60 * 60 * 1000; // 6 soatda bir yangilanadi

    private static String defaultTips() {
        return L.t("• Rasmiy hisoblar nomi yonida ko'k ✓ belgisi bo'ladi. Nomi o'xshash, lekin belgisi yo'q kanallar soxta bo'lishi mumkin.\n"
                        + "• Telegram xodimlari hech qachon birinchi bo'lib yozib, SMS kod yoki parol so'ramaydi.\n"
                        + "• Rasmiy xizmat xabarlari faqat \"Telegram\" (✓) hisobidan keladi.\n"
                        + "• \"Bepul Premium\", \"sovrin\" va'da qiladigan kanallarga ishonmang.",
                "• У официальных аккаунтов рядом с названием есть синяя галочка ✓. Похожие каналы без неё могут быть поддельными.\n"
                        + "• Сотрудники Telegram никогда не пишут первыми и не просят SMS-код или пароль.\n"
                        + "• Официальные служебные сообщения приходят только от аккаунта «Telegram» (✓).\n"
                        + "• Не верьте каналам, обещающим «бесплатный Premium» или «призы».");
    }

    /** {username, o'zbekcha tavsif, ruscha tavsif} */
    private static final String[][] DEFAULT = {
            {"CyberGramNews", "CyberGram — ilova yangiliklari", "CyberGram — новости приложения"},
            {"telegram", "Telegram News — rasmiy yangiliklar", "Telegram News — официальные новости"},
            {"TelegramTips", "Telegram Tips — foydali maslahatlar", "Telegram Tips — полезные советы"},
            {"durov", "Pavel Durov — Telegram asoschisi", "Павел Дуров — основатель Telegram"},
            {"BotNews", "Telegram Bot API yangiliklari", "Новости Telegram Bot API"},
            {"SpamBot", "Spam cheklovlarini tekshirish (rasmiy bot)", "Проверка спам-ограничений (официальный бот)"},
            {"BotFather", "Bot yaratish (rasmiy bot)", "Создание ботов (официальный бот)"},
            {"PremiumBot", "Telegram Premium (rasmiy bot)", "Telegram Premium (официальный бот)"},
    };

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private OfficialChannels() {
    }

    private static final class Data {
        final ArrayList<String[]> channels = new ArrayList<>();
        String tips = defaultTips();
    }

    public static void show(final Context context) {
        final Data data = load();
        final int n = data.channels.size();
        final CharSequence[] items = new CharSequence[n + 1];
        for (int i = 0; i < n; i++) {
            items[i] = "@" + data.channels.get(i)[0] + "\n" + data.channels.get(i)[1];
        }
        items[n] = L.t("💡 Soxta kanalni qanday ajratish mumkin?", "💡 Как распознать поддельный канал?");

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(L.t("📢 Rasmiy kanallar", "📢 Официальные каналы"));
        builder.setItems(items, (dialog, which) -> {
            dialog.dismiss();
            if (which < n) {
                Browser.openUrl(context, "https://t.me/" + data.channels.get(which)[0]);
            } else {
                AlertDialog.Builder tips = new AlertDialog.Builder(context);
                tips.setTitle(L.t("💡 Soxta kanallar", "💡 Поддельные каналы"));
                tips.setMessage(data.tips);
                tips.setPositiveButton(L.t("Tushunarli", "Понятно"), (d, w) -> d.dismiss());
                tips.show();
            }
        });
        builder.show();

        refreshIfNeeded();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Data load() {
        try {
            String json = prefs().getString(KEY_JSON, null);
            if (json != null) {
                Data d = parse(json);
                if (d != null && !d.channels.isEmpty()) {
                    return d;
                }
            }
        } catch (Exception ignore) {
        }
        Data d = new Data();
        boolean ru = L.ru();
        for (String[] c : DEFAULT) {
            d.channels.add(new String[]{c[0], ru ? c[2] : c[1]});
        }
        return d;
    }

    private static Data parse(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        JSONArray arr = obj.getJSONArray("channels");
        Data d = new Data();
        boolean ru = L.ru();
        for (int i = 0; i < arr.length() && i < 30; i++) {
            JSONObject c = arr.getJSONObject(i);
            String username = c.optString("username", "").replace("@", "").trim();
            String title = c.optString("title", "").trim();
            if (ru) {
                String titleRu = c.optString("title_ru", "").trim();
                if (!titleRu.isEmpty()) {
                    title = titleRu;
                }
            }
            // Faqat to'g'ri Telegram username (havola yoki boshqa narsa emas)
            if (username.matches("[A-Za-z0-9_]{4,32}")) {
                d.channels.add(new String[]{username, title.isEmpty() ? username : title});
            }
        }
        String tips = ru && !obj.optString("tips_ru", "").trim().isEmpty() ? obj.optString("tips_ru", "") : obj.optString("tips", "");
        if (!tips.trim().isEmpty()) {
            d.tips = tips;
        }
        return d;
    }

    private static void refreshIfNeeded() {
        try {
            if (System.currentTimeMillis() - prefs().getLong(KEY_TIME, 0) < REFRESH_MS) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String base = BuildConfig.CYBERGRAM_API_URL;
                if (base == null || base.isEmpty()) {
                    return;
                }
                if (base.endsWith("/")) {
                    base = base.substring(0, base.length() - 1);
                }
                connection = (HttpURLConnection) new URL(base + "/api/channels").openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                if (connection.getResponseCode() != 200) {
                    return;
                }
                InputStream is = connection.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int r;
                while ((r = is.read(buf)) > 0 && bos.size() < 64 * 1024) {
                    bos.write(buf, 0, r);
                }
                is.close();
                String json = new String(bos.toByteArray(), "UTF-8");
                Data d = parse(json);
                if (d != null && !d.channels.isEmpty()) {
                    prefs().edit().putString(KEY_JSON, json).putLong(KEY_TIME, System.currentTimeMillis()).apply();
                }
            } catch (Exception ignore) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}