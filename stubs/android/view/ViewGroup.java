// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.view;

import android.content.Context;

public abstract class ViewGroup extends View {
    public static class LayoutParams {
        public LayoutParams(int width, int height) { throw new RuntimeException("stub"); }
    }
    public ViewGroup(Context context) { super(context); }
    public void addView(View child) { throw new RuntimeException("stub"); }
    public void removeAllViews() { throw new RuntimeException("stub"); }
}
