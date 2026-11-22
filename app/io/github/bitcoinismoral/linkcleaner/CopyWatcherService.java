package io.github.bitcoinismoral.linkcleaner;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;
import java.util.Locale;

/**
 * Makes cleaning automatic: notices the moment you copy something, then briefly opens the
 * invisible CleanActivity (the only way Android 10+ allows reading the clipboard), which
 * cleans it and closes. You keep copying and pasting exactly as before.
 *
 * Privacy: it can NOT read screen contents (canRetrieveWindowContent is off). It only receives
 * three event types and looks at nothing but the short label of what was tapped:
 *   - taps on buttons/menu items labelled "Copy", "Copy link", "Copy link address", ...
 *   - Android's own "Text copied" clipboard confirmation (SystemUI)
 * Nothing is stored, logged, or sent anywhere (the app has no internet permission).
 */
public class CopyWatcherService extends AccessibilityService {

    static volatile boolean running = false;

    /** True if the user has switched the service on in Android's settings (even if not bound yet). */
    static boolean isEnabledInSettings(android.content.Context ctx) {
        try {
            String list = android.provider.Settings.Secure.getString(ctx.getContentResolver(),
                    "enabled_accessibility_services");
            return list != null && list.toLowerCase(Locale.ROOT).contains("io.github.bitcoinismoral.linkcleaner/");
        } catch (Throwable t) {
            return false;
        }
    }
    private static volatile long quietUntil = 0;

    private static final String SELF = "io.github.bitcoinismoral.linkcleaner";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String[] COPY_WORDS = {
            "copy", "copied", "copier", "copié", "kopieren", "kopiert", "copiar", "copiado", "copia",
            "copiato", "kopiëren", "gekopieerd", "kopiuj", "skopiowano", "kopiera", "kopierad",
            "копир", "скопир",
            "복사", "コピー", "复制", "複製"
    };

    private Handler handler;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        handler = new Handler(Looper.getMainLooper());
        // Never let an exception escape: Android switches off accessibility services that crash.
        try {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_ANNOUNCEMENT;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 100;
            info.flags = 0;
            setServiceInfo(info);
        } catch (Throwable ignored) {
        }
        running = true;
    }

    /** Called right before we write the clipboard ourselves, so the echo doesn't loop. */
    static void ignoreNextCopyEvent() {
        quietUntil = System.currentTimeMillis() + 2500;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            handle(event);
        } catch (Throwable ignored) {
            // never crash the service
        }
    }

    private void handle(AccessibilityEvent event) {
        if (event == null || handler == null) return;
        CharSequence pkgCs = event.getPackageName();
        String pkg = pkgCs == null ? "" : pkgCs.toString();
        if (SELF.equals(pkg)) return;

        int type = event.getEventType();
        boolean trigger;
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            trigger = mentionsCopy(labelOf(event));
        } else if (SYSTEM_UI.equals(pkg)) {
            String label = labelOf(event);
            trigger = mentionsCopy(label) || label.contains("clipboard");
        } else {
            trigger = false;
        }
        if (!trigger) return;

        long now = System.currentTimeMillis();
        if (now < quietUntil) return;
        quietUntil = now + 1500; // one clean per copy, even if several signals arrive

        handler.postDelayed(new Runnable() {
            @Override public void run() { launchCleaner(); }
        }, 300); // let the app finish writing the clipboard
    }

    private void launchCleaner() {
        Intent i = new Intent(this, CleanActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(CleanActivity.EXTRA_AUTO, true);
        try {
            startActivity(i);
        } catch (Throwable ignored) {
            // e.g. device locked; nothing to do
        }
    }

    private static String labelOf(AccessibilityEvent e) {
        StringBuilder b = new StringBuilder();
        List<CharSequence> texts = e.getText();
        if (texts != null) for (CharSequence t : texts) if (t != null) b.append(t).append(' ');
        CharSequence d = e.getContentDescription();
        if (d != null) b.append(d);
        // Only short labels: a long text means it's content, not a "Copy" button.
        String s = b.toString().toLowerCase(Locale.ROOT).trim();
        return s.length() > 60 ? "" : s;
    }

    private static boolean mentionsCopy(String label) {
        if (label.isEmpty() || label.contains("copyright")) return false;
        for (String w : COPY_WORDS) if (label.contains(w)) return true;
        return false;
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }
}
