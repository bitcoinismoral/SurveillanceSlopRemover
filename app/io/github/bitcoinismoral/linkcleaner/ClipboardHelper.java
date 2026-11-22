package io.github.bitcoinismoral.linkcleaner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

final class ClipboardHelper {

    private ClipboardHelper() {}

    /** Outcome of a clipboard clean. */
    static final class Outcome {
        final boolean changed; final String message;
        /** Nothing could be read. Right after a copy this means access is blocked, e.g. by
         *  GrapheneOS Secure Paste (per-app "clipboard access" toggle, added in 2026091700). */
        final boolean unreadable;
        Outcome(boolean changed, String message) { this(changed, message, false); }
        Outcome(boolean changed, String message, boolean unreadable) {
            this.changed = changed; this.message = message; this.unreadable = unreadable;
        }
    }

    /** Reads clipboard text. Only works while one of our windows has focus (Android 10+). */
    static CharSequence read(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            return clip.getItemAt(0).coerceToText(ctx);
        } catch (Throwable t) {
            return null; // e.g. access denied
        }
    }

    static void write(Context ctx, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        CopyWatcherService.ignoreNextCopyEvent(); // our own write must not re-trigger the watcher
        cm.setPrimaryClip(ClipData.newPlainText("Cleaned link", text));
    }

    /** Cleans the clipboard in place. */
    static Outcome cleanClipboard(Context ctx) {
        CharSequence current = read(ctx);
        if (current == null || current.length() == 0) return new Outcome(false, "Clipboard is empty", true);
        LinkCleaner.Result r = LinkCleaner.clean(current);
        if (r.linksFound == 0) return new Outcome(false, "No link in clipboard");
        if (r.linksCleaned == 0) return new Outcome(false, "Link already clean");
        write(ctx, r.text);
        return new Outcome(true, r.linksCleaned == 1 ? "Tracking removed from link" : "Tracking removed from " + r.linksCleaned + " links");
    }
}
