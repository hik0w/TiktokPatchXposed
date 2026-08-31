package com.golda.patchertiktok;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedBridge;

final class AutoStreakManager {
    static final String ACTION_AUTO_STREAK =
            "com.golda.patchertiktok.AUTO_STREAK_NUDGE";
    static final String ACTION_AUTO_STREAK_BACKUP =
            "com.golda.patchertiktok.AUTO_STREAK_NUDGE_BACKUP";

    private static final String TAG = "Xposed-TikTokPatcher[auto-streak]";
    private static final String PREFS_NAME = "ttmod_settings";
    private static final String KEY_ENABLED = "auto_streak_nudge_enabled";
    private static final String KEY_HOUR = "auto_streak_nudge_hour";
    private static final String KEY_MINUTE = "auto_streak_nudge_minute";
    private static final String KEY_LAST_RUN_DATE = "auto_streak_nudge_last_run_date";
    private static final String KEY_SENT_DATE = "auto_streak_nudge_sent_date";
    private static final String KEY_SENT_CONVERSATIONS =
            "auto_streak_nudge_sent_conversations";
    private static final String KEY_464_MIGRATED = "auto_streak_464_v2_migrated";

    private static final int DEFAULT_HOUR = 12;
    private static final int DEFAULT_MINUTE = 0;
    private static final int ALARM_REQUEST_CODE = 44021;
    private static final int BACKUP_ALARM_REQUEST_CODE = 44022;
    private static final long START_DELAY_MS = 20_000L;
    private static final long HEARTBEAT_MS = 300_000L;
    private static final long SEND_PAUSE_MS = 3_000L;

    private static final String IM_HOST_RUNTIME_INSTANCE = "LJFF";
    private static final String ACCOUNT_SERVICE_METHOD = "LIZ";
    private static final String[][] ACCOUNT_MAPPINGS = {
            {"X.03YM", "TikTok 46.7.3"},
            {"X.03ZI", "TikTok 46.6.3"},
            {"X.03PG", "TikTok 46.5.3"},
            {"X.067M", "TikTok 46.4.3"},
            {"X.0dOk", "TikTok 46.4.x"},
            {"X.0HQS", "TikTok 45.9.3"}
    };
    private static final String[][] STREAK_MAPPINGS = {
            {"X.0BmI", "X.0CXw", "TikTok 46.7.3"},
            {"X.0Bqe", "X.0CdZ", "TikTok 46.6.3"},
            {"X.0Bo9", "X.0CWG", "TikTok 46.5.3"},
            {"X.0TS5", "X.0TSt", "TikTok 46.4.3"},
            {"X.0dPg", "X.0dPq", "TikTok 46.4.x"},
            {"X.0Uqa", "X.0US4", "TikTok 45.9.3"}
    };
    private static final String STREAK_PROVIDER_FIELD = "LIZIZ";
    private static final String STREAK_PROVIDER_METHOD = "LIZ";
    private static final String STREAK_PROVIDER_METHOD_FALLBACK = "LJ";

    private static final String SERVICE_MANAGER_CLASS =
            "com.ss.android.ugc.aweme.framework.services.ServiceManager";
    private static final String LIGHT_INTERACTION_SERVICE_464 =
            "com.ss.android.ugc.aweme.im.lightinteract.api.platform.service.ILightInteractionPlatformService";
    private static final String LIGHT_INTERACTION_MANAGER_459 =
            "com.ss.android.ugc.aweme.im.lightinteract.impl.serviceimpl.LightInteractionManager";

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicBoolean TRIGGER_PENDING = new AtomicBoolean(false);
    private static final AtomicBoolean HEARTBEAT_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean SEND_IN_PROGRESS = new AtomicBoolean(false);

    private static volatile Context appContext;
    private static volatile ClassLoader targetClassLoader;
    private static volatile String alarmReceiverClassName;

    private static final Runnable HEARTBEAT = new Runnable() {
        @Override
        public void run() {
            try {
                Context context = appContext;
                if (context == null) return;
                if (!isEnabled(context)) {
                    cancelAlarm(context);
                    stopHeartbeat();
                    return;
                }
                if (getDueDate(context) == null) {
                    stopHeartbeat();
                    return;
                }
                runIfDueAsync(context, "heartbeat");
                scheduleNextAlarm(context);
            } catch (Throwable t) {
                log("heartbeat failed", t);
            } finally {
                if (HEARTBEAT_STARTED.get()) {
                    MAIN_HANDLER.postDelayed(this, HEARTBEAT_MS);
                }
            }
        }
    };

    private AutoStreakManager() {
    }

    static void configure(ClassLoader classLoader, String receiverClassName) {
        if (classLoader != null) targetClassLoader = classLoader;
        alarmReceiverClassName = receiverClassName;
    }

    static void initialize(Context context, ClassLoader classLoader, String receiverClassName) {
        if (context == null || classLoader == null) return;
        configure(classLoader, receiverClassName);

        Context resolved = context.getApplicationContext();
        appContext = resolved == null ? context : resolved;
        if (!INITIALIZED.compareAndSet(false, true)) return;
        try {
            SharedPreferences preferences = prefs(appContext);
            if (!preferences.getBoolean(KEY_464_MIGRATED, false)) {
                preferences.edit()
                        .putInt(KEY_HOUR, DEFAULT_HOUR)
                        .putInt(KEY_MINUTE, DEFAULT_MINUTE)
                        .putString(KEY_LAST_RUN_DATE, "")
                        .putString(KEY_SENT_DATE, "")
                        .remove(KEY_SENT_CONVERSATIONS)
                        .putBoolean(KEY_464_MIGRATED, true)
                        .commit();
            }

            log("initialized; mappings=46.7.3+46.6.3+46.5.3+46.4.3+46.4.1+45.9.3; schedule="
                    + getScheduleLabel(preferences));
            if (!isEnabled(appContext)) {
                cancelAlarm(appContext);
                return;
            }

            scheduleNextAlarm(appContext);
            if (getDueDate(appContext) != null) startHeartbeat();
            triggerAfterDelay(appContext, "init", START_DELAY_MS);
        } catch (Throwable t) {
            INITIALIZED.set(false);
            log("initialization failed", t);
        }
    }

    static void onAlarm(Context context) {
        Context resolved = context == null ? appContext : context.getApplicationContext();
        if (resolved == null || !isEnabled(resolved)) return;

        appContext = resolved;
        scheduleNextAlarm(resolved);
        startHeartbeat();
        triggerAfterDelay(resolved, "alarm", START_DELAY_MS);
    }

    private static void startHeartbeat() {
        if (HEARTBEAT_STARTED.compareAndSet(false, true)) {
            MAIN_HANDLER.removeCallbacks(HEARTBEAT);
            MAIN_HANDLER.postDelayed(HEARTBEAT, HEARTBEAT_MS);
        }
    }

    private static void stopHeartbeat() {
        HEARTBEAT_STARTED.set(false);
        MAIN_HANDLER.removeCallbacks(HEARTBEAT);
    }

    private static void triggerAfterDelay(final Context context, final String source, long delayMs) {
        if (!TRIGGER_PENDING.compareAndSet(false, true)) return;
        MAIN_HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    runIfDueAsync(context, source);
                } finally {
                    TRIGGER_PENDING.set(false);
                }
            }
        }, delayMs);
    }

    static void runIfDueAsync(final Context context, final String source) {
        final String dueDate = getDueDate(context);
        if (dueDate == null || !SEND_IN_PROGRESS.compareAndSet(false, true)) return;

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sendActiveStreakNudges(context, source, dueDate);
                } catch (Throwable t) {
                    log("run failed", t);
                } finally {
                    SEND_IN_PROGRESS.set(false);
                }
            }
        }, "tiktok-auto-streak");
        worker.setDaemon(true);
        worker.start();
    }

    private static void sendActiveStreakNudges(Context context, String source, String dueDate) {
        log("run started source=" + source + " dueDate=" + dueDate);
        String currentUserId = getCurrentUserId();
        if (TextUtils.isEmpty(currentUserId)) {
            log("account is not ready; retry deferred");
            return;
        }

        List<?> items = getStreakItems();
        if (items == null) {
            log("streak provider is not ready; retry deferred");
            return;
        }
        log("streak provider returned items=" + items.size());

        SharedPreferences preferences = prefs(context);
        Set<String> sentToday = loadSentConversations(preferences, dueDate);
        Set<String> seenConversations = new HashSet<>();
        int eligible = 0;
        int attempted = 0;
        int sent = 0;
        int failed = 0;

        for (Object item : items) {
            if (item == null || !isStreakAtRisk(item)) continue;
            try {
                String conversationId = getStringField(item, "convId");
                String peerUserId = getPeerUserId(item, currentUserId);
                if (TextUtils.isEmpty(conversationId)
                        || TextUtils.isEmpty(peerUserId)
                        || !seenConversations.add(conversationId)) {
                    continue;
                }

                eligible++;
                if (sentToday.contains(conversationId)) {
                    log("already sent today conv=" + maskId(conversationId));
                    continue;
                }

                attempted++;
                log("sending spark_v1 conv=" + maskId(conversationId)
                        + " peer=" + maskId(peerUserId));
                if (sendNudge(peerUserId, conversationId)) {
                    sent++;
                    sentToday.add(conversationId);
                    persistSentConversations(preferences, dueDate, sentToday);
                    log("spark_v1 invocation succeeded conv=" + maskId(conversationId));
                    Thread.sleep(SEND_PAUSE_MS);
                } else {
                    failed++;
                }
            } catch (InterruptedException e) {
                failed++;
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                failed++;
                log("conversation skipped", t);
            }
        }

        if (failed == 0) {
            preferences.edit().putString(KEY_LAST_RUN_DATE, dueDate).commit();
            stopHeartbeat();
        }
        log("run finished source=" + source
                + " items=" + items.size()
                + " unique=" + seenConversations.size()
                + " eligible=" + eligible
                + " attempted=" + attempted
                + " sent=" + sent
                + " failed=" + failed);
    }

    private static Set<String> loadSentConversations(
            SharedPreferences preferences,
            String dueDate
    ) {
        Set<String> result = new HashSet<>();
        if (dueDate.equals(preferences.getString(KEY_SENT_DATE, ""))) {
            Set<String> stored = preferences.getStringSet(KEY_SENT_CONVERSATIONS, null);
            if (stored != null) result.addAll(stored);
        } else {
            preferences.edit()
                    .putString(KEY_SENT_DATE, dueDate)
                    .remove(KEY_SENT_CONVERSATIONS)
                    .commit();
        }
        return result;
    }

    private static void persistSentConversations(
            SharedPreferences preferences,
            String dueDate,
            Set<String> sentConversations
    ) {
        preferences.edit()
                .putString(KEY_SENT_DATE, dueDate)
                .putStringSet(KEY_SENT_CONVERSATIONS, new HashSet<>(sentConversations))
                .commit();
    }

    private static String getCurrentUserId() {
        for (String[] mapping : ACCOUNT_MAPPINGS) {
            try {
                Object runtime = findField(
                        findClass(mapping[0]),
                        IM_HOST_RUNTIME_INSTANCE
                ).get(null);
                if (runtime == null) continue;

                Object account = findMethod(runtime.getClass(), ACCOUNT_SERVICE_METHOD)
                        .invoke(runtime);
                if (account == null) continue;
                if (!Boolean.TRUE.equals(findMethod(account.getClass(), "isLogin").invoke(account))) {
                    continue;
                }

                Object userId = findMethod(account.getClass(), "getCurUserId").invoke(account);
                if (userId instanceof String && !TextUtils.isEmpty((String) userId)) {
                    log("account mapping=" + mapping[1] + " (" + mapping[0] + ")");
                    return (String) userId;
                }
            } catch (Throwable t) {
                log("account mapping unavailable " + mapping[0], t);
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<?> getStreakItems() {
        for (String[] mapping : STREAK_MAPPINGS) {
            try {
                Object provider = findField(
                        findClass(mapping[0]),
                        STREAK_PROVIDER_FIELD
                ).get(null);
                if (provider == null) continue;

                Class<? extends Enum> typeClass =
                        (Class<? extends Enum>) findClass(mapping[1]);
                List<Enum> types = new ArrayList<>(2);
                addEnumIfPresent(types, typeClass, "USER");
                addEnumIfPresent(types, typeClass, "CONVERSATION");
                if (types.isEmpty()) continue;

                Method method;
                try {
                    method = findMethod(provider.getClass(), STREAK_PROVIDER_METHOD, List.class);
                } catch (NoSuchMethodException e) {
                    method = findMethod(
                            provider.getClass(),
                            STREAK_PROVIDER_METHOD_FALLBACK,
                            List.class
                    );
                }
                Object result = method.invoke(provider, types);
                if (result instanceof List<?>) {
                    log("streak mapping=" + mapping[2]
                            + " (" + mapping[0] + "/" + mapping[1] + ")");
                    return (List<?>) result;
                }
            } catch (Throwable t) {
                log("streak mapping unavailable " + mapping[0] + "/" + mapping[1], t);
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addEnumIfPresent(
            List<Enum> output,
            Class<? extends Enum> enumClass,
            String name
    ) {
        try {
            output.add(Enum.valueOf(enumClass, name));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static boolean isStreakAtRisk(Object item) {
        try {
            Object streak = findField(item.getClass(), "streak").get(item);
            Object users = findField(item.getClass(), "userStreak").get(item);
            long activeStart = getLongField(item, "activeStart", 0L);
            long activeBefore = getLongField(item, "activeBefore", 0L);
            long endAt = getLongField(item, "endAt", 0L);
            return StreakEligibility.isAtRisk(
                    hasStreakValue(streak),
                    users instanceof List<?> && !((List<?>) users).isEmpty(),
                    activeStart,
                    activeBefore,
                    endAt,
                    System.currentTimeMillis()
            );
        } catch (Throwable directFailure) {
            log("at-risk state check failed", directFailure);
            return false;
        }
    }

    private static boolean hasStreakValue(Object value) {
        return value instanceof Number && ((Number) value).longValue() != 0L;
    }

    private static String getPeerUserId(Object item, String currentUserId) throws Exception {
        Object users = findField(item.getClass(), "userStreak").get(item);
        if (!(users instanceof List<?>)) return null;

        String firstValid = null;
        for (Object user : (List<?>) users) {
            if (user == null) continue;
            String value = valueToId(findField(user.getClass(), "uid").get(user));
            if (TextUtils.isEmpty(value) || "0".equals(value)) continue;
            if (firstValid == null) firstValid = value;
            if (!value.equals(currentUserId)) return value;
        }
        return firstValid != null && !firstValid.equals(currentUserId) ? firstValid : null;
    }

    private static boolean sendNudge(String peerUserId, String conversationId) {
        Throwable currentApiFailure = null;
        try {
            Class<?> serviceInterface = findClass(LIGHT_INTERACTION_SERVICE_464);
            Object serviceManager = findMethod(findClass(SERVICE_MANAGER_CLASS), "get")
                    .invoke(null);
            Object service = findMethod(serviceManager.getClass(), "getService", Class.class)
                    .invoke(serviceManager, serviceInterface);
            if (service == null) {
                throw new IllegalStateException("ILightInteractionPlatformService is unavailable");
            }

            Object[] args = new Object[11];
            args[0] = "spark_v1";
            args[1] = conversationId;
            args[2] = peerUserId;
            args[4] = createMetadata();
            findNudgeMethod(service.getClass()).invoke(service, args);
            log("send API=ILightInteractionPlatformService");
            return true;
        } catch (Throwable t) {
            currentApiFailure = t;
        }

        try {
            Class<?> managerClass = findClass(LIGHT_INTERACTION_MANAGER_459);
            Object manager = findManagerInstance(managerClass);
            if (manager == null) return false;

            Object[] args = new Object[11];
            args[0] = "spark_v1";
            args[1] = conversationId;
            args[2] = peerUserId;
            args[3] = createMetadata();
            findNudgeMethod(managerClass).invoke(manager, args);
            log("send API=LightInteractionManager fallback");
            return true;
        } catch (Throwable fallbackFailure) {
            log("current nudge API failed", currentApiFailure);
            log("nudge invocation failed", fallbackFailure);
            return false;
        }
    }

    private static Map<String, String> createMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("enter_from", "streak_inbox");
        metadata.put("enter_method", "auto_streak");
        metadata.put("interaction_type", "quick_reaction");
        metadata.put("interaction_name", "streak");
        metadata.put("message_from", "spark");
        return metadata;
    }

    private static Object findManagerInstance(Class<?> managerClass) throws Exception {
        for (Class<?> current = managerClass; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        || !managerClass.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(null);
                if (managerClass.isInstance(value)) return value;
            }
        }

        Constructor<?> constructor = managerClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Method findNudgeMethod(Class<?> type) throws NoSuchMethodException {
        Method match = findNudgeMethodIn(type.getMethods());
        if (match != null) return match;

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            match = findNudgeMethodIn(current.getDeclaredMethods());
            if (match != null) return match;
        }
        throw new NoSuchMethodException(type.getName() + "#<11-arg streak nudge>");
    }

    private static Method findNudgeMethodIn(Method[] methods) {
        for (Method method : methods) {
            Class<?>[] params = method.getParameterTypes();
            if (method.getReturnType() != Void.TYPE || params.length != 11) continue;
            if (!String.class.isAssignableFrom(params[0])
                    || !String.class.isAssignableFrom(params[1])
                    || !String.class.isAssignableFrom(params[2])
                    || !Map.class.isAssignableFrom(params[3])
                    || !Map.class.isAssignableFrom(params[4])) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static Class<?> findClass(String name) throws ClassNotFoundException {
        ClassLoader loader = targetClassLoader;
        if (loader == null) throw new ClassNotFoundException(name + " (classloader unavailable)");
        return Class.forName(name, false, loader);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, params);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                for (Class<?> iface : current.getInterfaces()) {
                    try {
                        Method method = iface.getDeclaredMethod(name, params);
                        method.setAccessible(true);
                        return method;
                    } catch (NoSuchMethodException ignoredInterface) {
                    }
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static String getStringField(Object target, String name) {
        try {
            return valueToId(findField(target.getClass(), name).get(target));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long getLongField(Object target, String name, long fallback) {
        try {
            Object value = findField(target.getClass(), name).get(target);
            return value instanceof Number ? ((Number) value).longValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String valueToId(Object value) {
        if (value instanceof CharSequence) return value.toString();
        if (value instanceof Number) return String.valueOf(((Number) value).longValue());
        return null;
    }

    private static String maskId(String value) {
        if (TextUtils.isEmpty(value) || value.length() <= 4) return value;
        return "..." + value.substring(value.length() - 4);
    }

    private static String getDueDate(Context context) {
        if (!isEnabled(context)) return null;

        SharedPreferences preferences = prefs(context);
        int hour = clamp(preferences.getInt(KEY_HOUR, DEFAULT_HOUR), 0, 23, DEFAULT_HOUR);
        int minute = clamp(
                preferences.getInt(KEY_MINUTE, DEFAULT_MINUTE),
                0,
                59,
                DEFAULT_MINUTE
        );
        Calendar now = Calendar.getInstance();
        Calendar scheduled = (Calendar) now.clone();
        scheduled.set(Calendar.HOUR_OF_DAY, hour);
        scheduled.set(Calendar.MINUTE, minute);
        scheduled.set(Calendar.SECOND, 0);
        scheduled.set(Calendar.MILLISECOND, 0);

        Calendar activeSlot = findActiveDueSlot(now, scheduled);
        if (activeSlot == null) return null;
        String date = formatDate(activeSlot.getTime());
        return date.equals(preferences.getString(KEY_LAST_RUN_DATE, "")) ? null : date;
    }

    private static Calendar findActiveDueSlot(Calendar now, Calendar scheduledToday) {
        Calendar end = (Calendar) scheduledToday.clone();
        end.add(Calendar.HOUR_OF_DAY, 12);
        if (!now.before(scheduledToday) && now.before(end)) return scheduledToday;

        Calendar scheduledYesterday = (Calendar) scheduledToday.clone();
        scheduledYesterday.add(Calendar.DAY_OF_MONTH, -1);
        Calendar yesterdayEnd = (Calendar) scheduledYesterday.clone();
        yesterdayEnd.add(Calendar.HOUR_OF_DAY, 12);
        if (!now.before(scheduledYesterday) && now.before(yesterdayEnd)) {
            return scheduledYesterday;
        }
        return null;
    }

    static void scheduleNextAlarm(Context context) {
        String receiver = alarmReceiverClassName;
        if (context == null || TextUtils.isEmpty(receiver) || !isEnabled(context)) return;

        try {
            SharedPreferences preferences = prefs(context);
            int hour = clamp(preferences.getInt(KEY_HOUR, DEFAULT_HOUR), 0, 23, DEFAULT_HOUR);
            int minute = clamp(
                    preferences.getInt(KEY_MINUTE, DEFAULT_MINUTE),
                    0,
                    59,
                    DEFAULT_MINUTE
            );
            Calendar now = Calendar.getInstance();
            Calendar next = (Calendar) now.clone();
            next.set(Calendar.HOUR_OF_DAY, hour);
            next.set(Calendar.MINUTE, minute);
            next.set(Calendar.SECOND, 0);
            next.set(Calendar.MILLISECOND, 0);
            if (!now.before(next)) next.add(Calendar.DAY_OF_MONTH, 1);

            AlarmManager alarmManager =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            PendingIntent exact = createAlarmPendingIntent(
                    context,
                    receiver,
                    ACTION_AUTO_STREAK,
                    ALARM_REQUEST_CODE
            );
            PendingIntent backup = createAlarmPendingIntent(
                    context,
                    receiver,
                    ACTION_AUTO_STREAK_BACKUP,
                    BACKUP_ALARM_REQUEST_CODE
            );
            long triggerAt = next.getTimeInMillis();
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            exact
                    );
                }
            } catch (SecurityException e) {
                log("exact alarm unavailable; backup retained", e);
            }
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, backup);
        } catch (Throwable t) {
            log("alarm scheduling failed", t);
        }
    }

    private static void cancelAlarm(Context context) {
        if (context == null || TextUtils.isEmpty(alarmReceiverClassName)) return;
        try {
            AlarmManager alarmManager =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;
            alarmManager.cancel(createAlarmPendingIntent(
                    context,
                    alarmReceiverClassName,
                    ACTION_AUTO_STREAK,
                    ALARM_REQUEST_CODE
            ));
            alarmManager.cancel(createAlarmPendingIntent(
                    context,
                    alarmReceiverClassName,
                    ACTION_AUTO_STREAK_BACKUP,
                    BACKUP_ALARM_REQUEST_CODE
            ));
        } catch (Throwable t) {
            log("alarm cancel failed", t);
        }
    }

    private static PendingIntent createAlarmPendingIntent(
            Context context,
            String receiverClassName,
            String action,
            int requestCode
    ) {
        Intent intent = new Intent(action);
        intent.setComponent(new ComponentName(context.getPackageName(), receiverClassName));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static boolean isEnabled(Context context) {
        return context != null && prefs(context).getBoolean(KEY_ENABLED, true);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        return value >= min && value <= max ? value : fallback;
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    private static String getScheduleLabel(SharedPreferences preferences) {
        int hour = clamp(preferences.getInt(KEY_HOUR, DEFAULT_HOUR), 0, 23, DEFAULT_HOUR);
        int minute = clamp(
                preferences.getInt(KEY_MINUTE, DEFAULT_MINUTE),
                0,
                59,
                DEFAULT_MINUTE
        );
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static void log(String message, Throwable t) {
        XposedBridge.log(TAG + ": " + message + ": " + t);
    }
}
