package uz.cybergram;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ui.ActionBar.AlertDialog;

/**
 * Gemini AI orqali: xabarni tahlil qilish ("🤖 AI tahlili") va qisqartirish ("📝 Qisqacha").
 */
public final class CyberAiDialog {

    /** "Qisqacha" faqat shundan uzun matnlar uchun ko'rsatiladi. */
    public static final int MIN_SUMMARY_LENGTH = 300;

    private static String disclaimer() {
        return L.t("\n\nTahlilni Gemini AI bajardi. AI xato qilishi mumkin.", "\n\nАнализ выполнил Gemini AI. ИИ может ошибаться.");
    }

    private CyberAiDialog() {
    }

    public static void scan(final Context context, final String text) {
        scan(context, text, SafetyVerdict.SAFE);
    }

    /**
     * @param localVerdict telefondagi tekshiruv natijasi — AI natijasi undan "yumshoqroq" bo'lmaydi
     *                     (firibgar havola ichiga "bu xavfsiz" deb yozib AI'ni aldashi mumkin).
     */
    public static void scan(final Context context, final String text, final SafetyVerdict localVerdict) {
        if (!ready(context, text, L.t("🤖 AI tahlili", "🤖 Анализ ИИ"))) {
            return;
        }
        AiConsent.run(context, () -> {
            final AlertDialog loader = spinner(context);
            CyberApiClient.post("/api/scan", text, new CyberApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    dismiss(loader);
                    SafetyVerdict aiVerdict = parseVerdict(response.optString("verdict"));
                    SafetyVerdict verdict = localVerdict == null ? aiVerdict : aiVerdict.max(localVerdict);
                    SpannableStringBuilder sb = new SpannableStringBuilder();
                    int start = sb.length();
                    sb.append(verdict.label());
                    sb.setSpan(new ForegroundColorSpan(verdict.color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.append("\n\n");
                    JSONArray reasons = response.optJSONArray("reasons");
                    if (reasons != null) {
                        for (int i = 0; i < reasons.length(); i++) {
                            sb.append("• ").append(reasons.optString(i)).append("\n");
                        }
                    }
                    String advice = response.optString("advice");
                    if (!TextUtils.isEmpty(advice)) {
                        sb.append("\n💡 ").append(advice);
                    }
                    if (verdict != aiVerdict) {
                        sb.append(L.t("\n\n⚠️ AI xavfliligi pastroq deb baholadi, lekin telefondagi tekshiruv xavf belgilarini topgan — shuning uchun yakuniy baho pasaytirilmadi.",
                                "\n\n⚠️ ИИ оценил риск ниже, но проверка на телефоне нашла признаки опасности — поэтому итоговая оценка не снижена."));
                    }
                    sb.append(disclaimer());
                    message(context, L.t("🤖 AI tahlili", "🤖 Анализ ИИ"), sb);
                }

                @Override
                public void onError(String error) {
                    dismiss(loader);
                    message(context, L.t("🤖 AI tahlili", "🤖 Анализ ИИ"), error);
                }
            });
        });
    }

    /** Xabar "Qisqacha" uchun yetarlicha uzunmi. */
    public static boolean isLongText(org.telegram.messenger.MessageObject messageObject) {
        // O'chirilgan: "Qisqacha" butun xabar matnini AI'ga yuborishni talab qiladi (Telegram ToS 1.5).
        return false;
    }

    public static void summarize(final Context context, final String text) {
        if (!ready(context, text, L.t("📝 Qisqacha", "📝 Кратко"))) {
            return;
        }
        if (text.trim().length() < MIN_SUMMARY_LENGTH) {
            message(context, L.t("📝 Qisqacha", "📝 Кратко"), L.t("Xabar qisqa — uni qisqartirish shart emas.", "Сообщение короткое — сокращать его не нужно."));
            return;
        }
        AiConsent.run(context, () -> {
            final AlertDialog loader = spinner(context);
            CyberApiClient.post("/api/summarize", text, new CyberApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    dismiss(loader);
                    String summary = response.optString("summary");
                    message(context, L.t("📝 Qisqacha", "📝 Кратко"), TextUtils.isEmpty(summary)
                            ? L.t("AI qisqartma qaytarmadi.", "ИИ не вернул краткое содержание.")
                            : summary + disclaimer());
                }

                @Override
                public void onError(String error) {
                    dismiss(loader);
                    message(context, L.t("📝 Qisqacha", "📝 Кратко"), error);
                }
            });
        });
    }

    private static boolean ready(Context context, String text, String title) {
        if (!CyberSettings.ai(context)) {
            message(context, title, L.t("AI funksiyalari o'chirilgan. Yoqish uchun: 🛡 → ⚙️ Sozlamalar.", "Функции ИИ отключены. Включить: 🛡 → ⚙️ Настройки."));
            return false;
        }
        if (!CyberApiClient.isConfigured()) {
            message(context, title, L.t("AI sozlanmagan: local.properties faylida CYBERGRAM_API_URL va CYBERGRAM_API_SECRET yo'q.", "ИИ не настроен: в local.properties нет CYBERGRAM_API_URL и CYBERGRAM_API_SECRET."));
            return false;
        }
        if (text == null || text.trim().isEmpty()) {
            message(context, title, L.t("Bu xabarda tahlil qilinadigan matn yo'q.", "В этом сообщении нет текста для анализа."));
            return false;
        }
        return true;
    }

    private static SafetyVerdict parseVerdict(String value) {
        if ("dangerous".equals(value)) {
            return SafetyVerdict.DANGEROUS;
        }
        if ("safe".equals(value)) {
            return SafetyVerdict.SAFE;
        }
        return SafetyVerdict.SUSPICIOUS;
    }

    private static AlertDialog spinner(Context context) {
        AlertDialog loader = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        loader.setCanCancel(false);
        loader.show();
        return loader;
    }

    private static void dismiss(AlertDialog loader) {
        try {
            loader.dismiss();
        } catch (Exception ignore) {
        }
    }

    private static void message(Context context, String title, CharSequence text) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(title);
            builder.setMessage(text);
            builder.setPositiveButton(L.t("Tushunarli", "Понятно"), (dialog, which) -> dialog.dismiss());
            builder.show();
        } catch (Exception ignore) {
            // Oyna yopilib ketgan bo'lsa
        }
    }
}
