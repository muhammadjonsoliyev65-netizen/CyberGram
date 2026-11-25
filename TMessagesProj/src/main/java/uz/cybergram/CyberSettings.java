package uz.cybergram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.ui.ActionBar.AlertDialog;

/**
 * CyberGram sozlamalari: funksiyalarni yoqish/o'chirish.
 */
public final class CyberSettings {

    private static final String PREFS = "cybergram";
    private static final String LINK_GUARD = "link_guard";
    private static final String ONLINE_CHECK = "online_check";
    private static final String AI = "ai_enabled";
    private static final String ASK_UNKNOWN = "ask_unknown_sites";
    private static final String AI_CONSENT = "ai_consent_v2";

    private CyberSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Havola bosilganda avtomatik tekshirish. */
    public static boolean linkGuard(Context context) {
        return context == null || prefs(context).getBoolean(LINK_GUARD, true);
    }

    /** Havolalarni Google Safe Browsing orqali onlayn tekshirish. */
    public static boolean onlineCheck(Context context) {
        return context == null || prefs(context).getBoolean(ONLINE_CHECK, true);
    }

    /** Notanish saytni ochishdan oldin so'rash. */
    public static boolean askUnknown(Context context) {
        return context == null || prefs(context).getBoolean(ASK_UNKNOWN, true);
    }

    /** AI funksiyalari (AI tahlili, Qisqacha, Yordamchi). */
    public static boolean ai(Context context) {
        return context == null || prefs(context).getBoolean(AI, true);
    }

    public static void show(final Context context) {
        final SharedPreferences p = prefs(context);
        final boolean guard = p.getBoolean(LINK_GUARD, true);
        final boolean online = p.getBoolean(ONLINE_CHECK, true);
        final boolean ai = p.getBoolean(AI, true);
        final boolean ask = p.getBoolean(ASK_UNKNOWN, true);
        final boolean consent = p.getBoolean(AI_CONSENT, false);
        final boolean side = SideMenu.enabled();

        CharSequence[] items = new CharSequence[]{
                (guard ? "✅" : "⬜") + L.t(" Havolalarni avtomatik tekshirish", " Автоматическая проверка ссылок"),
                (online ? "✅" : "⬜") + L.t(" Onlayn tekshiruv (Google Safe Browsing, sayt yoshi)", " Онлайн-проверка (Google Safe Browsing, возраст сайта)"),
                (ai ? "✅" : "⬜") + L.t(" AI funksiyalari", " Функции ИИ"),
                (ask ? "✅" : "⬜") + L.t(" Notanish saytni ochishdan oldin so'rash", " Спрашивать перед открытием незнакомых сайтов"),
                (side ? "✅" : "⬜") + L.t(" ☰ Yon menyu (pastki panel o'rniga)", " ☰ Боковое меню (вместо нижней панели)"),
                consent ? L.t("🔄 AI roziligini bekor qilish", "🔄 Отозвать согласие на ИИ") : L.t("AI roziligi berilmagan", "Согласие на ИИ не дано")
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(L.t("⚙️ CyberGram sozlamalari", "⚙️ Настройки CyberGram"));
        builder.setItems(items, (dialog, which) -> {
            SharedPreferences.Editor e = p.edit();
            if (which == 0) {
                e.putBoolean(LINK_GUARD, !guard);
            } else if (which == 1) {
                e.putBoolean(ONLINE_CHECK, !online);
            } else if (which == 2) {
                e.putBoolean(AI, !ai);
            } else if (which == 3) {
                e.putBoolean(ASK_UNKNOWN, !ask);
            } else if (which == 4) {
                SideMenu.setEnabled(!side);
                android.widget.Toast.makeText(context, L.t("O'zgarish ilova qayta ochilganda kuchga kiradi.", "Изменение вступит в силу после перезапуска приложения."), android.widget.Toast.LENGTH_LONG).show();
            } else if (consent) {
                e.putBoolean(AI_CONSENT, false);
            }
            e.apply();
            dialog.dismiss();
            show(context);
        });
        builder.setPositiveButton(L.t("Yopish", "Закрыть"), (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
