package uz.cybergram;

import java.net.IDN;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            if (v != SafetyVerdict.SAFE) {
                reasons.remove(noDanger());
                reasons.remove(noDangerLinks());
            }
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

    /** To'lov xizmatlari va banklar: nom → rasmiy domenlar. Nom boshqa saytda ishlatilsa — soxta sayt. */
    private static final String[][] BRANDS = {
            {"click", "click.uz"},
            {"payme", "payme.uz"},
            {"uzum", "uzum.uz", "uzumbank.uz", "uzum.com"},
            {"paynet", "paynet.uz"},
            {"humo", "humocard.uz", "humo.uz", "humo.tj"},
            {"uzcard", "uzcard.uz"},
            {"kapitalbank", "kapitalbank.uz"},
            {"agrobank", "agrobank.uz"},
            {"hamkorbank", "hamkorbank.uz"},
            {"ipakyuli", "ipakyulibank.uz"},
            {"ipakyulibank", "ipakyulibank.uz"},
            {"anorbank", "anorbank.uz"},
            {"tbc", "tbcbank.uz"},
            {"tbcbank", "tbcbank.uz"},
            {"xazna", "xazna.uz"},
            {"mygov", "my.gov.uz", "gov.uz"},
            {"soliq", "soliq.uz"},
    };

    /** O'xshatib yasaladigan nomlar: asl nom → rasmiy domenlar. */
    private static final String[][] LOOKALIKE = {
            {"click", "click.uz"},
            {"payme", "payme.uz"},
            {"uzumbank", "uzumbank.uz"},
            {"paynet", "paynet.uz"},
            {"humocard", "humocard.uz"},
            {"uzcard", "uzcard.uz"},
            {"kapitalbank", "kapitalbank.uz"},
            {"agrobank", "agrobank.uz"},
            {"hamkorbank", "hamkorbank.uz"},
            {"ipakyulibank", "ipakyulibank.uz"},
            {"anorbank", "anorbank.uz"},
            {"tbcbank", "tbcbank.uz"},
            {"xazna", "xazna.uz"},
            {"soliq", "soliq.uz"},
            {"telegram", "telegram.org", "t.me", "telegram.me"},
            {"google", "google.com", "google.co.uz"},
            {"youtube", "youtube.com", "youtu.be"},
            {"instagram", "instagram.com"},
            {"facebook", "facebook.com"},
            {"whatsapp", "whatsapp.com"},
    };

    /** Haqiqiy, boshqa ma'noli so'zlar — o'xshash deb hisoblanmaydi. */
    private static final String[] LOOKALIKE_EXCEPT = {"telegraf", "goggles", "clicks", "googlee"};

    /** Xavfli xabarlar ichidagi havolalar: havola bosilganda xabar konteksti ham hisobga olinadi. */
    private static final int CONTEXT_MAX = 200;
    private static final Map<String, String> CONTEXT = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CONTEXT_MAX;
        }
    };

    private static String contextKey(String url) {
        String u = stripQuery(url.toLowerCase(Locale.ROOT).trim());
        u = u.replaceFirst("^[a-z]+://", "").replaceFirst("^www\\.", "");
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /** Havola xavfli xabar ichida kelgan bo'lsa — sababini qaytaradi, aks holda null. */
    public static String contextReason(String url) {
        if (url == null) {
            return null;
        }
        synchronized (CONTEXT) {
            return CONTEXT.get(contextKey(url));
        }
    }

    private static void rememberContext(Result r) {
        if (r.verdict != SafetyVerdict.DANGEROUS || r.links.isEmpty()) {
            return;
        }
        String reason = L.t("Bu havola firibgarlik belgilari bor xabar ichida kelgan.", "Эта ссылка пришла в сообщении с признаками мошенничества.");
        synchronized (CONTEXT) {
            for (String link : r.links) {
                CONTEXT.put(contextKey(link), reason);
            }
        }
    }

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
        rememberContext(r);
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
        String brand = fakeBrand(host);
        if (brand == null && !looksLikeTelegram(host)) {
            String[] look = lookalike(host);
            if (look != null) {
                SafetyVerdict v;
                String how;
                switch (look[2]) {
                    case "0":
                        v = SafetyVerdict.DANGEROUS;
                        how = L.t("harflari o'xshash belgilarga (raqam yoki boshqa alifbo) almashtirilgan", "буквы заменены похожими символами (цифрами или буквами другого алфавита)");
                        break;
                    case "3":
                        v = isFinancial(look[0]) ? SafetyVerdict.DANGEROUS : SafetyVerdict.SUSPICIOUS;
                        how = L.t("nomi ishlatilgan, lekin bu rasmiy sayt emas", "используется название, но это не официальный сайт");
                        break;
                    default:
                        v = "2".equals(look[2]) ? SafetyVerdict.DANGEROUS : SafetyVerdict.SUSPICIOUS;
                        how = L.t("bitta harf bilan farq qiladi", "отличается одной буквой");
                        break;
                }
                r.add(v, "\"" + host + "\" — " + L.t("\"" + look[0] + "\" nomiga o'xshatilgan (" + how + "). Rasmiy manzil: " + look[1] + ". Soxta sayt bo'lishi mumkin.",
                        "похож на «" + look[0] + "» (" + how + "). Официальный адрес: " + look[1] + ". Может быть поддельным сайтом."));
            }
        }
        if (brand != null) {
            r.add(SafetyVerdict.DANGEROUS, "\"" + host + "\" — " + L.t("\"" + brand + "\" nomi ishlatilgan, lekin bu uning rasmiy sayti emas. Soxta to'lov yoki bank sahifasi bo'lishi mumkin — karta ma'lumotlarini kiritmang.", "используется название «" + brand + "», но это не его официальный сайт. Может быть поддельной страницей оплаты или банка — не вводите данные карты."));
        }
        if (!TrustedSites.isTrusted(host)) {
            String bait = TrustedSites.baitWord(host);
            if (bait != null) {
                r.add(SafetyVerdict.SUSPICIOUS, "\"" + host + "\" — " + L.t("sayt nomida firibgarlar ko'p ishlatadigan so'z bor (\"" + bait + "\").", "в названии сайта есть слово, популярное у мошенников («" + bait + "»)."));
            }
            if (TrustedSites.isOpenHosting(host)) {
                r.add(SafetyVerdict.SUSPICIOUS, "\"" + host + "\" — " + L.t("bepul hostingdagi sahifa: uni istalgan odam ochishi mumkin, soxta sahifalar ko'p uchraydi.", "страница на бесплатном хостинге: её может создать кто угодно, поддельные страницы встречаются часто."));
            }
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

    /**
     * Mashhur nomga o'xshatilgan domen: c1ick.uz, paymе.uz (kirill "е"), kapitalbnak.uz, kapital-bank.com.
     * @return {asl nom, rasmiy domen, tur} — tur: "0" o'xshash belgilar, "2" bitta harf (uzun nom), "1" bitta harf (qisqa nom); yo'q bo'lsa null
     */
    static String[] lookalike(String host) {
        String h = host;
        try {
            h = IDN.toUnicode(host).toLowerCase(Locale.ROOT);
        } catch (Exception ignore) {
        }
        if (TrustedSites.isTrusted(host)) {
            return null;
        }
        for (String[] b : LOOKALIKE) {
            for (int k = 1; k < b.length; k++) {
                if (h.equals(b[k]) || h.endsWith("." + b[k])) {
                    return null;
                }
            }
        }
        String[] labels = h.split("\\.");
        if (labels.length < 2) {
            return null;
        }
        List<String> cands = new ArrayList<>();
        for (int i = 0; i < labels.length - 1; i++) {
            if (labels[i].contains("-")) {
                cands.add(labels[i].replace("-", ""));
            }
            for (String t : labels[i].split("-")) {
                if (t.length() >= 4) {
                    cands.add(t);
                }
            }
        }
        for (String c : cands) {
            for (String ex : LOOKALIKE_EXCEPT) {
                if (c.equals(ex)) {
                    c = null;
                    break;
                }
            }
            if (c == null) {
                continue;
            }
            String sc = skeleton(c);
            for (String[] b : LOOKALIKE) {
                String brand = b[0];
                if (c.equals(brand)) {
                    // "kapital-bank", "google-bonus" kabi: asl nom ishlatilgan
                    return new String[]{brand, b[1], "3"};
                }
                if (sc.equals(skeleton(brand))) {
                    return new String[]{brand, b[1], "0"};
                }
                if (brand.length() >= 6 && c.length() >= 5 && Math.abs(c.length() - brand.length()) <= 1
                        && distance(c, brand) == 1) {
                    return new String[]{brand, b[1], brand.length() >= 7 ? "2" : "1"};
                }
            }
        }
        return null;
    }

    private static boolean isFinancial(String brand) {
        for (String[] b : BRANDS) {
            if (brand.startsWith(b[0]) || b[0].startsWith(brand)) {
                return true;
            }
        }
        return false;
    }

    /** Ko'zga bir xil ko'rinadigan belgilarni bitta shaklga keltiradi. */
    static String skeleton(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            switch (c) {
                case 'а': case 'α': case '4': case '@': c = 'a'; break;
                case 'е': case 'ё': case 'є': case '3': c = 'e'; break;
                case 'о': case 'ο': case '0': c = 'o'; break;
                case 'р': case 'ρ': c = 'p'; break;
                case 'с': case 'ϲ': c = 'c'; break;
                case 'у': case 'γ': c = 'y'; break;
                case 'х': case 'χ': c = 'x'; break;
                case 'к': case 'κ': c = 'k'; break;
                case 'м': c = 'm'; break;
                case 'ѕ': case '5': case '$': c = 's'; break;
                case 'ԁ': c = 'd'; break;
                case 'ј': c = 'j'; break;
                case 'ԛ': c = 'q'; break;
                case 'ν': c = 'v'; break;
                case '7': c = 't'; break;
                case '8': c = 'b'; break;
                case '9': c = 'g'; break;
                case 'і': case 'ӏ': case 'ι': case 'ɩ': case '1': case '!': case '|': case 'i': c = 'l'; break;
                default: break;
            }
            sb.append(c);
        }
        return sb.toString().replace("rn", "m").replace("vv", "w");
    }

    /** Damerau-Levenshtein (qo'shni harflar almashuvi ham 1 hisoblanadi). */
    static int distance(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[n][m];
    }

    /** Sayt nomida bank/to'lov xizmati nomi bor, lekin domen rasmiy emas — nomni qaytaradi. */
    static String fakeBrand(String host) {
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return null;
        }
        for (int i = 0; i < labels.length - 1; i++) {
            for (String token : labels[i].split("-")) {
                for (String[] b : BRANDS) {
                    if (!token.equals(b[0])) {
                        continue;
                    }
                    for (int k = 1; k < b.length; k++) {
                        if (host.equals(b[k]) || host.endsWith("." + b[k])) {
                            return null;
                        }
                    }
                    return b[0];
                }
            }
        }
        return null;
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
