package uz.cybergram;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

/**
 * "🛡 CyberGram" menyusi: yordamchi, rasmiy kanallar, ilova haqida.
 */
public final class CyberMenu {


    private CyberMenu() {
    }

    public static void show(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("🛡 CyberGram");
        builder.setItems(new CharSequence[]{
                L.t("🤖 Yordamchi (savol bering)", "🤖 Помощник (задайте вопрос)"),
                L.t("📢 Rasmiy kanallar", "📢 Официальные каналы"),
                L.t("👻 Maxfiylik rejimi", "👻 Режим приватности"),
                L.t("⚙️ Sozlamalar", "⚙️ Настройки"),
                L.t("ℹ️ Ilova haqida", "ℹ️ О приложении")
        }, (dialog, which) -> {
            if (which == 0) {
                askAssistant(context);
            } else if (which == 1) {
                showOfficialChannels(context);
            } else if (which == 2) {
                PrivacyMode.show(context);
            } else if (which == 3) {
                CyberSettings.show(context);
            } else {
                showAbout(context);
            }
        });
        builder.show();
    }

    public static void askAssistant(final Context context) {
        if (!CyberSettings.ai(context)) {
            message(context, L.t("🤖 Yordamchi", "🤖 Помощник"), L.t("AI funksiyalari o'chirilgan. Yoqish uchun: 🛡 → ⚙️ Sozlamalar.", "Функции ИИ отключены. Включить: 🛡 → ⚙️ Настройки."));
            return;
        }
        final EditText input = new EditText(context);
        input.setHint(L.t("Masalan: ikki bosqichli parolni qanday yoqaman?", "Например: как включить двухэтапную аутентификацию?"));
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(5);

        FrameLayout container = new FrameLayout(context);
        int pad = AndroidUtilities.dp(24);
        container.setPadding(pad, AndroidUtilities.dp(8), pad, 0);
        container.addView(input);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(L.t("🤖 Yordamchi", "🤖 Помощник"));
        builder.setMessage(L.t("Telegram'dan foydalanish yoki xavfsizlik haqida savol bering.", "Задайте вопрос о Telegram или безопасности."));
        builder.setView(container);
        builder.setPositiveButton(L.t("So'rash", "Спросить"), (dialog, which) -> {
            final String question = input.getText().toString().trim();
            dialog.dismiss();
            if (TextUtils.isEmpty(question)) {
                return;
            }
            if (!CyberApiClient.isConfigured()) {
                message(context, L.t("🤖 Yordamchi", "🤖 Помощник"), L.t("AI sozlanmagan: local.properties faylida CYBERGRAM_API_URL va CYBERGRAM_API_SECRET yo'q.", "ИИ не настроен: в local.properties нет CYBERGRAM_API_URL и CYBERGRAM_API_SECRET."));
                return;
            }
            AiConsent.run(context, () -> {
                final AlertDialog loader = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
                loader.setCanCancel(false);
                loader.show();
                // Karta, telefon, email, kodlar AI'ga yuborilmaydi
                CyberApiClient.post("/api/assistant", Redact.text(question), new CyberApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        dismiss(loader);
                        String answer = response.optString("answer");
                        message(context, L.t("🤖 Yordamchi", "🤖 Помощник"), TextUtils.isEmpty(answer)
                                ? L.t("Javob olinmadi.", "Ответ не получен.")
                                : answer + L.t("\n\nJavobni Gemini AI berdi va u xato qilishi mumkin. Rasmiy ma'lumot: telegram.org/faq", "\n\nОтвет дал Gemini AI, он может ошибаться. Официальная информация: telegram.org/faq"));
                    }

                    @Override
                    public void onError(String error) {
                        dismiss(loader);
                        message(context, L.t("🤖 Yordamchi", "🤖 Помощник"), error);
                    }
                });
            });
        });
        builder.setNegativeButton(L.t("Bekor qilish", "Отмена"), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    public static void showOfficialChannels(final Context context) {
        OfficialChannels.show(context);
    }

    public static final String NEWS_CHANNEL = "https://t.me/CyberGramNews";
    public static final String SUPPORT = "https://t.me/CYBERGRAMUZ";
    public static final String PRIVACY_URL = "https://cybergram-api-tau.vercel.app/privacy.html";

    public static void showAbout(final Context context) {
        String version = BuildVars.BUILD_VERSION_STRING;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(L.t("ℹ️ CyberGram haqida", "ℹ️ О CyberGram"));
        builder.setMessage("CyberGram " + version + "\n\n"
                + L.t("CyberGram — Telegram API asosida ishlaydigan NORASMIY ilova va Telegram ekotizimining bir qismi. U Telegram'ning rasmiy ilovasi emas ", "CyberGram — НЕОФИЦИАЛЬНОЕ приложение на основе Telegram API, часть экосистемы Telegram. Это не официальное приложение Telegram ")
                + L.t("va Telegram jamoasi bilan bog'liq emas. Ilova ochiq kodli Telegram for Android (GPL-2.0) asosida qurilgan.\n\n", "и оно не связано с командой Telegram. Приложение построено на открытом коде Telegram for Android (GPL-2.0).\n\n")
                + L.t("🛡 Xavfsizlik:\n", "🛡 Безопасность:\n")
                + L.t("• Har bir xabar yonida avtomatik tekshiruv belgisi (🟢/🟡/🔴)\n", "• Значок автоматической проверки у каждого сообщения (🟢/🟡/🔴)\n")
                + L.t("• Havolalar va tugmalardagi havolalar ochilishidan oldin tekshiriladi\n", "• Ссылки, в том числе в кнопках, проверяются перед открытием\n")
                + L.t("• AI tahlili (havola va APK fayllar uchun) va AI yordamchi\n", "• Анализ ИИ (для ссылок и APK-файлов) и ИИ-помощник\n")
                + L.t("• 👻 Maxfiylik rejimi — Telegram'ning rasmiy sozlamalari bir joyda\n\n", "• 👻 Режим приватности — официальные настройки Telegram в одном месте\n\n")
                + L.t("🔒 Maxfiylik:\n", "🔒 Приватность:\n")
                + L.t("• Oflayn tekshiruv telefonda bajariladi\n", "• Офлайн-проверка выполняется на телефоне\n")
                + L.t("• Google Safe Browsing'ga faqat havola manzili yuboriladi\n", "• В Google Safe Browsing отправляется только адрес ссылки\n")
                + L.t("• AI'ga xabar matni yuborilmaydi — faqat havola manzillari va fayl nomi, faqat siz tugmani bosganingizda; CyberGram serveri ularni saqlamaydi\n", "• Текст сообщений ИИ не отправляется — только адреса ссылок и имя файла, и только по нажатию кнопки; сервер CyberGram их не хранит\n")
                + L.t("• Diqqat: Gemini bepul tarifida Google yordamchiga yozgan savollaringizdan foydalanishi mumkin\n\n", "• Внимание: на бесплатном тарифе Gemini Google может использовать ваши вопросы помощнику\n\n")
                + L.t("📢 Yangiliklar: @CyberGramNews\n", "📢 Новости: @CyberGramNews\n")
                + L.t("💬 Savollar: @CYBERGRAMUZ", "💬 Вопросы: @CYBERGRAMUZ"));
        builder.setPositiveButton(L.t("Yopish", "Закрыть"), (dialog, which) -> dialog.dismiss());
        builder.setNegativeButton(L.t("📢 Kanal", "📢 Канал"), (dialog, which) -> Browser.openUrl(context, NEWS_CHANNEL));
        builder.setNeutralButton(L.t("🔒 Maxfiylik", "🔒 Приватность"), (dialog, which) -> Browser.openUrl(context, PRIVACY_URL));
        builder.show();
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
        }
    }
}
