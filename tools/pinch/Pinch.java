import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

/**
 * A real two-finger pinch from the shell, for recording pinch behaviour on a phone (adb input sends one finger).
 * Build: tools/pinch/build.sh. Run:
 *   adb shell CLASSPATH=/data/local/tmp/pinch.dex app_process / Pinch cx cy fromSpan toSpan ms holdMs
 * toSpan < fromSpan pinches in. The shell user may inject input; nothing here is part of the app.
 */
public class Pinch {
    private static Object im;
    private static Method inject;

    public static void main(String[] a) throws Exception {
        float cx = Float.parseFloat(a[0]), cy = Float.parseFloat(a[1]);
        float from = Float.parseFloat(a[2]), to = Float.parseFloat(a[3]);
        long ms = Long.parseLong(a[4]), hold = a.length > 5 ? Long.parseLong(a[5]) : 0;
        Class<?> c = Class.forName("android.hardware.input.InputManagerGlobal");
        im = c.getMethod("getInstance").invoke(null);
        inject = c.getMethod("injectInputEvent", InputEvent.class, int.class);
        long down = SystemClock.uptimeMillis();
        send(down, MotionEvent.ACTION_DOWN, cx, cy, from, 1);
        send(down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), cx, cy, from, 2);
        long steps = Math.max(2, ms / 16);
        for (long i = 1; i <= steps; i++) {
            SystemClock.sleep(16);
            send(down, MotionEvent.ACTION_MOVE, cx, cy, from + (to - from) * i / steps, 2);
        }
        if (hold > 0) SystemClock.sleep(hold);
        send(down, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), cx, cy, to, 2);
        send(down, MotionEvent.ACTION_UP, cx, cy, to, 1);
    }

    private static void send(long down, int action, float cx, float cy, float span, int n) throws Exception {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
        for (int i = 0; i < n; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = cx;
            coords[i].y = cy + (i == 0 ? -span / 2 : span / 2);
            coords[i].pressure = 1f;
            coords[i].size = 1f;
        }
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, n, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        inject.invoke(im, e, 2); // wait for the app to handle it
        e.recycle();
    }
}
