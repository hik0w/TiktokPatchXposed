package com.golda.patchertiktok;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The sent time under each chat message. TikTok binds every message row through one method of its
 * row slot; after it runs, the row's message (an IM model with getCreatedAt) is read from the bound
 * item. The row's bottom padding, which TikTok sets again on every bind, gets room for one small
 * line, and the time is drawn there over the row, under the bubble and on the bubble's side. TikTok's
 * own layout is not changed, so swipes, long presses and reactions work as before.
 */
final class MessageTime {
    private static final String[] SLOTS = {
            "com.ss.android.ugc.aweme.im.sdk.chat.messagelist.skeleton.cell.IMSkeletonLayoutReusedUISlotAssem",
            "com.ss.android.ugc.aweme.im.sdk.chat.messagelist.skeleton.cell.SkeletonLayoutReusedUISlotAssem"};
    /** The bubble's container in the row; the time lines up under it. */
    private static final String BUBBLE = "f62";
    private static final String LABEL = "tpx_message_time";
    /** The "Unsent" mark is red, so a kept message stands out from the time next to it. */
    private static final int UNSENT_COLOR = 0xFFE53935;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Each bound row and its message; used on the main thread only. */
    private static final Map<ViewGroup, Object> ROWS = new WeakHashMap<>();

    private MessageTime() { }

    static void install(ClassLoader loader) {
        int hooks = 0;
        for (String name : SLOTS) {
            Class<?> slot = Hooks.findClass(name, loader);
            if (slot == null) continue;
            Method binder = binder(slot);
            if (binder == null) continue;
            Hooks.hook(binder, new Hooks.Hook() {
                @Override protected void after(Hooks.Call param) {
                    bound(param.thisObject, param.args[0]);
                }
            });
            hooks++;
        }
        RuntimeLog.log("message time: binders=" + hooks);
    }

    /** The row's bind method: one model argument, not the bridge that takes Object, nor a view setter. */
    private static Method binder(Class<?> slot) {
        Method found = null;
        for (Method method : Reflect.methods(slot)) {
            Class<?>[] p = method.getParameterTypes();
            if (method.isSynthetic() || method.isBridge() || Modifier.isStatic(method.getModifiers())
                    || Modifier.isAbstract(method.getModifiers()) || method.getReturnType() != void.class
                    || p.length != 1 || p[0].isPrimitive() || p[0] == Object.class || p[0] == String.class
                    || View.class.isAssignableFrom(p[0]) || p[0].getName().startsWith("java.")) continue;
            if (found != null) return null;
            found = method;
        }
        return found;
    }

    private static void bound(Object slot, Object item) {
        Object container = Reflect.call(slot, "getContainerView");
        if (!(container instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) container;
        Object message = Prefs.on(Prefs.MESSAGE_TIME) || Prefs.on(Prefs.KEEP_UNSENT) ? message(item) : null;
        ROWS.put(row, message);
        show(row, message);
    }

    /** Redraws the rows showing this message, so a kept message gets its mark as soon as it is unsent. */
    static void unsent(String uuid) {
        MAIN.post(Safe.run("unsent mark", () -> {
            List<ViewGroup> rows = new ArrayList<>();
            for (Map.Entry<ViewGroup, Object> entry : ROWS.entrySet()) {
                if (uuid.equals(Reflect.call(entry.getValue(), "getUuid"))) rows.add(entry.getKey());
            }
            for (ViewGroup row : rows) show(row, ROWS.get(row));
        }));
    }

    private static void show(ViewGroup row, Object message) {
        Label label = (Label) Hooks.getExtra(row, LABEL);
        boolean time = Prefs.on(Prefs.MESSAGE_TIME);
        Object created = time ? Reflect.call(message, "getCreatedAt") : null;
        Context context = row.getContext();
        String text = created instanceof Long && (Long) created > 0
                ? format(DateFormat.is24HourFormat(context)).format(new Date((Long) created)) : null;
        // A message the other person unsent but that is kept says so next to its time.
        String unsent = null;
        if (UnsentMessages.marked(message)) {
            unsent = I18n.get(I18n.S.UNSENT_MARK);
            text = text == null ? unsent : text + " · " + unsent;
        }
        if (text == null) {
            // Off, or a row without a time (notices): nothing drawn, TikTok's padding stays.
            if (label != null) label.setText(null, null);
            return;
        }
        if (label == null) {
            label = new Label(row);
            label.setBounds(0, 0, 1 << 14, 1 << 14);
            row.getOverlay().add(label);
            Hooks.setExtra(row, LABEL, label);
        }
        label.setText(text, unsent);
        // TikTok sets this padding on every bind; if a bind leaves ours in place, it is not added twice.
        int bottom = row.getPaddingBottom();
        if (bottom == label.applied) bottom = label.base;
        label.base = bottom;
        label.applied = bottom + label.height();
        row.setPadding(row.getPaddingLeft(), row.getPaddingTop(), row.getPaddingRight(), label.applied);
    }

    /** Rows bind on the main thread only, so one formatter per clock style is reused. */
    private static java.text.SimpleDateFormat clock24;
    private static java.text.SimpleDateFormat clock12;

    private static java.text.SimpleDateFormat format(boolean twentyFour) {
        if (twentyFour) {
            if (clock24 == null) clock24 = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
            return clock24;
        }
        if (clock12 == null) clock12 = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
        return clock12;
    }

    /** The time, drawn over the row in the room under the bubble. */
    private static final class Label extends Drawable {
        private final ViewGroup row;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int gap;
        private String text;
        private String mark;
        private View bubble;
        /** TikTok's own bottom padding, and the one set with room for the time. */
        int base;
        int applied = -1;

        Label(ViewGroup row) {
            this.row = row;
            float density = row.getResources().getDisplayMetrics().density;
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, row.getResources().getDisplayMetrics()));
            paint.setColor(0xFF8A8B91);
            markPaint.setTextSize(paint.getTextSize());
            markPaint.setColor(UNSENT_COLOR);
            gap = Math.round(2 * density);
        }

        int height() {
            Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
            return metrics.descent - metrics.ascent + gap * 2;
        }

        void setText(String value, String marked) {
            text = value;
            mark = marked;
            invalidateSelf();
            row.invalidate();
        }

        @Override public void draw(Canvas canvas) {
            if (text == null) return;
            if (bubble == null) {
                int id = row.getResources().getIdentifier(BUBBLE, "id", row.getContext().getPackageName());
                bubble = id == 0 ? null : row.findViewById(id);
            }
            View target = bubble != null && bubble.getVisibility() == View.VISIBLE && bubble.getWidth() > 0 ? bubble : largest(row);
            if (target == null) return;
            int left = target.getLeft();
            int right = target.getRight();
            // The top of a view tree is not a View, and a bubble moved out of the row ends there.
            for (android.view.ViewParent parent = target.getParent(); parent instanceof View && parent != row; parent = parent.getParent()) {
                left += ((View) parent).getLeft();
                right += ((View) parent).getLeft();
            }
            // Your messages sit on the right half; the time follows the bubble's outer edge.
            boolean mine = left + right > row.getWidth();
            float width = paint.measureText(text);
            float x = mine ? right - width - gap * 3 : left + gap * 3;
            Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
            float y = row.getHeight() - row.getPaddingBottom() + height() - gap - metrics.descent;
            float baseline = Math.min(y, row.getHeight() - metrics.descent);
            if (mark == null || !text.endsWith(mark)) {
                canvas.drawText(text, x, baseline, paint);
                return;
            }
            String plain = text.substring(0, text.length() - mark.length());
            canvas.drawText(plain, x, baseline, paint);
            canvas.drawText(mark, x + paint.measureText(plain), baseline, markPaint);
        }

        /** When the bubble's id is renamed: the biggest shown part of the row that does not span all of it. */
        private static View largest(ViewGroup row) {
            View best = null;
            long area = 0;
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || child.getWidth() >= row.getWidth() * 0.9f) continue;
                long size = (long) child.getWidth() * child.getHeight();
                if (size > area) {
                    area = size;
                    best = child;
                }
            }
            return best;
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            markPaint.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            markPaint.setColorFilter(filter);
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Per row model class: the fields leading to its message, found once (rows bind while scrolling). */
    private static final Map<Class<?>, Field[]> PATHS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The IM message inside the bound row model: the object with getCreatedAt and getUuid, a few fields deep. */
    static Object message(Object item) {
        if (item == null) return null;
        Field[] path = PATHS.get(item.getClass());
        if (path != null) {
            Object value = item;
            for (Field field : path) {
                try {
                    value = value == null ? null : field.get(value);
                } catch (Throwable error) {
                    value = null;
                }
            }
            if (value != null && isMessage(value)) return value;
        }
        Map<Object, Field[]> routes = new IdentityHashMap<>();
        ArrayDeque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[]{item, 0, new Field[0]});
        while (!queue.isEmpty() && routes.size() < 200) {
            Object[] entry = queue.poll();
            Object value = entry[0];
            int depth = (Integer) entry[1];
            Field[] route = (Field[]) entry[2];
            if (value == null || routes.put(value, route) != null) continue;
            if (isMessage(value)) {
                PATHS.put(item.getClass(), route);
                return value;
            }
            if (depth >= 4) continue;
            for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                if (type.getName().startsWith("java.") || type.getName().startsWith("android.")) break;
                for (Field field : Reflect.fields(type)) {
                    Class<?> kind = field.getType();
                    if (Modifier.isStatic(field.getModifiers()) || kind.isPrimitive() || kind == String.class
                            || View.class.isAssignableFrom(kind) || List.class.isAssignableFrom(kind)) continue;
                    try {
                        field.setAccessible(true);
                        Object next = field.get(value);
                        if (next == null) continue;
                        Field[] longer = java.util.Arrays.copyOf(route, route.length + 1);
                        longer[route.length] = field;
                        queue.add(new Object[]{next, depth + 1, longer});
                    } catch (Throwable ignored) {
                        // Next field.
                    }
                }
            }
        }
        return null;
    }

    private static boolean isMessage(Object value) {
        return Reflect.method(value.getClass(), "getCreatedAt") != null && Reflect.method(value.getClass(), "getUuid") != null;
    }
}
