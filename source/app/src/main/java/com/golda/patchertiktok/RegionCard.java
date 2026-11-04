package com.golda.patchertiktok;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class RegionCard {
    private static final String SOURCE = "com.golda.patchertiktok.regionSource";
    private static final String TAP = "com.golda.patchertiktok.regionTap";

    private RegionCard() { }

    private static final class Source {
        final String region;
        final WeakReference<Object> user;
        final WeakReference<Object> aweme;
        final WeakReference<Object> comment;

        Source(String region, Object user, Object aweme, Object comment) {
            this.region = region;
            this.user = new WeakReference<>(user);
            this.aweme = new WeakReference<>(aweme);
            this.comment = new WeakReference<>(comment);
        }
    }

    private static final class Entry {
        final String title;
        final String detail;
        final Glyph.Kind icon;
        final int tile;
        final String flag;
        final String copy;

        Entry(String title, String detail, Glyph.Kind icon, int tile, String flag, String copy) {
            this.title = title;
            this.detail = detail;
            this.icon = icon;
            this.tile = tile;
            this.flag = flag;
            this.copy = copy;
        }
    }

    static void attach(TextView label, String region, Object user, Object aweme, Object comment) {
        if (label == null || code(region) == null || !Prefs.on(Prefs.REGION_DETAILS)) return;
        Hooks.setExtra(label, SOURCE, new Source(code(region), user, aweme, comment));
        listen(label);
    }

    private static void listen(TextView label) {
        Object current = Reflect.get(Reflect.get(label, "mListenerInfo"), "mOnTouchListener");
        if (current instanceof FlagTap) return;
        if (current == null && Hooks.getExtra(label, TAP) != null) return;
        Hooks.setExtra(label, TAP, Boolean.TRUE);
        label.setOnTouchListener(new FlagTap(current instanceof View.OnTouchListener ? (View.OnTouchListener) current : null));
    }

    private static final class FlagTap implements View.OnTouchListener {
        private final View.OnTouchListener previous;
        private boolean tracking;
        private boolean still;
        private float downX;
        private float downY;

        FlagTap(View.OnTouchListener previous) {
            this.previous = previous;
        }

        @Override public boolean onTouch(View view, MotionEvent event) {
            try {
                if (handle((TextView) view, event)) return true;
            } catch (Throwable error) {
                tracking = false;
                Safe.log("region card", error);
            }
            return previous != null && previous.onTouch(view, event);
        }

        private boolean handle(TextView label, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                tracking = Prefs.on(Prefs.AUTHOR_REGION) && Prefs.on(Prefs.REGION_DETAILS) && Hooks.getExtra(label, SOURCE) != null
                        && onFlag(label, event.getX(), event.getY());
                still = tracking;
                downX = event.getX();
                downY = event.getY();
                return tracking;
            }
            if (!tracking) return false;
            int slop = ViewConfiguration.get(label.getContext()).getScaledTouchSlop();
            if (action == MotionEvent.ACTION_MOVE) {
                if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) still = false;
            } else if (action == MotionEvent.ACTION_UP) {
                tracking = false;
                if (still) open(label);
            } else if (action == MotionEvent.ACTION_CANCEL) {
                tracking = false;
            }
            return true;
        }
    }

    private static boolean onFlag(TextView label, float x, float y) {
        Layout layout = label.getLayout();
        if (layout == null || layout.getText() == null) return false;
        String text = layout.getText().toString();
        int start = RegionFlag.at(text);
        if (start < 0) return false;
        int line = layout.getLineForOffset(start);
        if (layout.getEllipsisCount(line) > 0 && start >= layout.getLineStart(line) + layout.getEllipsisStart(line)) return false;
        Path path = new Path();
        layout.getSelectionPath(start, text.length(), path);
        RectF box = new RectF();
        path.computeBounds(box, true);
        if (box.width() <= 0) return false;
        float slack = 10 * label.getResources().getDisplayMetrics().density;
        float at = x - label.getTotalPaddingLeft() + label.getScrollX();
        float top = y - label.getTotalPaddingTop() + label.getScrollY();
        return at >= box.left - slack && at <= box.right + slack
                && top >= layout.getLineTop(line) - slack && top <= layout.getLineBottom(line) + slack;
    }

    private static void open(TextView label) {
        Object stored = Hooks.getExtra(label, SOURCE);
        Activity activity = Views.activity(label.getContext());
        if (!(stored instanceof Source) || activity == null || activity.isFinishing()) return;
        Source source = (Source) stored;
        Locale locale = label.getResources().getConfiguration().getLocales().get(0);
        Object user = source.user.get();
        Object aweme = source.aweme.get();
        if (user == null && aweme != null) user = Reflect.call(aweme, "getAuthor");
        List<Entry> entries = entries(source, user, aweme, source.comment.get(), locale);
        if (entries.isEmpty()) return;
        float density = activity.getResources().getDisplayMetrics().density;
        List<DownloadSheet.Row> rows = new ArrayList<>();
        for (Entry entry : entries) {
            DownloadSheet.Row row = new DownloadSheet.Row(entry.title, entry.detail, entry.icon, entry.flag != null ? 0 : entry.tile, false);
            row.arrow = false;
            if (entry.flag != null) row.image = new FlagDot(entry.flag, Math.round(34 * density));
            rows.add(row);
        }
        String handle = text(user, "getUniqueId");
        String heading = RegionFlag.of(source.region) + "  " + country(source.region, locale);
        String hint = I18n.get(I18n.S.RI_TAP_COPY);
        DownloadSheet.show(activity, heading, handle != null ? "@" + handle + " · " + hint : hint, rows,
                which -> { if (which >= 0 && which < entries.size()) copy(activity, entries.get(which).copy); }, null);
    }

    private static List<Entry> entries(Source source, Object user, Object aweme, Object comment, Locale locale) {
        List<Entry> entries = new ArrayList<>();
        String own = code(text(user, "getRegion"));
        if (own == null && comment != null) own = code(AuthorRegion.seen(text(user, "getUniqueId")));
        String video = code(text(aweme, "getRegion"));
        if (own == null && !source.region.equals(video)) own = source.region;
        region(entries, I18n.S.RI_REGION, own, locale);
        language(entries, I18n.S.RI_LANGUAGE, text(user, "getLanguage"), Glyph.Kind.GLOBE, locale);

        region(entries, I18n.S.RI_VIDEO_REGION, video, locale);
        language(entries, I18n.S.RI_CAPTION_LANGUAGE, text(aweme, "getDescLanguage"), Glyph.Kind.TEXT, locale);
        place(entries, Reflect.call(aweme, "getPoiDataStruct"), locale);
        shownIn(entries, Reflect.call(aweme, "getGeofencingRegions"), locale);
        plain(entries, I18n.S.RI_DISTANCE, text(aweme, "getDistance"), Glyph.Kind.INFO, DownloadSheet.VIDEO_TILE);

        language(entries, I18n.S.RI_COMMENT_LANGUAGE, text(comment, "getCommentLanguage"), Glyph.Kind.COMMENT, locale);
        return entries;
    }

    private static void region(List<Entry> entries, I18n.S label, String code, Locale locale) {
        if (code == null) return;
        String name = country(code, locale);
        entries.add(new Entry(name, I18n.get(label) + " · " + code, Glyph.Kind.GLOBE, DownloadSheet.VIDEO_TILE,
                RegionFlag.of(code), name.equals(code) ? code : name + " (" + code + ")"));
    }

    private static void plain(List<Entry> entries, I18n.S label, String value, Glyph.Kind icon, int tile) {
        if (value == null) return;
        entries.add(new Entry(value, I18n.get(label), icon, tile, null, value));
    }

    private static void language(List<Entry> entries, I18n.S label, String code, Glyph.Kind icon, Locale locale) {
        if (code == null || "un".equalsIgnoreCase(code) || "und".equalsIgnoreCase(code)) return;
        Locale parsed = Locale.forLanguageTag(code.replace('_', '-'));
        String name = parsed.getDisplayName(locale);
        if (name.isEmpty() || name.equalsIgnoreCase(code)) name = code;
        else name = name.substring(0, 1).toUpperCase(locale) + name.substring(1);
        entries.add(new Entry(name, I18n.get(label) + " · " + code, icon, DownloadSheet.AUDIO_TILE, null,
                name.equals(code) ? code : name + " (" + code + ")"));
    }

    private static void place(List<Entry> entries, Object poi, Locale locale) {
        String name = string(Reflect.get(poi, "poiName"));
        Object address = Reflect.get(poi, "addressInfo");
        String region = code(string(Reflect.get(address, "regionCode")));
        String where = join(string(Reflect.get(address, "cityName")), string(Reflect.get(address, "address")));
        if (where == null) where = string(Reflect.get(poi, "locationDesc"));
        if (name == null) name = where;
        if (name == null) return;
        StringBuilder detail = new StringBuilder(I18n.get(I18n.S.RI_PLACE));
        if (where != null && !where.equals(name)) detail.append(" · ").append(where);
        if (region != null) detail.append(" · ").append(country(region, locale));
        String copy = where != null && !where.equals(name) ? name + ", " + where : name;
        entries.add(new Entry(name, detail.toString(), Glyph.Kind.INFO, DownloadSheet.VIDEO_TILE, RegionFlag.of(region), copy));
    }

    private static void shownIn(List<Entry> entries, Object value, Locale locale) {
        if (!(value instanceof List)) return;
        Set<String> codes = new LinkedHashSet<>();
        for (Object item : (List<?>) value) {
            String code = code(string(item));
            if (code != null) codes.add(code);
        }
        if (codes.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (String code : codes) names.add(country(code, locale));
        String title = String.join(", ", names);
        String flag = codes.size() == 1 ? RegionFlag.of(codes.iterator().next()) : null;
        entries.add(new Entry(title, I18n.get(I18n.S.RI_ONLY_IN) + " · " + String.join(", ", codes), Glyph.Kind.GLOBE,
                DownloadSheet.VIDEO_TILE, flag, title));
    }

    private static void copy(Context context, String value) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || value == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("TiktokPatchXposed", value));
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, I18n.get(I18n.S.RI_COPIED), Toast.LENGTH_SHORT).show();
    }

    private static String country(String code, Locale locale) {
        String name = new Locale("", code).getDisplayCountry(locale);
        return name == null || name.isEmpty() ? code : name;
    }

    private static String code(String value) {
        if (value == null) return null;
        String code = value.trim().toUpperCase(Locale.ROOT);
        return RegionFlag.of(code) == null ? null : code;
    }

    private static String text(Object target, String getter) {
        return string(Reflect.call(target, getter));
    }

    private static String string(Object value) {
        if (!(value instanceof String)) return null;
        String text = ((String) value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String join(String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part == null) continue;
            boolean repeated = false;
            for (String other : kept) repeated |= other.equalsIgnoreCase(part);
            if (!repeated) kept.add(part);
        }
        return kept.isEmpty() ? null : String.join(", ", kept);
    }
}
