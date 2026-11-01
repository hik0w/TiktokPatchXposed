package com.golda.patchertiktok;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * More videos for TikTok's own offline viewing (profile menu, "Offline videos"). TikTok keeps the
 * allowed video counts in one list, read where it loads the saved count, and describes each count
 * through an enum of choices named after the counts. The list gets one more count, and an enum
 * entry the list does not use answers for it. TikTok downloads the videos itself, so only its
 * choices change.
 */
final class OfflineMore {
    /** A choice the usual list leaves out; its name also finds the obfuscated enum. */
    private static final String SPARE = "DOWNLOAD_200_VIDEOS";
    /** TikTok's key for the saved count, which leads to the code that checks it against the list. */
    private static final String SAVED_COUNT = "keva_cache_count";
    private static final int LARGEST_USUAL = 480;
    private static final int COUNT = 1_500;
    private static final int MINUTES = 750;
    private static final int MEGABYTES = 5_000;

    private OfflineMore() { }

    static void install(ClassLoader loader) {
        Class<?> choices = null;
        for (String name : Discovery.classesUsing(SPARE)) {
            Class<?> type = Hooks.findClass(name, loader);
            if (type != null && type.isEnum() && Reflect.method(type, "getCount") != null) choices = type;
        }
        if (choices == null) {
            RuntimeLog.log("offline videos unavailable: choices not found");
            return;
        }
        int lists = hookLists(loader);
        if (lists == 0) {
            RuntimeLog.log("offline videos unavailable: list not found");
            return;
        }
        int answers = answer(choices, "getCount", COUNT) + answer(choices, "getMinutes", MINUTES) + answer(choices, "getSize", MEGABYTES);
        RuntimeLog.log("offline videos: lists=" + lists + " answers=" + answers);
    }

    /** The list's getter: a static () -> List called by the methods that use the saved count. */
    private static int hookLists(ClassLoader loader) {
        java.util.Set<Method> getters = new java.util.HashSet<>();
        for (String user : Discovery.methodsUsing(SAVED_COUNT)) {
            String[] place = user.split("#", 2);
            for (String call : Discovery.invokedBy(place[0], place[1])) {
                String[] parts = call.split("#", 2);
                if (parts[0].startsWith("java.") || parts[0].startsWith("kotlin.")) continue;
                Class<?> owner = Hooks.findClass(parts[0], loader);
                Method getter = owner == null ? null : staticList(owner, parts[1]);
                if (getter != null) getters.add(getter);
            }
        }
        for (Method getter : getters) Hooks.hook(getter, new Hooks.Hook() {
            @Override protected void after(Hooks.Call param) {
                if (Prefs.on(Prefs.OFFLINE_MORE) && param.getResult() instanceof List) param.setResult(longer((List<?>) param.getResult()));
            }
        });
        return getters.size();
    }

    private static Method staticList(Class<?> owner, String name) {
        for (Method method : Reflect.methods(owner)) {
            if (name.equals(method.getName()) && method.getParameterCount() == 0 && Modifier.isStatic(method.getModifiers())
                    && List.class.isAssignableFrom(method.getReturnType())) return method;
        }
        return null;
    }

    /** The usual list with the extra count after its largest; any other list stays as it is. */
    private static List<?> longer(List<?> counts) {
        if (!counts.contains(LARGEST_USUAL) || counts.contains(COUNT)) return counts;
        List<Object> result = new ArrayList<>(counts);
        result.add(COUNT);
        return result;
    }

    private static int answer(Class<?> choices, String getter, int value) {
        return Hooks.hookAll(choices, getter, new Hooks.Hook() {
            @Override protected void after(Hooks.Call param) {
                if (Prefs.on(Prefs.OFFLINE_MORE) && SPARE.equals(((Enum<?>) param.thisObject).name())) param.setResult(value);
            }
        });
    }
}
