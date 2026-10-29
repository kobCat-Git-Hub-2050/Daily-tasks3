package android.app;
import java.util.*;
public class AlarmManager {
  public static final int RTC_WAKEUP = 0;
  public final Map<PendingIntent, long[]> alarms = new LinkedHashMap<>();
  public final Map<PendingIntent, String> methods = new LinkedHashMap<>();
  public boolean exactAllowed = true;
  private void put(String m, int t, long at, PendingIntent pi) { alarms.put(pi, new long[]{t, at}); methods.put(pi, m); }
  public void set(int t, long at, PendingIntent pi) { put("set", t, at, pi); }
  public void setExact(int t, long at, PendingIntent pi) { put("setExact", t, at, pi); }
  public void setAndAllowWhileIdle(int t, long at, PendingIntent pi) { put("setAndAllowWhileIdle", t, at, pi); }
  public void setExactAndAllowWhileIdle(int t, long at, PendingIntent pi) { put("setExactAndAllowWhileIdle", t, at, pi); }
  public void cancel(PendingIntent pi) { alarms.remove(pi); methods.remove(pi); }
  public boolean canScheduleExactAlarms() { return exactAllowed; }
}
