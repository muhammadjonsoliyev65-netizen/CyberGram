package uz.cybergram;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI'ga yuborishdan OLDIN telefonning o'zida shaxsiy ma'lumotlarni yashiradi:
 * karta raqami, telefon, email, kodlar va havolalardagi tokenlar (?token=..., uzun ID'lar).
 * Server ham xuddi shu qoidalarni qo'llaydi (ikki qatlamli himoya).
 */
public final class Redact {

    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s<>\"']+");
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s()-]{8,}\\d");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d{4,}\\b");

    private Redact() {
    }

    /** Havoladan faqat sxema, sayt nomi va yo'l qoladi; ?query, #fragment, login va uzun ID'lar olib tashlanadi. */
    public static String url(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        String scheme = "http://";
        int p = u.indexOf("://");
        if (p > 0) {
            scheme = u.substring(0, p + 3).toLowerCase();
            u = u.substring(p + 3);
        }
        boolean hadQuery = false;
        int cut = indexOfAny(u, '?', '#');
        if (cut >= 0) {
            hadQuery = true;
            u = u.substring(0, cut);
        }
        int slash = u.indexOf('/');
        String host = slash >= 0 ? u.substring(0, slash) : u;
        String path = slash >= 0 ? u.substring(slash) : "";
        int at = host.lastIndexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1); // login:parol@sayt
        }
        StringBuilder sb = new StringBuilder(scheme).append(host.toLowerCase());
        if (!path.isEmpty()) {
            String[] parts = path.split("/", -1);
            for (int i = 1; i < parts.length; i++) {
                String seg = parts[i];
                boolean secretLike = seg.length() >= 16
                        || (seg.length() >= 8 && seg.matches(".*\\d.*") && seg.matches(".*[A-Za-z].*"));
                sb.append('/').append(secretLike ? "…" : seg);
            }
        }
        if (hadQuery) {
            sb.append("?…");
        }
        return sb.toString();
    }

    /** Erkin matndagi maxfiy ma'lumotlarni yashiradi (yordamchiga savol uchun). */
    public static String text(String text) {
        if (text == null) {
            return "";
        }
        Matcher m = URL.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(url(m.group())));
        }
        m.appendTail(out);
        String s = out.toString();
        s = EMAIL.matcher(s).replaceAll("[email]");
        s = CARD.matcher(s).replaceAll("[karta]");
        s = PHONE.matcher(s).replaceAll("[telefon]");
        s = NUMBER.matcher(s).replaceAll("[raqam]");
        return s;
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        if (i < 0) {
            return j;
        }
        if (j < 0) {
            return i;
        }
        return Math.min(i, j);
    }
}
