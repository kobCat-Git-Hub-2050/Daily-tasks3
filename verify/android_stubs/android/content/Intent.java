package android.content;
public class Intent {
  public static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
  public static final String ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED";
  public static final String ACTION_TIME_CHANGED = "android.intent.action.TIME_SET";
  public static final String ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED";
  public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000, FLAG_ACTIVITY_CLEAR_TOP = 0x04000000;
  public Class<?> cls; public String action; public int flags;
  public Intent(Context c, Class<?> cls) { this.cls = cls; }
  public Intent(String action) { this.action = action; }
  public Intent setAction(String a) { action = a; return this; }
  public String getAction() { return action; }
  public Intent setFlags(int f) { flags = f; return this; }
}
