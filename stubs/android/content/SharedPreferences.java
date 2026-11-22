// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.content;
public interface SharedPreferences {
    interface Editor {
        Editor putString(String key, String value);
        void apply();
    }
    String getString(String key, String defValue);
    Editor edit();
}
