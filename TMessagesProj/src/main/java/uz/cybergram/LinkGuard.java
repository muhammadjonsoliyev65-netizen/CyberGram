package uz.cybergram;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import org.telegram.ui.ActionBar.AlertDialog;

import java.util.Collections;

/**
 * Havola ochilishidan oldin uni avtomatik tekshiradi:
 * 1) oflayn qoidalar (bir zumda), 2) Google Safe Browsing (onlayn).
 * Xavfli havolalar bloklanadi, shubhalilari uchun ogohlantirish chiqadi.
 */
public final class LinkGuard {

    /** "Baribir ochish" yoki tekshiruvdan o'tgandan keyin bitta ochishni o'tkazib yuborish uchun. */
    private static boolean bypassOnce;

    private LinkGuard() {
    }

    /**
     * @param openAnyway havolani ochadigan kod (tekshiruvdan o'tsa yoki foydalanuvchi ruxsat bersa)
     * @return true bo'lsa, havolani hozir OCHMASLIK kerak
     */
    public static boolean intercept(Context context, String url, Runnable openAnyway) {
        if (bypassOnce) {
            bypassOnce = false;
            return false;
        }
        if (url == null || !CyberSettings.linkGuard(context)) {
            return false;
        }
        final String msgContext = LocalRules.contextReason(url);
        final LocalRules.Result result = LocalRules.analyze(url, null);
        if (msgContext != null) {
            if (TrustedSites.isTrusted(LinkExtractor.host(url)) && result.verdict == SafetyVerdict.SAFE) {
                // Rasmiy sayt, lekin shubhali xabar ichida — bloklanmaydi, foydalanuvchi roziligi bilan ochiladi
                result.add(SafetyVerdict.SUSPICIOUS, L.t(
                        "Bu sayt rasmiy, lekin u firibgarlik belgilari bor xabar ichida kelgan. Ochsangiz ham, SMS kod, parol yoki karta ma'lumotlarini hech kimga bermang.",
                        "Этот сайт официальный, но он пришёл в сообщении с признаками мошенничества. Даже если откроете, никому не сообщайте SMS-код, пароль или данные карты."));
            } else {
                result.add(SafetyVerdict.DANGEROUS, msgContext);
            }
        }
        final Activity activity = findActivity(context);
        if (activity == null) {
            return result.verdict == SafetyVerdict.DANGEROUS;
        }
        // AI shu saytni avval tekshirgan bo'lsa — uning bahosi ham hisobga olinadi
        final String host = LinkExtractor.host(url);
        SafetyVerdict remembered = TrustedSites.aiVerdict(host);
        if (remembered != null && remembered != SafetyVerdict.SAFE) {
            result.add(remembered, L.t("AI bu saytni avval tekshirgan va ", "ИИ ранее проверял этот сайт и оценил его как: ")
                    + L.t(remembered == SafetyVerdict.DANGEROUS ? "xavfli deb baholagan." : "shubhali deb baholagan.",
                    remembered == SafetyVerdict.DANGEROUS ? "опасный." : "подозрительный."));
        }
        if (result.verdict == SafetyVerdict.DANGEROUS) {
            showDialog(activity, url, result, openAnyway);
            return true;
        }

        final String normalized = SafeBrowsingClient.normalize(url);
        final boolean online = CyberSettings.onlineCheck(context) && isNetworkOnline();
        final boolean knownEstablished = UrlCheck.isKnownEstablished(host);
        final boolean useSafeBrowsing = online && normalized != null && SafeBrowsingClient.isConfigured();
        final boolean useServer = online && !knownEstablished && CyberApiClient.isConfigured() && !host.isEmpty()
                && !TrustedSites.isTrusted(host);
        if (!useSafeBrowsing && !useServer) {
            if (result.verdict == SafetyVerdict.SAFE) {
                return askIfUnknown(activity, url, host, result, openAnyway, knownEstablished);
            }
            showDialog(activity, url, result, openAnyway);
            return true;
        }

        // Onlayn tekshiruv: Google Safe Browsing va CyberGram server BIR VAQTDA (parallel) ishlaydi —
        // sekin internetda kutish ikki baravar qisqaradi. Ikkalasi tugagach (yoki vaqt o'tgach) natija chiqadi.
        final AlertDialog loader = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        loader.setCanCancel(false);
        loader.show();
        final int[] pending = {(useSafeBrowsing ? 1 : 0) + (useServer ? 1 : 0)};
        final boolean[] established = {knownEstablished};
        final Runnable oneDone = () -> {
            pending[0]--;
            if (pending[0] == 0) {
                finish(activity, loader, url, host, result, openAnyway, established[0]);
            }
        };
        if (useSafeBrowsing) {
            SafeBrowsingClient.check(Collections.singletonList(url), (threats, ok) -> {
                String threat = threats.get(normalized);
                if (threat != null) {
                    result.addOnline(url, threat);
                }
                oneDone.run();
            });
        }
        if (useServer) {
            UrlCheck.check(url, info -> {
                if (UrlCheck.apply(info, result)) {
                    established[0] = true;
                    UrlCheck.rememberEstablished(host);
                }
                oneDone.run();
            });
        }
        return true;
    }

    /** Internet umuman yo'q bo'lsa, onlayn tekshiruvni kutib o'tirmaymiz. */
    private static boolean isNetworkOnline() {
        try {
            return org.telegram.messenger.ApplicationLoader.isNetworkOnline();
        } catch (Throwable e) {
            return true;
        }
    }

    private static void finish(Activity activity, AlertDialog loader, String url, String host, LocalRules.Result result,
                               Runnable openAnyway, boolean established) {
        try {
            loader.dismiss();
        } catch (Exception ignore) {
        }
        if (result.verdict == SafetyVerdict.SAFE) {
            if (!askIfUnknown(activity, url, host, result, openAnyway, established)) {
                bypassOnce = true;
                openAnyway.run();
            }
        } else {
            showDialog(activity, url, result, openAnyway);
        }
    }

    /**
     * Xavf topilmagan, lekin sayt notanish bo'lsa — foydalanuvchidan so'raydi.
     * @return true bo'lsa, oyna ko'rsatildi (havolani hozir ochmaslik kerak)
     */
    private static boolean askIfUnknown(Activity activity, String url, String host, LocalRules.Result result, Runnable openAnyway,
                                        boolean established) {
        if (established || !CyberSettings.askUnknown(activity) || host.isEmpty() || TrustedSites.isTrusted(host)
                || TrustedSites.aiVerdict(host) == SafetyVerdict.SAFE) {
            return false;
        }
        final Runnable open = () -> {
            bypassOnce = true;
            openAnyway.run();
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(L.t("🛡 Notanish sayt", "🛡 Незнакомый сайт"));
        builder.setMessage(host + "\n\n"
                + L.t("Telefondagi tekshiruv xavf topmadi, lekin bu sayt ishonchli saytlar ro'yxatida yo'q. Yangi soxta saytlar hali hech qaysi bazaga tushmagan bo'lishi mumkin.\n\nOchishdan oldin AI bilan tekshirib ko'rasizmi?",
                "Проверка на телефоне не нашла угроз, но этого сайта нет в списке надёжных. Новые поддельные сайты могут ещё не попасть ни в одну базу.\n\nПроверить с помощью ИИ перед открытием?"));
        builder.setPositiveButton(L.t("🤖 Tekshirish", "🤖 Проверить"), (dialog, which) -> {
            dialog.dismiss();
            CyberAiDialog.scan(activity, Redact.url(url), result.verdict, open);
        });
        builder.setNegativeButton(L.t("Ochish", "Открыть"), (dialog, which) -> {
            dialog.dismiss();
            open.run();
        });
        builder.setNeutralButton(L.t("Bekor", "Отмена"), (dialog, which) -> dialog.dismiss());
        builder.show();
        return true;
    }

    private static void showDialog(Activity activity, String url, LocalRules.Result result, Runnable openAnyway) {
        boolean dangerous = result.verdict == SafetyVerdict.DANGEROUS;

        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(result.verdict.label());
        sb.setSpan(new ForegroundColorSpan(result.verdict.color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n\n").append(url).append("\n\n");
        for (String reason : result.reasons) {
            sb.append("• ").append(reason).append("\n");
        }
        for (String note : result.notes) {
            sb.append("ℹ️ ").append(note).append("\n");
        }
        if (result.online) {
            sb.append(SafeBrowsingClient.ADVISORY.trim()).append("\n");
        }
        sb.append(dangerous
                ? L.t("\nCyberGram sizni himoya qilish uchun bu havolani ochmadi.", "\nCyberGram не открыл эту ссылку, чтобы защитить вас.")
                : L.t("\nHavolani faqat manbaga ishonsangiz oching.", "\nОткрывайте ссылку, только если доверяете источнику."));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(dangerous ? L.t("🛡 Havola bloklandi", "🛡 Ссылка заблокирована") : L.t("🛡 Ehtiyot bo'ling", "🛡 Будьте осторожны"));
        builder.setMessage(sb);
        if (dangerous) {
            builder.setPositiveButton(L.t("Tushunarli", "Понятно"), (dialog, which) -> dialog.dismiss());
        } else {
            builder.setPositiveButton(L.t("Bekor qilish", "Отмена"), (dialog, which) -> dialog.dismiss());
            builder.setNegativeButton(L.t("Baribir ochish", "Всё равно открыть"), (dialog, which) -> {
                dialog.dismiss();
                bypassOnce = true;
                openAnyway.run();
            });
        }
        builder.show();
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
