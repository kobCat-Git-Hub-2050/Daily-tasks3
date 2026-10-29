package android.content;
public abstract class Context {
  public static final int MODE_PRIVATE = 0;
  public static final String NOTIFICATION_SERVICE = "notification", ALARM_SERVICE = "alarm", POWER_SERVICE = "power";
  public abstract Object getSystemService(String name);
  public abstract SharedPreferences getSharedPreferences(String name, int mode);
  public abstract Context getApplicationContext();
}
