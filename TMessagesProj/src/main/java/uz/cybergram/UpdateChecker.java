package uz.cybergram;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CyberGram'ning o'z yangilanish xabari.
 * Server (/api/version) eng so'nggi versiya raqamini beradi; u ilovadagidan katta bo'lsa — oyna chiqadi.
 *
 * YANGI VERSIYA CHIQARGANDA: pastdagi VERSION_CODE va VERSION_NAME ni oshiring,
 * keyin serverdagi api/version.js da ham xuddi shu raqamlarni yozing.
 */
public final class UpdateChecker {

    /** Ilovaning CyberGram versiyasi (Telegram versiyasidan alohida). */
    public static final int VERSION_CODE = 1;
    public static final String VERSION_NAME = "1.0";

    private static final String PREFS = "cybergram";
    private static final String KEY_LAST_CHECK = "update_last_check";
    private static final String KEY_SNOOZED = "update_snoozed_code";
    private static final long CHECK_EVERY_MS = 6L * 60 * 60 * 1000;
    private static final String DEFAULT_URL = "https://t.me/CyberGramNews";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static boolean checkedThisLaunch;

    private UpdateChecker() {
    }

    /** Ilova ochilganda chaqiriladi. Tez-tez tekshirmaydi (6 soatda bir). */
    public static void check(final Context context) {
        if (checkedThisLaunch || context == null) {
            return;
        }
        checkedThisLaunch = true;
        final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_EVERY_MS) {
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
                connection = (HttpURLConnection) new URL(base + "/api/version").openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                if (connection.getResponseCode() != 200) {
                    return;
                }
                InputStream is = connection.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int r;
                while ((r = is.read(buf)) > 0 && bos.size() < 16 * 1024) {
                    bos.write(buf, 0, r);
                }
                is.close();
                JSONObject obj = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

                final int latest = obj.optInt("latestCode", 0);
                final boolean force = obj.optBoolean("force", false);
                if (latest <= VERSION_CODE) {
                    return;
                }
                if (!force && prefs.getInt(KEY_SNOOZED, 0) == latest) {
                    return; // foydalanuvchi bu versiya uchun "Keyinroq" degan
                }
                final String name = obj.optString("latestName", "");
                final String message = obj.optString("message", "");
                String url = obj.optString("url", DEFAULT_URL);
                // Faqat https havolalarga ruxsat
                final String openUrl = url.startsWith("https://") ? url : DEFAULT_URL;
                AndroidUtilities.runOnUIThread(() -> show(context, prefs, latest, name, message, openUrl, force));
            } catch (Exception ignore) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static void show(Context context, SharedPreferences prefs, int latest, String name,
                             String message, String url, boolean force) {
        if (context instanceof Activity && ((Activity) context).isFinishing()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(L.t("Sizda: ", "У вас: ")).append(VERSION_NAME);
        if (!name.isEmpty()) {
            text.append(L.t("\nYangi: ", "\nНовая: ")).append(name);
        }
        if (!message.isEmpty()) {
            text.append("\n\n").append(message);
        }
        text.append(L.t("\n\n⚠️ Yangilanishni faqat rasmiy @CyberGramNews kanalidan yuklab oling. ", "\n\n⚠️ Скачивайте обновление только из официального канала @CyberGramNews. ")
                + L.t("Boshqa joydan yuborilgan \"CyberGram\" APK fayllari soxta bo'lishi mumkin.", "APK-файлы «CyberGram» из других источников могут быть поддельными."));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(L.t("🆕 CyberGram yangilandi", "🆕 CyberGram обновился"));
        builder.setMessage(text.toString());
        builder.setPositiveButton(L.t("Yuklab olish", "Скачать"), (dialog, which) -> Browser.openUrl(context, url));
        if (!force) {
            builder.setNegativeButton(L.t("Keyinroq", "Позже"), (dialog, which) ->
                    prefs.edit().putInt(KEY_SNOOZED, latest).apply());
        }
        builder.show();
    }
}