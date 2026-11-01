package com.golda.patchertiktok;

import android.content.Context;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Takes back every repost of the signed-in account with TikTok's own two calls, the ones behind
 * its repost tab and its repost button, so they are signed and sent as usual. The list comes from
 * the upvote service (the method is known by its parameters) and each repost goes through
 * IUpvoteApi.deleteUpvote. Both answer with reactive streams whose classes are obfuscated; each
 * source has just one method, so a proxy listens to them without knowing their names. A listed
 * repost is the reposted video itself: the server finds the repost by the video and CarrierType.ITEM.
 * It runs on the caller's thread, one repost at a time with a pause, and stops if the server keeps
 * refusing.
 * Idea from MargyT 2.2.0; written here from scratch.
 */
final class RepostCleaner {
    private static final String USER_SERVICE = "com.ss.android.ugc.aweme.framework.services.IUserService";
    private static final String UPVOTE_SERVICE = "com.ss.android.ugc.aweme.upvote.service.UpvoteServiceImpl";
    private static final String UPVOTE_API = "com.ss.android.ugc.aweme.upvote.api.UpvoteApi";
    private static final String CARRIER = "com.ss.android.ugc.aweme.feed.model.upvote.CarrierType";
    private static final int PAGE = 20;
    private static final int MAX_PAGES = 500;
    private static final int BARREN_PAGES = 3;
    private static final long PAGE_PAUSE_MS = 400L;
    private static final long PAUSE_MS = 1_500L;
    private static final long ANSWER_MS = 30_000L;
    private static final int MAX_FAILURES = 3;

    interface Progress {
        /** Whether to go on; asked before each repost. */
        boolean wanted();
        void removed(int done);
    }

    static final class Repost {
        final String item;
        final int carrier;

        Repost(String item, int carrier) {
            this.item = item;
            this.carrier = carrier;
        }
    }

    private RepostCleaner() { }

    /** All reposts of the account. A page that fails fails the whole list, so nothing is guessed. */
    static List<Repost> list(Context context) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Object id = Reflect.call(StreakApi.service(loader, USER_SERVICE), "getCurrentUserID");
        if (!(id instanceof String) || ((String) id).isEmpty()) throw new IOException("no account");
        Object service = Class.forName(UPVOTE_SERVICE, true, loader).getConstructor().newInstance();
        Method page = pageMethod(service.getClass());
        int carrier = itemCarrier(loader);
        Map<String, Repost> found = new LinkedHashMap<>();
        int barren = 0;
        for (int number = 0; number < MAX_PAGES; number++) {
            Object answer = await(page.invoke(service, id, (long) number * PAGE, PAGE, null));
            Object posts = Reflect.get(answer, "repostList");
            int before = found.size();
            if (posts instanceof List) {
                for (Object post : (List<?>) posts) {
                    Repost repost = repost(post, carrier);
                    if (repost != null) found.put(repost.item, repost);
                }
            }
            barren = found.size() == before ? barren + 1 : 0;
            // The server can keep saying there is more while it sends nothing new.
            if (!Reflect.getBoolean(answer, "hasMore") || barren >= BARREN_PAGES) break;
            Thread.sleep(PAGE_PAUSE_MS);
        }
        RuntimeLog.log("repost cleaner: " + found.size() + " reposts");
        return new ArrayList<>(found.values());
    }

    /** Takes the reposts back; returns how many went. Stops after a few refusals in a row. */
    static int remove(Context context, List<Repost> reposts, Progress progress) throws Exception {
        Class<?> type = Class.forName(UPVOTE_API, true, context.getClassLoader());
        Object api = single(type);
        Method delete = null;
        for (Method method : Reflect.publicMethods(type)) {
            if ("deleteUpvote".equals(method.getName()) && method.getParameterCount() == 4) delete = method;
        }
        if (api == null || delete == null) throw new IOException("delete call not found");
        int done = 0, failures = 0;
        for (Repost repost : reposts) {
            if (!progress.wanted()) break;
            try {
                check(await(delete.invoke(api, repost.item, repost.carrier, null, null)));
                failures = 0;
                progress.removed(++done);
            } catch (IOException | InvocationTargetException error) {
                RuntimeLog.log("repost cleaner: one refused, " + error);
                if (++failures >= MAX_FAILURES) break;
            }
            Thread.sleep(PAUSE_MS);
        }
        RuntimeLog.log("repost cleaner: removed " + done + " of " + reposts.size());
        return done;
    }

    private static Method pageMethod(Class<?> service) throws IOException {
        Method found = null;
        for (Method method : Reflect.publicMethods(service)) {
            Class<?>[] p = method.getParameterTypes();
            if (p.length != 4 || p[0] != String.class || p[1] != long.class || p[2] != int.class || p[3] != Integer.class) continue;
            if (found != null) throw new IOException("repost list call is not unique");
            found = method;
        }
        if (found == null) throw new IOException("repost list call not found");
        return found;
    }

    /** The class's singleton: its one static field of its own type. */
    private static Object single(Class<?> type) throws IllegalAccessException {
        for (Field field : Reflect.fields(type)) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != type) continue;
            field.setAccessible(true);
            return field.get(null);
        }
        return null;
    }

    /** Each listed post is the reposted video; the server finds the repost by the video and the type. */
    private static Repost repost(Object post, int carrier) {
        Object item = Reflect.call(post, "getAid");
        return item instanceof String && !((String) item).isEmpty() ? new Repost((String) item, carrier) : null;
    }

    /** CarrierType.ITEM's number: what TikTok sends for a repost of a video. */
    private static int itemCarrier(ClassLoader loader) throws Exception {
        for (Object constant : Class.forName(CARRIER, true, loader).getEnumConstants()) {
            if (!"ITEM".equals(((Enum<?>) constant).name())) continue;
            Object value = Reflect.call(constant, "getValue");
            if (value instanceof Number) return ((Number) value).intValue();
        }
        throw new IOException("carrier type not found");
    }

    /** TikTok's answers carry status_code; 0 or nothing to read means it was done. */
    private static void check(Object answer) throws IOException {
        Object code = Reflect.get(answer, "status_code");
        if (code instanceof Number && ((Number) code).intValue() != 0) throw new IOException("status " + code);
    }

    /** Subscribes to a stream or a single and waits for its first value. */
    private static Object await(Object source) throws Exception {
        if (source == null) throw new IOException("no answer");
        Method subscribe = subscribe(source.getClass());
        Class<?> listener = subscribe.getParameterTypes()[0];
        Object[] value = new Object[1];
        Throwable[] failure = new Throwable[1];
        CountDownLatch answered = new CountDownLatch(1);
        InvocationHandler handler = new InvocationHandler() {
            private boolean subscribed;

            @Override public Object invoke(Object proxy, Method method, Object[] args) {
                if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
                if (args == null || args.length == 0) {
                    answered.countDown();
                } else if (args[0] instanceof Throwable) {
                    failure[0] = (Throwable) args[0];
                    answered.countDown();
                } else if (!subscribed) {
                    subscribed = true;
                } else {
                    if (value[0] == null) value[0] = args[0];
                    answered.countDown();
                }
                return null;
            }
        };
        Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, handler);
        subscribe.setAccessible(true);
        subscribe.invoke(source, proxy);
        if (!answered.await(ANSWER_MS, TimeUnit.MILLISECONDS)) throw new IOException("no answer in time");
        if (failure[0] != null) throw new IOException(failure[0].toString());
        return value[0];
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            default: return "repost cleaner listener";
        }
    }

    /** The source interface's only method, which takes the listener interface. */
    private static Method subscribe(Class<?> type) throws IOException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> source : current.getInterfaces()) {
                Method[] methods = source.getMethods();
                if (methods.length == 1 && methods[0].getParameterCount() == 1 && methods[0].getParameterTypes()[0].isInterface()) {
                    return methods[0];
                }
            }
        }
        throw new IOException("not a stream: " + type.getName());
    }
}
