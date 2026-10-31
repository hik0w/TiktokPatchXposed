package com.golda.patchertiktok;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Everything the module does once TikTok's classes are available, for either entry point. */
final class Module {
    private static final String[] TARGETS = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"};
    private static final AtomicBoolean LOADED = new AtomicBoolean();
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private Module() { }

    static boolean targets(String packageName) {
        return TARGETS[0].equals(packageName) || TARGETS[1].equals(packageName);
    }

    static void load(String packageName, String processName, ClassLoader loader) {
        if (!targets(packageName) || !LOADED.compareAndSet(false, true)) return;
        boolean main = packageName.equals(processName);
        if (main) run("safe mode", () -> SafeMode.enter(packageName));

        if (main && !SafeMode.active()) {
            // These hooks read their toggle on every call, so they are safe before settings load.
            run("feed filter", () -> FeedFilter.install(loader));
            run("search ads", () -> ContentFilter.installSearch(loader));
            run("remember clear display", () -> CleanScreen.install(loader));
            run("resume position", () -> ResumePosition.install(loader));
            run("auto-scroll", () -> AutoScroll.install(loader));
            run("double tap like", () -> DoubleTapLike.install(loader));
            run("comment copy", () -> CommentCopy.install(loader));
            run("comment media", () -> CommentMedia.install(loader));
            run("external browser", () -> ExternalLinks.install(loader));
            run("interface", () -> InterfaceTweaks.install(loader));
            run("overlay", () -> Overlay.install(loader));
            run("author region", () -> AuthorRegion.install(loader));
            run("LIVE button", () -> LiveButton.install(loader));
            run("downloads", () -> Downloads.install(loader));
            run("clean links", CleanLinks::install);
            run("rendered ads", () -> RenderedAdSkip.install(loader));
            run("invisible mode", () -> Ghost.install(loader));
            run("refresh rate", RefreshRate::install);
            run("story download", () -> StoryDownload.install(loader));
            run("avatar download", () -> AvatarDownload.install(loader));
            run("message time", () -> MessageTime.install(loader));
            run("chat menu", () -> ChatMenu.install(loader));
            run("google login", () -> googleLoginFix(loader));
            run("trackers", Analytics::installTrackers);
        }
        // Every process: the settings screen and the restart trampoline are swapped in here.
        run("settings host", SettingsEntry::installHost);
        Hooks.findAndHook(Application.class, "attach", Context.class, new Hooks.Hook() {
            @Override protected void before(Hooks.Call param) {
                // TikTok loads and caches its fonts while attaching, so the font goes in first.
                if (!main || !(param.args[0] instanceof Context)) return;
                Context base = (Context) param.args[0];
                run("settings", () -> Prefs.load(base));
                if (Prefs.on(Prefs.FONT) && !SafeMode.active()) {
                    run("code index", () -> Discovery.init(base));
                    run("font", () -> FontSwap.install(base, loader));
                }
            }

            @Override protected void after(Hooks.Call param) {
                if (STARTED.compareAndSet(false, true)) startup((Application) param.thisObject, loader, main);
            }
        });
    }

    /** Runs once the Application context exists, before TikTok's own onCreate. */
    private static void startup(Application app, ClassLoader loader, boolean main) {
        // Every step goes through run(): one that fails must not stop the ones after it.
        run("settings", () -> Prefs.load(app));
        if (main) run("log", () -> RuntimeLog.attach(app));
        if (main) run("crash log", () -> CrashLog.install(app));
        if (main && SafeMode.active()) {
            safeStartup(app, loader);
            return;
        }
        if (main) run("overlay settings", Overlay::settingsLoaded);
        if (main) run("accent color", () -> {
            AccentColor.install();
            AccentColor.installFor(app);
            AccentColor.installIcons(loader);
            AccentColor.applyDarkMode(app);
        });
        run("region", () -> RegionSpoof.install(loader));
        run("privacy", Privacy::install);
        if (RegionSpoof.active() && Prefs.on(Prefs.REGION_LANGUAGE)) {
            run("recommendation", () -> Recommendation.install(app, loader));
        }
        if (!main) return;
        run("language", () -> I18n.use(app.getResources().getConfiguration().getLocales().get(0)));
        run("code index", () -> Discovery.init(app));
        run("friend card", () -> FeedFilter.installCardInsertion(loader));
        run("seen videos", () -> SeenVideos.install(app, loader));
        run("stories seen", () -> StoriesSeen.install(app, loader));
        run("unmute", () -> Unmute.install(loader));
        run("hd upload", () -> HdUpload.install(loader));
        run("read receipts", () -> ReadReceipts.install(loader));
        run("unsent messages", () -> UnsentMessages.install(loader));
        run("LIVE record", () -> LiveRecord.install(loader));
        run("read all", () -> InboxReadAll.install(loader));
        run("comment author", () -> CommentAuthor.install(loader));
        run("native auto-scroll", () -> AutoScroll.installNative(loader));
        run("exact time", () -> ExactTime.install(loader));
        run("exact counts", () -> ExactCounts.install(loader));
        run("keep brightness", () -> Brightness.install(loader));
        run("device checks", () -> DeviceChecks.install(loader));
        run("playback quality", () -> PlaybackQuality.install(loader));
        run("photo quality", () -> PhotoQuality.install(loader));
        run("analytics", () -> Analytics.install(loader));
        run("comment translation", () -> {
            Runnable configure = () -> CommentTranslate.configure(Prefs.on(Prefs.AUTO_TRANSLATE), Prefs.text(Prefs.TRANSLATE_EXCLUDE));
            configure.run();
            Prefs.listen(configure);
            CommentTranslate.install(loader);
        });
        run("playback speed", () -> {
            Runnable configure = () -> PlaybackSpeed.configure(Prefs.on(Prefs.REMEMBER_SPEED), Prefs.text(Prefs.SPEEDS));
            configure.run();
            Prefs.listen(configure);
            PlaybackSpeed.install(loader);
        });
        run("auto-scroll share panel", () -> AutoScroll.installSharePanel(loader));
        run("streak sticker", () -> StreakSticker.prepare(loader));
        run("downloads plus", () -> {
            Runnable configure = () -> {
                DownloadsPlus.videoDownloads = Prefs.on(Prefs.QUALITY_PICKER);
                DownloadsPlus.saveAllPhotos = Prefs.on(Prefs.SAVE_ALL_PHOTOS);
            };
            configure.run();
            Prefs.listen(configure);
            DownloadsPlus.install(loader);
        });
        run("A/B overrides", () -> AbOverrides.install(loader));
        run("startup ads", () -> StartupAds.install(loader));
        run("seekbar", () -> Seekbar.install(loader));
        run("settings row", () -> SettingsEntry.install(app, loader));
        run("auto streaks", () -> AutoStreakManager.install(app, loader));
        run("code index release", Discovery::release);
        RuntimeLog.log(BuildConfig.VERSION_NAME + " started in " + app.getPackageName());
        run("safe mode", SafeMode::started);
    }

    /**
     * After TikTok closed right after starting three times, only the settings row (with the bug
     * report) is installed, and the user is told why everything else is off this time.
     */
    private static void safeStartup(Application app, ClassLoader loader) {
        RuntimeLog.log("safe mode: TikTok closed right after starting several times, features are off for this launch");
        run("language", () -> I18n.use(app.getResources().getConfiguration().getLocales().get(0)));
        run("code index", () -> Discovery.init(app));
        run("settings row", () -> SettingsEntry.install(app, loader));
        run("code index release", Discovery::release);
        run("safe mode", SafeMode::started);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(Safe.run("safe mode notice", () ->
                android.widget.Toast.makeText(app, I18n.get(I18n.S.SAFE_MODE_SHORT), android.widget.Toast.LENGTH_LONG).show()), 3_000L);
    }

    private static void run(String name, Runnable task) {
        FeatureStatus.begin(name);
        try {
            task.run();
        } catch (Throwable error) {
            // Whatever goes wrong here must not stop the features after this one.
            String reason;
            try {
                reason = FeatureStatus.describe(error);
            } catch (Throwable broken) {
                reason = error.getClass().getSimpleName();
            }
            FeatureStatus.failed(name, reason);
            RuntimeLog.log(name + " unavailable: " + error);
        } finally {
            FeatureStatus.end();
        }
    }

    /** TikTok's Google sign-in breaks on devices it considers modified; keep the standard flow. */
    private static void googleLoginFix(ClassLoader loader) {
        for (String name : new String[]{"com.bytedance.lobby.google.GoogleAuth", "com.bytedance.lobby.google.GoogleOneTapAuth"}) {
            Class<?> type = Hooks.findClass(name, loader);
            if (type == null) continue;
            for (Method method : Reflect.methods(type)) {
                if (method.getParameterTypes().length == 0 && method.getReturnType() == boolean.class
                        && Modifier.isFinal(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                    Hooks.hook(method, Hooks.constant(false));
                }
            }
        }
    }
}
