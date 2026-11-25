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
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.FiltersSetupActivity;
import org.telegram.ui.GroupCreateActivity;
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

        // --- Sarlavha: ko'k fon, avatar, ism, telefon/username (status bar ostigacha) ---
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable headerBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF4A90D9, 0xFF2F6DB5});
        header.setBackground(headerBg);
        header.setPadding(dp(20), AndroidUtilities.statusBarHeight + dp(18), dp(20), dp(16));

        TLRPC.User me = null;
        try {
            me = UserConfig.getInstance(account).getCurrentUser();
        } catch (Throwable ignore) {
        }
        BackupImageView avatar = new BackupImageView(activity);
        avatar.setRoundRadius(dp(32));
        if (me != null) {
            avatar.setForUserOrChat(me, new AvatarDrawable(me));
        }
        header.addView(avatar, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView name = new TextView(activity);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        name.setTypeface(AndroidUtilities.bold());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(me != null ? UserObject.getUserName(me) : "CyberGram");
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(14);
        header.addView(name, nameLp);

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
        header.addView(sub, subLp);
        header.setOnClickListener(v -> close(() -> openProfile(fragment, account)));
        panel.addView(header);

        // --- Bo'limlar ---
        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, dp(6));
        scroll.addView(list);

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
        addItem(list, R.drawable.outline_saved_24, C_INDIGO, L.t("Saqlangan xabarlar", "Избранное"), text, () -> {
            Bundle args = new Bundle();
            args.putLong("user_id", UserConfig.getInstance(account).getClientUserId());
            fragment.presentFragment(new ChatActivity(args));
        });
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
