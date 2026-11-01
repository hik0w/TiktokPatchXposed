package com.golda.patchertiktok;

import android.app.Activity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * More videos for TikTok's own offline viewing (profile menu, "Offline videos"). The sheet makes
 * one row per video count (60, 120, 240, 480) and looks each up in an enum of choices named after
 * the counts, for its labels. An enum entry this list does not use answers as 1500 videos, and the
 * rows move one step up, to 120, 240, 480 and 1500. A saved 480 stays a valid choice. TikTok
 * downloads the videos itself, so only its choices change.
 */
final class OfflineMore {
    /** A choice TikTok's list leaves out here; its name also finds the obfuscated enum. */
    private static final String SPARE = "DOWNLOAD_200_VIDEOS";
    private static final String MANAGER = "OfflineModeManagerVM";
    private static final int COUNT = 1_500;
    private static final int MINUTES = 750;
    private static final int MEGABYTES = 5_000;
    /** TikTok's rows, each with the count it shows instead. */
    private static final int[][] ROWS = {{60, 120}, {120, 240}, {240, 480}, {480, COUNT}};

    private OfflineMore() { }

    static void install(ClassLoader loader) {
        Class<?> choices = null;
        for (String name : Discovery.classesUsing(SPARE)) {
            Class<?> type = Hooks.findClass(name, loader);
            if (type != null && type.isEnum() && Reflect.method(type, "getCount") != null) choices = type;
        }
        Method label = choices == null ? null : labelMethod(choices);
        if (label == null) {
            RuntimeLog.log("offline videos unavailable: choices not found");
            return;
        }
        int rows = 0;
        for (String caller : Discovery.methodsCalling(label.getDeclaringClass().getName(), label.getName())) {
            Class<?> type = Hooks.findClass(caller.split("#", 2)[0], loader);
            if (type != null) rows += hookRow(type);
        }
        if (rows == 0) {
            RuntimeLog.log("offline videos unavailable: rows not found");
            return;
        }
        int answers = answer(choices, "getCount", COUNT) + answer(choices, "getMinutes", MINUTES) + answer(choices, "getSize", MEGABYTES);
        RuntimeLog.log("offline videos: rows=" + rows + " answers=" + answers);
    }

    /** The enum companion's lookup of a count's label: (int, Activity) -> String. */
    private static Method labelMethod(Class<?> choices) {
        java.lang.reflect.Field companion = Reflect.field(choices, "Companion");
        if (companion == null) return null;
        for (Method method : Reflect.methods(companion.getType())) {
            Class<?>[] p = method.getParameterTypes();
            if (p.length == 2 && p[0] == int.class && p[1] == Activity.class && method.getReturnType() == String.class) return method;
        }
        return null;
    }

    /** A row's constructor takes its count first and the sheet's view model after it. */
    private static int hookRow(Class<?> type) {
        int count = 0;
        for (Constructor<?> constructor : Reflect.constructors(type)) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length < 2 || p[0] != int.class || !p[p.length - 1].getName().endsWith(MANAGER)) continue;
            Hooks.hook(constructor, new Hooks.Hook() {
                @Override protected void before(Hooks.Call param) {
                    if (Prefs.on(Prefs.OFFLINE_MORE)) param.args[0] = moved((Integer) param.args[0]);
                }
            });
            count++;
        }
        return count;
    }

    private static int moved(int count) {
        for (int[] row : ROWS) if (row[0] == count) return row[1];
        return count;
    }

    private static int answer(Class<?> choices, String getter, int value) {
        return Hooks.hookAll(choices, getter, new Hooks.Hook() {
            @Override protected void after(Hooks.Call param) {
                if (Prefs.on(Prefs.OFFLINE_MORE) && SPARE.equals(((Enum<?>) param.thisObject).name())) param.setResult(value);
            }
        });
    }
}
