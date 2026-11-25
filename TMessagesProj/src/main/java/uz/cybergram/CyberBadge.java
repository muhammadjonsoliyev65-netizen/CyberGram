package uz.cybergram;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Xabar yonidagi kichik 🛡 belgi: oflayn tekshiruv natijasi (🟢/🟡/🔴).
 * Bosilganda to'liq tekshiruv oynasi ochiladi (u yerda "🤖 AI tahlili" bor).
 */
public final class CyberBadge {

    private static final class Info {
        final boolean show;
        final SafetyVerdict verdict;

        Info(boolean show, SafetyVerdict verdict) {
            this.show = show;
            this.verdict = verdict;
        }
    }

    private static final Map<MessageObject, Info> CACHE = new WeakHashMap<>();
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static Drawable shield;

    private final RectF rect = new RectF();
    private boolean pressed;

    /** Tekshiruv uchun alohida fon oqimi — chat aylantirilganda qotmasligi uchun. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CyberBadge");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final Info PENDING = new Info(false, SafetyVerdict.SAFE);
    private static final Map<MessageObject, Boolean> QUEUED = new WeakHashMap<>();

    /** Faqat UI oqimidan chaqiriladi. Natija tayyor bo'lmasa — fon oqimida hisoblaydi. */
    private static Info info(final MessageObject m, final View cell) {
        Info info = CACHE.get(m);
        if (info != null) {
            return info;
        }
        if (QUEUED.containsKey(m)) {
            return PENDING;
        }
        QUEUED.put(m, Boolean.TRUE);
        WORKER.execute(() -> {
            final Info result = compute(m);
            AndroidUtilities.runOnUIThread(() -> {
                CACHE.put(m, result);
                QUEUED.remove(m);
                if (result.show) {
                    cell.invalidate();
                }
            });
        });
        return PENDING;
    }

    private static Info compute(MessageObject m) {
        boolean show = false;
        SafetyVerdict verdict = SafetyVerdict.SAFE;
        try {
            if (m != null && m.messageOwner != null && m.isFactCheckable()) {
                String text = CyberScanDialog.collectText(m);
                String file = CyberScanDialog.fileName(m);
                LocalRules.Result r = LocalRules.analyze(text, file);
                verdict = r.verdict;
                // Kelgan har bir xabarda ko'rsatamiz; o'zimiz yuborganlarda faqat xavf bo'lsa
                show = !m.isOutOwner() || verdict != SafetyVerdict.SAFE;
            }
        } catch (Throwable ignore) {
        }
        return new Info(show, verdict);
    }

    /**
     * @param left,right,top  xabar pufagining chegaralari
     * @param sideButtonY     yon tugma (ulashish) Y koordinatasi, bo'lmasa -1
     */
    public void draw(Canvas canvas, View cell, MessageObject m, int left, int right, int top, int sideButtonY) {
        rect.setEmpty();
        if (m == null || !CyberSettings.linkGuard(cell.getContext())) {
            return;
        }
        Info info = info(m, cell);
        if (!info.show) {
            return;
        }
        int size = AndroidUtilities.dp(26);
        int gap = AndroidUtilities.dp(6);
        float y = top + AndroidUtilities.dp(4);
        float x;
        boolean inside = sideButtonY >= 0 && y + size > sideButtonY - AndroidUtilities.dp(30);
        if (m.isOutOwner()) {
            x = left - gap - size;
            if (inside || x < AndroidUtilities.dp(2)) {
                x = left + gap;
            }
        } else {
            x = right + gap;
            if (inside || x + size > cell.getWidth() - AndroidUtilities.dp(2)) {
                x = right - gap - size;
            }
        }
        rect.set(x, y, x + size, y + size);

        PAINT.setColor(info.verdict.color);
        PAINT.setAlpha(pressed ? 170 : 235);
        canvas.drawCircle(rect.centerX(), rect.centerY(), size / 2f, PAINT);

        if (shield == null) {
            shield = cell.getContext().getResources().getDrawable(R.drawable.cybergram_shield).mutate();
        }
        int pad = AndroidUtilities.dp(5);
        shield.setBounds((int) rect.left + pad, (int) rect.top + pad, (int) rect.right - pad, (int) rect.bottom - pad);
        shield.draw(canvas);
    }

    public boolean onTouch(MotionEvent event, View cell, MessageObject m) {
        if (rect.isEmpty() || m == null) {
            pressed = false;
            return false;
        }
        float pad = AndroidUtilities.dp(6);
        boolean hit = event.getX() >= rect.left - pad && event.getX() <= rect.right + pad
                && event.getY() >= rect.top - pad && event.getY() <= rect.bottom + pad;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (hit) {
                    pressed = true;
                    cell.invalidate();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                return pressed;
            case MotionEvent.ACTION_UP:
                if (pressed) {
                    pressed = false;
                    cell.invalidate();
                    if (hit) {
                        CyberScanDialog.show(activity(cell.getContext()), m);
                    }
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                if (pressed) {
                    pressed = false;
                    cell.invalidate();
                }
                return false;
            default:
                return pressed;
        }
    }

    private static Context activity(Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) {
                return c;
            }
            c = ((ContextWrapper) c).getBaseContext();
        }
        return context;
    }
}
