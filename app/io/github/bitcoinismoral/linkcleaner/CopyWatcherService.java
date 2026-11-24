package io.github.bitcoinismoral.linkcleaner;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Makes cleaning automatic: notices the moment you copy something, then briefly adds an
 * invisible 1x1 overlay that takes window focus (the only way Android 10+ allows reading the
 * clipboard), cleans it and removes itself. You keep copying and pasting exactly as before.
 *
 * It also cleans at paste time (see cleanPastedText), which needs canRetrieveWindowContent so it
 * can replace the text in the box you pasted into. That access is only used on the source of a
 * "text changed" event that inserted a tracked link; the service never browses the screen.
 * It looks at: labels of tapped "Copy" buttons, Android's/apps' "Copied" notices, and text just
 * pasted into a text box. Nothing is stored or sent anywhere (no internet permission).
 */
public class CopyWatcherService extends AccessibilityService {

    static volatile boolean running = false;
    private static volatile boolean blockedWarned = false;

    static final String BLOCKED_MSG = "Surveillance Slop Remover can't read the clipboard. On GrapheneOS: "
            + "Settings \u2192 Apps \u2192 Surveillance Slop Remover \u2192 turn on clipboard access (Secure Paste).";

    // ---- diagnostics: last few steps, in memory only (never stored or sent), shown in the app
    private static final java.util.ArrayDeque<String> LOG = new java.util.ArrayDeque<>();

    static void log(String msg) {
        String line = new java.text.SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new java.util.Date()) + "  " + msg;
        synchronized (LOG) {
            LOG.addFirst(line);
            while (LOG.size() > 10) LOG.removeLast();
        }
    }

    static String recentLog() {
        synchronized (LOG) {
            if (LOG.isEmpty()) return "(nothing yet — copy a link, then come back here)";
            StringBuilder b = new StringBuilder();
            for (String l : LOG) b.append(l).append('\n');
            return b.toString().trim();
        }
    }

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
                    | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED // paste-time cleaning (3.5)
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED // "Copied" toasts
                    | AccessibilityEvent.TYPE_ANNOUNCEMENT;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 100;
            // Bug fixed in 3.3: without this flag Android drops events from views it considers
            // "not important for accessibility" (AbstractAccessibilityServiceConnection
            // .clientWantsEventLocked). SystemUI announces "Text copied" from the clipboard
            // overlay's root layout, which is exactly such a view, so the main copy signal never
            // reached us. Still no window-content access: canRetrieveWindowContent stays off.
            info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            setServiceInfo(info);
        } catch (Throwable t) {
            log("setServiceInfo failed: " + t);
        }
        running = true;
        log("service connected");
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
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            cleanPastedText(event, pkg);
            return;
        }
        boolean trigger;
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            trigger = mentionsCopy(labelOf(event));
        } else if (type == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            // toasts such as "Copied", "Copied to clipboard", "Link copied" (system or the app's own)
            String label = labelOf(event);
            trigger = label.contains("copied") || label.contains("clipboard");
        } else if (SYSTEM_UI.equals(pkg)) {
            // Android's own "Text copied" popup/announcement. No length limit here: the popup can
            // include a preview of what was copied, which made long links get ignored before 3.2.
            String text = fullText(event);
            trigger = mentionsCopy(text) || text.contains("clipboard");
        } else {
            trigger = false;
        }
        if (!trigger) return;

        long now = System.currentTimeMillis();
        if (now < quietUntil) { log("copy signal ignored (just cleaned)"); return; }
        log("copy detected via " + (type == AccessibilityEvent.TYPE_VIEW_CLICKED ? "tap in " + pkg
                : type == AccessibilityEvent.TYPE_ANNOUNCEMENT ? "Android 'copied' announcement" : "Android 'copied' popup"));
        quietUntil = now + 1500; // one clean per copy, even if several signals arrive

        handler.postDelayed(new Runnable() {
            @Override public void run() { launchCleaner(); }
        }, 300); // let the app finish writing the clipboard
    }

    /**
     * Cleans the clipboard without starting an activity.
     *
     * Bug fixed in 3.2: this used to start CleanActivity, but since Android 14 a background
     * activity start from an accessibility service is silently blocked (the system's binding only
     * grants it while the binder has a visible window — see BackgroundLaunchProcessController
     * .isBoundByForegroundUid in AOSP), so nothing happened.
     *
     * Instead we add a 1x1 invisible, untouchable TYPE_ACCESSIBILITY_OVERLAY window (allowed for
     * accessibility services without any extra permission). It is focusable, so it receives window
     * focus — which is exactly what ClipboardService requires (isUidFocused) — then cleans the
     * clipboard and removes itself. FLAG_ALT_FOCUSABLE_IM keeps it from taking over the keyboard.
     */
    private void launchCleaner() {
        final WindowManager wm;
        try {
            wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        } catch (Throwable t) {
            return;
        }
        if (wm == null) { log("no WindowManager"); return; }
        final boolean[] finished = {false};
        final View probe = new View(this) {
            @Override
            public void onWindowFocusChanged(boolean hasWindowFocus) {
                super.onWindowFocusChanged(hasWindowFocus);
                if (!hasWindowFocus || finished[0]) return;
                finished[0] = true;
                try {
                    ClipboardHelper.Outcome o = ClipboardHelper.cleanClipboard(CopyWatcherService.this);
                    log("overlay got focus -> " + o.message);
                    if (o.changed) {
                        Toast.makeText(CopyWatcherService.this, o.message, Toast.LENGTH_SHORT).show();
                    } else if (o.unreadable) {
                        // Something was just copied, so an empty read means access is blocked.
                        log("clipboard access is blocked for this app (paste-time cleaning still works)");
                        if (!blockedWarned) {
                            blockedWarned = true;
                            Toast.makeText(CopyWatcherService.this, BLOCKED_MSG, Toast.LENGTH_LONG).show();
                        }
                    }
                } catch (Throwable t) {
                    log("clipboard read failed: " + t);
                }
                removeQuietly(wm, this);
            }
        };
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        try {
            wm.addView(probe, lp);
        } catch (Throwable t) {
            log("overlay could not be added: " + t);
            return;
        }
        // Safety net: never leave the overlay behind, even if focus never arrives.
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!finished[0]) {
                    log("overlay never got focus (timed out)");
                    Toast.makeText(CopyWatcherService.this,
                            "Surveillance Slop Remover: Android didn't let it read the clipboard this time (no focus)",
                            Toast.LENGTH_SHORT).show();
                }
                finished[0] = true;
                removeQuietly(wm, probe);
            }
        }, 1500);
    }

    /**
     * Paste-time cleaning (3.5). Copy-time detection can't see copies made inside web pages
     * (e.g. X's website in Vanadium shows its own "Copied" banner and Android shows nothing), so
     * we also act when a link lands in a text box: if a single change inserted 8+ characters
     * (a paste, not typing) and that inserted part contains a tracked link, the text box gets the
     * cleaned version and the clean link goes back on the clipboard. Only the source of this
     * "text changed" event is touched; password fields are skipped; nothing is read otherwise.
     */
    private void cleanPastedText(AccessibilityEvent e, String pkg) {
        int added = e.getAddedCount();
        int from = e.getFromIndex();
        if (added < 8 || from < 0) return;
        List<CharSequence> texts = e.getText();
        if (texts == null || texts.isEmpty() || texts.get(0) == null) return;
        String text = texts.get(0).toString();
        if (from + added > text.length()) return;
        String inserted = text.substring(from, from + added);
        if (!inserted.contains("http") && !inserted.contains(".com/") && !inserted.contains(".be/")) return;
        LinkCleaner.Result r = LinkCleaner.clean(inserted);
        if (r.linksCleaned == 0) return;

        AccessibilityNodeInfo node = e.getSource();
        if (node == null) { log("pasted dirty link in " + pkg + " but can't reach the text box"); return; }
        if (!node.isEditable() || node.isPassword()) return;
        CharSequence currentCs = node.getText();
        String current = currentCs != null ? currentCs.toString() : text;
        if (!current.regionMatches(from, inserted, 0, inserted.length())) {
            int at = current.indexOf(inserted);           // text moved since the event; find it again
            if (at < 0) return;
            from = at;
        }
        String updated = current.substring(0, from) + r.text + current.substring(from + inserted.length());
        Bundle setText = new Bundle();
        setText.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)) {
            log("pasted dirty link in " + pkg + " but the text box refused the edit");
            return;
        }
        int caret = from + r.text.length();
        Bundle sel = new Bundle();
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret);
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
        try { ClipboardHelper.write(this, r.text); } catch (Throwable ignored) {} // writing needs no focus
        log("cleaned link pasted in " + pkg);
        Toast.makeText(this, "Tracking removed from pasted link", Toast.LENGTH_SHORT).show();
    }

    private static void removeQuietly(WindowManager wm, View v) {
        try {
            wm.removeView(v);
        } catch (Throwable ignored) {
            // already removed
        }
    }

    private static String fullText(AccessibilityEvent e) {
        StringBuilder b = new StringBuilder();
        List<CharSequence> texts = e.getText();
        if (texts != null) for (CharSequence t : texts) if (t != null) b.append(t).append(' ');
        CharSequence d = e.getContentDescription();
        if (d != null) b.append(d);
        return b.toString().toLowerCase(Locale.ROOT);
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
        if (label.isEmpty()) return false;
        label = label.replace("copyright", "");
        for (String w : COPY_WORDS) if (label.contains(w)) return true;
        return false;
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        log("service stopped");
        running = false;
        super.onDestroy();
    }
}
