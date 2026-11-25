package uz.cybergram;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.AlertDialog;

/**
 * "🛡 Tekshirish" natijasini ko'rsatadigan oyna.
 */
public final class CyberScanDialog {

    private CyberScanDialog() {
    }

    /** Xabar matni + yashirin havolalar. */
    public static String collectText(MessageObject messageObject) {
        StringBuilder sb = new StringBuilder();
        if (messageObject == null || messageObject.messageOwner == null) {
            return "";
        }
        TLRPC.Message msg = messageObject.messageOwner;
        if (msg.message != null) {
            sb.append(msg.message);
        }
        if (msg.entities != null) {
            for (TLRPC.MessageEntity entity : msg.entities) {
                if (entity instanceof TLRPC.TL_messageEntityTextUrl && entity.url != null) {
                    sb.append('\n').append(entity.url);
                }
            }
        }
        appendButtons(msg, sb);
        return sb.toString();
    }

    /** Xabar ostidagi tugmalar: matni va ichidagi havolalar. */
    private static void appendButtons(TLRPC.Message msg, StringBuilder sb) {
        try {
            if (!(msg.reply_markup instanceof TLRPC.TL_replyInlineMarkup)) {
                return;
            }
            TLRPC.TL_replyInlineMarkup markup = (TLRPC.TL_replyInlineMarkup) msg.reply_markup;
            if (markup.rows == null) {
                return;
            }
            for (TL_keyboard.KeyboardInlineButtonRow row : markup.rows) {
                if (row == null || row.buttons == null) {
                    continue;
                }
                for (TL_keyboard.KeyboardInlineButton button : row.buttons) {
                    if (button == null) {
                        continue;
                    }
                    if (button.text != null) {
                        sb.append('\n').append(button.text);
                    }
                    String url = null;
                    if (button.type instanceof TL_keyboard.TL_inlineButtonTypeUrl) {
                        url = ((TL_keyboard.TL_inlineButtonTypeUrl) button.type).url;
                    } else if (button.type instanceof TL_keyboard.TL_inlineButtonTypeWebView) {
                        url = ((TL_keyboard.TL_inlineButtonTypeWebView) button.type).url;
                    } else if (button.type instanceof TL_keyboard.TL_inlineButtonTypeUrlAuth) {
                        url = ((TL_keyboard.TL_inlineButtonTypeUrlAuth) button.type).url;
                    }
                    if (url != null && !url.isEmpty()) {
                        sb.append('\n').append(url);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public static String fileName(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        TLRPC.Document document = messageObject.getDocument();
        return document != null ? FileLoader.getDocumentFileName(document) : null;
    }

    /** .apk va shunga o'xshash o'rnatiladigan fayllar */
    public static boolean isAppFile(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase();
        return n.endsWith(".apk") || n.endsWith(".xapk") || n.endsWith(".apks") || n.endsWith(".exe");
    }

    /** AI'ga yuboriladigan ma'lumot: faqat havolalar va fayl nomi (xabar matni emas). */
    static String aiPayload(LocalRules.Result result, String file) {
        StringBuilder sb = new StringBuilder();
        for (String link : result.links) {
            sb.append(Redact.url(link)).append('\n');
        }
        if (isAppFile(file)) {
            sb.append("File: ").append(file).append('\n');
        }
        return sb.toString().trim();
    }

    /** AI tahlili faqat havola yoki ilova fayli bor xabarlarda */
    public static boolean needsAi(LocalRules.Result result, String file) {
        return !result.links.isEmpty() || isAppFile(file);
    }

    public static void show(final Context context, MessageObject messageObject) {
        final String file = fileName(messageObject);
        final String body = collectText(messageObject);
        final LocalRules.Result result = LocalRules.analyze(body, file);
        final boolean ai = needsAi(result, file);
        // Telegram qoidasi (ToS 1.5): AI'ga xabar matni YUBORILMAYDI — faqat havola manzillari va fayl nomi
        final String text = aiPayload(result, file);
        if (result.links.isEmpty() || !SafeBrowsingClient.isConfigured() || !CyberSettings.onlineCheck(context)) {
            render(context, result, false, text, ai);
            return;
        }
        final AlertDialog loader = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        loader.setCanCancel(false);
        loader.show();
        SafeBrowsingClient.check(result.links, (threats, ok) -> {
            try {
                loader.dismiss();
            } catch (Exception ignore) {
            }
            for (String link : result.links) {
                String normalized = SafeBrowsingClient.normalize(link);
                String threat = normalized != null ? threats.get(normalized) : null;
                if (threat != null) {
                    result.addOnline(link, threat);
                }
            }
            render(context, result, ok, text, ai);
        });
    }

    private static void render(final Context context, LocalRules.Result result, boolean checkedOnline, final String text, boolean ai) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(result.verdict.label());
        sb.setSpan(new ForegroundColorSpan(result.verdict.color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n\n");
        for (String reason : result.reasons) {
            sb.append("• ").append(reason).append("\n");
        }
        for (String note : result.notes) {
            sb.append("• ").append(note).append("\n");
        }
        if (!result.links.isEmpty()) {
            sb.append(L.t("\nTopilgan havolalar:\n", "\nНайденные ссылки:\n"));
            for (String link : result.links) {
                sb.append("   ").append(link).append("\n");
            }
        }
        if (result.links.isEmpty()) {
            sb.append(L.t("\nTekshiruv telefoningizda bajarildi va 100% kafolat bermaydi.", "\nПроверка выполнена на вашем телефоне и не даёт 100% гарантии."));
        } else if (checkedOnline) {
            sb.append(L.t("\nHavolalar Google Safe Browsing orqali ham tekshirildi. Baribir 100% kafolat yo'q — shubha bo'lsa, ochmang.", "\nСсылки также проверены через Google Safe Browsing. 100% гарантии всё равно нет — если сомневаетесь, не открывайте.") + SafeBrowsingClient.ADVISORY);
        } else {
            sb.append(L.t("\nOnlayn tekshiruv bajarilmadi (internet yo'qmi?) — faqat telefondagi qoidalar ishlatildi.", "\nОнлайн-проверка не выполнена (нет интернета?) — использованы только правила на телефоне."));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(L.t("🛡 CyberGram tekshiruvi", "🛡 Проверка CyberGram"));
        builder.setMessage(sb);
        builder.setPositiveButton(L.t("Tushunarli", "Понятно"), (dialog, which) -> dialog.dismiss());
        if (ai && text != null && !text.trim().isEmpty() && CyberApiClient.isConfigured() && CyberSettings.ai(context)) {
            builder.setNegativeButton(L.t("🤖 AI tahlili", "🤖 Анализ ИИ"), (dialog, which) -> {
                dialog.dismiss();
                CyberAiDialog.scan(context, text, result.verdict);
            });
        }
        builder.show();
    }
}
