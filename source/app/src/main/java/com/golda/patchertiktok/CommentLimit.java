package com.golda.patchertiktok;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;

/**
 * Lifts the length limit of the comment box. TikTok keeps it in CommentCharLimitConfig, which it
 * reads from its settings: the object is made first and its fields are filled in right after, so
 * the limit is set when the object is made and once more a moment later. Only the box on this
 * phone changes; TikTok's server may still refuse a very long comment.
 */
final class CommentLimit {
    private static final String CONFIG = "com.ss.android.ugc.aweme.comment.commentlist.ui.CommentCharLimitConfig";
    private static final int LIMIT = 100_000;
    private static final long FILLED_IN_MS = 1_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CommentLimit() { }

    static void install(ClassLoader loader) {
        Class<?> config = Hooks.findClass(CONFIG, loader);
        Field limit = config == null ? null : Reflect.field(config, "charLimit");
        if (limit == null) {
            RuntimeLog.log("comment limit unavailable: limit not found");
            return;
        }
        int hooks = Hooks.hookConstructors(config, new Hooks.Hook() {
            @Override protected void after(Hooks.Call param) {
                if (!Prefs.on(Prefs.COMMENT_NO_LIMIT)) return;
                Object made = param.thisObject;
                lift(limit, made);
                MAIN.postDelayed(Safe.run("comment limit", () -> lift(limit, made)), FILLED_IN_MS);
            }
        });
        RuntimeLog.log("comment limit: hooks=" + hooks);
    }

    private static void lift(Field limit, Object config) {
        try {
            limit.setInt(config, LIMIT);
        } catch (IllegalAccessException | IllegalArgumentException error) {
            RuntimeLog.log("comment limit failed: " + error);
        }
    }
}
