package io.dhia.matcher;

import java.util.*;

/** Minimal JSON parser/serializer (objects, arrays, strings, numbers, booleans, null). No dependencies. */
public final class Json {
    private final String s; private int i;
    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text); p.ws(); Object v = p.value(); p.ws();
        if (p.i != p.s.length()) throw p.err("trailing characters");
        return v;
    }

    private Object value() {
        if (i >= s.length()) throw err("unexpected end");
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> lit("true", Boolean.TRUE);
            case 'f' -> lit("false", Boolean.FALSE);
            case 'n' -> lit("null", null);
            default -> number();
        };
    }
    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>(); i++; ws();
        if (peek('}')) { i++; return m; }
        while (true) {
            ws(); String k = string(); ws(); expect(':'); ws(); m.put(k, value()); ws();
            if (peek(',')) { i++; continue; } expect('}'); return m;
        }
    }
    private List<Object> array() {
        List<Object> l = new ArrayList<>(); i++; ws();
        if (peek(']')) { i++; return l; }
        while (true) { ws(); l.add(value()); ws(); if (peek(',')) { i++; continue; } expect(']'); return l; }
    }
    private String string() {
        expect('"'); StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n'); case 't' -> b.append('\t'); case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b'); case 'f' -> b.append('\f');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> b.append(e);
                }
            } else b.append(c);
        }
        throw err("unterminated string");
    }
    private Object number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw err("unexpected character '" + s.charAt(i) + "'");
        String n = s.substring(st, i);
        return (n.contains(".") || n.contains("e") || n.contains("E")) ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n);
    }
    private Object lit(String w, Object v) { if (!s.startsWith(w, i)) throw err("expected " + w); i += w.length(); return v; }
    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
    private void expect(char c) { if (!peek(c)) throw err("expected '" + c + "'"); i++; }
    private IllegalArgumentException err(String m) { return new IllegalArgumentException("JSON error at " + i + ": " + m); }

    public static String write(Object v) { StringBuilder b = new StringBuilder(); write(v, b); return b.toString(); }
    @SuppressWarnings("unchecked")
    private static void write(Object v, StringBuilder b) {
        if (v == null) b.append("null");
        else if (v instanceof String str) quote(str, b);
        else if (v instanceof Number || v instanceof Boolean) {
            if (v instanceof Double d && (d.isNaN() || d.isInfinite())) b.append("null"); else b.append(v);
        } else if (v instanceof Map<?, ?> m) {
            b.append('{'); boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) { if (!first) b.append(','); first = false; quote(String.valueOf(e.getKey()), b); b.append(':'); write(e.getValue(), b); }
            b.append('}');
        } else if (v instanceof Collection<?> c) {
            b.append('['); boolean first = true;
            for (Object o : c) { if (!first) b.append(','); first = false; write(o, b); }
            b.append(']');
        } else quote(v.toString(), b);
    }
    private static void quote(String s, StringBuilder b) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        b.append('"');
    }
}
