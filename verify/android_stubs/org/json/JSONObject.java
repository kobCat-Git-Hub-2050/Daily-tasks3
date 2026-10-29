package org.json;
import java.util.*;
public class JSONObject {
  private final Map<String,Object> m;
  JSONObject(Map<String,Object> m) { this.m = m; }
  public String optString(String k, String d) { Object o = m.get(k); return o == null ? d : String.valueOf(o); }
  public int optInt(String k, int d) { Object o = m.get(k); return o instanceof Number ? ((Number) o).intValue() : d; }
}
