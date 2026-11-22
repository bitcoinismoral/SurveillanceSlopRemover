// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.view;

import android.content.Context;

public class View {
    public interface OnClickListener {
        void onClick(View v);
    }
    public View(Context context) { throw new RuntimeException("stub"); }
    public void setPadding(int left, int top, int right, int bottom) { throw new RuntimeException("stub"); }
    public void setOnClickListener(OnClickListener l) { throw new RuntimeException("stub"); }
    public void setFitsSystemWindows(boolean fitSystemWindows) { throw new RuntimeException("stub"); }
    public static final int VISIBLE = 0;
    public static final int GONE = 8;
    public void setVisibility(int visibility) { throw new RuntimeException("stub"); }
}
