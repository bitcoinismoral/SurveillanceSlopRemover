package io.github.bitcoinismoral.linkcleaner;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serves the cleaned photo copies to the app you share them with. Not exported: other apps can only
 * read a file when the share grants them permission for that one file, and only read-only.
 * Copies are deleted automatically after an hour.
 */
public class CleanFileProvider extends ContentProvider {

    static final String AUTHORITY = "io.github.bitcoinismoral.linkcleaner.files";
    private static final long MAX_AGE_MS = 60L * 60 * 1000;

    static File sharedDir(Context ctx) {
        File d = new File(ctx.getCacheDir(), "shared");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTHORITY + "/" + f.getName());
    }

    static void deleteOldFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        for (File f : files) if (now - f.lastModified() > MAX_AGE_MS) f.delete();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("\\") || name.startsWith(".")) {
            throw new FileNotFoundException();
        }
        File f = new File(sharedDir(getContext()), name);
        if (!f.isFile()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try { f = fileFor(uri); } catch (FileNotFoundException e) { return null; }
        String[] cols = projection != null ? projection : new String[] {"_display_name", "_size"};
        Object[] row = new Object[cols.length];
        for (int k = 0; k < cols.length; k++) {
            if ("_display_name".equals(cols[k])) row[k] = f.getName();
            else if ("_size".equals(cols[k])) row[k] = f.length();
        }
        MatrixCursor c = new MatrixCursor(cols);
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String p = uri.toString().toLowerCase();
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode == null || !mode.equals("r")) throw new FileNotFoundException("read-only");
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
