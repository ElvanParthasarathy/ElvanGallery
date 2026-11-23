import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

/**
 * A real two-finger pinch from the shell, for recording pinch behaviour on a phone (adb input sends one finger).
 * Build: tools/pinch/build.sh. Run:
 *   adb shell CLASSPATH=/data/local/tmp/pinch.dex app_process / Pinch cx cy fromSpan toSpan ms holdMs [turnDegrees]
 * toSpan < fromSpan pinches in; turnDegrees turns the two fingers about the centre as they move. The shell user may inject input; nothing here is part of the app.
 */
public class Pinch {
    private static Object im;
    private static Method inject;
    private static double turn, at;

    public static void main(String[] a) throws Exception {
        if (a[0].equals("tap2")) { // tap2 x y: a real double tap (two taps 90 ms apart)
            init();
            float x = Float.parseFloat(a[1]), y = Float.parseFloat(a[2]);
            for (int k = 0; k < 2; k++) {
                long d = SystemClock.uptimeMillis();
                send(d, MotionEvent.ACTION_DOWN, x, y, 0, 1);
                SystemClock.sleep(40);
                send(d, MotionEvent.ACTION_UP, x, y, 0, 1);
                if (k == 0) SystemClock.sleep(90);
            }
            return;
        }
        float cx = Float.parseFloat(a[0]), cy = Float.parseFloat(a[1]);
        float from = Float.parseFloat(a[2]), to = Float.parseFloat(a[3]);
        long ms = Long.parseLong(a[4]), hold = a.length > 5 ? Long.parseLong(a[5]) : 0;
        turn = a.length > 6 ? Math.toRadians(Double.parseDouble(a[6])) : 0;
        init();
        long down = SystemClock.uptimeMillis();
        send(down, MotionEvent.ACTION_DOWN, cx, cy, from, 1);
        send(down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), cx, cy, from, 2);
        long steps = Math.max(2, ms / 16);
        for (long i = 1; i <= steps; i++) {
            SystemClock.sleep(16);
            at = turn * i / steps;
            send(down, MotionEvent.ACTION_MOVE, cx, cy, from + (to - from) * i / steps, 2);
        }
        if (hold > 0) SystemClock.sleep(hold);
        send(down, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), cx, cy, to, 2);
        send(down, MotionEvent.ACTION_UP, cx, cy, to, 1);
    }

    private static void init() throws Exception {
        Class<?> c = Class.forName("android.hardware.input.InputManagerGlobal");
        im = c.getMethod("getInstance").invoke(null);
        inject = c.getMethod("injectInputEvent", InputEvent.class, int.class);
    }

    private static void send(long down, int action, float cx, float cy, float span, int n) throws Exception {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
        for (int i = 0; i < n; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            double r = (i == 0 ? -span / 2 : span / 2);
            coords[i].x = (float) (cx - r * Math.sin(at));
            coords[i].y = (float) (cy + r * Math.cos(at));
            coords[i].pressure = 1f;
            coords[i].size = 1f;
        }
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, n, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        inject.invoke(im, e, 2); // wait for the app to handle it
        e.recycle();
    }
}
