package uz.cybergram;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Internetsiz, telefonning o'zida ishlaydigan xavfsizlik tekshiruvi.
 */
public final class LocalRules {

    public static final class Result {
        public SafetyVerdict verdict = SafetyVerdict.SAFE;
        /** Google Safe Browsing tahdid topdimi. */
        public boolean online;
        public final List<String> reasons = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public final List<String> links = new ArrayList<>();

        void add(SafetyVerdict v, String reason) {
            verdict = verdict.max(v);
            if (!reasons.contains(reason)) {
                reasons.add(reason);
            }
        }

        /** Google Safe Browsing topgan tahdid. */
        public void addOnline(String link, String threatType) {
            online = true;
            reasons.remove(noDanger());
            reasons.remove(noDangerLinks());
            add(SafetyVerdict.DANGEROUS, "\"" + LinkExtractor.host(link) + "\" — "
                    + L.t("Google Safe Browsing ma'lumotiga ko'ra, bu sayt xavfli bo'lishi mumkin: ", "по данным Google Safe Browsing, этот сайт может быть опасен: ")
                    + SafeBrowsingClient.describe(threatType) + ".");
        }

        void note(String note) {
            if (!notes.contains(note)) {
                notes.add(note);
            }
        }
    }

    static String noDanger() {
        return L.t("Xavfli belgilar topilmadi.", "Признаков опасности не найдено.");
    }

    static String noDangerLinks() {
        return L.t("Havolalarda xavfli belgilar topilmadi.", "В ссылках признаков опасности не найдено.");
    }

    private static final String[] OFFICIAL_DOMAINS = {
            "t.me", "telegram.me", "telegram.org", "telegra.ph", "telesco.pe", "fragment.com"
    };

    private static final String[] SHORTENERS = {
            "bit.ly", "tinyurl.com", "cutt.ly", "goo.gl", "t.ly", "is.gd", "clck.ru",
            "shorturl.at", "rebrand.ly", "ow.ly", "rb.gy", "v.gd", "tiny.cc", "s.id"
    };

    private static final String[] RISKY_TLDS = {
            ".xyz", ".top", ".click", ".zip", ".mov", ".tk", ".ml", ".ga", ".cf", ".gq",
            ".icu", ".buzz", ".rest", ".monster", ".cam", ".quest"
    };

    private static final String[] DANGEROUS_FILES = {
            ".apk", ".xapk", ".apks", ".exe", ".scr", ".bat", ".cmd", ".msi", ".vbs", ".jar"
    };

    private static final String[] ASKS_SECRETS = {
            "sms kod", "smsdagi kod", "kodni yubor", "kodni ayt", "kodni tashla", "kodni ber", "tasdiqlash kod", "parolingiz",
            "parolni yubor", "karta raqam", "kartangiz raqam", "cvv", "pin kod", "amal qilish muddat",
            "код из смс", "код подтверждения", "пришлите код", "номер карты", "срок действия карты",
            "send the code", "verification code", "card number", "your password"
    };

    /** Aniq firibgarlik iboralari. */
    private static final String[] PRIZE = {
            "yutdingiz", "yutib oldingiz", "bonusni oling", "g'olib bo'ldingiz", "bepul premium",
            "tekin premium", "premium sovg'a", "bonus oling", "pul ishlang",
            "вы выиграли", "бесплатный премиум", "заберите приз", "получите подарок",
            "you won", "free premium", "claim your"
    };

    /** Oddiy xabarlarda ham ko'p uchraydigan so'zlar — faqat havola bilan birga bo'lsa shubhali. */
    private static final String[] PRIZE_WEAK = {
            "sovrin", "sovg'a", "приз", "подарок", "giveaway", "розыгрыш", "konkurs"
    };

    private static final String[] URGENCY = {
            "hisobingiz bloklan", "akkauntingiz bloklan", "24 soat ichida", "zudlik bilan", "shoshiling",
            "аккаунт заблокирован", "срочно", "account will be", "suspended", "act now"
    };

    private static final String[] INVESTMENT = {
            "kafolatlangan foyda", "kuniga $", "investitsiya qiling", "100% foyda", "pulingizni ikki",
            "гарантированный доход", "доход в день", "guaranteed profit", "double your money"
    };

    private LocalRules() {
    }

    /**
     * @param text     xabar matni (yashirin havolalar ham qo'shilgan bo'lishi mumkin)
     * @param fileName xabardagi fayl nomi yoki null
     */
    public static Result analyze(String text, String fileName) {
        Result r = new Result();
        if (text == null) {
            text = "";
        }
        String norm = normalize(text);

        if (fileName != null) {
            String f = fileName.toLowerCase(Locale.ROOT);
            for (String ext : DANGEROUS_FILES) {
                if (f.endsWith(ext)) {
                    r.add(SafetyVerdict.DANGEROUS, L.t("Xabarda dastur fayli bor (" + fileName + "). Notanish joydan kelgan .apk fayllarni o'rnatmang — ular telefoningizni boshqarib olishi mumkin.", "В сообщении есть файл программы (" + fileName + "). Не устанавливайте .apk из неизвестных источников — они могут захватить управление телефоном."));
                    break;
                }
            }
        }

        for (String url : LinkExtractor.extract(text)) {
            r.links.add(url);
            checkLink(url, r);
        }

        if (containsAny(norm, ASKS_SECRETS)) {
            r.add(SafetyVerdict.DANGEROUS, L.t("Xabarda maxfiy ma'lumot (SMS kod, karta raqami yoki parol) so'ralyapti. Bularni hech kimga bermang — Telegram xodimlari ham so'ramaydi.", "В сообщении просят секретные данные (SMS-код, номер карты или пароль). Никому их не сообщайте — сотрудники Telegram их тоже не просят."));
        }
        if (containsAny(norm, PRIZE)) {
            if (!r.links.isEmpty()) {
                r.add(SafetyVerdict.DANGEROUS, L.t("Sovrin yoki bepul sovg'a va'da qilinib, havolaga kirish so'ralyapti — bu firibgarlikning eng keng tarqalgan usuli.", "Обещают приз или бесплатный подарок и просят перейти по ссылке — самая распространённая схема мошенничества."));
            } else {
                r.add(SafetyVerdict.SUSPICIOUS, L.t("Sovrin yoki bepul sovg'a va'da qilinyapti.", "Обещают приз или бесплатный подарок."));
            }
        } else if (!r.links.isEmpty() && containsAny(norm, PRIZE_WEAK)) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Sovg'a yoki konkurs haqida yozilib, havola berilgan — havola rasmiy manbaga olib borishini tekshiring.", "Пишут о подарке или розыгрыше и дают ссылку — проверьте, что ссылка ведёт на официальный источник."));
        }
        if (containsAny(norm, URGENCY)) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Shoshiltirish yoki qo'rqitish bor (\"hisobingiz bloklanadi\", \"zudlik bilan\").", "Есть давление или запугивание («аккаунт будет заблокирован», «срочно»)."));
        }
        if (containsAny(norm, INVESTMENT)) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Kafolatlangan yoki juda katta foyda va'da qilinyapti.", "Обещают гарантированную или очень большую прибыль."));
        }

        if (r.reasons.isEmpty()) {
            r.reasons.add(r.links.isEmpty() ? noDanger() : noDangerLinks());
        }
        return r;
    }

    private static void checkLink(String url, Result r) {
        String lower = url.toLowerCase(Locale.ROOT);
        String host = LinkExtractor.host(url);
        if (host.isEmpty()) {
            return;
        }
        if (!matchesDomain(host, OFFICIAL_DOMAINS) && looksLikeTelegram(host)) {
            r.add(SafetyVerdict.DANGEROUS, "\"" + host + "\" — " + L.t("Telegram'ga o'xshatilgan soxta manzil. Rasmiy manzillar: t.me va telegram.org.", "поддельный адрес, похожий на Telegram. Официальные адреса: t.me и telegram.org."));
        }
        if (host.contains("xn--")) {
            r.add(SafetyVerdict.SUSPICIOUS, "\"" + host + "\" — " + L.t("manzilida boshqa alifbodagi o'xshash harflar ishlatilgan.", "в адресе использованы похожие буквы из другого алфавита."));
        }
        if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Havola nom o'rniga IP manzilga olib boradi (", "Ссылка ведёт на IP-адрес вместо имени (") + host + ").");
        }
        if (matchesDomain(host, SHORTENERS)) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Qisqartirilgan havola (", "Сокращённая ссылка (") + host + L.t("): asl manzil yashirin.", "): настоящий адрес скрыт."));
        }
        for (String tld : RISKY_TLDS) {
            if (host.endsWith(tld)) {
                r.add(SafetyVerdict.SUSPICIOUS, "\"" + host + "\" — " + L.t("firibgarlar ko'p ishlatadigan domen zonasi (", "доменная зона, популярная у мошенников (") + tld + ").");
                break;
            }
        }
        String path = stripQuery(lower);
        for (String ext : DANGEROUS_FILES) {
            if (path.endsWith(ext)) {
                r.add(SafetyVerdict.DANGEROUS, L.t("Havola dastur faylini (", "Ссылка скачивает файл программы (") + ext + L.t(") yuklab beradi — telefoningizga zararli dastur o'rnatilishi mumkin.", ") — на телефон может установиться вредоносная программа."));
                break;
            }
        }
        if (lower.startsWith("http://")) {
            r.note(L.t("Havola shifrlanmagan (http) ulanishdan foydalanadi.", "Ссылка использует незашифрованное соединение (http)."));
        }
    }

    private static boolean looksLikeTelegram(String host) {
        return host.contains("telegram") || host.contains("teleqram") || host.contains("telegrarn")
                || host.contains("telegarm") || host.contains("telegrm") || host.contains("telgram")
                || host.contains("teiegram") || host.contains("tlgrm") || host.contains("t-me")
                || host.startsWith("tme.") || host.contains(".tme.") || host.startsWith("t.me.");
    }

    private static boolean matchesDomain(String host, String[] domains) {
        for (String d : domains) {
            if (host.equals(d) || host.endsWith("." + d)) {
                return true;
            }
        }
        return false;
    }

    private static String stripQuery(String url) {
        int end = url.length();
        int q = url.indexOf('?');
        int h = url.indexOf('#');
        if (q >= 0) {
            end = Math.min(end, q);
        }
        if (h >= 0) {
            end = Math.min(end, h);
        }
        return url.substring(0, end);
    }

    private static boolean containsAny(String text, String[] needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace('ʻ', '\'').replace('‘', '\'').replace('’', '\'')
                .replace('`', '\'').replace('ʼ', '\'')
                .replaceAll("\\s+", " ");
    }
}
