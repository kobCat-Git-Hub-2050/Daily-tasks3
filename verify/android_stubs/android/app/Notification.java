package android.app;
public class Notification {
  public static final int PRIORITY_HIGH = 1, DEFAULT_ALL = -1, VISIBILITY_PUBLIC = 1;
  public static final String CATEGORY_REMINDER = "reminder";
  public String title, text, channelId, category; public int priority, defaults;
  public static class Style {}
  public static class BigTextStyle extends Style { public BigTextStyle bigText(CharSequence c) { return this; } }
  public static class Builder {
    final Notification n = new Notification();
    public Builder(android.content.Context c) {}
    public Builder(android.content.Context c, String channelId) { n.channelId = channelId; }
    public Builder setPriority(int p) { n.priority = p; return this; }
    public Builder setDefaults(int d) { n.defaults = d; return this; }
    public Builder setSmallIcon(int i) { return this; }
    public Builder setContentTitle(CharSequence c) { n.title = String.valueOf(c); return this; }
    public Builder setContentText(CharSequence c) { n.text = String.valueOf(c); return this; }
    public Builder setStyle(Style s) { return this; }
    public Builder setContentIntent(PendingIntent p) { return this; }
    public Builder setAutoCancel(boolean b) { return this; }
    public Builder setCategory(String c) { n.category = c; return this; }
    public Builder setVisibility(int v) { return this; }
    public Notification build() { return n; }
  }
}
