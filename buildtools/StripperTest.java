import io.github.bitcoinismoral.linkcleaner.MetadataStripper;
import java.util.Base64;

/** Build-time check of the photo metadata stripper on an embedded JPEG (GPS, phone model, rotation 8, comment, appended video). */
final class StripperTest {
    static final String JPEG_B64 =
        "/9j/4AAQSkZJRgABAQAAAQABAAD/4QCsRXhpZgAATU0AKgAAAAgAAwEQAAIAAAAMAAAAMgESAAMAAAABAAgAAIglAAQAAAABAAAA" +
        "PgAAAABTRUNSRVRQSE9ORQAABAABAAIAAAACTgAAAAACAAUAAAADAAAAdAADAAIAAAACVwAAAAAEAAUAAAADAAAAjAAAAAAAAAAz" +
        "AAAAAQAAAB4AAAABAAAAAQAAAAEAAAAAAAAAAQAAAAcAAAABAAAAJwAAAAH//gAPU0VDUkVUQ09NTUVOVP/bAEMABgQFBgUEBgYF" +
        "BgcHBggKEAoKCQkKFA4PDBAXFBgYFxQWFhodJR8aGyMcFhYgLCAjJicpKikZHy0wLSgwJSgpKP/bAEMBBwcHCggKEwoKEygaFhoo" +
        "KCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKP/AABEIABAAEAMBIgACEQEDEQH/xAAfAAAB" +
        "BQEBAQEBAQAAAAAAAAAAAQIDBAUGBwgJCgv/xAC1EAACAQMDAgQDBQUEBAAAAX0BAgMABBEFEiExQQYTUWEHInEUMoGRoQgjQrHB" +
        "FVLR8CQzYnKCCQoWFxgZGiUmJygpKjQ1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoOEhYaHiImKkpOUlZaX" +
        "mJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4eLj5OXm5+jp6vHy8/T19vf4+fr/xAAfAQADAQEBAQEBAQEB" +
        "AAAAAAAAAQIDBAUGBwgJCgv/xAC1EQACAQIEBAMEBwUEBAABAncAAQIDEQQFITEGEkFRB2FxEyIygQgUQpGhscEJIzNS8BVictEK" +
        "FiQ04SXxFxgZGiYnKCkqNTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqCg4SFhoeIiYqSk5SVlpeYmZqio6Sl" +
        "pqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2dri4+Tl5ufo6ery8/T19vf4+fr/2gAMAwEAAhEDEQA/ANCiiivnz8uP/9lm" +
        "dHlwbXA0MlNFQ1JFVFZJREVP";

    static int run() {
        int fails = 0;
        byte[] in = Base64.getDecoder().decode(JPEG_B64);
        MetadataStripper.Result r = MetadataStripper.strip(in);
        if (r == null) { System.out.println("FAIL stripper returned null"); return 1; }
        String s = new String(r.data, java.nio.charset.StandardCharsets.ISO_8859_1);
        for (String secret : new String[] {"SECRET", "ftyp"}) if (s.contains(secret)) { fails++; System.out.println("FAIL still contains " + secret); }
        int[] exif = findExif(r.data);
        if (exif == null || exif[0] != 8) { fails++; System.out.println("FAIL rotation not preserved"); }
        if (exif != null && exif[1] != 0) { fails++; System.out.println("FAIL GPS still present"); }
        if (!r.removed.contains("GPS location")) { fails++; System.out.println("FAIL did not report GPS removal"); }
        if ((r.data[r.data.length - 2] & 0xff) != 0xFF || (r.data[r.data.length - 1] & 0xff) != 0xD9) { fails++; System.out.println("FAIL output does not end at EOI"); }
        if (MetadataStripper.strip(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13}) != null) { fails++; System.out.println("FAIL accepted garbage"); }
        System.out.println((fails == 0 ? "photo stripper: all checks passed" : "photo stripper: " + fails + " FAILED"));
        return fails;
    }

    /** {orientation, hasGps} from the first APP1 Exif segment, or null. */
    static int[] findExif(byte[] b) {
        for (int i = 2; i + 4 < b.length; ) {
            int m = b[i + 1] & 0xff;
            int len = ((b[i + 2] & 0xff) << 8) | (b[i + 3] & 0xff);
            if (m == 0xE1 && b[i + 4] == 'E' && b[i + 5] == 'x') {
                int tiff = i + 10; boolean le = b[tiff] == 'I';
                int ifd = tiff + rd(b, tiff + 4, 4, le);
                int n = rd(b, ifd, 2, le), orient = 0, gps = 0;
                for (int k = 0; k < n; k++) {
                    int e = ifd + 2 + 12 * k, tag = rd(b, e, 2, le);
                    if (tag == 0x0112) orient = rd(b, e + 8, 2, le);
                    if (tag == 0x8825) gps = 1;
                }
                return new int[] {orient, gps};
            }
            if (m == 0xDA) break;
            i += 2 + len;
        }
        return null;
    }
    static int rd(byte[] b, int off, int n, boolean le) {
        int v = 0;
        for (int k = 0; k < n; k++) v = le ? v | ((b[off + k] & 0xff) << (8 * k)) : (v << 8) | (b[off + k] & 0xff);
        return v;
    }
}
