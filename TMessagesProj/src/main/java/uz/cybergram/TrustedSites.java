package uz.cybergram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.List;
import java.util.Locale;

/**
 * Ishonchli saytlar ro'yxati va AI natijalarini eslab qolish.
 * Havola bosilganda: ishonchli sayt → darhol ochiladi; AI avval tekshirgan sayt → uning bahosi ishlatiladi;
 * notanish sayt → foydalanuvchidan so'raladi (Ochish / AI bilan tekshirish / Bekor).
 */
public final class TrustedSites {

    private static final String PREFS = "cybergram";
    private static final String AI_PREFIX = "ai_host_";
    private static final long AI_TTL_MS = 7L * 24 * 60 * 60 * 1000; // 7 kun

    /** Keng tarqalgan, ishonchli saytlar (subdomenlari ham). */
    private static final String[] TRUSTED = {
            // Telegram
            "t.me", "telegram.me", "telegram.org", "telegra.ph", "fragment.com",
            // Xalqaro
            "google.com", "google.co.uz", "youtube.com", "youtu.be", "wikipedia.org", "github.com",
            "instagram.com", "facebook.com", "x.com", "twitter.com", "linkedin.com", "tiktok.com",
            "apple.com", "microsoft.com", "live.com", "office.com", "yandex.uz", "yandex.ru", "yandex.com",
            "vk.com", "spotify.com", "netflix.com", "amazon.com", "coursera.org", "udemy.com",
            "openai.com", "chatgpt.com", "claude.ai", "anthropic.com", "gemini.google.com",
            // O'zbekiston
            "gov.uz", "my.gov.uz", "kun.uz", "gazeta.uz", "daryo.uz", "uzdaily.uz", "podrobno.uz",
            "uzum.uz", "olx.uz", "payme.uz", "click.uz", "uzcard.uz", "humocard.uz", "lex.uz",
            "edu.uz", "uzbekistan.travel", "uzumbank.uz", "uzum.com", "paynet.uz", "humo.uz",
            "kapitalbank.uz", "agrobank.uz", "hamkorbank.uz", "ipakyulibank.uz", "anorbank.uz",
            "tbcbank.uz", "xazna.uz", "soliq.uz"
    };

    /** Bepul hosting/forma xizmatlari — ularda istalgan odam sahifa ochishi mumkin (fishing ko'p). */
    private static final String[] OPEN_HOSTING = {
            "vercel.app", "netlify.app", "github.io", "pages.dev", "web.app", "firebaseapp.com",
            "glitch.me", "repl.co", "replit.app", "000webhostapp.com", "weebly.com", "wixsite.com",
            "blogspot.com", "sites.google.com", "forms.gle", "ngrok.io", "ngrok-free.app", "duckdns.org",
            "workers.dev", "onrender.com", "herokuapp.com", "tilda.ws", "carrd.co"
    };

    /** Domen nomida firibgarlar ko'p ishlatadigan so'zlar. */
    private static final String[] HOST_BAIT = {
            "login", "signin", "verify", "verif", "secure", "account", "update", "support", "wallet",
            "bonus", "gift", "free", "premium", "prize", "airdrop", "claim", "reward", "promo", "lottery",
            "sovga", "yutuq", "bepul", "aksiya"
    };

    private TrustedSites() {
    }

    public static boolean isTrusted(String host) {
        return host != null && !isOpenHosting(host) && matches(host, TRUSTED);
    }

    public static boolean isOpenHosting(String host) {
        return host != null && matches(host, OPEN_HOSTING);
    }

    /** Domen nomida "login", "bonus" kabi aldov so'z bormi. */
    public static String baitWord(String host) {
        if (host == null) {
            return null;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String w : HOST_BAIT) {
            if (h.contains(w)) {
                return w;
            }
        }
        return null;
    }

    private static boolean matches(String host, String[] domains) {
        String h = host.toLowerCase(Locale.ROOT);
        for (String d : domains) {
            if (h.equals(d) || h.endsWith("." + d)) {
                return true;
            }
        }
        return false;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** AI tahlil natijasini saytlar bo'yicha eslab qoladi. */
    public static void rememberAi(String payload, SafetyVerdict verdict) {
        try {
            List<String> links = LinkExtractor.extract(payload);
            if (links.isEmpty()) {
                return;
            }
            SharedPreferences.Editor e = prefs().edit();
            String value = verdict.name() + ":" + System.currentTimeMillis();
            for (String link : links) {
                String host = LinkExtractor.host(link);
                if (!host.isEmpty()) {
                    e.putString(AI_PREFIX + host, value);
                }
            }
            e.apply();
        } catch (Exception ignore) {
        }
    }

    /** Shu sayt uchun AI'ning so'nggi 7 kundagi bahosi yoki null. */
    public static SafetyVerdict aiVerdict(String host) {
        try {
            String v = prefs().getString(AI_PREFIX + host, null);
            if (v == null) {
                return null;
            }
            int p = v.indexOf(':');
            long time = Long.parseLong(v.substring(p + 1));
            if (System.currentTimeMillis() - time > AI_TTL_MS) {
                return null;
            }
            return SafetyVerdict.valueOf(v.substring(0, p));
        } catch (Exception e) {
            return null;
        }
    }
}
