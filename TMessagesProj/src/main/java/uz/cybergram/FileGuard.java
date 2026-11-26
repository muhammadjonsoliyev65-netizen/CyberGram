package uz.cybergram;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 🛡 Fayl va APK qo'riqchi: chatdagi faylni ochishdan (yoki APK'ni o'rnatishdan) OLDIN tekshiradi.
 * Hammasi telefonning o'zida, internetsiz bajariladi. Hech narsa hech qayerga yuborilmaydi.
 */
public final class FileGuard {

    private static boolean bypassOnce;

    private static final Set<String> APK_EXT = set("apk", "xapk", "apks", "apkm");
    private static final Set<String> WINDOWS_EXEC = set("exe", "msi", "bat", "cmd", "scr", "com", "pif", "vbs", "vbe", "js", "jse", "wsf", "hta", "ps1", "jar", "lnk", "reg");
    private static final Set<String> ARCHIVES = set("zip", "rar", "7z", "tar", "gz");
    private static final Set<String> WEB_PAGES = set("html", "htm", "xhtml", "svg", "mht", "shtml");
    private static final Set<String> MACRO_DOCS = set("docm", "xlsm", "pptm", "dotm", "xltm");
    /** Niqob sifatida ishlatiladigan "zararsiz" kengaytmalar. */
    private static final Set<String> HARMLESS_LOOK = set("jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "txt", "mp4", "mp3", "mov");

    /** Fayl nomida tez-tez uchraydigan aldov so'zlar. */
    private static final String[] LURE = {
            "taklif", "to'y", "toy", "svadba", "свадьб", "приглаш", "rasm", "foto", "photo", "фото", "video", "видео",
            "sud", "суд", "chaqiruv", "повестк", "jarima", "штраф", "yordam", "помощь", "subsid", "nafaqa", "пособ",
            "prezident", "президент", "dokument", "hujjat", "документ", "check", "chek", "чек", "kvitans", "invoice",
            "bonus", "sovg'a", "подарок", "yutuq", "kredit", "кредит"
    };

    /** Taniqli ilovalar nomi → rasmiy paket nomi boshlanishi. Nomi mos, paketi boshqa bo'lsa — soxta. */
    private static final String[][] BRANDS = {
            {"telegram", "org.telegram."},
            {"whatsapp", "com.whatsapp"},
            {"instagram", "com.instagram."},
            {"google", "com.google."},
            {"youtube", "com.google.android.youtube"},
            {"payme", "uz.dida.payme"},
            {"click", "air.com.ssdsoftwaresolutions.clickuz"},
            {"uzum", "uz.uzum"},
            {"humo", "uz.humo"},
            {"my.gov", "uz.egov"},
            {"yandex", "ru.yandex."},
    };

    private FileGuard() {
    }

    /**
     * Faylni ochishdan oldin chaqiriladi.
     * @param openAnyway faylni Telegram'ning odatiy usulida ochish
     * @return true bo'lsa — hozir OCHMASLIK kerak (oyna ko'rsatildi)
     */
    public static boolean intercept(Activity activity, File file, String fileName, Runnable openAnyway) {
        if (bypassOnce) {
            bypassOnce = false;
            return false;
        }
        if (activity == null || file == null || !file.exists() || !CyberSettings.linkGuard(activity)) {
            return false;
        }
        Report report;
        try {
            report = analyze(activity, file, fileName != null ? fileName : file.getName());
        } catch (Throwable t) {
            return false;
        }
        if (report.verdict == SafetyVerdict.SAFE && !report.isApk) {
            return false; // oddiy fayl (rasm, PDF, ...) — so'ramasdan ochiladi
        }
        show(activity, file, report, openAnyway);
        return true;
    }

    // ------------------------------------------------------------------ tahlil

    static final class Report {
        SafetyVerdict verdict = SafetyVerdict.SAFE;
        final List<String> reasons = new ArrayList<>();
        final List<String> info = new ArrayList<>();
        boolean isApk;
        String sha256;

        void add(SafetyVerdict v, String reason) {
            verdict = verdict.max(v);
            if (!reasons.contains(reason)) {
                reasons.add(reason);
            }
        }
    }

    static Report analyze(Activity activity, File file, String fileName) throws Exception {
        Report r = new Report();
        String name = fileName.toLowerCase(Locale.ROOT).trim();
        String ext = extension(name);
        String magic = magic(file);

        // 1) Ikki kengaytma: "rasm.jpg.apk", "hujjat.pdf.exe"
        String inner = extension(name.substring(0, Math.max(0, name.length() - ext.length() - 1)));
        if (!inner.isEmpty() && HARMLESS_LOOK.contains(inner) && (APK_EXT.contains(ext) || WINDOWS_EXEC.contains(ext))) {
            r.add(SafetyVerdict.DANGEROUS, L.t("Ikki kengaytmali nom (\"." + inner + "." + ext + "\"): fayl o'zini rasm yoki hujjat qilib ko'rsatyapti, aslida esa dastur.",
                    "Двойное расширение («." + inner + "." + ext + "»): файл притворяется картинкой или документом, а на самом деле это программа."));
        }

        // 2) Haqiqiy turi: nomi rasm, ichi esa APK yoki Windows dasturi
        boolean zipLike = "zip".equals(magic);
        boolean apkInside = zipLike && looksLikeApk(file);
        if ("exe".equals(magic) && !WINDOWS_EXEC.contains(ext)) {
            r.add(SafetyVerdict.DANGEROUS, L.t("Fayl nomi \"." + ext + "\", lekin ichida Windows dasturi (.exe) bor — niqoblangan.",
                    "Файл назван «." + ext + "», но внутри программа Windows (.exe) — замаскирован."));
        }
        if (apkInside && !APK_EXT.contains(ext)) {
            r.add(SafetyVerdict.DANGEROUS, L.t("Fayl nomi \"." + ext + "\", lekin aslida bu Android ilovasi (APK) — niqoblangan.",
                    "Файл назван «." + ext + "», но на самом деле это приложение Android (APK) — замаскирован."));
        }

        // 3) Turiga qarab
        if (APK_EXT.contains(ext) || apkInside) {
            r.isApk = true;
            analyzeApk(activity, file, fileName, r);
        } else if (WINDOWS_EXEC.contains(ext)) {
            r.add(SafetyVerdict.DANGEROUS, L.t("Bu Windows dasturi yoki skript (." + ext + "). Telefonda ishlamaydi, kompyuterda ochilsa zararli bo'lishi mumkin.",
                    "Это программа или скрипт Windows (." + ext + "). На телефоне не работает, на компьютере может быть вредоносной."));
        } else if (ARCHIVES.contains(ext)) {
            if (zipLike) {
                inspectArchive(file, r);
            } else {
                r.add(SafetyVerdict.SUSPICIOUS, L.t("Arxiv fayli (." + ext + "): ichidagisini tekshirib bo'lmadi. Ichida dastur bo'lsa, o'rnatmang.",
                        "Архив (." + ext + "): содержимое проверить не удалось. Если внутри программа — не устанавливайте."));
            }
        } else if (WEB_PAGES.contains(ext)) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Veb-sahifa fayli (." + ext + "). Firibgarlar shu tarzda soxta kirish sahifalarini yuboradi — ochilgan sahifaga login, parol yoki karta raqamini kiritmang.",
                    "Файл веб-страницы (." + ext + "). Так мошенники присылают поддельные страницы входа — не вводите на ней логин, пароль или номер карты."));
        } else if (MACRO_DOCS.contains(ext)) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Makrosli ofis hujjati (." + ext + "). Kompyuterda ochganda \"makroslarni yoqish\"ni bosmang.",
                    "Офисный документ с макросами (." + ext + "). На компьютере не нажимайте «включить макросы»."));
        }

        if (r.isApk || r.verdict != SafetyVerdict.SAFE) {
            r.sha256 = sha256(file);
        }
        return r;
    }

    private static void analyzeApk(Activity activity, File file, String fileName, Report r) {
        // Telegram'dan kelgan har qanday APK — kamida shubhali (mutaxassislar tavsiyasi)
        r.add(SafetyVerdict.SUSPICIOUS, L.t("Bu ilova o'rnatish fayli (APK). Ilovalarni faqat Play Market'dan o'rnating — Telegram orqali kelgan APK'lar O'zbekistonda firibgarlikning eng keng tarqalgan usuli.",
                "Это установочный файл приложения (APK). Устанавливайте приложения только из Play Market — APK из Telegram самый распространённый способ мошенничества."));

        PackageManager pm = activity.getPackageManager();
        int flags = PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS;
        flags |= Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo pi = null;
        try {
            pi = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        } catch (Throwable ignore) {
        }
        if (pi == null) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("APK ichini o'qib bo'lmadi (buzilgan yoki bir nechta qismdan iborat). Bunday faylni o'rnatmang.",
                    "Не удалось прочитать APK (повреждён или состоит из нескольких частей). Не устанавливайте такой файл."));
            return;
        }

        String label = "";
        if (pi.applicationInfo != null) {
            pi.applicationInfo.sourceDir = file.getAbsolutePath();
            pi.applicationInfo.publicSourceDir = file.getAbsolutePath();
            try {
                CharSequence l = pi.applicationInfo.loadLabel(pm);
                label = l != null ? l.toString() : "";
            } catch (Throwable ignore) {
            }
        }
        String pkg = pi.packageName != null ? pi.packageName : "";
        r.info.add(L.t("Ilova: ", "Приложение: ") + (label.isEmpty() ? "?" : label) + " (" + pkg + ")");
        r.info.add(L.t("Hajmi: ", "Размер: ") + (file.length() / 1024) + " KB");

        Set<String> perms = new HashSet<>();
        if (pi.requestedPermissions != null) {
            for (String p : pi.requestedPermissions) {
                if (p != null) {
                    perms.add(p.replace("android.permission.", ""));
                }
            }
        }

        // SMS o'g'risi — O'zbekistondagi eng ko'p tarqalgan zararli dastur turi
        boolean sms = perms.contains("RECEIVE_SMS") || perms.contains("READ_SMS");
        if (sms) {
            r.add(SafetyVerdict.DANGEROUS, L.t("SMS xabarlaringizni o'qiy oladi — bank va to'lov kodlarini o'g'irlash uchun ishlatiladi (SMS o'g'risi belgisi).",
                    "Может читать ваши SMS — так крадут коды банков и платёжных систем (признак SMS-стилера)."));
        }
        if (perms.contains("SEND_SMS")) {
            r.add(SafetyVerdict.DANGEROUS, L.t("Sizning nomingizdan SMS yubora oladi.", "Может отправлять SMS от вашего имени."));
        }
        if (hasServicePermission(pi.services, "android.permission.BIND_ACCESSIBILITY_SERVICE")) {
            r.add(SafetyVerdict.DANGEROUS, L.t("\"Maxsus imkoniyatlar\" xizmatini so'raydi — ekraningizni o'qib, telefonni o'zi boshqara oladi.",
                    "Запрашивает «Специальные возможности» — может читать экран и сам управлять телефоном."));
        }
        if (hasServicePermission(pi.services, "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")) {
            r.add(SafetyVerdict.DANGEROUS, L.t("Barcha bildirishnomalaringizni (jumladan, kodlarni) o'qiy oladi.",
                    "Может читать все ваши уведомления (включая коды)."));
        }
        if (hasReceiverPermission(pi.receivers, "android.permission.BIND_DEVICE_ADMIN")) {
            r.add(SafetyVerdict.DANGEROUS, L.t("Qurilma administratori huquqini so'raydi — uni o'chirib tashlash qiyinlashadi.",
                    "Запрашивает права администратора устройства — его будет сложно удалить."));
        }
        if (perms.contains("SYSTEM_ALERT_WINDOW")) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Boshqa ilovalar ustida oyna chiqara oladi (soxta bank oynasi uchun ishlatiladi).",
                    "Может показывать окна поверх других приложений (используется для поддельных окон банков)."));
        }
        if (perms.contains("REQUEST_INSTALL_PACKAGES")) {
            r.add(file.length() < 3L * 1024 * 1024 ? SafetyVerdict.DANGEROUS : SafetyVerdict.SUSPICIOUS,
                    L.t("O'zi boshqa ilovalarni o'rnata oladi" + (file.length() < 3L * 1024 * 1024 ? " va juda kichik — bu \"dropper\" (keyin asl zararli dasturni yuklaydi) belgisi." : "."),
                            "Может сам устанавливать другие приложения" + (file.length() < 3L * 1024 * 1024 ? " и очень маленький — признак «дроппера» (потом скачивает настоящий вредонос)." : ".")));
        }
        if (perms.contains("READ_CONTACTS") && sms) {
            r.add(SafetyVerdict.DANGEROUS, L.t("Kontaktlaringizni o'qiy oladi — zararli dastur o'zini do'stlaringizga ham yuboradi.",
                    "Может читать контакты — вредонос рассылает себя вашим друзьям."));
        }
        if (perms.contains("CALL_PHONE") || perms.contains("READ_CALL_LOG") || perms.contains("ANSWER_PHONE_CALLS")) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Qo'ng'iroqlarni boshqara oladi yoki qo'ng'iroqlar tarixini o'qiy oladi.",
                    "Может управлять звонками или читать журнал звонков."));
        }

        // Niqob: fayl nomi "taklifnoma", ilova esa "Settings"
        String lowerFile = fileName.toLowerCase(Locale.ROOT);
        String lowerLabel = label.toLowerCase(Locale.ROOT);
        for (String w : LURE) {
            if (lowerFile.contains(w) && !lowerLabel.contains(w)) {
                r.add(SafetyVerdict.DANGEROUS, L.t("Fayl nomi \"" + fileName + "\", lekin ichidagi ilova boshqa (\"" + label + "\") — bu niqob.",
                        "Имя файла «" + fileName + "», но внутри другое приложение («" + label + "») — это маскировка."));
                break;
            }
        }
        // Taniqli ilova nomidan foydalanish
        for (String[] b : BRANDS) {
            if (lowerLabel.contains(b[0]) && !pkg.startsWith(b[1])) {
                r.add(SafetyVerdict.DANGEROUS, L.t("O'zini \"" + label + "\" deb ko'rsatyapti, lekin rasmiy ilova emas (paket nomi boshqa).",
                        "Выдаёт себя за «" + label + "», но это не официальное приложение (другое имя пакета)."));
                break;
            }
        }

        // Imzo
        X509Certificate cert = certificate(pi);
        if (cert != null) {
            String subject = String.valueOf(cert.getSubjectX500Principal().getName());
            if (subject.contains("Android Debug")) {
                r.add(SafetyVerdict.DANGEROUS, L.t("Sinov (debug) kaliti bilan imzolangan — haqiqiy ilovalar bunday tarqatilmaydi.",
                        "Подписан тестовым (debug) ключом — настоящие приложения так не распространяются."));
            }
            // Telefonda shu nomli ilova bor, lekin imzo boshqa — soxta "yangilanish"
            try {
                PackageInfo installed = pm.getPackageInfo(pkg, Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
                X509Certificate installedCert = certificate(installed);
                if (installedCert != null && !installedCert.equals(cert)) {
                    r.add(SafetyVerdict.DANGEROUS, L.t("Telefoningizda \"" + pkg + "\" ilovasi bor, lekin bu fayl BOSHQA kalit bilan imzolangan — soxta \"yangilanish\".",
                            "На телефоне уже есть «" + pkg + "», но этот файл подписан ДРУГИМ ключом — поддельное «обновление»."));
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private static void inspectArchive(File file, Report r) {
        try (ZipFile zip = new ZipFile(file)) {
            int count = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements() && count++ < 2000) {
                String n = entries.nextElement().getName().toLowerCase(Locale.ROOT);
                String e = extension(n);
                if (APK_EXT.contains(e)) {
                    r.add(SafetyVerdict.DANGEROUS, L.t("Arxiv ichida Android ilovasi bor (" + n + ") — o'rnatmang.", "В архиве приложение Android (" + n + ") — не устанавливайте."));
                    return;
                }
                if (WINDOWS_EXEC.contains(e)) {
                    r.add(SafetyVerdict.DANGEROUS, L.t("Arxiv ichida Windows dasturi bor (" + n + ").", "В архиве программа Windows (" + n + ")."));
                    return;
                }
            }
        } catch (Throwable t) {
            r.add(SafetyVerdict.SUSPICIOUS, L.t("Arxivni ochib bo'lmadi (parolli yoki buzilgan bo'lishi mumkin) — parolli arxivlar zararli dasturni yashirish uchun ishlatiladi.",
                    "Не удалось открыть архив (возможно, с паролем или повреждён) — архивы с паролем используют, чтобы спрятать вредонос."));
        }
    }

    // ------------------------------------------------------------------ oyna

    private static void show(final Activity activity, final File file, final Report r, final Runnable openAnyway) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(r.verdict.label());
        sb.setSpan(new ForegroundColorSpan(r.verdict.color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n\n");
        for (String i : r.info) {
            sb.append(i).append("\n");
        }
        if (!r.info.isEmpty()) {
            sb.append("\n");
        }
        for (String reason : r.reasons) {
            sb.append("• ").append(reason).append("\n");
        }
        sb.append(L.t("\nTekshiruv telefonda bajarildi va 100% kafolat bermaydi.", "\nПроверка выполнена на телефоне и не даёт 100% гарантии."));
        if (r.verdict == SafetyVerdict.DANGEROUS) {
            sb.append(L.t("\n\n❗ Agar shunday faylni oldin o'rnatgan bo'lsangiz: internetni o'chiring, ilovani o'chirib tashlang va bankingizga qo'ng'iroq qilib, kartani vaqtincha bloklang.",
                    "\n\n❗ Если вы уже устанавливали такой файл: отключите интернет, удалите приложение и позвоните в банк, чтобы временно заблокировать карту."));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(r.isApk ? L.t("🛡 APK qo'riqchi", "🛡 Защита от APK") : L.t("🛡 Fayl qo'riqchi", "🛡 Проверка файла"));
        builder.setMessage(sb);
        builder.setPositiveButton(L.t("Yopish", "Закрыть"), (d, w) -> d.dismiss());
        if (r.verdict == SafetyVerdict.DANGEROUS) {
            builder.setNegativeButton(L.t("🗑 Faylni o'chirish", "🗑 Удалить файл"), (d, w) -> {
                try {
                    file.delete();
                } catch (Throwable ignore) {
                }
                d.dismiss();
            });
        } else {
            builder.setNegativeButton(r.isApk ? L.t("Baribir o'rnatish", "Всё равно установить") : L.t("Baribir ochish", "Всё равно открыть"), (d, w) -> {
                d.dismiss();
                bypassOnce = true;
                openAnyway.run();
            });
        }
        if (r.sha256 != null) {
            // Faqat faylning "barmoq izi" (SHA-256) ochiladi — faylning o'zi hech qayerga yuborilmaydi
            builder.setNeutralButton(L.t("🔎 70+ antivirus bilan tekshirish", "🔎 Проверить 70+ антивирусами"), (d, w) -> Browser.openUrl(activity, "https://www.virustotal.com/gui/file/" + r.sha256));
        }
        builder.show();
    }

    // ------------------------------------------------------------------ yordamchilar

    private static boolean hasServicePermission(ServiceInfo[] services, String permission) {
        if (services == null) {
            return false;
        }
        for (ServiceInfo s : services) {
            if (s != null && permission.equals(s.permission)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReceiverPermission(ActivityInfo[] receivers, String permission) {
        if (receivers == null) {
            return false;
        }
        for (ActivityInfo a : receivers) {
            if (a != null && permission.equals(a.permission)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static X509Certificate certificate(PackageInfo pi) {
        try {
            Signature[] sigs = null;
            if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
                sigs = pi.signingInfo.getApkContentsSigners();
            }
            if (sigs == null || sigs.length == 0) {
                sigs = pi.signatures;
            }
            if (sigs == null || sigs.length == 0) {
                return null;
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(sigs[0].toByteArray()));
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean looksLikeApk(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            return zip.getEntry("AndroidManifest.xml") != null && zip.getEntry("classes.dex") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Faylning boshidagi baytlar bo'yicha haqiqiy turi: "zip", "exe" yoki "". */
    private static String magic(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] b = new byte[4];
            int n = in.read(b);
            if (n >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4) {
                return "zip";
            }
            if (n >= 2 && b[0] == 'M' && b[1] == 'Z') {
                return "exe";
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    private static String sha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest()) {
                sb.append(String.format(Locale.ROOT, "%02x", x));
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 && i < name.length() - 1 ? name.substring(i + 1) : "";
    }

    private static Set<String> set(String... items) {
        Set<String> s = new HashSet<>();
        for (String i : items) {
            s.add(i);
        }
        return s;
    }
}
