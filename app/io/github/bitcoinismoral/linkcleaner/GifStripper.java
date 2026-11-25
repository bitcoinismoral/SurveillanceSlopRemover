package io.github.bitcoinismoral.linkcleaner;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** Copies the rendering blocks of a GIF without decoding or re-encoding frames. */
final class GifStripper {
    private GifStripper() {}

    static boolean recognizes(byte[] data) {
        if (data == null || data.length < 6) return false;
        String version = ascii(data, 0, 6);
        return version.equals("GIF87a") || version.equals("GIF89a");
    }

    private static String ascii(byte[] data, int start, int length) {
        return new String(data, start, length, StandardCharsets.US_ASCII);
    }

    private static int paletteSize(byte flags) {
        return (flags & 0x80) == 0 ? 0 : 3 * (2 << (flags & 7));
    }

    /** End of a sequence of length-prefixed data blocks, including its zero terminator. */
    private static int blocksEnd(byte[] data, int pos) {
        while (pos < data.length) {
            int size = data[pos++] & 0xff;
            if (size == 0) return pos;
            if (size > data.length - pos) break;
            pos += size;
        }
        throw new IllegalArgumentException("Incomplete GIF block");
    }

    static MetadataStripper.Result strip(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        Set<String> removed = new LinkedHashSet<>();
        int pos = 13 + paletteSize(data[10]);
        out.write(data, 0, pos);
        boolean hasImage = false;
        while (pos < data.length) {
            int start = pos;
            int tag = data[pos++] & 0xff;
            if (tag == 0x3b && hasImage) {
                out.write(tag);
                if (pos < data.length) removed.add("trailing data");
                return new MetadataStripper.Result(out.toByteArray(), "gif", "image/gif", removed);
            }
            if (tag == 0x2c) {
                // Nine descriptor bytes, optional local palette, then LZW code size and data.
                pos += 9 + paletteSize(data[pos + 8]);
                pos = blocksEnd(data, pos + 1);
                out.write(data, start, pos - start);
                hasImage = true;
                continue;
            }
            if (tag != 0x21) return null;
            int extension = data[pos++] & 0xff;
            int end = blocksEnd(data, pos);
            switch (extension) {
                case 0xf9: // Graphic control: disposal, delay, user input and transparency.
                    if (data[pos] != 4 || end != pos + 6) return null;
                    out.write(data, start, end - start);
                    break;
                case 0xfe:
                    removed.add(MetadataStripper.COMMENT);
                    break;
                case 0xff:
                    if (data[pos] != 11) return null;
                    String id = ascii(data, pos + 1, 11);
                    if (id.equals("NETSCAPE2.0") || id.equals("ANIMEXTS1.0")) {
                        if (end != pos + 17 || data[pos + 12] != 3 || data[pos + 13] != 1) return null;
                        out.write(data, start, end - start);
                    } else if (id.equals("ICCRGBG1012")) {
                        return null; // Dropping this profile may change the colours.
                    } else {
                        removed.add(id.equals("XMP DataXMP") ? MetadataStripper.XMP : "application metadata");
                    }
                    break;
                default:
                    return null; // Includes the visible Plain Text Extension.
            }
            pos = end;
        }
        return null;
    }
}
