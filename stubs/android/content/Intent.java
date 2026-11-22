// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.content;
import android.net.Uri;
import android.os.Parcelable;
import java.util.ArrayList;
public class Intent {
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE";
    public static final String ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT";
    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";
    public static final String EXTRA_STREAM = "android.intent.extra.STREAM";
    public static final String EXTRA_PROCESS_TEXT = "android.intent.extra.PROCESS_TEXT";
    public static final String EXTRA_PROCESS_TEXT_READONLY = "android.intent.extra.PROCESS_TEXT_READONLY";
    public static final int FLAG_GRANT_READ_URI_PERMISSION = 1;
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_NO_ANIMATION = 0x00010000;
    public static final int FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS = 0x00800000;
    public Intent() { throw new RuntimeException("stub"); }
    public Intent(String action) { throw new RuntimeException("stub"); }
    public Intent(String action, Uri uri) { throw new RuntimeException("stub"); }
    public Intent(Context packageContext, Class<?> cls) { throw new RuntimeException("stub"); }
    public static Intent createChooser(Intent target, CharSequence title) { throw new RuntimeException("stub"); }
    public String getAction() { throw new RuntimeException("stub"); }
    public Uri getData() { throw new RuntimeException("stub"); }
    public CharSequence getCharSequenceExtra(String name) { throw new RuntimeException("stub"); }
    public boolean getBooleanExtra(String name, boolean defaultValue) { throw new RuntimeException("stub"); }
    public <T extends Parcelable> T getParcelableExtra(String name) { throw new RuntimeException("stub"); }
    public <T extends Parcelable> ArrayList<T> getParcelableArrayListExtra(String name) { throw new RuntimeException("stub"); }
    public ClipData getClipData() { throw new RuntimeException("stub"); }
    public void setClipData(ClipData clip) { throw new RuntimeException("stub"); }
    public Intent putExtra(String name, CharSequence value) { throw new RuntimeException("stub"); }
    public Intent putExtra(String name, boolean value) { throw new RuntimeException("stub"); }
    public Intent putExtra(String name, Parcelable value) { throw new RuntimeException("stub"); }
    public Intent putParcelableArrayListExtra(String name, ArrayList<? extends Parcelable> value) { throw new RuntimeException("stub"); }
    public Intent addFlags(int flags) { throw new RuntimeException("stub"); }
    public Intent addCategory(String category) { throw new RuntimeException("stub"); }
    public Intent setPackage(String packageName) { throw new RuntimeException("stub"); }
    public Intent setType(String type) { throw new RuntimeException("stub"); }
}
