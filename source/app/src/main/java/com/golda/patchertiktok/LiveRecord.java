package com.golda.patchertiktok;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Records a LIVE. The room on screen keeps its streams in a "stream_data" JSON; the newest one
 * TikTok's LIVE model hands to the player is kept in case the room can't be read. A REC button in
 * the LIVE room copies the chosen stream's FLV until it is tapped again, the LIVE ends or the room
 * is left, then saves it as MP4 in Movies/TikTok (the FLV stays if that fails). Nothing is sent to
 * TikTok: the stream is the one the player already pulls from TikTok's CDN. Needs Android 10 for
 * the Movies folder; recording stops early when the phone runs low on space.
 * Idea and first version by RSR1337 (PR #16).
 */
final class LiveRecord {
    private static final String[] PULL_DATA = {
            "com.bytedance.android.livesdkapi.depend.model.live.LiveCoreSDKData$PullData",
            "com.ss.android.ugc.aweme.feed.model.live.LiveCoreSDKData$PullData",
    };
    /** The LIVE room; it lives in TikTok's LIVE add-on, so it is matched by name. */
    private static final String ROOM = "com.ss.android.ugc.aweme.live.LivePlayActivity";
    private static final String ROOM_MODEL = "com.bytedance.android.livesdkapi.depend.model.live.Room";
    private static final String TAG = "tiktokpatchxposed.liveRecord";
    /** A stream read longer ago than this belongs to a LIVE that is no longer on screen. */
    private static final long FRESH_MS = 15 * 60_000L;
    private static final long MAX_BYTES = 4L << 30;
    private static final long MAX_MS = 6 * 3600_000L;
    /** Recording stops when less than this is free; the MP4 copy needs as much room again. */
    private static final long MIN_FREE = 1L << 30;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String POSITION = "tpx_live_record";
    private static final String ALPHA = "tpx_live_record_alpha";
    private static final long FADE_MS = 3000;
    private static final float FADED = 0.35f;
    private static volatile long touchedAt;

    private static volatile String stream;
    private static volatile long streamAt;
    private static volatile Recorder recorder;
    private static volatile WeakReference<TextView> button = new WeakReference<>(null);
    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            try {
                refresh();
            } catch (Throwable error) {
                Safe.log("LIVE record timer", error);
            }
            if (recorder != null) MAIN.postDelayed(this, 1000);
        }
    };

    private LiveRecord() { }

    static void install(ClassLoader loader) {
        int hooks = 0;
        for (String name : PULL_DATA) {
            Class<?> type = Hooks.findClass(name, loader);
            if (type == null) continue;
            hooks += Hooks.hookAll(type, "getStreamData", new Hooks.Hook() {
                @Override protected void after(Hooks.Call param) {
                    if (Prefs.on(Prefs.LIVE_RECORD)) seen(param.getResult());
                }
            });
            hooks += Hooks.hookAll(type, "setStreamData", new Hooks.Hook() {
                @Override protected void before(Hooks.Call param) {
                    if (Prefs.on(Prefs.LIVE_RECORD) && param.args.length == 1) seen(param.args[0]);
                }
            });
        }
        if (hooks == 0) RuntimeLog.log("LIVE record: stream model not found, reading rooms only");
        Hooks.findAndHook(Activity.class, "onResume", new Hooks.Hook() {
            @Override protected void after(Hooks.Call param) {
                Activity activity = (Activity) param.thisObject;
                if (ROOM.equals(activity.getClass().getName()) && Build.VERSION.SDK_INT >= 29) attach(activity);
            }
        });
        Hooks.findAndHook(Activity.class, "onDestroy", new Hooks.Hook() {
            @Override protected void before(Hooks.Call param) {
                Recorder active = recorder;
                if (active != null && active.owner.get() == param.thisObject) active.stop();
            }
        });
        RuntimeLog.log("LIVE record: hooks=" + hooks);
    }

    /** Keeps the newest stream; the player reads it often, so an unchanged one costs a compare. */
    private static void seen(Object value) {
        if (!(value instanceof String)) return;
        String data = (String) value;
        streamAt = SystemClock.elapsedRealtime();
        if (data == stream || data.isEmpty() || !data.contains("flv")) return;
        if (!data.equals(stream)) RuntimeLog.log("LIVE record: stream seen (" + data.length() + " chars)");
        stream = data;
    }

    private static String freshStream() {
        String data = stream;
        return data != null && SystemClock.elapsedRealtime() - streamAt < FRESH_MS ? data : null;
    }

    // ---- The REC button ------------------------------------------------------------------

    private static void attach(Activity activity) {
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) decor;
        View existing = root.findViewWithTag(TAG);
        if (!Prefs.on(Prefs.LIVE_RECORD)) {
            if (existing != null) root.removeView(existing);
            return;
        }
        TextView view = existing instanceof TextView ? (TextView) existing : create(activity, root);
        button = new WeakReference<>(view);
        refresh();
    }

    private static TextView create(Activity activity, ViewGroup root) {
        TextView view = new TextView(activity);
        view.setTag(TAG);
        view.setTextColor(0xFFFFFFFF);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setGravity(Gravity.CENTER);
        int padH = dp(activity, 10), padV = dp(activity, 6);
        view.setPadding(padH, padV, padH, padV);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x99000000);
        pill.setCornerRadius(dp(activity, 16));
        view.setBackground(pill);
        view.setElevation(dp(activity, 4));
        view.setOnClickListener(Safe.click("LIVE record button", clicked -> tapped(activity)));
        view.setOnTouchListener(new Drag(activity));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        params.setMarginStart(dp(activity, 8));
        root.addView(view, params);
        SharedPreferences saved = positions(activity);
        view.setTranslationX(saved.getFloat("x", 0));
        view.setTranslationY(saved.getFloat("y", 0));
        view.addOnLayoutChangeListener((moved, l, t, r, b, oldL, oldT, oldR, oldB) ->
                Safe.run("LIVE record layout", () -> place(moved, moved.getTranslationX(), moved.getTranslationY())).run());
        return view;
    }

    private static void refresh() {
        TextView view = button.get();
        if (view == null) return;
        Recorder active = recorder;
        long stoppedAt = active == null ? 0 : active.stoppedAt;
        long end = stoppedAt > 0 ? stoppedAt : SystemClock.elapsedRealtime();
        view.setText(active == null ? "\u25CF REC"
                : "\u25A0 " + LiveStreams.elapsed(end - active.startedAt) + (stoppedAt > 0 ? " \u2026" : ""));
        view.setTextColor(active == null ? 0xFFFFFFFF : 0xFFFF4D4D);
        boolean faded = active != null && SystemClock.elapsedRealtime() - Math.max(active.startedAt, touchedAt) > FADE_MS;
        fade(view, faded ? FADED : 1f);
    }

    private static void fade(View view, float alpha) {
        Object target = Hooks.getExtra(view, ALPHA);
        if (target instanceof Float ? (Float) target == alpha : view.getAlpha() == alpha) return;
        Hooks.setExtra(view, ALPHA, alpha);
        view.animate().alpha(alpha).setDuration(alpha < 1f ? 600 : 150);
    }

    private static void touched(View view) {
        touchedAt = SystemClock.elapsedRealtime();
        view.animate().cancel();
        view.setAlpha(1f);
        Hooks.setExtra(view, ALPHA, 1f);
    }

    private static void place(View view, float x, float y) {
        if (!(view.getParent() instanceof View)) return;
        View parent = (View) view.getParent();
        view.setTranslationX(clamp(x, -view.getLeft(), parent.getWidth() - view.getRight()));
        view.setTranslationY(clamp(y, -view.getTop(), parent.getHeight() - view.getBottom()));
    }

    private static float clamp(float value, float min, float max) {
        return max < min ? min : Math.max(min, Math.min(max, value));
    }

    private static SharedPreferences positions(Context context) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        return app.getSharedPreferences(POSITION, Context.MODE_PRIVATE);
    }

    private static final class Drag implements View.OnTouchListener {
        private final int slop;
        private float downX, downY, fromX, fromY;
        private boolean moving;

        Drag(Context context) {
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override public boolean onTouch(View view, MotionEvent event) {
            try {
                return touch(view, event);
            } catch (Throwable error) {
                Safe.log("LIVE record drag", error);
                return false;
            }
        }

        private boolean touch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    fromX = view.getTranslationX();
                    fromY = view.getTranslationY();
                    moving = false;
                    touched(view);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!moving && Math.hypot(dx, dy) > slop) moving = true;
                    if (moving) place(view, fromX + dx, fromY + dy);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (moving) {
                        positions(view.getContext()).edit()
                                .putFloat("x", view.getTranslationX()).putFloat("y", view.getTranslationY()).apply();
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        view.performClick();
                    }
                    touched(view);
                    return true;
                default:
                    return true;
            }
        }
    }

    private static void tapped(Activity activity) {
        Recorder active = recorder;
        if (active != null) {
            active.stop();
            return;
        }
        List<LiveStreams.Option> fromRoom = choices(roomStream(activity));
        List<LiveStreams.Option> choices = fromRoom.isEmpty() ? choices(freshStream()) : fromRoom;
        if (choices.isEmpty()) {
            toast(activity, I18n.get(I18n.S.LIVE_REC_NONE));
            return;
        }
        LiveStreams.Option best = LiveStreams.best(choices);
        List<DownloadSheet.Row> rows = new ArrayList<>();
        for (LiveStreams.Option option : choices) {
            rows.add(option.audioOnly()
                    ? new DownloadSheet.Row(I18n.get(I18n.S.DL_AUDIO), LiveStreams.detail(option),
                            Glyph.Kind.NOTE, DownloadSheet.AUDIO_TILE, false)
                    : new DownloadSheet.Row(LiveStreams.label(option), LiveStreams.detail(option),
                            Glyph.Kind.PLAY, DownloadSheet.VIDEO_TILE, option == best && choices.size() > 1));
        }
        DownloadSheet.Row copy = new DownloadSheet.Row(I18n.get(I18n.S.LIVE_REC_COPY), I18n.get(I18n.S.LIVE_REC_COPY_DESC),
                Glyph.Kind.GLOBE, DownloadSheet.APP_TILE, false);
        copy.arrow = false;
        rows.add(copy);
        String link = (best != null ? best : choices.get(0)).flv;
        String[] host = host(activity);
        DownloadSheet.show(activity, I18n.get(I18n.S.LIVE_REC_TITLE), I18n.get(I18n.S.LIVE_REC_PICK), rows, which -> {
            if (which >= 0 && which < choices.size()) start(activity, choices.get(which), host);
            else if (which == choices.size()) copyLink(activity, link);
        }, null);
    }

    private static List<LiveStreams.Option> choices(String data) {
        return data == null ? new ArrayList<>() : LiveStreams.choices(options(data));
    }

    static String roomStream(Activity activity) {
        try {
            Object room = currentRoom(activity);
            if (room == null) return null;
            Object data = Reflect.call(room, "getMultiStreamData");
            if (!(data instanceof String)) {
                Object pull = Reflect.get(Reflect.get(Reflect.get(room, "streamUrl"), "liveCoreSDKData"), "pullData");
                data = Reflect.get(pull, "streamData");
            }
            return data instanceof String && ((String) data).contains("flv") ? (String) data : null;
        } catch (Throwable error) {
            RuntimeLog.log("LIVE record: room stream unreadable: " + error);
            return null;
        }
    }

    private static Object currentRoom(Activity activity) {
        Object fragment = activity;
        for (int depth = 0; depth < 4; depth++) {
            Object next = Reflect.call(fragment, "getCurrentFragment");
            if (next == null || next == fragment) break;
            fragment = next;
        }
        return fragment == activity ? null : room(fragment);
    }

    private static String[] host(Activity activity) {
        try {
            Object owner = Reflect.call(currentRoom(activity), "getOwner");
            Object handle = Reflect.call(owner, "getDisplayId");
            if (handle == null || String.valueOf(handle).isEmpty()) handle = Reflect.call(owner, "getUniqueId");
            Object name = Reflect.call(owner, "getNickName");
            if (name == null) name = Reflect.call(owner, "getNickname");
            return new String[]{DownloadsPlus.text(handle), DownloadsPlus.text(name)};
        } catch (Throwable error) {
            return new String[]{null, null};
        }
    }

    private static Object room(Object fragment) {
        for (Class<?> type = fragment.getClass(); type != null && !type.getName().startsWith("android"); type = type.getSuperclass()) {
            for (Field field : Reflect.fields(type)) {
                if (Modifier.isStatic(field.getModifiers()) || !ROOM_MODEL.equals(field.getType().getName())) continue;
                try {
                    field.setAccessible(true);
                    Object room = field.get(fragment);
                    if (room != null) return room;
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static void copyLink(Activity activity, String url) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("LIVE", url));
        toast(activity, I18n.get(I18n.S.LIVE_REC_COPIED));
    }

    private static synchronized void start(Activity activity, LiveStreams.Option option, String[] host) {
        if (recorder != null) return;
        Recorder next = new Recorder(activity, option, host);
        recorder = next;
        Thread thread = new Thread(next, "tpx-live-record");
        thread.setDaemon(true);
        thread.start();
        toast(activity, I18n.get(I18n.S.LIVE_REC_STARTED));
        MAIN.removeCallbacks(TICK);
        MAIN.post(TICK);
    }

    /** Every quality of a stream_data JSON, or its first FLV link if the JSON is not as expected. */
    static List<LiveStreams.Option> options(String data) {
        List<LiveStreams.Option> options = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(data);
            JSONObject qualities = root.optJSONObject("data");
            if (qualities == null) qualities = root;
            for (Iterator<String> keys = qualities.keys(); keys.hasNext();) {
                String key = keys.next();
                JSONObject quality = qualities.optJSONObject(key);
                if (quality == null) continue;
                JSONObject main = quality.optJSONObject("main");
                if (main == null) main = quality;
                String flv = main.optString("flv", null);
                JSONObject params = params(main.opt("sdk_params"));
                int[] size = LiveStreams.resolution(params == null ? null : params.optString("resolution", null));
                String codec = params == null ? null : params.optString("VCodec", null);
                options.add(new LiveStreams.Option(key, flv, size[0], size[1], codec));
            }
        } catch (Throwable error) {
            RuntimeLog.log("LIVE record: stream_data not JSON: " + error.getClass().getSimpleName());
        }
        if (LiveStreams.best(options) == null) {
            String flv = LiveStreams.firstFlv(data);
            if (flv != null) options.add(new LiveStreams.Option("", flv, 0, 0, null));
        }
        return options;
    }

    private static JSONObject params(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (!(value instanceof String) || ((String) value).isEmpty()) return null;
        try {
            return new JSONObject((String) value);
        } catch (Throwable error) {
            return null;
        }
    }

    private static void finished(Recorder done, Context app, boolean saved) {
        synchronized (LiveRecord.class) {
            if (recorder == done) recorder = null;
        }
        MAIN.post(Safe.run("LIVE record finished", () -> {
            refresh();
            toast(app, saved ? String.format(Locale.ROOT, I18n.get(I18n.S.LIVE_REC_SAVED), done.path)
                    : I18n.get(I18n.S.LIVE_REC_FAILED));
        }));
    }

    private static void toast(Context context, String message) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        MAIN.post(Safe.run("LIVE record toast", () -> Toast.makeText(app, message, Toast.LENGTH_LONG).show()));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    // ---- Recording -----------------------------------------------------------------------

    /** Copies the FLV stream into a pending MediaStore row, then publishes it as MP4, or as FLV if that fails. */
    private static final class Recorder implements Runnable {
        final WeakReference<Activity> owner;
        final Context app;
        final String url;
        final int width;
        final int height;
        final String[] host;
        volatile String path = "";
        final long startedAt = SystemClock.elapsedRealtime();
        volatile long stoppedAt;
        private volatile boolean stopping;
        private volatile HttpURLConnection connection;

        Recorder(Activity activity, LiveStreams.Option option, String[] host) {
            this.owner = new WeakReference<>(activity);
            this.app = activity.getApplicationContext();
            this.url = option.flv;
            this.width = option.width;
            this.height = option.height;
            this.host = host;
        }

        void stop() {
            if (stoppedAt == 0) stoppedAt = SystemClock.elapsedRealtime();
            stopping = true;
            HttpURLConnection open = connection;
            // A blocked read ends at once when the connection goes away.
            if (open != null) new Thread(() -> {
                try { open.disconnect(); } catch (Throwable ignored) { }
            }).start();
        }

        @Override public void run() {
            ContentResolver resolver = app.getContentResolver();
            Uri row = null;
            long total = 0;
            boolean saved = false;
            long now = System.currentTimeMillis();
            String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date(now));
            DownloadName.Info info = new DownloadName.Info(host[0], host[1], "LIVE_" + time, now);
            try {
                MediaDownload.Target target = MediaDownload.target(DownloadFormat.Kind.VIDEO, info, now);
                path = target.path;
                connection = open(url);
                if (stopping) throw new IOException("stopped before start");
                row = MediaDownload.pending(resolver, target, name(info, time, now, "flv"), "video/x-flv");
                if (row == null) throw new IOException("MediaStore insert failed");
                try (InputStream input = connection.getInputStream(); OutputStream output = resolver.openOutputStream(row, "w")) {
                    if (output == null) throw new IOException("MediaStore stream unavailable");
                    byte[] buffer = new byte[64 * 1024];
                    try {
                        for (int read; !stopping && (read = input.read(buffer)) >= 0;) {
                            if (total == 0 && !flv(buffer, read)) throw new IOException("not an FLV stream");
                            output.write(buffer, 0, read);
                            total += read;
                            if (total > MAX_BYTES || SystemClock.elapsedRealtime() - startedAt > MAX_MS) break;
                            // Checked every ~16 MB: a full phone would stop TikTok and the gallery from working.
                            if ((total & 0xFFFFFF) < read && lowOnSpace()) {
                                RuntimeLog.log("LIVE record: stopped, little free space");
                                break;
                            }
                        }
                    } catch (IOException error) {
                        // Stopping disconnects the stream under the read; what was copied is kept.
                        if (!stopping || total == 0) throw error;
                    }
                }
                if (stoppedAt == 0) stoppedAt = SystemClock.elapsedRealtime();
                disconnect();
                if (total < 64 * 1024) throw new IOException("stream ended at once");
                Uri mp4 = mp4(resolver, row, target, name(info, time, now, "mp4"));
                if (mp4 != null) {
                    try { resolver.delete(row, null, null); } catch (RuntimeException ignored) { }
                    row = mp4;
                }
                ContentValues publish = new ContentValues();
                publish.put(MediaStore.MediaColumns.IS_PENDING, 0);
                saved = resolver.update(row, publish, null, null) == 1;
            } catch (Throwable error) {
                RuntimeLog.log("LIVE record failed after " + total + " bytes: " + error);
            } finally {
                if (row != null && !saved) {
                    try { resolver.delete(row, null, null); } catch (RuntimeException ignored) { }
                }
                disconnect();
                RuntimeLog.log("LIVE record: " + (saved ? "saved " : "dropped ") + total + " bytes");
                finished(this, app, saved);
            }
        }

        private String name(DownloadName.Info info, String time, long now, String extension) {
            String template = Prefs.text(Prefs.DOWNLOAD_NAME);
            if (template.trim().isEmpty()) return "TikTok_LIVE_" + time + "." + extension;
            String quality = width > 0 || height > 0 ? DownloadFormat.resolution(width, height) : "";
            return DownloadName.file(template, info, DownloadFormat.Kind.VIDEO, quality, 1, 1, time, extension, now,
                    java.util.TimeZone.getDefault());
        }

        private Uri mp4(ContentResolver resolver, Uri flv, MediaDownload.Target target, String name) {
            Uri row = null;
            boolean done = false;
            long started = SystemClock.elapsedRealtime();
            try {
                row = MediaDownload.pending(resolver, target, name, "video/mp4");
                if (row == null) return null;
                try (ParcelFileDescriptor out = resolver.openFileDescriptor(row, "rw")) {
                    if (out == null) return null;
                    FlvRemux.remux(() -> resolver.openInputStream(flv), out.getFileDescriptor(), width, height);
                }
                done = true;
                RuntimeLog.log("LIVE record: MP4 written in " + (SystemClock.elapsedRealtime() - started) + " ms");
                return row;
            } catch (Throwable error) {
                RuntimeLog.log("LIVE record: kept FLV, MP4 failed: " + error);
                return null;
            } finally {
                if (!done && row != null) {
                    try { resolver.delete(row, null, null); } catch (RuntimeException ignored) { }
                }
            }
        }

        private void disconnect() {
            HttpURLConnection open = connection;
            if (open != null) {
                try { open.disconnect(); } catch (Throwable ignored) { }
            }
        }

        private static boolean lowOnSpace() {
            try {
                return Environment.getExternalStorageDirectory().getUsableSpace() < MIN_FREE;
            } catch (Throwable error) {
                return false;
            }
        }

        private static boolean flv(byte[] head, int length) {
            return length >= 3 && head[0] == 'F' && head[1] == 'L' && head[2] == 'V';
        }

        private static HttpURLConnection open(String address) throws IOException {
            String current = address;
            for (int hop = 0; hop <= 5; hop++) {
                if (!DownloadFormat.webUrl(current)) throw new IOException("Unsupported URL");
                HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
                boolean accepted = false;
                try {
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(12_000);
                    connection.setReadTimeout(30_000);
                    connection.setRequestProperty("Accept-Encoding", "identity");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    int status = connection.getResponseCode();
                    if (status == HttpURLConnection.HTTP_OK) {
                        accepted = true;
                        return connection;
                    }
                    if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308)
                        throw new IOException("HTTP " + status);
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirect without location");
                    current = new URL(new URL(current), location).toExternalForm();
                } finally {
                    if (!accepted) connection.disconnect();
                }
            }
            throw new IOException("Too many redirects");
        }
    }
}
