package com.whale.clipboardsync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON 工具，零外部依赖，避免编译/运行时缺库。 */
public final class Json {
    private Json() {}

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static Object parse(String s) {
        P p = new P(s);
        Object v = p.value();
        p.ws();
        return v;
    }

    private static final class P {
        final String s; int i;
        P(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        char peek() { return i < s.length() ? s.charAt(i) : '\0'; }

        Object value() {
            ws();
            char c = peek();
            if (c == '"') return string();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == 't') { expect("true");  return Boolean.TRUE; }
            if (c == 'f') { expect("false"); return Boolean.FALSE; }
            if (c == 'n') { expect("null");  return null; }
            return number();
        }

        void expect(String w) {
            for (int k = 0; k < w.length(); k++) {
                if (peek() != w.charAt(k)) throw new RuntimeException("bad json literal");
                i++;
            }
        }

        String string() {
            i++; // 跳过 "
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': {
                            int cp = Integer.parseInt(s.substring(i, i + 4), 16);
                            i += 4;
                            sb.append((char) cp);
                            break;
                        }
                        default: throw new RuntimeException("bad json escape");
                    }
                } else sb.append(c);
            }
            throw new RuntimeException("unterminated string");
        }

        Map<String, Object> object() {
            i++; // 跳过 {
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                if (peek() != ':') throw new RuntimeException("expected colon");
                i++;
                m.put(k, value());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
                throw new RuntimeException("expected , or }");
            }
            return m;
        }

        List<Object> array() {
            i++; // 跳过 [
            List<Object> a = new ArrayList<>();
            ws();
            if (peek() == ']') { i++; return a; }
            while (true) {
                a.add(value());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
                throw new RuntimeException("expected , or ]");
            }
            return a;
        }

        Object number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(st, i);
            if (t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0)
                return Double.parseDouble(t);
            return Long.parseLong(t);
        }
    }
}
