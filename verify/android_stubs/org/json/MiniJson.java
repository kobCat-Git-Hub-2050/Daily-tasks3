package org.json;
import java.util.*;
/** محلل JSON مبسّط للاختبار فقط */
class MiniJson {
  private final String s; private int i = 0;
  MiniJson(String s) { this.s = s; }
  static Object parse(String s) throws JSONException { MiniJson p = new MiniJson(s); p.ws(); Object v = p.val(); p.ws(); if (p.i != s.length()) throw new JSONException("trailing"); return v; }
  void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
  Object val() throws JSONException {
    if (i >= s.length()) throw new JSONException("eof");
    char c = s.charAt(i);
    if (c == '{') { i++; Map<String,Object> m = new LinkedHashMap<>(); ws(); if (s.charAt(i) == '}') { i++; return m; }
      while (true) { ws(); String k = str(); ws(); if (s.charAt(i++) != ':') throw new JSONException(":"); ws(); m.put(k, val()); ws(); char d = s.charAt(i++); if (d == '}') return m; if (d != ',') throw new JSONException(","); } }
    if (c == '[') { i++; List<Object> l = new ArrayList<>(); ws(); if (s.charAt(i) == ']') { i++; return l; }
      while (true) { ws(); l.add(val()); ws(); char d = s.charAt(i++); if (d == ']') return l; if (d != ',') throw new JSONException(","); } }
    if (c == '"') return str();
    if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
    if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
    if (s.startsWith("null", i)) { i += 4; return null; }
    int st = i; while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
    if (st == i) throw new JSONException("bad token at " + i);
    double d = Double.parseDouble(s.substring(st, i)); return d == Math.rint(d) ? (Object) (long) d : (Object) d;
  }
  String str() throws JSONException {
    if (s.charAt(i++) != '"') throw new JSONException("\"");
    StringBuilder b = new StringBuilder();
    while (true) { char c = s.charAt(i++); if (c == '"') return b.toString();
      if (c == '\\') { char e = s.charAt(i++); switch (e) { case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break; case 'b': b.append('\b'); break; case 'f': b.append('\f'); break;
        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break; default: b.append(e); } }
      else b.append(c); }
  }
}
