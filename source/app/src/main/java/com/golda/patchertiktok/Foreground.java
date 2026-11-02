package com.golda.patchertiktok;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether any TikTok screen is shown. The module's once-a-second timers have nothing to do while
 * TikTok is put away, so they stop and start again from {@link #listen}. Picture-in-picture counts
 * as shown. Used on the main thread only.
 */
final class Foreground {
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static boolean installed;
    private static int started;

    private Foreground() { }

    static void install(Application app) {
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity activity) {
                if (started++ > 0) return;
                for (Runnable listener : LISTENERS) Safe.run("foreground", listener).run();
            }

            @Override public void onActivityStopped(Activity activity) {
                if (started > 0) started--;
            }

            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    /** Before install it answers true, so a timer never stays off for good. */
    static boolean shown() {
        return !installed || started > 0;
    }

    /** {@code listener} runs each time TikTok comes back to the screen. */
    static void listen(Runnable listener) {
        LISTENERS.add(listener);
    }
}
