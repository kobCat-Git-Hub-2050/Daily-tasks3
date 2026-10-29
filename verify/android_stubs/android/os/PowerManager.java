package android.os;
public class PowerManager {
  public static final int SCREEN_BRIGHT_WAKE_LOCK = 10, ACQUIRE_CAUSES_WAKEUP = 0x10000000, ON_AFTER_RELEASE = 0x20000000;
  public boolean interactive = false; public int acquires = 0;
  public boolean isInteractive() { return interactive; }
  public WakeLock newWakeLock(int flags, String tag) { final boolean wakes = (flags & ACQUIRE_CAUSES_WAKEUP) != 0; return new WakeLock() { @Override public void acquire(long ms) { if (wakes) acquires++; } }; }
  public static class WakeLock { public void acquire(long ms) {} }
}
