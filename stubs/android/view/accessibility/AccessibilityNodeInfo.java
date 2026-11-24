// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.view.accessibility;
public class AccessibilityNodeInfo {
    public static final int ACTION_SET_SELECTION = 0x00020000;
    public static final int ACTION_SET_TEXT = 0x00200000;
    public static final String ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE = "ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE";
    public static final String ACTION_ARGUMENT_SELECTION_START_INT = "ACTION_ARGUMENT_SELECTION_START_INT";
    public static final String ACTION_ARGUMENT_SELECTION_END_INT = "ACTION_ARGUMENT_SELECTION_END_INT";
    public boolean isEditable() { throw new RuntimeException("stub"); }
    public boolean isPassword() { throw new RuntimeException("stub"); }
    public CharSequence getText() { throw new RuntimeException("stub"); }
    public boolean performAction(int action, android.os.Bundle arguments) { throw new RuntimeException("stub"); }
}
