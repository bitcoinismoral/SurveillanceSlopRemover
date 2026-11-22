import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Minimal, open-source replacement for aapt2 for this app. Writes, in Android's binary formats:
 *   AndroidManifest.xml                       (binary XML)
 *   res/drawable-anydpi-v26/ic_launcher.xml   (adaptive icon, binary XML)
 *   resources.arsc                            (resource table: icon layers per screen density)
 * Attribute/style/color IDs come from AOSP's open-source frameworks/base/core/res/res/values/public-final.xml.
 */
public class ResEncoder {

    static final String PKG = "io.github.bitcoinismoral.linkcleaner";
    static final int VERSION_CODE = 5;
    static final String VERSION_NAME = "3.1";

    // ---- framework attribute IDs (public-final.xml)
    static final Map<String, Integer> IDS = new HashMap<>();
    static {
        IDS.put("theme", 0x01010000);
        IDS.put("label", 0x01010001);
        IDS.put("icon", 0x01010002);
        IDS.put("name", 0x01010003);
        IDS.put("permission", 0x01010006);
        IDS.put("exported", 0x01010010);
        IDS.put("taskAffinity", 0x01010012);
        IDS.put("excludeFromRecents", 0x01010017);
        IDS.put("authorities", 0x01010018);
        IDS.put("grantUriPermissions", 0x0101001b);
        IDS.put("mimeType", 0x01010026);
        IDS.put("scheme", 0x01010027);
        IDS.put("drawable", 0x01010199);
        IDS.put("minSdkVersion", 0x0101020c);
        IDS.put("versionCode", 0x0101021b);
        IDS.put("versionName", 0x0101021c);
        IDS.put("noHistory", 0x0101022d);
        IDS.put("targetSdkVersion", 0x01010270);
        IDS.put("allowBackup", 0x01010280);
    }
    static final int THEME_DEVICE_DEFAULT_DAYNIGHT = 0x010302e3;
    static final int THEME_TRANSLUCENT_NOTITLEBAR = 0x01030010;
    static final int COLOR_BLACK = 0x0106000c;

    // ---- this app's resources (package 0x7f, type 1 = drawable)
    static final String[] ENTRIES = {"ic_launcher", "ic_fg", "ic_mono", "ic_tile"};
    static final int R_IC_LAUNCHER = 0x7f010000, R_IC_FG = 0x7f010001, R_IC_MONO = 0x7f010002, R_IC_TILE = 0x7f010003;
    static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    static final int[] DENSITY_DPI = {160, 240, 320, 480, 640};
    static final String ADAPTIVE_PATH = "res/drawable-anydpi-v26/ic_launcher.xml";

    // =====================================================================================
    // XML trees

    static final String NS = "http://schemas.android.com/apk/res/android";
    static final int T_REF = 0x01, T_STR = 0x03, T_INT = 0x10, T_BOOL = 0x12;

    static final class Attr {
        final boolean android; final String name; final int type; final Object val;
        Attr(boolean android, String name, int type, Object val) { this.android = android; this.name = name; this.type = type; this.val = val; }
        int resId() { if (!android) return 0; Integer id = IDS.get(name); if (id == null) throw new IllegalArgumentException("no ID for android:" + name); return id; }
    }

    static final class El {
        final String name; final List<Attr> attrs = new ArrayList<>(); final List<El> kids = new ArrayList<>();
        El(String name) { this.name = name; }
        El a(String n, String v) { attrs.add(new Attr(true, n, T_STR, v)); return this; }
        El a(String n, int v) { attrs.add(new Attr(true, n, T_INT, v)); return this; }
        El a(String n, boolean v) { attrs.add(new Attr(true, n, T_BOOL, v)); return this; }
        El ref(String n, int id) { attrs.add(new Attr(true, n, T_REF, id)); return this; }
        El plain(String n, String v) { attrs.add(new Attr(false, n, T_STR, v)); return this; }
        El k(El... e) { kids.addAll(Arrays.asList(e)); return this; }
    }

    static El e(String name) { return new El(name); }
    static El action(String n) { return e("action").a("name", n); }
    static El category(String n) { return e("category").a("name", n); }
    static El mime(String m) { return e("data").a("mimeType", m); }
    static String cls(String simple) { return PKG + "." + simple; }

    static El manifest() {
        return e("manifest").plain("package", PKG).a("versionCode", VERSION_CODE).a("versionName", VERSION_NAME).k(
            e("uses-sdk").a("minSdkVersion", 29).a("targetSdkVersion", 34),
            // package visibility (Android 11+): lets the link opener find your real browsers
            e("queries").k(e("intent").k(action("android.intent.action.VIEW"),
                category("android.intent.category.BROWSABLE"), e("data").a("scheme", "https"))),
            e("application").a("label", "Surveillance Slop Remover").ref("icon", R_IC_LAUNCHER).a("allowBackup", false).k(
                e("activity").a("name", cls("MainActivity")).a("exported", true)
                    .a("label", "Surveillance Slop Remover").ref("theme", THEME_DEVICE_DEFAULT_DAYNIGHT).k(
                    e("intent-filter").k(action("android.intent.action.MAIN"), category("android.intent.category.LAUNCHER"))),
                e("activity").a("name", cls("CleanActivity")).a("exported", true)
                    .a("label", "Clean links").a("excludeFromRecents", true).a("noHistory", true)
                    .a("taskAffinity", "").ref("theme", THEME_TRANSLUCENT_NOTITLEBAR).k(
                    e("intent-filter").a("label", "Clean links").k(action("android.intent.action.SEND"),
                        category("android.intent.category.DEFAULT"), mime("text/plain")),
                    e("intent-filter").a("label", "Clean links").k(action("android.intent.action.PROCESS_TEXT"),
                        category("android.intent.category.DEFAULT"), mime("text/plain"))),
                // link opener: offered as a browser so it can be set as the default for web links
                e("activity").a("name", cls("OpenLinkActivity")).a("exported", true)
                    .a("label", "Surveillance Slop Remover").a("excludeFromRecents", true).a("noHistory", true)
                    .a("taskAffinity", "").ref("theme", THEME_TRANSLUCENT_NOTITLEBAR).k(
                    e("intent-filter").k(action("android.intent.action.VIEW"),
                        category("android.intent.category.DEFAULT"), category("android.intent.category.BROWSABLE"),
                        e("data").a("scheme", "http"), e("data").a("scheme", "https"))),
                // photo metadata stripper: share target for images
                e("activity").a("name", cls("StripActivity")).a("exported", true)
                    .a("label", "Remove photo metadata").a("excludeFromRecents", true)
                    .a("taskAffinity", "").ref("theme", THEME_TRANSLUCENT_NOTITLEBAR).k(
                    e("intent-filter").a("label", "Remove photo metadata").k(action("android.intent.action.SEND"),
                        category("android.intent.category.DEFAULT"), mime("image/*")),
                    e("intent-filter").a("label", "Remove photo metadata").k(action("android.intent.action.SEND_MULTIPLE"),
                        category("android.intent.category.DEFAULT"), mime("image/*"))),
                e("provider").a("name", cls("CleanFileProvider")).a("authorities", PKG + ".files")
                    .a("exported", false).a("grantUriPermissions", true),
                e("service").a("name", cls("CleanTileService")).a("exported", true)
                    .a("label", "Clean links").ref("icon", R_IC_TILE)
                    .a("permission", "android.permission.BIND_QUICK_SETTINGS_TILE").k(
                    e("intent-filter").k(action("android.service.quicksettings.action.QS_TILE"))),
                e("service").a("name", cls("CopyWatcherService")).a("exported", true)
                    .a("label", "Surveillance Slop Remover — auto-clean copied links")
                    .a("permission", "android.permission.BIND_ACCESSIBILITY_SERVICE").k(
                    e("intent-filter").k(action("android.accessibilityservice.AccessibilityService")))));
    }

    static El adaptiveIcon() {
        return e("adaptive-icon").k(
            e("background").ref("drawable", COLOR_BLACK),
            e("foreground").ref("drawable", R_IC_FG),
            e("monochrome").ref("drawable", R_IC_MONO)); // themed icons (Android 13+)
    }

    // =====================================================================================
    // binary writers

    static void u8(ByteArrayOutputStream o, int v) { o.write(v & 0xff); }
    static void u16(ByteArrayOutputStream o, int v) { o.write(v & 0xff); o.write((v >> 8) & 0xff); }
    static void u32(ByteArrayOutputStream o, int v) { u16(o, v & 0xffff); u16(o, (v >>> 16) & 0xffff); }

    /** UTF-16 string pool chunk. */
    static byte[] stringPool(List<String> strings) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offs = new int[strings.size()];
        for (int i = 0; i < strings.size(); i++) {
            offs[i] = data.size();
            String s = strings.get(i);
            u16(data, s.length());
            for (char c : s.toCharArray()) u16(data, c);
            u16(data, 0);
        }
        while (data.size() % 4 != 0) data.write(0);
        int header = 28, start = header + 4 * strings.size();
        ByteArrayOutputStream sp = new ByteArrayOutputStream();
        u16(sp, 0x0001); u16(sp, header); u32(sp, start + data.size());
        u32(sp, strings.size()); u32(sp, 0); u32(sp, 0); u32(sp, start); u32(sp, 0);
        for (int off : offs) u32(sp, off);
        sp.writeBytes(data.toByteArray());
        return sp.toByteArray();
    }

    static final class AxmlWriter {
        final List<String> pool = new ArrayList<>();
        final Map<String, Integer> idx = new HashMap<>();
        final List<Integer> resMap = new ArrayList<>();

        int s(String str) {
            Integer i = idx.get(str);
            if (i == null) { i = pool.size(); pool.add(str); idx.put(str, i); }
            return i;
        }
        void collect(El el, TreeMap<Integer, String> out) {
            for (Attr a : el.attrs) if (a.android) out.put(a.resId(), a.name);
            for (El k : el.kids) collect(k, out);
        }
        static void node(ByteArrayOutputStream o, int type, int size) { u16(o, type); u16(o, 16); u32(o, size); u32(o, 1); u32(o, -1); }

        byte[] encode(El root) {
            TreeMap<Integer, String> named = new TreeMap<>();
            collect(root, named);
            for (Map.Entry<Integer, String> en : named.entrySet()) { s(en.getValue()); resMap.add(en.getKey()); }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            int prefix = s("android"), uri = s(NS);
            node(body, 0x0100, 24); u32(body, prefix); u32(body, uri);
            element(body, root, uri);
            node(body, 0x0101, 24); u32(body, prefix); u32(body, uri);
            byte[] sp = stringPool(pool);
            ByteArrayOutputStream rm = new ByteArrayOutputStream();
            u16(rm, 0x0180); u16(rm, 8); u32(rm, 8 + 4 * resMap.size());
            for (int id : resMap) u32(rm, id);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            u16(out, 0x0003); u16(out, 8); u32(out, 8 + sp.length + rm.size() + body.size());
            out.writeBytes(sp); out.writeBytes(rm.toByteArray()); out.writeBytes(body.toByteArray());
            return out.toByteArray();
        }
        void element(ByteArrayOutputStream o, El el, int uri) {
            List<Attr> attrs = new ArrayList<>(el.attrs);
            attrs.sort(Comparator.comparingLong(a -> a.resId() & 0xffffffffL)); // framework requires ID order
            int name = s(el.name);
            node(o, 0x0102, 16 + 20 + 20 * attrs.size());
            u32(o, -1); u32(o, name);
            u16(o, 20); u16(o, 20); u16(o, attrs.size()); u16(o, 0); u16(o, 0); u16(o, 0);
            for (Attr a : attrs) {
                u32(o, a.android ? uri : -1);
                u32(o, s(a.name));
                int data;
                if (a.type == T_STR) { data = s((String) a.val); u32(o, data); }
                else { u32(o, -1); data = a.type == T_BOOL ? (((Boolean) a.val) ? -1 : 0) : (Integer) a.val; }
                u16(o, 8); o.write(0); o.write(a.type); u32(o, data);
            }
            for (El k : el.kids) element(o, k, uri);
            node(o, 0x0103, 24); u32(o, -1); u32(o, name);
        }
    }

    /** 64-byte ResTable_config with only density and sdkVersion set. */
    static byte[] config(int density, int sdk) {
        byte[] c = new byte[64];
        c[0] = 64;
        c[14] = (byte) density; c[15] = (byte) (density >> 8);
        c[24] = (byte) sdk; c[25] = (byte) (sdk >> 8);
        return c;
    }

    /** One RES_TABLE_TYPE chunk: values[i] = global-pool string index for entry i, or -1 if absent. */
    static byte[] typeChunk(byte[] config, int[] values) {
        int n = values.length, header = 20 + config.length, entriesStart = header + 4 * n;
        ByteArrayOutputStream offsets = new ByteArrayOutputStream(), entries = new ByteArrayOutputStream();
        for (int i = 0; i < n; i++) {
            if (values[i] < 0) { u32(offsets, -1); continue; }
            u32(offsets, entries.size());
            u16(entries, 8); u16(entries, 0); u32(entries, i);                   // ResTable_entry: key = keyStrings[i]
            u16(entries, 8); u8(entries, 0); u8(entries, T_STR); u32(entries, values[i]); // Res_value: file path
        }
        ByteArrayOutputStream t = new ByteArrayOutputStream();
        u16(t, 0x0201); u16(t, header); u32(t, entriesStart + entries.size());
        u8(t, 1); u8(t, 0); u16(t, 0); u32(t, n); u32(t, entriesStart);
        t.writeBytes(config);
        t.writeBytes(offsets.toByteArray()); t.writeBytes(entries.toByteArray());
        return t.toByteArray();
    }

    static byte[] resourcesArsc() {
        List<String> values = new ArrayList<>();
        values.add(ADAPTIVE_PATH);                                   // 0
        for (String d : DENSITIES) {
            values.add("res/drawable-" + d + "-v4/ic_fg.png");
            values.add("res/drawable-" + d + "-v4/ic_mono.png");
            values.add("res/drawable-" + d + "-v4/ic_tile.png");
        }
        byte[] global = stringPool(values);
        byte[] typeStrings = stringPool(Collections.singletonList("drawable"));
        byte[] keyStrings = stringPool(Arrays.asList(ENTRIES));

        ByteArrayOutputStream spec = new ByteArrayOutputStream();
        u16(spec, 0x0202); u16(spec, 16); u32(spec, 16 + 4 * ENTRIES.length);
        u8(spec, 1); u8(spec, 0); u16(spec, 0); u32(spec, ENTRIES.length);
        for (int i = 0; i < ENTRIES.length; i++) u32(spec, 0x0100 | 0x0400); // varies by density + version

        ByteArrayOutputStream types = new ByteArrayOutputStream();
        types.writeBytes(typeChunk(config(0xFFFE, 26), new int[] {0, -1, -1, -1})); // anydpi-v26: adaptive icon
        for (int d = 0; d < DENSITIES.length; d++) {
            int base = 1 + 3 * d;
            types.writeBytes(typeChunk(config(DENSITY_DPI[d], 4), new int[] {-1, base, base + 1, base + 2}));
        }

        int pkgHeader = 288;
        ByteArrayOutputStream pkg = new ByteArrayOutputStream();
        int pkgSize = pkgHeader + typeStrings.length + keyStrings.length + spec.size() + types.size();
        u16(pkg, 0x0200); u16(pkg, pkgHeader); u32(pkg, pkgSize);
        u32(pkg, 0x7f);
        byte[] name = new byte[256];
        byte[] nb = PKG.getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(nb, 0, name, 0, Math.min(nb.length, 254));
        pkg.writeBytes(name);
        u32(pkg, pkgHeader);                        // typeStrings offset
        u32(pkg, 1);                                // lastPublicType
        u32(pkg, pkgHeader + typeStrings.length);   // keyStrings offset
        u32(pkg, ENTRIES.length);                   // lastPublicKey
        u32(pkg, 0);                                // typeIdOffset
        pkg.writeBytes(typeStrings); pkg.writeBytes(keyStrings);
        pkg.writeBytes(spec.toByteArray()); pkg.writeBytes(types.toByteArray());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u16(out, 0x0002); u16(out, 12); u32(out, 12 + global.length + pkg.size()); u32(out, 1);
        out.writeBytes(global); out.writeBytes(pkg.toByteArray());
        return out.toByteArray();
    }

    // =====================================================================================
    // plain-text XML (for the standard Gradle build that F-Droid uses), from the same trees

    static final Map<Integer, String> REF_NAMES = new HashMap<>();
    static {
        REF_NAMES.put(R_IC_LAUNCHER, "@drawable/ic_launcher");
        REF_NAMES.put(R_IC_FG, "@drawable/ic_fg");
        REF_NAMES.put(R_IC_MONO, "@drawable/ic_mono");
        REF_NAMES.put(R_IC_TILE, "@drawable/ic_tile");
        REF_NAMES.put(THEME_DEVICE_DEFAULT_DAYNIGHT, "@android:style/Theme.DeviceDefault.DayNight");
        REF_NAMES.put(THEME_TRANSLUCENT_NOTITLEBAR, "@android:style/Theme.Translucent.NoTitleBar");
        REF_NAMES.put(COLOR_BLACK, "@android:color/black");
    }

    /** Gradle sets package/version/SDK levels itself, so those are left out of the text manifest. */
    static final Set<String> GRADLE_OWNED = new HashSet<>(Arrays.asList("package", "versionCode", "versionName"));

    static String escape(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static void text(StringBuilder b, El el, int depth, boolean root) {
        if (el.name.equals("uses-sdk")) return;
        String ind = "    ".repeat(depth);
        b.append(ind).append('<').append(el.name);
        if (root) b.append(" xmlns:android=\"").append(NS).append('"');
        for (Attr a : el.attrs) {
            if (GRADLE_OWNED.contains(a.name)) continue;
            String v;
            if (a.type == T_REF) v = REF_NAMES.get((Integer) a.val);
            else if (a.type == T_BOOL) v = String.valueOf(a.val);
            else v = escape(String.valueOf(a.val));
            if (v == null) throw new IllegalStateException("no name for ref " + a.val);
            b.append("\n").append(ind).append("    ").append(a.android ? "android:" : "").append(a.name).append("=\"").append(v).append('"');
        }
        if (el.kids.isEmpty()) { b.append(" />\n"); return; }
        b.append(">\n");
        for (El k : el.kids) text(b, k, depth + 1, false);
        b.append(ind).append("</").append(el.name).append(">\n");
    }

    static String textXml(El root) {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        b.append("<!-- Generated by buildtools/ResEncoder.java. Edit the tree there, not this file. -->\n");
        text(b, root, 0, true);
        return b.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--text")) {
            Files.write(Paths.get(args[1]), textXml(manifest()).getBytes(StandardCharsets.UTF_8));
            Files.write(Paths.get(args[2]), textXml(adaptiveIcon()).getBytes(StandardCharsets.UTF_8));
            System.out.println("wrote text manifest + adaptive icon");
            return;
        }
        Path out = Paths.get(args[0]);
        Files.createDirectories(out.resolve("res/drawable-anydpi-v26"));
        Files.write(out.resolve("AndroidManifest.xml"), new AxmlWriter().encode(manifest()));
        Files.write(out.resolve(ADAPTIVE_PATH), new AxmlWriter().encode(adaptiveIcon()));
        Files.write(out.resolve("resources.arsc"), resourcesArsc());
        System.out.println("wrote AndroidManifest.xml, " + ADAPTIVE_PATH + ", resources.arsc");
    }
}
