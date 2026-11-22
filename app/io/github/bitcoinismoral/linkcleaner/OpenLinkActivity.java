package io.github.bitcoinismoral.linkcleaner;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Link opener: set Surveillance Slop Remover as the default browser and every link you tap in any app comes
 * here first. It strips the tracking and immediately hands the clean link to your real browser
 * (Vanadium by default). No screen is shown; it takes a few milliseconds.
 */
public class OpenLinkActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent in = getIntent();
            Uri data = in != null ? in.getData() : null;
            if (data != null) {
                String original = data.toString();
                String cleaned = LinkCleaner.cleanSingleUrl(original);
                if (!Browsers.open(this, cleaned)) {
                    Toast.makeText(this, "No browser found to open the link", Toast.LENGTH_LONG).show();
                } else if (!cleaned.equals(original)) {
                    Toast.makeText(this, "Tracking removed", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Throwable ignored) {
        }
        finish();
    }
}
