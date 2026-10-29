package android.app;
import android.content.*;
import java.util.Objects;
public class PendingIntent {
  public static final int FLAG_UPDATE_CURRENT = 0x08000000, FLAG_IMMUTABLE = 0x04000000;
  public final String kind; public final int req; public final String action; public final Class<?> cls; public final int flags;
  private PendingIntent(String kind, int req, Intent i, int flags) { this.kind = kind; this.req = req; this.action = i.action; this.cls = i.cls; this.flags = flags; }
  public static PendingIntent getBroadcast(Context c, int req, Intent i, int flags) { return new PendingIntent("b", req, i, flags); }
  public static PendingIntent getActivity(Context c, int req, Intent i, int flags) { return new PendingIntent("a", req, i, flags); }
  @Override public boolean equals(Object o) { if (!(o instanceof PendingIntent)) return false; PendingIntent p = (PendingIntent) o; return kind.equals(p.kind) && req == p.req && Objects.equals(action, p.action) && cls == p.cls; }
  @Override public int hashCode() { return Objects.hash(kind, req, action, cls); }
}
