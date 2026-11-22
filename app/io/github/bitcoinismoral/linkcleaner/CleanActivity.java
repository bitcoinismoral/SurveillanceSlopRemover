package io.github.bitcoinismoral.linkcleaner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Invisible, instant activity that does the actual cleaning for:
 *  - automatic mode (launched by CopyWatcherService right after you copy something)
 *  - the Quick Settings tile
 *  - Share -> "Clean links"
 *  - Select text -> "Clean links"
 *
 * Android 10+ only lets the focused app read the clipboard, so the read happens once
 * this window gains focus, then it closes immediately.
 */
public class CleanActivity extends Activity {

    static final String EXTRA_AUTO = "io.github.bitcoinismoral.linkcleaner.AUTO";

    private boolean done = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        String action = i != null ? i.getAction() : null;

        if (Intent.ACTION_SEND.equals(action)) {
            finishWithText(i.getCharSequenceExtra(Intent.EXTRA_TEXT), null);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            CharSequence text = i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            Intent result = null;
            if (!i.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)) {
                result = new Intent();
                result.putExtra(Intent.EXTRA_PROCESS_TEXT, LinkCleaner.clean(text).text);
            }
            finishWithText(text, result);
        }
        // otherwise (auto / tile / plain launch): wait for window focus, then read the clipboard
    }

    private void finishWithText(CharSequence text, Intent result) {
        done = true;
        LinkCleaner.Result r = LinkCleaner.clean(text);
        String msg;
        if (r.linksFound == 0) {
            msg = "No link found";
        } else {
            ClipboardHelper.write(this, r.text);
            msg = r.linksCleaned > 0 ? "Cleaned & copied" : "Already clean — copied";
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        if (result != null) setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || done) return;
        done = true;
        boolean auto = getIntent() != null && getIntent().getBooleanExtra(EXTRA_AUTO, false);
        ClipboardHelper.Outcome o = ClipboardHelper.cleanClipboard(this);
        // In automatic mode stay silent unless something was actually removed.
        if (!auto || o.changed) Toast.makeText(this, o.message, Toast.LENGTH_SHORT).show();
        finish();
    }
}
