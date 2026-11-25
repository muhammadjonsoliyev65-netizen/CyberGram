package uz.cybergram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.ui.ActionBar.AlertDialog;

/**
 * AI funksiyalaridan birinchi marta foydalanishdan oldin rozilik so'raydi.
 */
public final class AiConsent {

    private static final String PREFS = "cybergram";
    private static final String KEY = "ai_consent_v2";

    private AiConsent() {
    }

    public static void run(Context context, Runnable action) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY, false)) {
            action.run();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(L.t("🤖 AI'dan foydalanish", "🤖 Использование ИИ"));
        builder.setMessage(L.t("AI tahlili uchun xabardagi havola manzillari va fayl nomi CyberGram serveri orqali Google Gemini AI'ga yuboriladi. Xabar matni yuborilmaydi.\n\n", "Для анализа ИИ адреса ссылок и имя файла из сообщения отправляются в Google Gemini AI через сервер CyberGram. Текст сообщения не отправляется.\n\n")
                + L.t("• Ma'lumot faqat siz tugmani bosganingizda yuboriladi.\n", "• Данные отправляются только когда вы нажимаете кнопку.\n")
                + L.t("• CyberGram serveri ularni saqlamaydi.\n", "• Сервер CyberGram их не сохраняет.\n")
                + L.t("• Yordamchiga yozgan savolingiz ham AI'ga yuboriladi; bepul tarifda Google undan o'z xizmatlarini yaxshilash uchun foydalanishi mumkin.\n", "• Ваш вопрос помощнику тоже отправляется ИИ; на бесплатном тарифе Google может использовать его для улучшения своих сервисов.\n")
                + L.t("• Karta raqami, telefon, email, kodlar va havolalardagi maxfiy qismlar telefonning o'zida yashiriladi va AI'ga yuborilmaydi. Shunday bo'lsa ham, parol yozmang.\n\n", "• Номера карт, телефоны, email, коды и секретные части ссылок скрываются прямо на телефоне и не отправляются ИИ. И всё же не пишите пароли.\n\n")
                + L.t("Rozimisiz?", "Вы согласны?"));
        builder.setPositiveButton(L.t("Roziman", "Согласен"), (dialog, which) -> {
            prefs.edit().putBoolean(KEY, true).apply();
            dialog.dismiss();
            action.run();
        });
        builder.setNegativeButton(L.t("Yo'q", "Нет"), (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
