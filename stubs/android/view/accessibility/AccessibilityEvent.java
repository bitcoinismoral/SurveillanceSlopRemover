// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.view.accessibility;

import java.util.List;

public final class AccessibilityEvent {
    public static final int TYPE_VIEW_CLICKED = 1;
    public static final int TYPE_WINDOW_STATE_CHANGED = 32;
    public static final int TYPE_NOTIFICATION_STATE_CHANGED = 64;
    public static final int TYPE_ANNOUNCEMENT = 16384;
    public int getEventType() { throw new RuntimeException("stub"); }
    public CharSequence getPackageName() { throw new RuntimeException("stub"); }
    public List<CharSequence> getText() { throw new RuntimeException("stub"); }
    public CharSequence getContentDescription() { throw new RuntimeException("stub"); }
}
