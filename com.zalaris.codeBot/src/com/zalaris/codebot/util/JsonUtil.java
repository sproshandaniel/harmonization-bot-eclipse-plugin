package com.zalaris.codebot.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtil {

    private JsonUtil() {
    }

    public static String stringify(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, map);
        return sb.toString();
    }

    public static Object parse(String json) {
        return new Parser(json).parse();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object parsed = parse(json);
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        return new LinkedHashMap<>();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String) {
            writeString(sb, (String) value);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
            return;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                writeString(sb, entry.getKey());
                sb.append(":");
                writeValue(sb, entry.getValue());
            }
            sb.append("}");
            return;
        }
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            sb.append("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                writeValue(sb, item);
            }
            sb.append("]");
            return;
        }
        writeString(sb, String.valueOf(value));
    }

    private static void writeString(StringBuilder sb, String value) {
        sb.append("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            default:
                if (c < 32) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
                break;
            }
        }
        sb.append("\"");
    }

    private static final class Parser {
        private final String text;
        private int pos = 0;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        Object parse() {
            skipWs();
            Object value = parseValue();
            skipWs();
            return value;
        }

        private Object parseValue() {
            skipWs();
            if (pos >= text.length()) {
                return null;
            }
            char c = text.charAt(pos);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (startsWith("true")) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (startsWith("false")) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (startsWith("null")) {
                pos += 4;
                return null;
            }
            return parseNumberOrStringToken();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (pos < text.length()) {
                skipWs();
                String key = parseString();
                skipWs();
                consume(':');
                Object value = parseValue();
                map.put(key, value);
                skipWs();
                if (peek('}')) {
                    pos++;
                    break;
                }
                consume(',');
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (pos < text.length()) {
                Object value = parseValue();
                list.add(value);
                skipWs();
                if (peek(']')) {
                    pos++;
                    break;
                }
                consume(',');
            }
            return list;
        }

        private String parseString() {
            consume('"');
            StringBuilder sb = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && pos < text.length()) {
                    char n = text.charAt(pos++);
                    switch (n) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 <= text.length()) {
                            String hex = text.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                // ignore invalid escape
                            }
                        }
                        break;
                    default:
                        sb.append(n);
                        break;
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object parseNumberOrStringToken() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                pos++;
            }
            String token = text.substring(start, pos);
            try {
                if (token.contains(".") || token.contains("e") || token.contains("E")) {
                    return Double.parseDouble(token);
                }
                return Long.parseLong(token);
            } catch (NumberFormatException ex) {
                return token;
            }
        }

        private void skipWs() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private boolean peek(char ch) {
            return pos < text.length() && text.charAt(pos) == ch;
        }

        private boolean startsWith(String value) {
            return text.regionMatches(pos, value, 0, value.length());
        }

        private void consume(char expected) {
            skipWs();
            if (pos < text.length() && text.charAt(pos) == expected) {
                pos++;
            }
        }
    }
}
