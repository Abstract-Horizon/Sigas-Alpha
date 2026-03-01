package org.ah.sigas.json;

import java.util.Collection;
import java.util.Map;

public class JSON {

    public static byte[] asJSON(Map<String, Object> map) {
        StringBuilder buf = new StringBuilder();
        processMap(buf, map);
        return buf.toString().getBytes();
    }

    private static void processMap(StringBuilder buf, Map<String, Object> map) {
        buf.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (first) {
                first = false;
            } else {
                buf.append(',');
            }
            buf.append('"').append(key).append("\":");
            processObject(buf, value);
        }
        buf.append("}");
    }

    @SuppressWarnings("unused")
    private static void processCollection(StringBuilder buf, Collection<Object> collection) {
        buf.append("{");
        boolean first = true;
        for (Object value : collection) {
            if (first) {
                first = false;
            } else {
                buf.append(',');
            }
            processObject(buf, value);
        }
        buf.append("}");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void processObject(StringBuilder buf, Object value) {
        if (value instanceof String) {
            buf.append('"').append(value).append('"');
        } else if (value instanceof Integer || value instanceof Double || value instanceof Float || value instanceof Boolean) {
            buf.append(value.toString());
        } else if (value instanceof Map) {
            buf.append('{');
            processMap(buf, (Map)value);
            buf.append('}');
        } else if (value instanceof Collection) {
            buf.append('{');
            processMap(buf, (Map)value);
            buf.append('}');
        } else {
            buf.append("null");
        }
    }

}
