// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.content;
public abstract class Context {
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public static final int MODE_PRIVATE = 0;
    public Object getSystemService(String name) { throw new RuntimeException("stub"); }
    public void startActivity(Intent intent) { throw new RuntimeException("stub"); }
    public ContentResolver getContentResolver() { throw new RuntimeException("stub"); }
    public java.io.File getCacheDir() { throw new RuntimeException("stub"); }
    public android.content.pm.PackageManager getPackageManager() { throw new RuntimeException("stub"); }
    public SharedPreferences getSharedPreferences(String name, int mode) { throw new RuntimeException("stub"); }
}
