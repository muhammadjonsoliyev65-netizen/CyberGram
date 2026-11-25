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
        final LocalRules.Result result = LocalRules.analyze(url, null);
        final Activity activity = findActivity(context);
        if (activity == null) {
            return result.verdict == SafetyVerdict.DANGEROUS;
        }
        if (result.verdict == SafetyVerdict.DANGEROUS) {
            showDialog(activity, url, result, openAnyway);
            return true;
        }

        final String normalized = SafeBrowsingClient.normalize(url);
        if (normalized == null || !SafeBrowsingClient.isConfigured() || !CyberSettings.onlineCheck(context)) {
            if (result.verdict == SafetyVerdict.SAFE) {
                return false;
            }
            showDialog(activity, url, result, openAnyway);
            return true;
        }

        // Onlayn tekshiruv (Google Safe Browsing)
        final AlertDialog loader = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        loader.setCanCancel(false);
        loader.show();
        SafeBrowsingClient.check(Collections.singletonList(url), (threats, ok) -> {
            try {
                loader.dismiss();
            } catch (Exception ignore) {
            }
            String threat = threats.get(normalized);
            if (threat != null) {
                result.addOnline(url, threat);
            }
            if (result.verdict == SafetyVerdict.SAFE) {
                bypassOnce = true;
                openAnyway.run();
            } else {
                showDialog(activity, url, result, openAnyway);
            }
        });
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
