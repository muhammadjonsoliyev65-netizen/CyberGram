package uz.cybergram;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.FiltersSetupActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LoginActivity;
import org.telegram.ui.ThemeActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;

/**
 * Yon menyu (☰): pastki panel o'rniga. Profil, Kontaktlar, Qo'ng'iroqlar, Saqlangan xabarlar, Sozlamalar
 * va CyberGram bo'limlari shu yerda.
 */
public final class SideMenu {

    private static final String PREFS = "cybergram";
    private static final String KEY = "side_menu";

    private static Dialog current;

    private SideMenu() {
    }

    /** Pastki panel o'rniga yon menyu ishlatilsinmi (o'zgarish ilova qayta ochilganda kuchga kiradi). */
    public static boolean enabled() {
        try {
            return prefs().getBoolean(KEY, true);
        } catch (Throwable e) {
            return true;
        }
    }

    public static void setEnabled(boolean value) {
        prefs().edit().putBoolean(KEY, value).apply();
    }

    // --- Sarlavha rangi (foydalanuvchi tanlaydi) ---
    private static final String KEY_COLOR = "side_color";
    private static final int[][] PALETTE = {
            {0xFF4A90D9, 0xFF2F6DB5}, // Ko'k
            {0xFF8E6CEF, 0xFF5E3FC4}, // Binafsha
            {0xFF3CC37A, 0xFF1F8F55}, // Yashil
            {0xFF2EC4C9, 0xFF16858F}, // Moviy (feruza)
            {0xFFFF9F45, 0xFFE06A1B}, // To'q sariq
            {0xFFF25C5C, 0xFFC23535}, // Qizil
            {0xFFF06EA9, 0xFFC23F7E}, // Pushti
            {0xFF4A5568, 0xFF262C36}, // Grafit (to'q)
    };

    public static CharSequence[] colorNames() {
        return new CharSequence[]{
                L.t("🔵 Ko'k", "🔵 Синий"), L.t("🟣 Binafsha", "🟣 Фиолетовый"), L.t("🟢 Yashil", "🟢 Зелёный"),
                L.t("🩵 Feruza", "🩵 Бирюзовый"), L.t("🟠 To'q sariq", "🟠 Оранжевый"), L.t("🔴 Qizil", "🔴 Красный"),
                L.t("🩷 Pushti", "🩷 Розовый"), L.t("⚫ Grafit", "⚫ Графит"), L.t("🎨 Mavzu rangi (Telegram mavzusi bo'yicha)", "🎨 Цвет темы Telegram")
        };
    }

    public static int getColorIndex() {
        try {
            return prefs().getInt(KEY_COLOR, 0);
        } catch (Throwable e) {
            return 0;
        }
    }

    public static void setColorIndex(int index) {
        prefs().edit().putInt(KEY_COLOR, index).apply();
    }

    private static int[] headerColors() {
        int i = getColorIndex();
        if (i >= 0 && i < PALETTE.length) {
            return PALETTE[i];
        }
        // Mavzu rangi: Telegram mavzusining sarlavha rangi; juda och bo'lsa matn ko'rinmay qoladi, shuning uchun qorong'ilashtiramiz
        int c = Theme.getColor(Theme.key_actionBarDefault);
        if (luminance(c) > 0.6f) {
            c = Theme.getColor(Theme.key_chats_actionBackground);
        }
        if (luminance(c) > 0.6f) {
            return PALETTE[0];
        }
        return new int[]{c, darker(c)};
    }

    private static float luminance(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    private static int darker(int c) {
        return Color.rgb((int) (Color.red(c) * 0.78f), (int) (Color.green(c) * 0.78f), (int) (Color.blue(c) * 0.78f));
    }

    private static void applyHeaderColor(View header, GradientDrawable bg) {
        bg.setColors(headerColors());
        header.invalidate();
    }

    /** Rang tanlash oynasi (yon menyudagi 🎨 va CyberGram sozlamalaridan). */
    public static void chooseColor(Context context, Runnable after) {
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        b.setTitle(L.t("Yon menyu rangi", "Цвет бокового меню"));
        CharSequence[] names = colorNames();
        int cur = getColorIndex();
        if (cur >= 0 && cur < names.length) {
            names[cur] = names[cur] + "  ✓";
        }
        b.setItems(names, (d, which) -> {
            setColorIndex(which);
            d.dismiss();
            if (after != null) {
                after.run();
            }
        });
        b.setPositiveButton(L.t("Yopish", "Закрыть"), (d, w) -> d.dismiss());
        b.show();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int dp(float v) {
        return AndroidUtilities.dp(v);
    }

    // Rangli doira ichidagi oq belgilar (Telegram sozlamalari uslubida)
    private static final int C_BLUE = 0xFF3D8EF0, C_GREEN = 0xFF34C759, C_ORANGE = 0xFFFF9500, C_PURPLE = 0xFFAF52DE,
            C_RED = 0xFFF2484A, C_TEAL = 0xFF2CB5C9, C_INDIGO = 0xFF5B5FE0, C_GRAY = 0xFF8E8E93, C_PINK = 0xFFFF5D8F;

    public static void show(final BaseFragment fragment) {
        final Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (current != null && current.isShowing()) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        current = dialog;

        final int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        final int width = Math.min((int) (screenW * 0.82f), dp(320));

        final LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(bg);

        // --- Sarlavha: ko'k fon, avatar, ism, telefon; o'ngda kun/tun va saqlanganlar, hisoblar strelkasi ---
        FrameLayout header = new FrameLayout(activity);
        final GradientDrawable headerBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, headerColors());
        header.setBackground(headerBg);
        header.setPadding(dp(20), AndroidUtilities.statusBarHeight + dp(18), dp(16), dp(14));

        TLRPC.User me = null;
        try {
            me = UserConfig.getInstance(account).getCurrentUser();
        } catch (Throwable ignore) {
        }
        LinearLayout headerCol = new LinearLayout(activity);
        headerCol.setOrientation(LinearLayout.VERTICAL);
        BackupImageView avatar = new BackupImageView(activity);
        avatar.setRoundRadius(dp(32));
        if (me != null) {
            avatar.setForUserOrChat(me, new AvatarDrawable(me));
        }
        avatar.setOnClickListener(v -> close(() -> openProfile(fragment, account)));
        headerCol.addView(avatar, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView name = new TextView(activity);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        name.setTypeface(AndroidUtilities.bold());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(me != null ? UserObject.getUserName(me) : "CyberGram");
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(14);
        nameLp.rightMargin = dp(44);
        headerCol.addView(name, nameLp);

        TextView sub = new TextView(activity);
        sub.setTextColor(0xCCFFFFFF);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sub.setSingleLine(true);
        String subText = null;
        if (me != null && !TextUtils.isEmpty(me.phone)) {
            try {
                subText = PhoneFormat.getInstance().format("+" + me.phone);
            } catch (Throwable e) {
                subText = "+" + me.phone;
            }
        }
        if (subText == null && me != null && !TextUtils.isEmpty(UserObject.getPublicUsername(me))) {
            subText = "@" + UserObject.getPublicUsername(me);
        }
        sub.setText(subText != null ? subText : L.t("Profilni ko'rish", "Открыть профиль"));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        subLp.rightMargin = dp(44);
        headerCol.addView(sub, subLp);
        header.addView(headerCol, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Kun/tun rejimi
        ImageView themeBtn = new ImageView(activity);
        themeBtn.setScaleType(ImageView.ScaleType.CENTER);
        themeBtn.setImageResource(Theme.isCurrentThemeDark() ? R.drawable.menu_day_mode_24 : R.drawable.menu_night_mode_24);
        themeBtn.setColorFilter(Color.WHITE);
        themeBtn.setBackground(Theme.createSelectorDrawable(0x33FFFFFF, 1));
        themeBtn.setOnClickListener(v -> toggleTheme(fragment, v));
        header.addView(themeBtn, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END));

        // Sarlavha rangini tanlash (🎨)
        TextView colorBtn = new TextView(activity);
        colorBtn.setText("🎨");
        colorBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        colorBtn.setGravity(Gravity.CENTER);
        colorBtn.setBackground(Theme.createSelectorDrawable(0x33FFFFFF, 1));
        colorBtn.setOnClickListener(v -> chooseColor(activity, () -> applyHeaderColor(header, headerBg)));
        FrameLayout.LayoutParams colorLp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
        colorLp.topMargin = dp(48);
        header.addView(colorBtn, colorLp);

        // Hisoblar ro'yxatini ochish/yopish strelkasi
        final TextView arrow = new TextView(activity);
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        arrow.setGravity(Gravity.CENTER);
        arrow.setText(accountsOpen ? "▲" : "▼");
        arrow.setBackground(Theme.createSelectorDrawable(0x33FFFFFF, 1));
        header.addView(arrow, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM | Gravity.END));
        panel.addView(header);

        // --- Bo'limlar ---
        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, dp(6));
        scroll.addView(list);

        final LinearLayout accountsBox = new LinearLayout(activity);
        accountsBox.setOrientation(LinearLayout.VERTICAL);
        fillAccounts(accountsBox, fragment, account, text, gray);
        accountsBox.setVisibility(accountsOpen ? View.VISIBLE : View.GONE);
        list.addView(accountsBox);
        View.OnClickListener toggleAccounts = v -> {
            accountsOpen = !accountsOpen;
            accountsBox.setVisibility(accountsOpen ? View.VISIBLE : View.GONE);
            arrow.setText(accountsOpen ? "▲" : "▼");
        };
        arrow.setOnClickListener(toggleAccounts);
        headerCol.setOnClickListener(toggleAccounts);

        addItem(list, R.drawable.settings_account, C_BLUE, L.t("Profilim", "Мой профиль"), text, () -> openProfile(fragment, account));
        addDivider(list, gray);
        addItem(list, R.drawable.outline_groups_24, C_GREEN, L.t("Yangi guruh", "Создать группу"), text,
                () -> fragment.presentFragment(new GroupCreateActivity(new Bundle())));
        addItem(list, R.drawable.msg_channel, C_ORANGE, L.t("Yangi kanal", "Создать канал"), text,
                () -> fragment.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE)));
        addDivider(list, gray);
        addItem(list, R.drawable.msg_contacts_name, C_TEAL, L.t("Kontaktlar", "Контакты"), text, () -> {
            Bundle args = new Bundle();
            args.putBoolean("needPhonebook", true);
            fragment.presentFragment(new ContactsActivity(args));
        });
        addItem(list, R.drawable.msg_calls, C_GREEN, L.t("Qo'ng'iroqlar", "Звонки"), text, () -> fragment.presentFragment(new CallLogActivity()));
        addItem(list, R.drawable.outline_saved_24, C_INDIGO, L.t("Saqlangan xabarlar", "Избранное"), text, () -> openSaved(fragment, account));
        addItem(list, R.drawable.msg_archive, C_TEAL, L.t("Arxivlangan chatlar", "Архив чатов"), text, () -> openArchive(fragment));
        addItem(list, R.drawable.settings_folders, C_PURPLE, L.t("Chat jildlari", "Папки с чатами"), text, () -> fragment.presentFragment(new FiltersSetupActivity()));
        addItem(list, R.drawable.msg_settings_old, C_GRAY, L.t("Sozlamalar", "Настройки"), text, () -> fragment.presentFragment(new SettingsActivity()));
        addDivider(list, gray);
        addItem(list, R.drawable.cybergram_shield, C_RED, L.t("CyberGram himoyasi", "Защита CyberGram"), text, () -> CyberMenu.show(activity));
        addItem(list, R.drawable.settings_faq, C_PINK, L.t("Rasmiy kanallar", "Официальные каналы"), text, () -> CyberMenu.showOfficialChannels(activity));
        addItem(list, R.drawable.msg_info, C_BLUE, L.t("CyberGram haqida", "О CyberGram"), text, () -> CyberMenu.showAbout(activity));
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView footer = new TextView(activity);
        footer.setTextColor(gray);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        footer.setPadding(dp(20), dp(8), dp(20), dp(14) + AndroidUtilities.navigationBarHeight);
        footer.setText(L.t("CyberGram · norasmiy ilova, Telegram API asosida", "CyberGram · неофициальное приложение на Telegram API"));
        panel.addView(footer);

        // Chap tomondan chiqadigan panel; tashqarisiga bosilsa yopiladi
        FrameLayout root = new FrameLayout(activity);
        root.setOnClickListener(v -> close(null));
        panel.setClickable(true);
        root.addView(panel, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        // Panelni chapga surib yopish
        final float[] downX = new float[1];
        panel.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downX[0] = e.getRawX();
            } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = Math.min(0, e.getRawX() - downX[0]);
                panel.setTranslationX(dx);
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                if (panel.getTranslationX() < -width / 4f) {
                    close(null);
                } else {
                    panel.animate().translationX(0).setDuration(150).start();
                }
            }
            return false;
        });

        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.START);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.setDimAmount(0.45f);
        }
        dialog.setOnDismissListener(d -> {
            if (current == dialog) {
                current = null;
            }
        });

        panel.setTranslationX(-width);
        dialog.show();
        panel.animate().translationX(0).setDuration(220).start();
        panelRef = panel;
        panelWidth = width;
    }

    private static boolean accountsOpen;

    /** Hisoblar ro'yxati: har bir faol hisob, o'qilmaganlar soni, "Hisob qo'shish". */
    private static void fillAccounts(LinearLayout box, final BaseFragment fragment, final int current, int textColor, int gray) {
        Context context = box.getContext();
        int active = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final int acc = a;
            UserConfig config = UserConfig.getInstance(a);
            if (!config.isClientActivated()) {
                continue;
            }
            active++;
            TLRPC.User user = config.getCurrentUser();
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), 0, dp(16), 0);
            row.setBackground(Theme.getSelectorDrawable(false));

            BackupImageView av = new BackupImageView(context);
            av.setRoundRadius(dp(18));
            if (user != null) {
                av.setForUserOrChat(user, new AvatarDrawable(user));
            }
            row.addView(av, new LinearLayout.LayoutParams(dp(36), dp(36)));

            TextView label = new TextView(context);
            label.setText((user != null ? UserObject.getUserName(user) : "—") + (a == current ? "  ✓" : ""));
            label.setTextColor(textColor);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.leftMargin = dp(18);
            row.addView(label, labelLp);

            int unread = 0;
            try {
                unread = MessagesStorage.getInstance(a).getMainUnreadCount();
            } catch (Throwable ignore) {
            }
            if (unread > 0 && a != current) {
                TextView badge = new TextView(context);
                badge.setText(unread > 999 ? "999+" : String.valueOf(unread));
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                badge.setTypeface(AndroidUtilities.bold());
                badge.setGravity(Gravity.CENTER);
                badge.setPadding(dp(8), dp(2), dp(8), dp(2));
                GradientDrawable bb = new GradientDrawable();
                bb.setColor(C_BLUE);
                bb.setCornerRadius(dp(12));
                badge.setBackground(bb);
                row.addView(badge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)));
            }
            row.setOnClickListener(v -> close(() -> {
                if (acc != current && LaunchActivity.instance != null) {
                    LaunchActivity.instance.switchToAccount(acc, true);
                }
            }));
            box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        }
        if (active < UserConfig.getMaxAccountCount()) {
            addItem(box, R.drawable.menu_add_tab_24, C_GRAY, L.t("Hisob qo'shish", "Добавить аккаунт"), textColor, () -> {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (!UserConfig.getInstance(a).isClientActivated()) {
                        fragment.presentFragment(new LoginActivity(a));
                        return;
                    }
                }
            });
        }
        addDivider(box, gray);
    }

    private static void openArchive(BaseFragment fragment) {
        Bundle args = new Bundle();
        args.putInt("folderId", 1);
        fragment.presentFragment(new DialogsActivity(args));
    }

    private static void openSaved(BaseFragment fragment, int account) {
        Bundle args = new Bundle();
        args.putLong("user_id", UserConfig.getInstance(account).getClientUserId());
        fragment.presentFragment(new ChatActivity(args));
    }

    /** Kun/tun rejimini almashtirish (Telegram'ning o'z mexanizmi orqali). */
    private static void toggleTheme(BaseFragment fragment, View from) {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Context.MODE_PRIVATE);
            String dayThemeName = preferences.getString("lastDayTheme", "Blue");
            if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                dayThemeName = "Blue";
            }
            String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
            if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                nightThemeName = "Dark Blue";
            }
            Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
            if (dayThemeName.equals(nightThemeName)) {
                if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                    dayThemeName = "Blue";
                } else {
                    nightThemeName = "Dark Blue";
                }
            }
            boolean toDark = dayThemeName.equals(themeInfo.getKey());
            themeInfo = Theme.getTheme(toDark ? nightThemeName : dayThemeName);
            int[] pos = new int[2];
            from.getLocationInWindow(pos);
            pos[0] += from.getMeasuredWidth() / 2;
            pos[1] += from.getMeasuredHeight() / 2;
            final Theme.ThemeInfo target = themeInfo;
            final boolean dark = toDark;
            close(() -> {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, target, false, pos, -1, dark, null, null, null, true);
                Theme.turnOffAutoNight(BulletinFactory.of(fragment), () -> fragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT)));
            });
        } catch (Throwable ignore) {
        }
    }

    private static View panelRef;
    private static int panelWidth;

    /** Panelni yopadi, so'ng (ixtiyoriy) amalni bajaradi. */
    private static void close(final Runnable after) {
        final Dialog dialog = current;
        final View panel = panelRef;
        if (dialog == null) {
            if (after != null) {
                after.run();
            }
            return;
        }
        current = null;
        Runnable finish = () -> {
            try {
                dialog.dismiss();
            } catch (Exception ignore) {
            }
            if (after != null) {
                after.run();
            }
        };
        if (panel != null) {
            panel.animate().translationX(-panelWidth).setDuration(180).withEndAction(finish).start();
        } else {
            finish.run();
        }
    }

    private static void openProfile(BaseFragment fragment, int account) {
        Bundle args = new Bundle();
        args.putLong("user_id", UserConfig.getInstance(account).getClientUserId());
        args.putBoolean("my_profile", true);
        fragment.presentFragment(new ProfileActivity(args));
    }

    private static void addItem(LinearLayout list, int iconRes, int iconColor, String title, int textColor, final Runnable action) {
        Context context = list.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(16), 0);
        row.setBackground(Theme.getSelectorDrawable(false));

        FrameLayout circle = new FrameLayout(context);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(iconColor);
        circle.setBackground(circleBg);
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        try {
            icon.setImageResource(iconRes);
        } catch (Throwable ignore) {
        }
        icon.setColorFilter(Color.WHITE);
        circle.addView(icon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        row.addView(circle, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView label = new TextView(context);
        label.setText(title);
        label.setTextColor(textColor);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.leftMargin = dp(20);
        row.addView(label, labelLp);

        row.setOnClickListener(v -> close(action));
        list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
    }

    private static void addDivider(LinearLayout list, int color) {
        View line = new View(list.getContext());
        GradientDrawable d = new GradientDrawable();
        d.setColor((color & 0x00FFFFFF) | 0x33000000);
        line.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        list.addView(line, lp);
    }
}
