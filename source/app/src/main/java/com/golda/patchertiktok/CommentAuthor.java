package com.golda.patchertiktok;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Who wrote a comment: the commenter's country flag (with "Author's country") and their @handle,
 * after the name in the comment row. The row is found by TikTok's comment cell bind (it calls
 * PowerCell.onBindItemView and reads the comment's language); the name is the row's text that
 * starts with the commenter's nickname. Rows are reused, so what we add is remembered and replaced
 * on every bind. The flag is the region TikTok gives the account, not where the person is.
 */
final class CommentAuthor {
    private static final String CELL = "com.ss.android.ugc.aweme.commentv2.commentlist.powercell.BaseCommentCell";
    private static final String COMMENT = "com.ss.android.ugc.aweme.comment.model.Comment";
    private static final String POWER_CELL = "com.bytedance.ies.powerlist.PowerCell";
    /** The text we last wrote into a name label, to tell it from TikTok's own writes. */
    private static final String WRITTEN = "com.golda.patchertiktok.commentAuthor";
    /** TikTok's own text of that label, before ours was added. */
    private static final String BASE = "com.golda.patchertiktok.commentAuthorBase";

    private static final List<Method> COMMENTS = new ArrayList<>();
    private static Field itemView;

    private CommentAuthor() { }

    static void install(ClassLoader loader) {
        Class<?> cell = Hooks.findClass(CELL, loader);
        Class<?> comment = Hooks.findClass(COMMENT, loader);
        if (cell == null || comment == null) {
            RuntimeLog.log("comment author unavailable: comment cell not found");
            return;
        }
        for (Method method : Reflect.methods(cell)) {
            if (method.getReturnType() == comment && method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers())) {
                method.setAccessible(true);
                COMMENTS.add(method);
            }
        }
        itemView = Reflect.field(cell, "itemView");
        int binds = 0;
        for (Method method : Reflect.methods(cell)) {
            if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())
                    || method.getReturnType() != void.class || method.getParameterCount() != 1) continue;
            List<String> calls = Discovery.invokedBy(cell.getName(), method.getName());
            if (!calls.contains(POWER_CELL + "#onBindItemView") || !calls.contains(COMMENT + "#getCommentLanguage")) continue;
            Hooks.hook(method, new Hooks.Hook() {
                @Override protected void after(Hooks.Call param) {
                    if (Prefs.on(Prefs.AUTHOR_REGION) || Prefs.on(Prefs.COMMENT_HANDLE)) bound(param.thisObject);
                }
            });
            binds++;
        }
        RuntimeLog.log("comment author: binds=" + binds + " comment getters=" + COMMENTS.size());
    }

    private static void bound(Object cell) {
        Object row = null;
        try {
            row = itemView == null ? null : itemView.get(cell);
        } catch (IllegalAccessException ignored) {
            // No row.
        }
        if (!(row instanceof ViewGroup)) return;
        Object comment = comment(cell);
        Object user = Reflect.call(comment, "getUser");
        Object nickname = Reflect.call(user, "getNickname");
        if (!(nickname instanceof String) || ((String) nickname).isEmpty()) return;
        String code = Prefs.on(Prefs.AUTHOR_REGION) ? region(user) : null;
        if (code == null && Prefs.on(Prefs.AUTHOR_REGION) && Prefs.on(Prefs.COMMENT_REGION)) lookUp(cell, user);
        String flag = RegionFlag.of(code);
        Object handle = Prefs.on(Prefs.COMMENT_HANDLE) ? Reflect.call(user, "getUniqueId") : null;
        String at = handle instanceof String && !((String) handle).isEmpty() && !handle.equals(nickname) ? "@" + handle : null;
        ViewGroup view = (ViewGroup) row;
        // TikTok can style the name a moment after the bind; mark it then.
        view.post(Safe.run("comment author", () -> {
            TextView name = nameLabel(view, (String) nickname);
            if (name == null) return;
            if (flag != null) RegionCard.attach(name, code, user, null, comment);
            mark(name, at, flag);
        }));
    }

    private static void lookUp(Object cell, Object user) {
        Object handle = Reflect.call(user, "getUniqueId");
        Object uid = Reflect.call(user, "getUid");
        Object secUid = Reflect.call(user, "getSecUid");
        if (!(handle instanceof String) || !(secUid instanceof String)) return;
        RegionLookup.request(cell.getClass().getClassLoader(), (String) handle, uid instanceof String ? (String) uid : null,
                (String) secUid, () -> bound(cell));
    }

    private static Object comment(Object cell) {
        for (Method method : COMMENTS) {
            try {
                Object value = method.invoke(cell);
                if (value != null) return value;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Next getter.
            }
        }
        return null;
    }

    /** The row's first text that starts with the nickname: the name, not the comment text under it. */
    private static TextView nameLabel(ViewGroup group, String nickname) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && text.toString().startsWith(nickname)) return (TextView) child;
            } else if (child instanceof ViewGroup) {
                // Replies sit in their own rows; stop at a list inside this one.
                if (Views.isRecycler(child)) continue;
                TextView found = nameLabel((ViewGroup) child, nickname);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void mark(TextView name, String handle, String flag) {
        CharSequence text = name.getText();
        if (text == null) return;
        // Our text from the last bind of this reused row goes; TikTok's own (and its spans) stays.
        Object written = Hooks.getExtra(name, WRITTEN);
        Object before = Hooks.getExtra(name, BASE);
        CharSequence base = written instanceof String && before instanceof CharSequence && text.toString().equals(written)
                ? (CharSequence) before : text;
        Hooks.setExtra(name, BASE, base);
        SpannableStringBuilder result = new SpannableStringBuilder(base);
        if (handle != null) {
            int start = result.length();
            result.append(' ').append(handle);
            result.setSpan(new ForegroundColorSpan((name.getCurrentTextColor() & 0x00FFFFFF) | 0x99000000), start,
                    result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            result.setSpan(new RelativeSizeSpan(0.9f), start, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (flag != null) result.append(' ').append(flag);
        if (result.toString().equals(text.toString())) return;
        Hooks.setExtra(name, WRITTEN, result.toString());
        name.setText(result);
    }

    /**
     * TikTok's comment payload usually carries no country for the commenter; then the one seen for
     * the same @handle in the feed or on a profile is used, else no flag.
     */
    private static String region(Object user) {
        for (String getter : new String[]{"getRegion", "getAccountRegion", "getIsoCountryCode"}) {
            Object value = Reflect.call(user, getter);
            if (value instanceof String && ((String) value).length() == 2) return (String) value;
        }
        Object handle = Reflect.call(user, "getUniqueId");
        if (!(handle instanceof String)) return null;
        String seen = AuthorRegion.seen((String) handle);
        return seen != null || !Prefs.on(Prefs.COMMENT_REGION) ? seen : RegionLookup.found((String) handle);
    }
}
