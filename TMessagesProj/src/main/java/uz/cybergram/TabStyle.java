package uz.cybergram;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * Tepadagi jild (folder) tablarini "jonliroq" qilish: tanlangan tab ikonkasi kattalashib, biroz
 * "sakraydi", tanlanmaganlari xiraroq ko'rinadi. FilterTabsView.TabView.onDraw dan chaqiriladi.
 */
public final class TabStyle {

    /**
     * Telegram'ning umumiy siljiydigan "tabletka"si. Endi har bir tab o'z rangli fonini o'zi chizadi,
     * shuning uchun eskisi yashiriladi (0).
     */
    public static final int SELECTOR_ALPHA = 0;

    private static final float INACTIVE_TINT = 0.16f;  // tanlanmagan tab fonining rang kuchi
    private static final Paint BG = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    // Jild turiga qarab rang (FolderIcons emojilari bilan mos)
    private static final String[] EMOJI = {"💬", "👤", "👥", "📢", "🤖", "📩", "📁"};
    private static final int[] EMOJI_COLOR = {
            0xFF3D8EF0, // 💬 barcha chatlar — ko'k
            0xFF34C759, // 👤 shaxsiy — yashil
            0xFFAF52DE, // 👥 guruhlar — binafsha
            0xFFFF9500, // 📢 kanallar — to'q sariq
            0xFF2CB5C9, // 🤖 botlar — feruza
            0xFFF2484A, // 📩 o'qilmaganlar — qizil
            0xFFFF5D8F, // 📁 o'zingiz yaratgan — pushti
    };
    private static final int[] OTHER = {0xFF5B5FE0, 0xFFFF5D8F, 0xFF34C759, 0xFFFF9500, 0xFF2CB5C9, 0xFFAF52DE};

    /** Jild nomi (emoji) bo'yicha rang. */
    public static int colorFor(CharSequence title) {
        String t = title == null ? "" : title.toString().trim();
        for (int i = 0; i < EMOJI.length; i++) {
            if (t.startsWith(EMOJI[i])) {
                return EMOJI_COLOR[i];
            }
        }
        return OTHER[Math.abs(t.hashCode()) % OTHER.length];
    }

    /** 👤 va 👥 emojilari kulrang-ko'k siluet — qorong'i fonda yo'qolib qoladi, ularga yorqinroq fon kerak. */
    private static boolean lowContrast(CharSequence title) {
        String t = title == null ? "" : title.toString().trim();
        return t.startsWith("👤") || t.startsWith("👥") || t.startsWith("👨") || t.startsWith("👩");
    }

    /** Rangni oqqa yaqinlashtirish (0..1). */
    private static int lighter(int c, float k) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, float a) {
        int alpha = Math.max(0, Math.min(255, (int) (255 * a)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static final float ACTIVE_SCALE = 0.14f;   // tanlangan tab 14% kattaroq
    private static final float BOUNCE = 0.12f;         // o'tish paytidagi qo'shimcha "sakrash"
    private static final float INACTIVE_ALPHA = 0.9f;  // tanlanmagan tablar emojisi deyarli to'liq ko'rinadi

    private TabStyle() {
    }

    /**
     * Tab chizilishidan oldin canvas'ni sozlaydi. Chaqiruvchi oldin canvas.save() qilib, oxirida
     * canvas.restoreToCount(...) bilan qaytarishi kerak.
     */
    public static void apply(Canvas canvas, View tab, int tabId, int selectedId, int previousId,
                             int manualId, boolean animating, float progress) {
        apply(canvas, tab, null, tabId, selectedId, previousId, manualId, animating, progress);
    }

    public static void apply(Canvas canvas, View tab, CharSequence title, int tabId, int selectedId, int previousId,
                             int manualId, boolean animating, float progress) {
        try {
            float s;          // 0 = tanlanmagan, 1 = to'liq tanlangan
            float bounce = 0; // faqat yangi tanlanayotgan tabga
            if (manualId != -1) {
                // barmoq bilan surilayotganda
                if (tabId == manualId) {
                    s = progress;
                } else if (tabId == selectedId) {
                    s = 1f - progress;
                } else {
                    s = 0;
                }
            } else if (animating) {
                if (tabId == selectedId) {
                    s = progress;
                    bounce = (float) Math.sin(Math.PI * progress);
                } else if (tabId == previousId) {
                    s = 1f - progress;
                } else {
                    s = 0;
                }
            } else {
                s = tabId == selectedId ? 1f : 0f;
            }
            s = Math.max(0f, Math.min(1f, s));

            float w = tab.getWidth();
            float h = tab.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            // Har bir tabning o'z rangli foni: tanlanmagan — och, tanlangan — to'liq rang
            int color = colorFor(title);
            float pillW = Math.max(AndroidUtilities.dp(40), w - AndroidUtilities.dp(6));
            float pillH = Math.min(h - AndroidUtilities.dp(4), AndroidUtilities.dp(32));
            R.set((w - pillW) / 2f, (h - pillH) / 2f, (w + pillW) / 2f, (h + pillH) / 2f);
            boolean low = lowContrast(title);
            if (low) {
                // siluet ko'rinishi uchun: och (oqroq) va quyuqroq fon
                color = lighter(color, 0.35f);
            }
            float tint = low ? 0.38f : INACTIVE_TINT;
            BG.setColor(withAlpha(color, tint + (1f - tint) * s));
            canvas.drawRoundRect(R, pillH / 2f, pillH / 2f, BG);

            float minAlpha = low ? 1f : INACTIVE_ALPHA;
            float alpha = minAlpha + (1f - minAlpha) * s;
            if (alpha < 0.99f) {
                canvas.saveLayerAlpha(0, 0, w, h, (int) (255 * alpha));
            }
            float scale = 1f + ACTIVE_SCALE * s + BOUNCE * bounce;
            canvas.translate(0, -AndroidUtilities.dp(1) * s);
            canvas.scale(scale, scale, w / 2f, h / 2f);
        } catch (Throwable ignore) {
        }
    }
}
