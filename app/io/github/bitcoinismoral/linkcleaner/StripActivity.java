package io.github.bitcoinismoral.linkcleaner;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Share target "Remove photo metadata": receives photos from any app's share menu, strips their
 * metadata, and opens the share menu again with the clean copies. Originals are never modified.
 * Clean copies get neutral names (photo-1.jpg, ...) because camera file names contain timestamps.
 */
public class StripActivity extends Activity {

    private static final long MAX_BYTES = 80L * 1024 * 1024;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final List<Uri> inputs = collectInputs(getIntent());
        if (inputs.isEmpty()) {
            Toast.makeText(this, "No photo received", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Toast.makeText(this, "Removing metadata…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() { process(inputs); }
        }).start();
    }

    private void process(List<Uri> inputs) {
        final ArrayList<Uri> outputs = new ArrayList<>();
        final Set<String> removed = new LinkedHashSet<>();
        String mime = null;
        int failed = 0;
        File dir = CleanFileProvider.sharedDir(this);
        CleanFileProvider.deleteOldFiles(dir);
        for (int n = 0; n < inputs.size(); n++) {
            try {
                byte[] in = readAll(getContentResolver().openInputStream(inputs.get(n)));
                MetadataStripper.Result r = MetadataStripper.strip(in);
                byte[] data; String ext, type;
                if (r != null) {
                    data = r.data; ext = r.extension; type = r.mimeType; removed.addAll(r.removed);
                } else {
                    data = reencodeAsJpeg(in); ext = "jpg"; type = "image/jpeg";
                    removed.add("all metadata (re-saved as JPEG)");
                }
                File f = new File(dir, "photo-" + System.currentTimeMillis() % 100000 + "-" + (n + 1) + "." + ext);
                FileOutputStream fo = new FileOutputStream(f);
                try { fo.write(data); } finally { fo.close(); }
                outputs.add(CleanFileProvider.uriFor(f));
                mime = mime == null ? type : (mime.equals(type) ? mime : "image/*");
            } catch (Throwable t) {
                failed++;
            }
        }
        final String finalMime = mime;
        final int finalFailed = failed;
        runOnUiThread(new Runnable() {
            @Override public void run() { share(outputs, finalMime, removed, finalFailed); }
        });
    }

    private void share(ArrayList<Uri> outputs, String mime, Set<String> removed, int failed) {
        try {
            if (outputs.isEmpty()) {
                Toast.makeText(this, "Couldn't read that photo", Toast.LENGTH_LONG).show();
                return;
            }
            String summary = removed.isEmpty() ? "No metadata found — sharing a clean copy"
                    : "Removed: " + join(removed);
            if (failed > 0) summary += " (" + failed + " photo(s) skipped)";
            Toast.makeText(this, summary, Toast.LENGTH_LONG).show();

            Intent send;
            if (outputs.size() == 1) {
                send = new Intent(Intent.ACTION_SEND);
                send.putExtra(Intent.EXTRA_STREAM, (Parcelable) outputs.get(0));
            } else {
                send = new Intent(Intent.ACTION_SEND_MULTIPLE);
                send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, outputs);
            }
            send.setType(mime != null ? mime : "image/*");
            ClipData clip = ClipData.newRawUri("", outputs.get(0));
            for (int k = 1; k < outputs.size(); k++) clip.addItem(new ClipData.Item(outputs.get(k)));
            send.setClipData(clip);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share without metadata"));
        } catch (Throwable ignored) {
        } finally {
            finish();
        }
    }

    private static List<Uri> collectInputs(Intent i) {
        List<Uri> out = new ArrayList<>();
        if (i == null) return out;
        try {
            if (Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())) {
                ArrayList<Parcelable> list = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (list != null) for (Parcelable p : list) if (p instanceof Uri) out.add((Uri) p);
            } else {
                Parcelable p = i.getParcelableExtra(Intent.EXTRA_STREAM);
                if (p instanceof Uri) out.add((Uri) p);
            }
            if (out.isEmpty() && i.getClipData() != null) {
                ClipData c = i.getClipData();
                for (int k = 0; k < c.getItemCount(); k++) {
                    Uri u = c.getItemAt(k).getUri();
                    if (u != null) out.add(u);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** For formats that can't be cleaned losslessly (HEIC, AVIF, GIF, ...): decode, fix rotation, save as JPEG. */
    private static byte[] reencodeAsJpeg(byte[] in) throws Exception {
        int orientation = 1;
        try {
            orientation = new ExifInterface(new ByteArrayInputStream(in))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
        } catch (Throwable ignored) {
        }
        Bitmap bmp = BitmapFactory.decodeByteArray(in, 0, in.length);
        if (bmp == null) throw new IllegalArgumentException("not an image");
        Matrix m = new Matrix();
        switch (orientation) {
            case 2: m.postScale(-1, 1); break;
            case 3: m.postRotate(180); break;
            case 4: m.postScale(1, -1); break;
            case 5: m.postRotate(90); m.postScale(-1, 1); break;
            case 6: m.postRotate(90); break;
            case 7: m.postRotate(270); m.postScale(-1, 1); break;
            case 8: m.postRotate(270); break;
            default: break;
        }
        if (orientation >= 2 && orientation <= 8) {
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (rotated != bmp) bmp.recycle();
            bmp = rotated;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out); // Bitmap.compress writes no EXIF/GPS
        bmp.recycle();
        return out.toByteArray();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) throw new IllegalArgumentException("no stream");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IllegalArgumentException("too large");
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static String join(Set<String> s) {
        StringBuilder b = new StringBuilder();
        for (String x : s) { if (b.length() > 0) b.append(", "); b.append(x); }
        return b.toString();
    }
}
