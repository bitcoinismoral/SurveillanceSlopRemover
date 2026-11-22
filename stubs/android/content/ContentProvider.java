// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.content;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
public abstract class ContentProvider {
    public abstract boolean onCreate();
    public abstract Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder);
    public abstract String getType(Uri uri);
    public abstract Uri insert(Uri uri, ContentValues values);
    public abstract int delete(Uri uri, String selection, String[] selectionArgs);
    public abstract int update(Uri uri, ContentValues values, String selection, String[] selectionArgs);
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException { throw new RuntimeException("stub"); }
    public final Context getContext() { throw new RuntimeException("stub"); }
}
