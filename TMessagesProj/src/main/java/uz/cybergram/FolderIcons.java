package uz.cybergram;

import org.telegram.messenger.MessagesController;

/**
 * Papka (folder) yorliqlari uchun ikonkalar — Plus Messenger uslubida.
 */
public final class FolderIcons {

    public static final String ALL_CHATS = "💬";

    private FolderIcons() {
    }

    /** Chatlar ro'yxati sarlavhasi: "Barcha chatlar" uchun "CyberGram", boshqa papkalar uchun "🎓 O'qish". */
    public static String title(MessagesController.DialogFilter filter) {
        if (filter == null || filter.isDefault() || filter.name == null || filter.name.trim().isEmpty()) {
            return "CyberGram";
        }
        String name = filter.name.trim();
        String emoji = leadingEmoji(name);
        if (emoji != null) {
            return name; // nomda emoji bor — o'zini ko'rsatamiz
        }
        return icon(filter) + " " + name;
    }

    public static String icon(MessagesController.DialogFilter filter) {
        if (filter == null) {
            return "📁";
        }
        // 1) Papka nomi emoji bilan boshlansa ("🎓 O'qish") — o'sha emoji
        String fromName = leadingEmoji(filter.name);
        if (fromName != null) {
            return fromName;
        }
        // 2) Papka turi bo'yicha
        int flags = filter.flags;
        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
            return "📩";
        }
        if (!filter.alwaysShow.isEmpty()) {
            return "📁";
        }
        int types = flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;
        int people = MessagesController.DIALOG_FILTER_FLAG_CONTACTS | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
        if (types != 0 && (types & ~people) == 0) {
            return "👤";
        }
        if (types == MessagesController.DIALOG_FILTER_FLAG_GROUPS) {
            return "👥";
        }
        if (types == MessagesController.DIALOG_FILTER_FLAG_CHANNELS) {
            return "📢";
        }
        if (types == MessagesController.DIALOG_FILTER_FLAG_BOTS) {
            return "🤖";
        }
        if (types == MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) {
            return "💬";
        }
        return "📁";
    }

    /** Nomning birinchi so'zi faqat emoji/belgilardan iborat bo'lsa, uni qaytaradi. */
    static String leadingEmoji(String name) {
        if (name == null) {
            return null;
        }
        String s = name.trim();
        if (s.isEmpty()) {
            return null;
        }
        int space = s.indexOf(' ');
        String first = space > 0 ? s.substring(0, space) : s;
        if (first.length() > 16) {
            return null;
        }
        int i = 0;
        boolean hasSymbol = false;
        while (i < first.length()) {
            int cp = first.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                return null;
            }
            int type = Character.getType(cp);
            if (type == Character.OTHER_SYMBOL || type == Character.SURROGATE) {
                hasSymbol = true;
            }
            i += Character.charCount(cp);
        }
        return hasSymbol ? first : null;
    }
}
