package android.app;
public class NotificationChannel {
  public final String id; public final int importance; public boolean vibration; public boolean hasSound;
  public NotificationChannel(String id, CharSequence name, int importance) { this.id = id; this.importance = importance; }
  public void setDescription(String d) {} public void enableVibration(boolean b) { vibration = b; } public void setVibrationPattern(long[] p) {}
  public void enableLights(boolean b) {} public void setLockscreenVisibility(int v) {}
  public void setSound(android.net.Uri u, android.media.AudioAttributes a) { hasSound = true; }
}
