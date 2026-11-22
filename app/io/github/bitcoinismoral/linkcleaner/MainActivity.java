package io.github.bitcoinismoral.linkcleaner;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Setup screen: three privacy features, each with a self-checking status and one-tap setup.
 * Opening it also cleans the clipboard immediately. UI is built in code (no layout resources).
 */
public class MainActivity extends Activity {

    private static final String SELF = Browsers.SELF;

    // ---- copy cleaning
    private static final String STEP_1 =
            "Step 1 — Allow the setting (one time only)\n"
            + "Android blocks accessibility for apps installed from a file until you allow it. "
            + "Tap the button, then ⋮ (top right) → \"Allow restricted settings\" and confirm.\n"
            + "Don't see that option? Do Step 2 once first (Android will say it's restricted), "
            + "then come back — the option appears after that. Installing from F-Droid skips this step.";
    private static final String STEP_2 =
            "Step 2 — Switch it on\n"
            + "Tap the button → \"Downloaded apps\" (or \"Installed apps\") → Surveillance Slop Remover "
            + "→ turn on \"Use Surveillance Slop Remover\" → Allow. Leave the shortcut / floating button off.";
    private static final String COPY_ON =
            "Every link you copy is cleaned automatically within a split second.";
    private static final String STUCK =
            "Android has it switched on, but it isn't running yet. Open the settings below and turn "
            + "\"Use Surveillance Slop Remover\" off and on again. If it keeps happening, restart the phone.";

    // ---- link opener
    private static final String OPENER_OFF =
            "Make Surveillance Slop Remover your default browser app. Links you tap in any app are cleaned and "
            + "opened in your real browser instantly — you won't see it at all.";
    private static final String OPENER_ON =
            "Links you tap in other apps are cleaned, then opened in your browser.";

    // ---- photos
    private static final String PHOTOS =
            "Share any photo → \"Remove photo metadata\" → pick the app to send it to.\n"
            + "Removes GPS location, phone model, timestamps, edit history, comments and embedded "
            + "Motion Photo video. JPEG, PNG and WebP keep their exact original quality (no "
            + "re-compression); the photo's rotation and colours are kept. Other formats (HEIC, AVIF) "
            + "are re-saved as JPEG. Your original is never changed, and the clean copy gets a "
            + "neutral file name.";

    private static final String HOW_TO =
            "What gets removed from links:\n"
            + "• X/Twitter: ?s=… &t=… and other share trackers (search terms kept)\n"
            + "• YouTube: si, pp, feature and other share trackers (timestamps kept)\n"
            + "• AI answers: utm_source=chatgpt.com / perplexity / gemini / copilot on cited sources, "
            + "and #:~:text= highlight fragments\n"
            + "• Ad click IDs: fbclid, gclid, msclkid, ttclid, twclid and more\n"
            + "• Instagram, Threads, Facebook, TikTok, Reddit, LinkedIn, Spotify, Apple, Amazon, eBay, "
            + "Google & Bing search, newsletters (Mailchimp, HubSpot, Substack…)\n"
            + "• Redirect wrappers (google.com/url, l.facebook.com, YouTube & Outlook redirects) are "
            + "unwrapped to the real destination\n\n"
            + "Also: Quick Settings tile \"Clean links\", Share → \"Clean links\", "
            + "Select text → ⋮ → \"Clean links\".\n\n"
            + "Privacy: no internet permission, fully offline, open source (GPL-3.0). Automatic mode "
            + "only sees the label of the button you tap (e.g. \"Copy link\") and Android's \"Copied\" "
            + "notice — it cannot read your screen.";

    private static final int REQ_BROWSER_ROLE = 7;
    private static final int PAD = 64;

    private TextView diagnostics;
    private TextView status, autoStatus, step1, step2, note, openerStatus, openerNote, browserLabel;
    private Button step1Button, step2Button, openerButton;
    private LinearLayout browserList;
    private boolean autoCleaned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(PAD, PAD, PAD, PAD);

        TextView title = text(28f, 0, 0);
        title.setText("Surveillance Slop Remover");
        col.addView(title);

        // ---------------- 1. automatic copy cleaning
        col.addView(header("Automatic copy cleaning"));
        autoStatus = text(17f, PAD / 4, PAD / 4);
        note = text(14f, 0, 0);
        step1 = text(14f, PAD / 2, 0);
        step1.setText(STEP_1);
        step1Button = button("Open app info", new View.OnClickListener() {
            @Override public void onClick(View v) { openAppInfo(); }
        });
        step2 = text(14f, PAD / 2, 0);
        step2.setText(STEP_2);
        step2Button = button("Open accessibility settings", new View.OnClickListener() {
            @Override public void onClick(View v) { open("android.settings.ACCESSIBILITY_SETTINGS"); }
        });
        col.addView(autoStatus);
        col.addView(note);
        col.addView(step1);
        col.addView(step1Button);
        col.addView(step2);
        col.addView(step2Button);

        // ---------------- 2. link opener
        col.addView(header("Link opener"));
        openerStatus = text(17f, PAD / 4, PAD / 4);
        openerNote = text(14f, 0, 0);
        openerButton = button("Make it the link opener", new View.OnClickListener() {
            @Override public void onClick(View v) { requestBrowserRole(); }
        });
        browserLabel = text(14f, PAD / 2, 0);
        browserList = new LinearLayout(this);
        browserList.setOrientation(LinearLayout.VERTICAL);
        col.addView(openerStatus);
        col.addView(openerNote);
        col.addView(openerButton);
        col.addView(browserLabel);
        col.addView(browserList);

        // ---------------- 3. photos
        col.addView(header("Photo metadata"));
        TextView photos = text(14f, PAD / 4, 0);
        photos.setText(PHOTOS);
        col.addView(photos);

        // ---------------- clipboard + details
        col.addView(header("Clipboard"));
        status = text(15f, PAD / 4, PAD / 4);
        status.setTextIsSelectable(true);
        col.addView(status);
        col.addView(button("Clean clipboard now", new View.OnClickListener() {
            @Override public void onClick(View v) { runClipboardClean(); }
        }));
        col.addView(header("Diagnostics"));
        TextView diagNote = text(13f, PAD / 4, 0);
        diagNote.setText("What automatic cleaning did most recently (kept in memory only, never saved or sent):");
        col.addView(diagNote);
        diagnostics = text(13f, PAD / 4, 0);
        diagnostics.setTextIsSelectable(true);
        col.addView(diagnostics);

        TextView how = text(14f, PAD, 0);
        how.setText(HOW_TO);
        col.addView(how);

        scroll.addView(col);
        setContentView(scroll);
    }

    private TextView text(float size, int top, int bottom) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setPadding(0, top, 0, bottom);
        return t;
    }

    private TextView header(String s) {
        TextView t = text(21f, PAD + PAD / 2, 0);
        t.setText(s);
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        autoCleaned = false;
        refresh();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refresh(); // catches services/roles that switch on a moment later
        if (hasFocus && !autoCleaned) {
            autoCleaned = true;
            runClipboardClean();
        }
    }

    private void refresh() {
        diagnostics.setText(CopyWatcherService.recentLog());
        refreshCopyCleaning();
        refreshOpener();
    }

    private void refreshCopyCleaning() {
        boolean enabled = CopyWatcherService.isEnabledInSettings(this);
        boolean running = CopyWatcherService.running;
        if (running || enabled) {
            autoStatus.setText(running ? "✅ ON" : "⚠️ Starting…");
            note.setText(running ? COPY_ON : STUCK);
            step1.setVisibility(View.GONE);
            step1Button.setVisibility(View.GONE);
            step2.setVisibility(View.GONE);
            step2Button.setVisibility(running ? View.GONE : View.VISIBLE);
        } else {
            autoStatus.setText("⚪ OFF");
            note.setText("Two quick steps and you never have to open this app again:");
            step1.setVisibility(View.VISIBLE);
            step1Button.setVisibility(View.VISIBLE);
            step2.setVisibility(View.VISIBLE);
            step2Button.setVisibility(View.VISIBLE);
        }
    }

    private RoleManager roleManager() {
        try {
            Object o = getSystemService("role");
            return o instanceof RoleManager ? (RoleManager) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isLinkOpener() {
        try {
            RoleManager rm = roleManager();
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_BROWSER);
        } catch (Throwable t) {
            return false;
        }
    }

    private void refreshOpener() {
        boolean on = isLinkOpener();
        openerStatus.setText(on ? "✅ ON" : "⚪ OFF");
        openerNote.setText(on ? OPENER_ON : OPENER_OFF);
        openerButton.setVisibility(on ? View.GONE : View.VISIBLE);

        browserList.removeAllViews();
        List<Browsers.Browser> all = Browsers.list(this);
        if (all.isEmpty()) {
            browserLabel.setText("No browser installed — install Vanadium or another browser first.");
            return;
        }
        browserLabel.setText("Cleaned links open in:");
        String chosen = Browsers.chosen(this);
        for (final Browsers.Browser b : all) {
            boolean sel = b.packageName.equals(chosen);
            browserList.addView(button((sel ? "✓  " : "     ") + b.label, new View.OnClickListener() {
                @Override public void onClick(View v) { Browsers.choose(MainActivity.this, b.packageName); refreshOpener(); }
            }));
        }
    }

    private void requestBrowserRole() {
        try {
            RoleManager rm = roleManager();
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER), REQ_BROWSER_ROLE);
                return;
            }
        } catch (Throwable ignored) {
        }
        open("android.settings.MANAGE_DEFAULT_APPS_SETTINGS");
    }

    private void openAppInfo() {
        try {
            startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + SELF)));
        } catch (Throwable t) {
            open("android.settings.ACCESSIBILITY_SETTINGS");
        }
    }

    private void open(String action) {
        try { startActivity(new Intent(action)); } catch (Throwable ignored) {}
    }

    private void runClipboardClean() {
        ClipboardHelper.Outcome o = ClipboardHelper.cleanClipboard(this);
        CharSequence now = ClipboardHelper.read(this);
        status.setText(now != null && now.length() > 0 ? o.message + "\n" + now : o.message);
    }
}
