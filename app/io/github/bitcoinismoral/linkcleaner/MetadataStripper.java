package io.github.bitcoinismoral.linkcleaner;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lossless photo metadata removal. The image data itself is copied byte-for-byte, so there is
 * no re-compression and no quality loss.
 *
 *  JPEG: drops EXIF (GPS location, camera/phone model, serial numbers, timestamps, thumbnail),
 *        XMP (incl. Google/Adobe edit history), IPTC/Photoshop, comments, MPF and everything after
 *        the end of the image (embedded Motion Photo video, HDR gain maps). Keeps the colour
 *        profile, and re-adds a minimal EXIF containing ONLY the rotation so photos aren't
 *        shown sideways.
 *  PNG:  drops text chunks (tEXt/zTXt/iTXt, often software/author/creation data), eXIf, tIME and
 *        unknown ancillary chunks. Keeps colour/transparency/animation chunks.
 *  WebP: drops EXIF and XMP chunks and fixes up the header flags and size.
 * GIF: keeps image and playback blocks and removes non-rendering metadata.
 * Other formats return null (the caller re-encodes them instead).
 */
public final class MetadataStripper {

    private MetadataStripper() {}

    public static final class Result {
        public final byte[] data;
        public final String extension;
        public final String mimeType;
        /** Human-readable kinds of metadata that were found and removed (empty = nothing found). */
        public final Set<String> removed;
        Result(byte[] data, String extension, String mimeType, Set<String> removed) {
            this.data = data; this.extension = extension; this.mimeType = mimeType; this.removed = removed;
        }
    }

    public static Result strip(byte[] in) {
        if (in == null || in.length < 12) return null;
        try {
            if (GifStripper.recognizes(in)) return GifStripper.strip(in);
            if ((in[0] & 0xff) == 0xFF && (in[1] & 0xff) == 0xD8) return jpeg(in);
            if (startsWith(in, 0, PNG_SIG)) return png(in);
            if (ascii(in, 0, 4).equals("RIFF") && ascii(in, 8, 4).equals("WEBP")) return webp(in);
        } catch (RuntimeException e) {
            return null; // malformed: let the caller fall back to re-encoding
        }
        return null;
    }

    // ------------------------------------------------------------------ JPEG

    static final String LOCATION = "GPS location";
    static final String CAMERA = "camera & timestamp data (EXIF)";
    static final String XMP = "XMP / edit history";
    static final String IPTC = "IPTC / Photoshop data";
    static final String COMMENT = "comments";
    static final String EMBEDDED = "embedded video / extra images";
    static final String TEXT = "text metadata";

    private static Result jpeg(byte[] b) {
        Set<String> removed = new LinkedHashSet<>();
        int orientation = 1;
        // pass 1: read the rotation (and whether GPS is present) from EXIF
        for (int i = 2; i + 4 <= b.length; ) {
            if ((b[i] & 0xff) != 0xFF) break;
            int m = b[i + 1] & 0xff;
            if (m == 0xFF) { i++; continue; }
            if (m == 0xDA || m == 0xD9) break;
            if (m == 0x01 || (m >= 0xD0 && m <= 0xD7)) { i += 2; continue; }
            int len = u16be(b, i + 2);
            if (m == 0xE1 && startsWith(b, i + 4, "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1))) {
                int[] info = readExif(b, i + 10, i + 2 + len);
                if (info[0] >= 1 && info[0] <= 8) orientation = info[0];
                if (info[1] == 1) removed.add(LOCATION);
            }
            i += 2 + len;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
        out.write(0xFF); out.write(0xD8);
        boolean exifWritten = false, sawEoi = false;
        int i = 2;
        while (i < b.length) {
            if ((b[i] & 0xff) != 0xFF) return null; // not at a marker: malformed
            int m = b[i + 1] & 0xff;
            if (m == 0xFF) { i++; continue; }
            if (m == 0xD9) { out.write(0xFF); out.write(0xD9); sawEoi = true; i += 2; break; }
            if (m == 0x01 || (m >= 0xD0 && m <= 0xD7)) { out.write(0xFF); out.write(m); i += 2; continue; }
            int len = u16be(b, i + 2);
            int end = i + 2 + len;
            if (len < 2 || end > b.length) return null;

            // the minimal rotation-only EXIF goes right after the JFIF header
            if (!exifWritten && m != 0xE0) {
                if (orientation != 1) writeMinimalExif(out, orientation);
                exifWritten = true;
            }

            boolean keep;
            if (m == 0xE0) {
                keep = startsWith(b, i + 4, "JFIF\0".getBytes(StandardCharsets.ISO_8859_1));
                if (!keep) removed.add(EMBEDDED); // JFXX thumbnails etc.
            } else if (m == 0xE1) {
                keep = false;
                if (startsWith(b, i + 4, "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1))) removed.add(CAMERA);
                else removed.add(XMP);
            } else if (m == 0xE2) {
                keep = startsWith(b, i + 4, "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1));
                if (!keep) removed.add(EMBEDDED); // MPF (multi-picture), FlashPix
            } else if (m == 0xEE) {
                keep = startsWith(b, i + 4, "Adobe".getBytes(StandardCharsets.ISO_8859_1)); // colour transform flag
            } else if (m >= 0xE3 && m <= 0xEF) {
                keep = false;
                removed.add(m == 0xED ? IPTC : CAMERA);
            } else if (m == 0xFE) {
                keep = false; removed.add(COMMENT);
            } else {
                keep = true; // DQT, DHT, SOF, SOS, DRI, ...: the image itself
            }
            if (keep) out.write(b, i, end - i);
            i = end;

            if (m == 0xDA) { // start of scan: copy entropy-coded data up to the next real marker
                int j = i;
                while (j + 1 < b.length) {
                    if ((b[j] & 0xff) == 0xFF) {
                        int n = b[j + 1] & 0xff;
                        if (n == 0x00 || (n >= 0xD0 && n <= 0xD7)) { j += 2; continue; }
                        if (n == 0xFF) { j++; continue; }
                        break;
                    }
                    j++;
                }
                out.write(b, i, j - i);
                i = j;
            }
        }
        if (!sawEoi) return null;
        if (i < b.length) removed.add(EMBEDDED); // Motion Photo video / gain map appended after the image
        return new Result(out.toByteArray(), "jpg", "image/jpeg", removed);
    }

    /** Returns {orientation (0 if absent), hasGps (1/0)} from a TIFF/EXIF block. */
    static int[] readExif(byte[] b, int tiff, int limit) {
        int[] res = {0, 0};
        if (tiff + 8 > limit) return res;
        boolean le;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') le = true;
        else if (b[tiff] == 'M' && b[tiff + 1] == 'M') le = false;
        else return res;
        int ifd = tiff + u32(b, tiff + 4, le);
        if (ifd + 2 > limit || ifd < tiff) return res;
        int n = u16(b, ifd, le);
        for (int k = 0; k < n; k++) {
            int e = ifd + 2 + 12 * k;
            if (e + 12 > limit) break;
            int tag = u16(b, e, le);
            if (tag == 0x0112) res[0] = u16(b, e + 8, le);
            if (tag == 0x8825) res[1] = 1; // GPS IFD pointer
        }
        return res;
    }

    /** APP1 "Exif" with a single IFD0 entry: Orientation. Nothing else (34 bytes). */
    static void writeMinimalExif(ByteArrayOutputStream out, int orientation) {
        byte[] seg = {
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // big-endian TIFF header, IFD0 at 8
                0x00, 0x01,                                     // 1 entry
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, // tag 0x0112 SHORT x1
                0x00, (byte) orientation, 0x00, 0x00,          // value
                0x00, 0x00, 0x00, 0x00                          // no next IFD
        };
        out.write(seg, 0, seg.length);
    }

    // ------------------------------------------------------------------ PNG

    private static final byte[] PNG_SIG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final Set<String> PNG_KEEP = new HashSet<>(Arrays.asList(
            "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "bKGD", "pHYs", "acTL", "fcTL", "fdAT",
            "cICP", "mDCV", "mDCv", "cLLI", "cLLi"));

    private static Result png(byte[] b) {
        Set<String> removed = new LinkedHashSet<>();
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
        out.write(PNG_SIG, 0, 8);
        int i = 8;
        boolean sawEnd = false;
        while (i + 12 <= b.length) {
            int len = u32(b, i, false);
            if (len < 0 || i + 12 + len > b.length) return null;
            String type = ascii(b, i + 4, 4);
            boolean critical = Character.isUpperCase(type.charAt(0));
            boolean keep = critical || PNG_KEEP.contains(type);
            if (keep) out.write(b, i, 12 + len);
            else if (type.equals("eXIf")) removed.add(CAMERA);
            else if (type.endsWith("TXt") || type.equals("tEXt")) removed.add(TEXT);
            else if (type.equals("tIME")) removed.add("timestamp");
            else removed.add(TEXT);
            i += 12 + len;
            if (type.equals("IEND")) { sawEnd = true; break; }
        }
        if (!sawEnd) return null;
        if (i < b.length) removed.add(EMBEDDED);
        return new Result(out.toByteArray(), "png", "image/png", removed);
    }

    // ------------------------------------------------------------------ WebP

    private static Result webp(byte[] b) {
        Set<String> removed = new LinkedHashSet<>();
        int riffEnd = Math.min(b.length, 8 + u32(b, 4, true));
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
        out.write(b, 0, 12);
        int i = 12;
        while (i + 8 <= riffEnd) {
            String fourcc = ascii(b, i, 4);
            int size = u32(b, i + 4, true);
            int padded = size + (size & 1);
            if (size < 0 || i + 8 + size > b.length) return null;
            int chunkLen = Math.min(8 + padded, b.length - i);
            if (fourcc.equals("EXIF")) { removed.add(CAMERA); }
            else if (fourcc.equals("XMP ")) { removed.add(XMP); }
            else {
                int start = out.size();
                out.write(b, i, chunkLen);
                if (fourcc.equals("VP8X")) {
                    byte[] cur = out.toByteArray();
                    cur[start + 8] = (byte) (cur[start + 8] & ~0x0C); // clear EXIF + XMP flags
                    out.reset(); out.write(cur, 0, cur.length);
                }
            }
            i += 8 + padded;
        }
        byte[] r = out.toByteArray();
        int riffSize = r.length - 8;
        r[4] = (byte) riffSize; r[5] = (byte) (riffSize >> 8); r[6] = (byte) (riffSize >> 16); r[7] = (byte) (riffSize >> 24);
        return new Result(r, "webp", "image/webp", removed);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean startsWith(byte[] b, int off, byte[] prefix) {
        if (off < 0 || off + prefix.length > b.length) return false;
        for (int k = 0; k < prefix.length; k++) if (b[off + k] != prefix[k]) return false;
        return true;
    }
    private static String ascii(byte[] b, int off, int n) {
        if (off + n > b.length) return "";
        return new String(b, off, n, StandardCharsets.ISO_8859_1);
    }
    private static int u16be(byte[] b, int off) { return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff); }
    private static int u16(byte[] b, int off, boolean le) {
        return le ? ((b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)) : u16be(b, off);
    }
    private static int u32(byte[] b, int off, boolean le) {
        if (le) return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16) | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }
}
