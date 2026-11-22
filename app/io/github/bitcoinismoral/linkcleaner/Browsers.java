package io.github.bitcoinismoral.linkcleaner;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** Finds the real browsers on the device and forwards cleaned links to the one you picked. */
final class Browsers {

    private Browsers() {}

    static final String SELF = "io.github.bitcoinismoral.linkcleaner";
    private static final String VANADIUM = "app.vanadium.browser";
    private static final String PREFS = "settings";
    private static final String KEY_BROWSER = "browser";
    private static final String ACTION_VIEW = "android.intent.action.VIEW";
    private static final String CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE";

    /** A browser that can open web links (never Surveillance Slop Remover itself). */
    static final class Browser {
        final String packageName; final String label;
        Browser(String packageName, String label) { this.packageName = packageName; this.label = label; }
    }

    static List<Browser> list(Context ctx) {
        List<Browser> out = new ArrayList<>();
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent probe = new Intent(ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(CATEGORY_BROWSABLE);
            List<ResolveInfo> found = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL);
            if (found != null) {
                for (ResolveInfo ri : found) {
                    if (ri == null || ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (pkg == null || SELF.equals(pkg) || contains(out, pkg)) continue;
                    CharSequence label = ri.loadLabel(pm);
                    out.add(new Browser(pkg, label != null ? label.toString() : pkg));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static boolean contains(List<Browser> list, String pkg) {
        for (Browser b : list) if (b.packageName.equals(pkg)) return true;
        return false;
    }

    /** The browser cleaned links go to: your pick, else Vanadium, else the first one found. */
    static String chosen(Context ctx) {
        List<Browser> all = list(ctx);
        String saved = prefs(ctx).getString(KEY_BROWSER, null);
        if (saved != null && contains(all, saved)) return saved;
        if (contains(all, VANADIUM)) return VANADIUM;
        return all.isEmpty() ? null : all.get(0).packageName;
    }

    static void choose(Context ctx, String pkg) {
        prefs(ctx).edit().putString(KEY_BROWSER, pkg).apply();
    }

    /** Opens the (already cleaned) URL in the chosen browser. Returns false if there is no browser. */
    static boolean open(Context ctx, String url) {
        String pkg = chosen(ctx);
        if (pkg == null) return false;
        Intent view = new Intent(ACTION_VIEW, Uri.parse(url))
                .addCategory(CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        view.setPackage(pkg); // explicit target, so it can never loop back to Surveillance Slop Remover
        ctx.startActivity(view);
        return true;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
