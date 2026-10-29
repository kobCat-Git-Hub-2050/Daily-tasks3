package org.json;
import java.util.*;
public class JSONArray {
  private final List<Object> l;
  @SuppressWarnings("unchecked")
  public JSONArray(String s) throws JSONException { Object o = MiniJson.parse(s); if (!(o instanceof List)) throw new JSONException("not array"); l = (List<Object>) o; }
  public int length() { return l.size(); }
  @SuppressWarnings("unchecked")
  public JSONObject optJSONObject(int i) { Object o = i >= 0 && i < l.size() ? l.get(i) : null; return o instanceof Map ? new JSONObject((Map<String,Object>) o) : null; }
  public int optInt(int i, int d) { Object o = i >= 0 && i < l.size() ? l.get(i) : null; return o instanceof Number ? ((Number) o).intValue() : d; }
}
