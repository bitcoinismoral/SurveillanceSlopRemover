// Compile-time stub only (never packaged). The real class is provided by the phone.
package android.os;

public class Build {
    public static class VERSION {
        // Deliberately NOT a compile-time constant, so javac reads the real value at runtime.
        public static final int SDK_INT;
        static { SDK_INT = Integer.parseInt("0"); }
    }
}
