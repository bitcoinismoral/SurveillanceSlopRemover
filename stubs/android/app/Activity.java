// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.app;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
public class Activity extends Context {
    public static final int RESULT_OK = -1;
    public Activity() { throw new RuntimeException("stub"); }
    protected void onCreate(Bundle savedInstanceState) { throw new RuntimeException("stub"); }
    protected void onResume() { throw new RuntimeException("stub"); }
    public void onWindowFocusChanged(boolean hasFocus) { throw new RuntimeException("stub"); }
    public void setContentView(View view) { throw new RuntimeException("stub"); }
    public Intent getIntent() { throw new RuntimeException("stub"); }
    public void finish() { throw new RuntimeException("stub"); }
    public final void setResult(int resultCode, Intent data) { throw new RuntimeException("stub"); }
    public final void runOnUiThread(Runnable action) { throw new RuntimeException("stub"); }
    public void startActivityForResult(Intent intent, int requestCode) { throw new RuntimeException("stub"); }
    @Override public Object getSystemService(String name) { throw new RuntimeException("stub"); }
}
