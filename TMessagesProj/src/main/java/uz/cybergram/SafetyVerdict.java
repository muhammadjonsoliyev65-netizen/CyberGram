package uz.cybergram;

/**
 * Xabar yoki havolaning xavfsizlik bahosi.
 */
public enum SafetyVerdict {
    SAFE("🟢 Xavf topilmadi", "🟢 Угроз не найдено", 0xFF2E7D32),
    SUSPICIOUS("🟡 Shubhali", "🟡 Подозрительно", 0xFFF9A825),
    DANGEROUS("🔴 Xavfli", "🔴 Опасно", 0xFFC62828);

    private final String uz;
    private final String ru;
    public final int color;

    SafetyVerdict(String uz, String ru, int color) {
        this.uz = uz;
        this.ru = ru;
        this.color = color;
    }

    /** Ilova tiliga mos nom. */
    public String label() {
        return L.t(uz, ru);
    }

    /** Ikki bahodan xavfliroqini qaytaradi. */
    public SafetyVerdict max(SafetyVerdict other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
