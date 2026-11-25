package uz.cybergram;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CyberGram serveri (Vercel) bilan ishlash: /api/scan va /api/summarize.
 */
public final class CyberApiClient {

    public interface Callback {
        void onSuccess(JSONObject response);

        void onError(String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CyberApiClient() {
    }

    public static boolean isConfigured() {
        return !TextUtils.isEmpty(BuildConfig.CYBERGRAM_API_URL) && !TextUtils.isEmpty(BuildConfig.CYBERGRAM_API_SECRET);
    }

    /** Natija doim asosiy (UI) oqimda qaytadi. */
    public static void post(final String path, final String text, final Callback callback) {
        EXECUTOR.execute(() -> {
            JSONObject result = null;
            String error;
            try {
                result = request(path, text);
                error = null;
            } catch (ApiException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = L.t("Internetga ulanib bo'lmadi. Ulanishni tekshirib, qayta urinib ko'ring.", "Не удалось подключиться к интернету. Проверьте соединение и попробуйте снова.");
            }
            final JSONObject finalResult = result;
            final String finalError = error;
            MAIN.post(() -> {
                if (finalError != null) {
                    callback.onError(finalError);
                } else {
                    callback.onSuccess(finalResult);
                }
            });
        });
    }

    private static JSONObject request(String path, String text) throws Exception {
        String base = BuildConfig.CYBERGRAM_API_URL;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        JSONObject body = new JSONObject();
        body.put("text", text);

        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-CyberGram-Key", BuildConfig.CYBERGRAM_API_SECRET);
            connection.setRequestProperty("X-CyberGram-Device", deviceId());
            connection.setRequestProperty("X-CyberGram-Lang", L.lang());
            OutputStream os = connection.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int code = connection.getResponseCode();
            if (code == 401) {
                throw new ApiException(L.t("Ilova paroli serverdagi bilan mos emas (401).", "Пароль приложения не совпадает с сервером (401)."));
            }
            if (code == 429) {
                throw new ApiException(L.t("Bugungi AI limiti tugadi. Ertaga yana urinib ko'ring.\n\nOflayn tekshiruv (🛡 belgi) va havola himoyasi esa ishlashda davom etadi.", "Дневной лимит ИИ исчерпан. Попробуйте завтра.\n\nОфлайн-проверка (значок 🛡) и защита ссылок продолжают работать."));
            }
            if (code == 502) {
                throw new ApiException(L.t("AI hozir javob bermadi. Birozdan keyin qayta urinib ko'ring.", "ИИ сейчас не ответил. Попробуйте чуть позже."));
            }
            if (code != 200) {
                throw new ApiException(L.t("Server xatosi (", "Ошибка сервера (") + code + ").");
            }
            InputStream is = connection.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = is.read(buffer)) > 0) {
                bos.write(buffer, 0, n);
            }
            is.close();
            return new JSONObject(new String(bos.toByteArray(), "UTF-8"));
        } finally {
            connection.disconnect();
        }
    }

    private static String cachedDeviceId;

    /** Tasodifiy qurilma ID'si (faqat limit uchun; telefon yoki akkaunt bilan bog'liq emas). */
    private static synchronized String deviceId() {
        if (cachedDeviceId != null) {
            return cachedDeviceId;
        }
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("cybergram", Context.MODE_PRIVATE);
            String id = prefs.getString("device_id", null);
            if (id == null) {
                id = UUID.randomUUID().toString();
                prefs.edit().putString("device_id", id).apply();
            }
            cachedDeviceId = id;
        } catch (Exception e) {
            cachedDeviceId = UUID.randomUUID().toString();
        }
        return cachedDeviceId;
    }

    private static final class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }
}
