package com.golda.patchertiktok;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Settings stored inside TikTok's own data directory. Hooks read cached values,
 * so a toggle applies immediately where the hooked code is consulted again.
 */
final class Prefs {
    static final String FILE = "tiktokpatchxposed";

    static final String ADS = "block_ads";
    static final String LIVE = "hide_live";
    static final String ACQUAINTANCES = "hide_acquaintances";
    static final String SEEKBAR = "seekbar";
    static final String NO_WATERMARK = "no_watermark";
    static final String DOWNLOAD_ANY = "download_any";
    static final String SCREENSHOTS = "allow_screenshots";
    static final String QUALITY_PICKER = "download_quality_picker";
    static final String AUTO_SCROLL = "auto_scroll_everywhere";
    /** Auto-scroll in a person's videos (profile, liked list), switched on its own in the long-press menu. */
    static final String AUTO_SCROLL_PROFILES = "auto_scroll_profiles";
    static final String COMMENT_MEDIA = "comment_media_save";
    /** Favourite sticker sent by auto streaks instead of the flame; empty for the flame. */
    static final String STREAK_STICKER = "auto_streak_sticker_id";
    static final String STREAK_STICKER_TITLE = "auto_streak_sticker_title";
    /** Preview picture of the chosen sticker, for the settings preview. */
    static final String STREAK_STICKER_URL = "auto_streak_sticker_url";
    /** 0: when the streak starts to fade; otherwise 1 + minute of the day to send at. */
    static final String STREAK_TIME = "auto_streak_time";
    /** Comma-separated conversation ids that auto streaks skip. */
    static final String STREAK_EXCLUDED = "auto_streak_excluded";
    /** Off: every streak chat; on: all but {@link #STREAK_EXCLUDED}. */
    static final String STREAK_ONLY_SELECTED = "auto_streak_only_selected";
    static final String SAVE_ALL_PHOTOS = "save_all_photos";
    static final String COMMENT_REPOSTS = "comment_reposts";
    static final String NEW_PROFILE = "new_profile";
    static final String EXTRA_FEATURES = "extra_features";
    static final String CLEAN_LINKS = "clean_links";
    static final String REGION = "region";
    /** With a region selected: keep recommendations in the user's language instead of the country's. */
    static final String REGION_LANGUAGE = "region_keep_language";
    /** Recommendations from another country than the SIM's region. */
    static final String FEED_REGION_OWN = "feed_region_own";
    static final String FEED_REGION = "feed_region";
    static final String AUTO_STREAK = "auto_streak";
    static final String GHOST_STORIES = "ghost_stories";
    /** Profile highlights; until set, it follows {@link #GHOST_STORIES}, which covered them before (#2). */
    static final String GHOST_HIGHLIGHTS = "ghost_highlights";
    static final String GHOST_PROFILE = "ghost_profile_views";
    static final String GHOST_POSTS = "ghost_post_views";
    /** Chats: no "typing…" and no "online" heartbeat. */
    static final String GHOST_TYPING = "ghost_typing";
    static final String GHOST_ONLINE = "ghost_online";
    /** Chats: the read mark is not sent; "Mark as read" sends it on demand. */
    static final String GHOST_READ = "ghost_read";
    /** With "Read" hidden, replying in a chat still sends "Read" for that chat. */
    static final String READ_ON_REPLY = "ghost_read_on_reply";
    /** The sent time under each chat message. */
    static final String MESSAGE_TIME = "message_time";
    /** Hide recommended videos published longer ago than {@link #oldVideoDays()}. */
    static final String OLD_VIDEOS = "hide_old_videos";
    /** Leave videos already watched out of For You when TikTok sends them again. */
    static final String HIDE_SEEN = "hide_seen_videos";
    static final String KEEP_UNSENT = "keep_unsent_messages";
    static final String NO_DOUBLE_TAP_LIKE = "no_double_tap_like";
    static final String ACCOUNT_DATE = "account_created_date";
    /** A REC button in LIVE rooms. Idea and first version by RSR1337 (PR #16). */
    static final String LIVE_RECORD = "live_record";
    static final String OLD_VIDEO_DAYS = "old_video_days";
    static final int DEFAULT_OLD_VIDEO_DAYS = 90;
    /** Paid content, shop/product videos and search ads. */
    static final String PROMO = "hide_promo";
    static final String AI_CONTENT = "hide_ai";
    /** The user's own filter, see {@link FilterRules}. */
    static final String CUSTOM_FILTER = "custom_filter";
    static final String FILTER_WORDS = "filter_words";
    static final String FILTER_AUTHORS = "filter_authors";
    /** Comma-separated ISO codes of countries whose authors the own filter hides. */
    static final String FILTER_COUNTRIES = "filter_countries";
    static final String FILTER_MIN_LIKES = "filter_min_likes";
    static final String FILTER_MIN_VIEWS = "filter_min_views";
    static final String REMEMBER_CLEAN = "remember_clean_screen";
    static final String RESUME_POSITION = "resume_position";
    static final String REMEMBER_SPEED = "remember_speed";
    static final String COPY_COMMENT_TEXT = "copy_comment_text";
    static final String EXTERNAL_BROWSER = "external_browser";
    static final String HIDE_INBOX_STORIES = "hide_inbox_stories";
    static final String ACCENT = "accent_enabled";
    static final String ACCENT_COLOR = "accent_color";
    static final long DEFAULT_ACCENT = 0x8B5CF6;
    /** Opacity of the layer over videos, 10..100 percent. */
    static final String OVERLAY_OPACITY = "overlay_opacity";
    static final String BURN_IN = "burn_in_protection";
    static final String AUTHOR_REGION = "author_region";
    static final String COMMENT_HANDLE = "comment_handle";
    static final String KEEP_BRIGHTNESS = "keep_brightness";
    static final String EXACT_TIME = "exact_time";
    static final String EXACT_TIME_SECONDS = "exact_time_seconds";
    static final String EXACT_COUNTS = "exact_counts";
    static final String HIDE_ROOT = "hide_root";
    static final String HIDE_PASSKEY = "hide_passkey";
    static final String NO_MOTION_SENSORS = "no_motion_sensors";
    static final String NO_HDR = "no_hdr";
    static final String PREFER_H264 = "prefer_h264";
    static final String PHOTO_BEST = "photo_best";
    static final String PIP_FEED = "pip_feed";
    static final String NO_TRACKERS = "no_trackers";
    static final String UPLOAD_ORIGINAL = "upload_original";
    static final String NO_TIKTOK_ANALYTICS = "no_tiktok_analytics";
    /** Playback quality per network: 0 TikTok's pick, 1 best, 2 smallest. */
    static final String QUALITY_WIFI = "quality_wifi";
    static final String QUALITY_MOBILE = "quality_mobile";
    static final String NO_LOGIN_WALL = "no_login_wall";
    static final String AUTO_TRANSLATE = "auto_translate_comments";
    static final String HIDE_BUTTONS = "hide_side_buttons";
    static final String HIDE_CAPTION = "hide_caption";
    static final String HIDE_MUSIC = "hide_music_disc";
    static final String HIDE_TAKO = "hide_tako";
    /** Off: burn-in protection only shifts the buttons. */
    static final String BURN_IN_DIM = "burn_in_dim";
    static final String AMOLED = "amoled_theme";
    static final String BLOCK_LOCATION = "block_location";
    static final String HIDE_VPN = "hide_vpn";
    static final String SPOOF_ANDROID_ID = "spoof_android_id";
    /** Play videos TikTok muted over a copyright claim, while their audio is still delivered. */
    static final String UNMUTE = "unmute_copyright";
    static final String SELF_REPOST = "self_repost";
    /** Replace TikTok's interface font: {@link #FONT_SOURCE} is "system" or "file" (a copy in TikTok's files). */
    static final String FONT = "custom_font";
    static final String FONT_SOURCE = "custom_font_source";
    /** Name of the imported font file, shown in settings. */
    static final String FONT_FILE = "custom_font_file";
    /** Screen refresh rate in Hz while TikTok is open; 0 lets TikTok decide. */
    static final String REFRESH_RATE = "refresh_rate";
    /** Holding a story or highlight opens the download sheet. */
    static final String STORY_DOWNLOAD = "story_download";
    /** Holding the enlarged profile photo saves it in full size. */
    static final String AVATAR_DOWNLOAD = "avatar_download";
    /** Holding a sticker in a chat adds "Download sticker" to TikTok's menu. */
    static final String CHAT_STICKER = "chat_sticker_download";
    /** Uploads as with TikTok's "Upload HD" switched on. */
    static final String HD_UPLOAD = "hd_upload";
    /** With HD upload: the encoder's bitrate floor in Mbit/s; 0 keeps TikTok's. */
    static final String HD_BITRATE = "hd_upload_mbps";
    /** TikTok features behind its own experiment flags, read once at start. */
    static final String READ_ALL = "inbox_read_all";
    static final String COMMENT_PHOTOS = "comment_multi_photos";
    static final String NO_HASHTAG_LIMIT = "no_hashtag_limit";
    static final String SPEED_3X = "speed_3x";
    static final String CAMERA_EXTRAS = "camera_extras";
    /** The random Android ID shown instead of the real one; made once. */
    static final String ANDROID_ID = "android_id_value";
    /** Languages left untranslated, e.g. "en, uk". */
    static final String TRANSLATE_EXCLUDE = "translate_exclude";
    /** Comma-separated speeds for TikTok's speed menu; empty keeps TikTok's own list. */
    static final String SPEEDS = "custom_speeds";
    /** Germany keeps the behaviour of earlier releases; users can pick another country or turn it off. */
    static final String DEFAULT_REGION = "de";

    /** Keys whose hooks only take effect when TikTok starts. */
    static final String[] RESTART_KEYS = {COMMENT_REPOSTS, NEW_PROFILE, EXTRA_FEATURES, REGION, REGION_LANGUAGE, FEED_REGION_OWN, ADS, HIDE_INBOX_STORIES, ACCENT, AMOLED, SPOOF_ANDROID_ID, SELF_REPOST, FONT,
            COMMENT_PHOTOS, NO_HASHTAG_LIMIT, SPEED_3X, CAMERA_EXTRAS, PIP_FEED, HIDE_PASSKEY, NO_MOTION_SENSORS, NO_TRACKERS, UPLOAD_ORIGINAL};

    private static final Map<String, Boolean> DEFAULTS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FLAGS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile SharedPreferences preferences;
    private static volatile String region = DEFAULT_REGION;
    private static volatile int oldVideoDays = DEFAULT_OLD_VIDEO_DAYS;
    private static final Map<String, String> TEXTS = new ConcurrentHashMap<>();
    private static final Map<String, Long> NUMBERS = new ConcurrentHashMap<>();
    private static volatile FilterRules rules = FilterRules.NONE;
    private static volatile String startSnapshot;

    static {
        DEFAULTS.put(ADS, true);
        DEFAULTS.put(LIVE, true);
        DEFAULTS.put(ACQUAINTANCES, true);
        DEFAULTS.put(SEEKBAR, true);
        DEFAULTS.put(NO_WATERMARK, true);
        DEFAULTS.put(DOWNLOAD_ANY, true);
        DEFAULTS.put(SCREENSHOTS, true);
        DEFAULTS.put(QUALITY_PICKER, false);
        DEFAULTS.put(AUTO_SCROLL, false);
        DEFAULTS.put(AUTO_SCROLL_PROFILES, false);
        DEFAULTS.put(COMMENT_MEDIA, true);
        DEFAULTS.put(SAVE_ALL_PHOTOS, false);
        DEFAULTS.put(COMMENT_REPOSTS, true);
        DEFAULTS.put(NEW_PROFILE, true);
        DEFAULTS.put(EXTRA_FEATURES, true);
        DEFAULTS.put(CLEAN_LINKS, true);
        DEFAULTS.put(REGION_LANGUAGE, true);
        DEFAULTS.put(FEED_REGION_OWN, false);
        DEFAULTS.put(GHOST_STORIES, false);
        DEFAULTS.put(GHOST_HIGHLIGHTS, false);
        DEFAULTS.put(GHOST_PROFILE, false);
        DEFAULTS.put(GHOST_POSTS, false);
        DEFAULTS.put(GHOST_TYPING, false);
        DEFAULTS.put(GHOST_ONLINE, false);
        DEFAULTS.put(GHOST_READ, false);
        DEFAULTS.put(READ_ON_REPLY, false);
        DEFAULTS.put(MESSAGE_TIME, false);
        DEFAULTS.put(OLD_VIDEOS, false);
        DEFAULTS.put(HIDE_SEEN, false);
        DEFAULTS.put(KEEP_UNSENT, false);
        DEFAULTS.put(NO_DOUBLE_TAP_LIKE, false);
        DEFAULTS.put(ACCOUNT_DATE, false);
        DEFAULTS.put(LIVE_RECORD, false);
        DEFAULTS.put(PROMO, true);
        DEFAULTS.put(AI_CONTENT, false);
        DEFAULTS.put(CUSTOM_FILTER, false);
        DEFAULTS.put(REMEMBER_CLEAN, false);
        DEFAULTS.put(RESUME_POSITION, false);
        DEFAULTS.put(REMEMBER_SPEED, false);
        DEFAULTS.put(COPY_COMMENT_TEXT, true);
        DEFAULTS.put(EXTERNAL_BROWSER, true);
        DEFAULTS.put(HIDE_INBOX_STORIES, false);
        DEFAULTS.put(ACCENT, false);
        DEFAULTS.put(BURN_IN, false);
        DEFAULTS.put(AUTHOR_REGION, false);
        DEFAULTS.put(COMMENT_HANDLE, false);
        DEFAULTS.put(KEEP_BRIGHTNESS, false);
        DEFAULTS.put(EXACT_TIME, false);
        DEFAULTS.put(EXACT_TIME_SECONDS, false);
        DEFAULTS.put(EXACT_COUNTS, false);
        DEFAULTS.put(HIDE_ROOT, false);
        DEFAULTS.put(HIDE_PASSKEY, false);
        DEFAULTS.put(NO_MOTION_SENSORS, false);
        DEFAULTS.put(NO_HDR, false);
        DEFAULTS.put(PREFER_H264, false);
        DEFAULTS.put(PHOTO_BEST, false);
        DEFAULTS.put(PIP_FEED, false);
        DEFAULTS.put(NO_TRACKERS, false);
        DEFAULTS.put(UPLOAD_ORIGINAL, false);
        DEFAULTS.put(NO_TIKTOK_ANALYTICS, false);
        DEFAULTS.put(NO_LOGIN_WALL, true);
        DEFAULTS.put(AUTO_TRANSLATE, false);
        DEFAULTS.put(HIDE_BUTTONS, false);
        DEFAULTS.put(HIDE_CAPTION, false);
        DEFAULTS.put(HIDE_MUSIC, false);
        DEFAULTS.put(HIDE_TAKO, false);
        DEFAULTS.put(BURN_IN_DIM, true);
        DEFAULTS.put(AMOLED, false);
        DEFAULTS.put(BLOCK_LOCATION, false);
        DEFAULTS.put(HIDE_VPN, false);
        DEFAULTS.put(SPOOF_ANDROID_ID, false);
        DEFAULTS.put(UNMUTE, false);
        DEFAULTS.put(SELF_REPOST, false);
        DEFAULTS.put(FONT, false);
        DEFAULTS.put(STORY_DOWNLOAD, false);
        DEFAULTS.put(AVATAR_DOWNLOAD, false);
        DEFAULTS.put(CHAT_STICKER, false);
        DEFAULTS.put(HD_UPLOAD, false);
        DEFAULTS.put(READ_ALL, false);
        DEFAULTS.put(COMMENT_PHOTOS, false);
        DEFAULTS.put(NO_HASHTAG_LIMIT, false);
        DEFAULTS.put(SPEED_3X, false);
        DEFAULTS.put(CAMERA_EXTRAS, false);
        // Automated messages are opt-in only.
        DEFAULTS.put(AUTO_STREAK, false);
        DEFAULTS.put(STREAK_ONLY_SELECTED, false);
    }

    private Prefs() { }

    static synchronized void load(Context context) {
        if (preferences != null || context == null) return;
        SharedPreferences loaded = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        read(loaded);
        preferences = loaded;
        startSnapshot = snapshot();
    }

    private static void read(SharedPreferences loaded) {
        for (String key : DEFAULTS.keySet()) FLAGS.put(key, loaded.getBoolean(key, DEFAULTS.get(key)));
        // Saved right away: otherwise a later change of the story switch would move this one too.
        if (!loaded.contains(GHOST_HIGHLIGHTS)) {
            FLAGS.put(GHOST_HIGHLIGHTS, FLAGS.get(GHOST_STORIES));
            loaded.edit().putBoolean(GHOST_HIGHLIGHTS, FLAGS.get(GHOST_STORIES)).apply();
        }
        region = Regions.normalize(loaded.getString(REGION, DEFAULT_REGION));
        oldVideoDays = Math.max(1, loaded.getInt(OLD_VIDEO_DAYS, DEFAULT_OLD_VIDEO_DAYS));
        for (String key : new String[]{FILTER_WORDS, FILTER_AUTHORS, FILTER_COUNTRIES, SPEEDS, TRANSLATE_EXCLUDE, STREAK_STICKER,
                STREAK_STICKER_TITLE, STREAK_STICKER_URL, STREAK_EXCLUDED, ANDROID_ID, FONT_SOURCE, FONT_FILE, FEED_REGION}) TEXTS.put(key, loaded.getString(key, ""));
        for (String key : new String[]{FILTER_MIN_LIKES, FILTER_MIN_VIEWS}) NUMBERS.put(key, loaded.getLong(key, 0));
        NUMBERS.put(ACCENT_COLOR, loaded.getLong(ACCENT_COLOR, DEFAULT_ACCENT));
        NUMBERS.put(OVERLAY_OPACITY, loaded.getLong(OVERLAY_OPACITY, 100));
        NUMBERS.put(REFRESH_RATE, loaded.getLong(REFRESH_RATE, 0));
        NUMBERS.put(HD_BITRATE, loaded.getLong(HD_BITRATE, 0));
        NUMBERS.put(STREAK_TIME, loaded.getLong(STREAK_TIME, 0));
        NUMBERS.put(QUALITY_WIFI, loaded.getLong(QUALITY_WIFI, 0));
        NUMBERS.put(QUALITY_MOBILE, loaded.getLong(QUALITY_MOBILE, 0));
        rebuildRules();
    }

    /** Text strings, numbers and the region, besides the switches in {@link #DEFAULTS}. */
    private static final String[] TEXT_KEYS = {REGION, FILTER_WORDS, FILTER_AUTHORS, FILTER_COUNTRIES, SPEEDS, TRANSLATE_EXCLUDE,
            STREAK_STICKER, STREAK_STICKER_TITLE, STREAK_STICKER_URL, STREAK_EXCLUDED, FEED_REGION};
    private static final String[] LONG_KEYS = {FILTER_MIN_LIKES, FILTER_MIN_VIEWS, ACCENT_COLOR, OVERLAY_OPACITY, REFRESH_RATE, HD_BITRATE, STREAK_TIME, QUALITY_WIFI, QUALITY_MOBILE};

    /** All settings as JSON, for a backup the user keeps wherever they like. */
    static String export() {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("module", "TiktokPatchXposed");
            json.put("version", 1);
            for (String key : DEFAULTS.keySet()) json.put(key, on(key));
            json.put(REGION, region);
            for (String key : TEXT_KEYS) if (!REGION.equals(key)) json.put(key, text(key));
            for (String key : LONG_KEYS) json.put(key, number(key));
            json.put(OLD_VIDEO_DAYS, oldVideoDays);
        } catch (org.json.JSONException ignored) {
            // Keys and values are plain; this does not happen.
        }
        try {
            return json.toString(2);
        } catch (org.json.JSONException error) {
            return json.toString();
        }
    }

    /** Applies a backup from {@link #export()}; unknown keys are ignored. False if it is not one. */
    static synchronized boolean importJson(String text) {
        SharedPreferences current = preferences;
        if (current == null || text == null) return false;
        org.json.JSONObject json;
        try {
            json = new org.json.JSONObject(text.trim());
        } catch (org.json.JSONException error) {
            return false;
        }
        if (!"TiktokPatchXposed".equals(json.optString("module"))) return false;
        SharedPreferences.Editor editor = current.edit();
        for (String key : DEFAULTS.keySet()) if (json.has(key)) editor.putBoolean(key, json.optBoolean(key));
        // Backups from before 4.4.3: the story switch covered highlights.
        if (json.has(GHOST_STORIES) && !json.has(GHOST_HIGHLIGHTS)) editor.putBoolean(GHOST_HIGHLIGHTS, json.optBoolean(GHOST_STORIES));
        for (String key : TEXT_KEYS) if (json.has(key)) editor.putString(key, json.optString(key));
        for (String key : LONG_KEYS) if (json.has(key)) editor.putLong(key, json.optLong(key));
        if (json.has(OLD_VIDEO_DAYS)) editor.putInt(OLD_VIDEO_DAYS, Math.max(1, json.optInt(OLD_VIDEO_DAYS)));
        editor.commit();
        read(current);
        notifyChanged();
        return true;
    }

    /** True when a setting that is applied only at startup differs from the running process. */
    static boolean restartPending() {
        return startSnapshot != null && !startSnapshot.equals(snapshot());
    }

    static boolean loaded() { return preferences != null; }

    static boolean on(String key) {
        Boolean value = FLAGS.get(key);
        if (value != null) return value;
        Boolean fallback = DEFAULTS.get(key);
        return fallback != null && fallback;
    }

    static void set(String key, boolean value) {
        FLAGS.put(key, value);
        SharedPreferences current = preferences;
        if (current != null) current.edit().putBoolean(key, value).apply();
        notifyChanged();
    }

    /** Selected spoof region as lower-case ISO code, or empty when disabled. */
    static String region() { return region; }

    /** The country recommendations come from: the own one when picked, else empty (the SIM's region). */
    static String feedRegion() {
        return on(FEED_REGION_OWN) ? Regions.normalize(text(FEED_REGION)) : "";
    }

    static void setRegion(String iso) {
        region = Regions.normalize(iso);
        SharedPreferences current = preferences;
        if (current != null) current.edit().putString(REGION, region).apply();
        notifyChanged();
    }

    static int oldVideoDays() { return oldVideoDays; }

    static void setOldVideoDays(int days) {
        oldVideoDays = Math.max(1, days);
        SharedPreferences current = preferences;
        if (current != null) current.edit().putInt(OLD_VIDEO_DAYS, oldVideoDays).apply();
        notifyChanged();
    }

    /**
     * Every setting back to its default; the random device id stays, so TikTok does not see a new
     * device. Idea by RSR1337 (PR #14).
     */
    static synchronized void reset() {
        SharedPreferences current = preferences;
        if (current == null) return;
        SharedPreferences.Editor editor = current.edit();
        for (String key : DEFAULTS.keySet()) editor.remove(key);
        for (String key : TEXT_KEYS) editor.remove(key);
        for (String key : LONG_KEYS) editor.remove(key);
        editor.remove(OLD_VIDEO_DAYS).remove(FONT_SOURCE).remove(FONT_FILE);
        editor.commit();
        read(current);
        rebuildRules();
        notifyChanged();
    }

    /** Every switch and whether it is on, for bug reports. */
    static Map<String, Boolean> switches() {
        return new java.util.TreeMap<>(FLAGS);
    }

    static String text(String key) {
        String value = TEXTS.get(key);
        return value == null ? "" : value;
    }

    static void setText(String key, String value) {
        TEXTS.put(key, value == null ? "" : value);
        SharedPreferences current = preferences;
        if (current != null) current.edit().putString(key, text(key)).apply();
        rebuildRules();
        notifyChanged();
    }

    static long number(String key) {
        Long value = NUMBERS.get(key);
        return value == null ? 0 : value;
    }

    static void setNumber(String key, long value) {
        NUMBERS.put(key, Math.max(0, value));
        SharedPreferences current = preferences;
        if (current != null) current.edit().putLong(key, number(key)).apply();
        rebuildRules();
        notifyChanged();
    }

    /** The user's filter as parsed rules; empty when nothing is configured. */
    static FilterRules rules() { return rules; }

    private static void rebuildRules() {
        rules = FilterRules.parse(text(FILTER_WORDS), text(FILTER_AUTHORS),
                number(FILTER_MIN_LIKES), number(FILTER_MIN_VIEWS), text(FILTER_COUNTRIES));
    }

    static String snapshot() {
        StringBuilder value = new StringBuilder(region);
        for (String key : RESTART_KEYS) value.append(on(key) ? '1' : '0');
        if (on(ACCENT)) value.append(number(ACCENT_COLOR));
        if (on(FONT)) value.append(text(FONT_SOURCE)).append(text(FONT_FILE));
        if (on(FEED_REGION_OWN)) value.append(text(FEED_REGION));
        return value.toString();
    }

    static void listen(Runnable listener) { LISTENERS.addIfAbsent(listener); }

    static void unlisten(Runnable listener) { LISTENERS.remove(listener); }

    /** Each listener on its own: one that fails neither stops the others nor closes TikTok. */
    private static void notifyChanged() {
        for (Runnable listener : LISTENERS) Safe.run("settings listener", listener).run();
    }
}
