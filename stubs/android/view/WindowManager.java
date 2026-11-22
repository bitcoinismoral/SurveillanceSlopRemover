// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.view;
public interface WindowManager extends ViewManager {
    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int TYPE_ACCESSIBILITY_OVERLAY = 2032;
        public static final int FLAG_NOT_TOUCHABLE = 0x00000010;
        public static final int FLAG_NOT_TOUCH_MODAL = 0x00000020;
        public static final int FLAG_ALT_FOCUSABLE_IM = 0x00020000;
        public int gravity;
        public LayoutParams(int w, int h, int _type, int _flags, int _format) { super(w, h); }
    }
}
