package uz.cybergram;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matndan havolalarni ajratib oladi.
 */
public final class LinkExtractor {

    private static final Pattern URL = Pattern.compile(
            "(?i)((?:https?://|www\\.)[^\\s<>\"']+|\\b(?:[a-z0-9-]+\\.)+[a-z]{2,24}\\b(?:/[^\\s<>\"']*)?)");

    private LinkExtractor() {
    }

    public static List<String> extract(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher m = URL.matcher(text);
        while (m.find()) {
            String u = m.group(1);
            while (u.length() > 0 && ".,;:!?)]}".indexOf(u.charAt(u.length() - 1)) >= 0) {
                u = u.substring(0, u.length() - 1);
            }
            if (u.length() > 3 && !out.contains(u)) {
                out.add(u);
            }
        }
        return out;
    }

    /** "https://user@www.Example.com:8080/a?b" -> "example.com" */
    public static String host(String url) {
        String s = url.toLowerCase(Locale.ROOT);
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            s = s.substring(scheme + 3);
        }
        int end = s.length();
        for (char c : new char[]{'/', '?', '#'}) {
            int j = s.indexOf(c);
            if (j >= 0 && j < end) {
                end = j;
            }
        }
        s = s.substring(0, end);
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            s = s.substring(at + 1);
        }
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(0, colon);
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        return s;
    }
}