package uz.cybergram;

import org.telegram.messenger.LocaleController;

import java.util.Locale;

/**
 * CyberGram yozuvlari uchun til tanlovi: ilova tili rus bo'lsa — ruscha, aks holda o'zbekcha.
 * Ishlatilishi: L.t("O'zbekcha matn", "Русский текст")
 */
public final class L {

    private L() {
    }

    /** Ilova (yoki telefon) tili rus tilimi. */
    public static boolean ru() {
        String code = null;
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null) {
                code = info.shortName;
            }
        } catch (Throwable ignore) {
        }
        if (code == null || code.isEmpty()) {
            code = Locale.getDefault().getLanguage();
        }
        return code != null && code.toLowerCase(Locale.ROOT).startsWith("ru");
    }

    public static String t(String uz, String ru) {
        return ru() ? ru : uz;
    }

    /** Serverga yuboriladigan til kodi. */
    public static String lang() {
        return ru() ? "ru" : "uz";
    }
}