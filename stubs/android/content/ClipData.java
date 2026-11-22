// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.content;
import android.net.Uri;
public class ClipData {
    public static class Item {
        public Item(Uri uri) { throw new RuntimeException("stub"); }
        public CharSequence coerceToText(Context context) { throw new RuntimeException("stub"); }
        public Uri getUri() { throw new RuntimeException("stub"); }
    }
    public static ClipData newPlainText(CharSequence label, CharSequence text) { throw new RuntimeException("stub"); }
    public static ClipData newRawUri(CharSequence label, Uri uri) { throw new RuntimeException("stub"); }
    public void addItem(Item item) { throw new RuntimeException("stub"); }
    public int getItemCount() { throw new RuntimeException("stub"); }
    public Item getItemAt(int index) { throw new RuntimeException("stub"); }
}
