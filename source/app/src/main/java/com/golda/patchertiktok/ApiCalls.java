package com.golda.patchertiktok;

import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLEncoder;

final class ApiCalls {
    private static final String PROFILE_COMPONENT = "com.ss.android.ugc.aweme.services.ProfileDependentComponentImpl";
    private static final String API = "com.ss.android.ugc.aweme.app.api.Api";
    private static final String USER_SERVICE = "com.ss.android.ugc.aweme.framework.services.IUserService";

    private static volatile Object component;
    private static volatile Method execute;
    private static volatile String host;

    private ApiCalls() { }

    static JSONObject get(ClassLoader loader, String path) throws Exception {
        prepare(loader);
        Object body = execute.invoke(component, host + path, String.class, null, false, null, null);
        if (!(body instanceof String)) throw new IOException("no answer");
        JSONObject json = new JSONObject((String) body);
        int code = json.optInt("status_code", 0);
        if (code != 0) throw new IOException("status " + code);
        return json;
    }

    private static synchronized void prepare(ClassLoader loader) throws Exception {
        if (execute != null) return;
        Object service = Class.forName(PROFILE_COMPONENT, true, loader)
                .getMethod("createIProfileDependentComponentServicebyMonsterPlugin", boolean.class).invoke(null, false);
        if (service == null) throw new IOException("profile component unavailable");
        Method found = null;
        for (Method method : service.getClass().getMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if ("apiExecuteGetJSONObject".equals(method.getName()) && p.length == 6 && p[0] == String.class && p[1] == Class.class) {
                found = method;
            }
        }
        if (found == null) throw new IOException("request call not found");
        String base = null;
        for (Field field : Reflect.fields(Class.forName(API, true, loader))) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof String && ((String) value).matches("https://[^/]+")) base = (String) value;
        }
        if (base == null) throw new IOException("API host not found");
        found.setAccessible(true);
        component = service;
        host = base;
        execute = found;
    }

    static Object me(ClassLoader loader) throws Exception {
        Object user = Reflect.call(StreakApi.service(loader, USER_SERVICE), "getCurrentUser");
        Object id = Reflect.call(user, "getUid");
        if (!(id instanceof String) || ((String) id).isEmpty() || "0".equals(id)) throw new IOException("no account");
        return user;
    }

    static String text(Object model, String getter) {
        Object value = Reflect.call(model, getter);
        return value instanceof String ? (String) value : "";
    }

    static long count(Object model, String getter) {
        Object value = Reflect.call(model, getter);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    static boolean flag(JSONObject json, String key) {
        Object value = json.opt(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return value instanceof String && ("true".equals(value) || "1".equals(value));
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            return "";
        }
    }
}
