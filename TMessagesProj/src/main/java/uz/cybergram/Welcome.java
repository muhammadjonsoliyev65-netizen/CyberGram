package uz.cybergram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.AlertDialog;

/**
 * Birinchi ochilishdagi tanishtiruv: CyberGram nimasi bilan farq qiladi. Faqat bir marta chiqadi.
 * 3 ta qisqa sahifa: belgilar → havolalar va AI → maxfiylik.
 */
public final class Welcome {

    private static final String PREFS = "cybergram";
    private static final String KEY = "welcome_shown_v2";

    private static final String[] TITLES_UZ = {
            "🛡 CyberGram'ga xush kelibsiz!",
            "🔗 Havolalar va AI",
            "👻 Maxfiylik va yordam"
    };

    private static final String[] PAGES_UZ = {
            "CyberGram — firibgarlikdan himoyalangan Telegram ilovasi.\n\n"
                    + "Har bir kelgan xabar yonida kichik 🛡 belgi bo'ladi:\n\n"
                    + "🟢 — xavfli narsa topilmadi\n"
                    + "🟡 — shubhali: ehtiyot bo'ling\n"
                    + "🔴 — xavfli: havolani ochmang, kod yoki parol bermang\n\n"
                    + "Belgini bossangiz — nima uchun shunday baholangani ko'rsatiladi.\n\n"
                    + "⚠️ Tekshiruv 100% kafolat bermaydi: ba'zan xavfsiz narsa shubhali, xavfli narsa esa xavfsiz deb ko'rsatilishi mumkin.",

            "• Havolani bosganingizda CyberGram uni avval tekshiradi (telefonda va Google Safe Browsing orqali). Xavfli sayt bloklanadi, shubhalisida ogohlantirish chiqadi.\n\n"
                    + "• Xabar ostidagi tugmalardagi yashirin havolalar ham tekshiriladi.\n\n"
                    + "• Havola yoki .apk fayl bor xabarda 🛡 → \"🤖 AI tahlili\" ni bosib, sun'iy intellekt fikrini olishingiz mumkin.\n\n"
                    + "• AI'ga faqat havola manzillari va fayl nomi yuboriladi — xabar matni emas.",

            "• Chatlar ro'yxati tepasidagi 🛡 tugma — CyberGram menyusi: AI yordamchi, rasmiy kanallar, 👻 maxfiylik rejimi va sozlamalar.\n\n"
                    + "• Oflayn tekshiruv telefoningizda bajariladi. AI'ga ma'lumot faqat siz tugmani bosganingizda yuboriladi.\n\n"
                    + "• CyberGram — norasmiy ilova: Telegram API asosida ishlaydi va Telegram ekotizimining bir qismi.\n\n"
                    + "📢 Yangiliklar: @CyberGramNews\n"
                    + "💬 Savollar: @CYBERGRAMUZ"
    };

    private static final String[] TITLES_RU = {
            "🛡 Добро пожаловать в CyberGram!",
            "🔗 Ссылки и ИИ",
            "👻 Приватность и помощь"
    };

    private static final String[] PAGES_RU = {
            "CyberGram — Telegram-приложение с защитой от мошенничества.\n\n"
                    + "Рядом с каждым входящим сообщением есть маленький значок 🛡:\n\n"
                    + "🟢 — ничего опасного не найдено\n"
                    + "🟡 — подозрительно: будьте осторожны\n"
                    + "🔴 — опасно: не открывайте ссылку, не сообщайте код или пароль\n\n"
                    + "Нажмите на значок — увидите, почему сообщение получило такую оценку.\n\n"
                    + "⚠️ Проверка не даёт 100% гарантии: иногда безопасное может быть отмечено как подозрительное, а опасное — как безопасное.",

            "• Когда вы нажимаете на ссылку, CyberGram сначала проверяет её (на телефоне и через Google Safe Browsing). Опасный сайт блокируется, на подозрительном появится предупреждение.\n\n"
                    + "• Скрытые ссылки в кнопках под сообщениями тоже проверяются.\n\n"
                    + "• В сообщении со ссылкой или .apk-файлом нажмите 🛡 → «🤖 Анализ ИИ», чтобы получить мнение искусственного интеллекта.\n\n"
                    + "• ИИ получает только адреса ссылок и имя файла — не текст сообщения.",

            "• Кнопка 🛡 над списком чатов — меню CyberGram: ИИ-помощник, официальные каналы, 👻 режим приватности и настройки.\n\n"
                    + "• Офлайн-проверка выполняется на вашем телефоне. Данные отправляются ИИ только когда вы нажимаете кнопку.\n\n"
                    + "• CyberGram — неофициальное приложение: работает на Telegram API и является частью экосистемы Telegram.\n\n"
                    + "📢 Новости: @CyberGramNews\n"
                    + "💬 Вопросы: @CYBERGRAMUZ"
    };

    private static String[] titles() {
        return L.ru() ? TITLES_RU : TITLES_UZ;
    }

    private static String[] pages() {
        return L.ru() ? PAGES_RU : PAGES_UZ;
    }

    private Welcome() {
    }

    /** Ilova birinchi marta ochilganda tanishtiruvni ko'rsatadi. */
    public static void showOnce(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY, true).apply();
        showPage(context, 0);
    }

    /** Menyudan qayta ko'rish uchun. */
    public static void show(Context context) {
        showPage(context, 0);
    }

    private static void showPage(final Context context, final int page) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titles()[page] + "  (" + (page + 1) + "/" + pages().length + ")");
        builder.setMessage(pages()[page]);
        if (page < pages().length - 1) {
            builder.setPositiveButton(L.t("Keyingisi →", "Далее →"), (dialog, which) -> showPage(context, page + 1));
            builder.setNegativeButton(L.t("O'tkazib yuborish", "Пропустить"), (dialog, which) -> dialog.dismiss());
        } else {
            builder.setPositiveButton(L.t("Boshladik! ✅", "Начнём! ✅"), (dialog, which) -> dialog.dismiss());
            builder.setNegativeButton(L.t("📢 Kanal", "📢 Канал"), (dialog, which) ->
                    org.telegram.messenger.browser.Browser.openUrl(context, CyberMenu.NEWS_CHANNEL));
        }
        if (page > 0) {
            builder.setNeutralButton(L.t("← Orqaga", "← Назад"), (dialog, which) -> showPage(context, page - 1));
        }
        builder.show();
    }
}
