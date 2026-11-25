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

    /** AI funksiyalari (AI tahlili, Qisqacha, Yordamchi). */
    public static boolean ai(Context context) {
        return context == null || prefs(context).getBoolean(AI, true);
    }

    public static void show(final Context context) {
        final SharedPreferences p = prefs(context);
        final boolean guard = p.getBoolean(LINK_GUARD, true);
        final boolean online = p.getBoolean(ONLINE_CHECK, true);
        final boolean ai = p.getBoolean(AI, true);
        final boolean consent = p.getBoolean(AI_CONSENT, false);

        CharSequence[] items = new CharSequence[]{
                (guard ? "✅" : "⬜") + L.t(" Havolalarni avtomatik tekshirish", " Автоматическая проверка ссылок"),
                (online ? "✅" : "⬜") + L.t(" Google Safe Browsing (onlayn tekshiruv)", " Google Safe Browsing (онлайн-проверка)"),
                (ai ? "✅" : "⬜") + L.t(" AI funksiyalari", " Функции ИИ"),
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
