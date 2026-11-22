// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.accessibilityservice;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

public abstract class AccessibilityService extends Context {
    public abstract void onAccessibilityEvent(AccessibilityEvent event);
    public abstract void onInterrupt();
    protected void onServiceConnected() { throw new RuntimeException("stub"); }
    public final void setServiceInfo(AccessibilityServiceInfo info) { throw new RuntimeException("stub"); }
    public void onDestroy() { throw new RuntimeException("stub"); }
}
