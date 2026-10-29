package android.app;
import java.util.*;
public class NotificationManager {
  public static final int IMPORTANCE_HIGH = 4;
  public final Map<String, NotificationChannel> channels = new HashMap<>();
  public final List<Object[]> posted = new ArrayList<>();
  public NotificationChannel getNotificationChannel(String id) { return channels.get(id); }
  public void createNotificationChannel(NotificationChannel c) { channels.put(c.id, c); }
  public void notify(int id, Notification n) { posted.add(new Object[]{id, n}); }
}
