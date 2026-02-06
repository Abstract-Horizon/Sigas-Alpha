package org.ah.sigas.json;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class JSONParser {

    @SuppressWarnings("unused")
    public static class ParserError extends Exception {
        private String msg;
        private int ptr;
        private int token;

        public ParserError(int ptr, String msg) {
            super(msg + " at " + ptr);
            this.ptr = ptr;
            this.msg = msg;
        }

        public ParserError(int ptr, String msg, int token) {
            super(msg + " at " + ptr + " got " + tokens.get(token));
            this.ptr = ptr;
            this.msg = msg;
            this.token = token;
        }
    }

    private static int NO_TOKEN = -1;
    private static int TOKEN_EOF = 0;
    private static int TOKEN_COMMA = 1;
    private static int TOKEN_COLON = 2;
    private static int TOKEN_OPEN_CURLY_BRACE = 3;
    private static int TOKEN_CLOSED_CURLY_BRACE = 4;
    private static int TOKEN_OPEN_SQUARE_BRACE = 5;
    private static int TOKEN_CLOSED_SQUARE_BRACE = 6;
    private static int TOKEN_TRUE = 7;
    private static int TOKEN_FALSE = 8;
    private static int TOKEN_NULL = 9;
    private static int TOKEN_STRING = 10;
    private static int TOKEN_INTEGER = 11;
    private static int TOKEN_FLOAT = 12;

    private static Map<Integer, String> tokens = new HashMap<>() {{
        put(NO_TOKEN, "No token");
        put(TOKEN_EOF, "EOF");
        put(TOKEN_COMMA, "','");
        put(TOKEN_COLON, "':'");
        put(TOKEN_OPEN_CURLY_BRACE, "'{'");
        put(TOKEN_CLOSED_CURLY_BRACE, "'}'");
        put(TOKEN_OPEN_SQUARE_BRACE, "'['");
        put(TOKEN_CLOSED_SQUARE_BRACE, "']'");
        put(TOKEN_TRUE, "'true'");
        put(TOKEN_FALSE, "'false'");
        put(TOKEN_NULL, "'null'");
        put(TOKEN_STRING, "string");
        put(TOKEN_INTEGER, "integer");
        put(TOKEN_FLOAT, "float");
    }};

    private String buf;
    private int ptr = 0;
    private int s = 0;

    private StringBuilder tokenValue = new StringBuilder();
    private int prevToken = NO_TOKEN;
    private Object currentObject;
    private Object resultValue;

    public JSONParser(String buf) {
        this.buf = buf;
    }

    public <T> void parse(T obj) throws ParserError {
        currentObject = obj;
        int t = nextToken();
        if (obj instanceof Collection) {
            if (t != TOKEN_OPEN_SQUARE_BRACE) {
                throw new ParserError(ptr, "Expected '[' so result can be added to a collection", t);
            }
            prevToken = t;
            array();
        } else {
            if (t != TOKEN_OPEN_CURLY_BRACE) {
                throw new ParserError(ptr, "Expected '{' so result can be stored in an map or object", t);
            }
            prevToken = t;
            object();
        }
    }

    public void object() throws ParserError {
        int t = nextToken();
        if (t != TOKEN_OPEN_CURLY_BRACE) {
            throw new ParserError(ptr, "Expected '{'", t);
        }

        member();

        t = nextToken();
        while (t == TOKEN_COMMA) {
            member();
            t = nextToken();
        }
        if (t != TOKEN_CLOSED_CURLY_BRACE) {
            throw new ParserError(ptr, "Expected '}'", t);
        }
        resultValue = currentObject;
    }

    public void member() throws ParserError {
        int t = nextToken();
        if (t != TOKEN_STRING) {
            throw new ParserError(ptr, "Expected string", t);
        }
        String propertyName = tokenValue.toString();
        t = nextToken();
        if (t != TOKEN_COLON) {
            throw new ParserError(ptr, "Expected ':'", t);
        }
        value();
        if (currentObject instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>)currentObject;
            ((Map<String, Object>)map).put(propertyName, resultValue);
        } else {
            try {
                Field property = currentObject.getClass().getDeclaredField(propertyName);
                property.setAccessible(true);
                property.set(currentObject, resultValue);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                throw new ParserError(ptr, "Failed setting property '" + propertyName + "' " + e.getMessage());
            }
        }
    }

    public void array() throws ParserError {
        int t = nextToken();
        if (t != TOKEN_OPEN_SQUARE_BRACE) {
            throw new ParserError(ptr, "Expected '['", t);
        }

        ArrayList<Object> list = new ArrayList<Object>();

        value();

        list.add(resultValue);

        t = nextToken();
        while (t == TOKEN_COMMA) {
            value();
            list.add(resultValue);
            t = nextToken();
        }
        if (t != TOKEN_CLOSED_SQUARE_BRACE) {
            throw new ParserError(ptr, "Expected ']'", t);
        }
        resultValue = list;
    }

    public void value() throws ParserError {
        int t = nextToken();
        if (t == TOKEN_OPEN_CURLY_BRACE) {
            prevToken = t;
            object();
        } else if (t == TOKEN_OPEN_SQUARE_BRACE) {
            prevToken = t;
            array();
        } else if (t == TOKEN_STRING) {
            resultValue = tokenValue.toString();
        } else if (t == TOKEN_INTEGER) {
            resultValue = Integer.parseInt(tokenValue.toString());
        } else if (t == TOKEN_FLOAT) {
            resultValue = Double.parseDouble(tokenValue.toString());
        } else if (t == TOKEN_NULL) {
            resultValue = null;
        } else if (t == TOKEN_TRUE) {
            resultValue = Boolean.TRUE;
        } else if (t == TOKEN_FALSE) {
            resultValue = Boolean.FALSE;
        }
    }

    public int nextToken() throws ParserError {
        if (prevToken != NO_TOKEN) {
            int t = prevToken;
            prevToken = NO_TOKEN;
            return t;
        }

        int len = buf.length();
        while (ptr < len) {
            char c = buf.charAt(ptr);
            ptr++;

            if (s == 0) {
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    // skip white space
                } else if (c == ',') {
                    s = 0;
                    return TOKEN_COMMA;
                } else if (c == ':') {
                    s = 0;
                    return TOKEN_COLON;
                } else if (c == '{') {
                    s = 0;
                    return TOKEN_OPEN_CURLY_BRACE;
                } else if (c == '}') {
                    s = 0;
                    return TOKEN_CLOSED_CURLY_BRACE;
                } else if (c == '[') {
                    s = 0;
                    return TOKEN_OPEN_SQUARE_BRACE;
                } else if (c == ']') {
                    s = 0;
                    return TOKEN_CLOSED_SQUARE_BRACE;
                } else if (c == '"') {
                    tokenValue.delete(0,  tokenValue.length());
                    s = 1;
                } else if (Character.isDigit(c)) {
                    s = 10;
                    tokenValue.delete(0, tokenValue.length());
                    tokenValue.append(c);
                } else if (c == 't') {
                    s = 3;
                } else if (c == 'f') {
                    s = 6;
                } else if (c == 'n') {
                    s = 12;
                } else {
                    throw new ParserError(ptr, "Unexpected char");
                }
            } else if (s == 1) {
                if (c == '"') {
                    s = 0;
                    return TOKEN_STRING;
                } else if (c == '\\') {
                    s = 2;
                } else {
                    tokenValue.append(c);
                }
            } else if (s == 2) {
                if (c == '\\') {
                    tokenValue.append(c);
                } else if (c == 't') {
                    tokenValue.append('\t');
                } else if (c == 'n') {
                    tokenValue.append('\n');
                } else if (c == 'r') {
                    tokenValue.append('\r');
                } else if (c == 'b') {
                    tokenValue.append('\b');
                } else if (c == 'f') {
                    tokenValue.append('\f');
                    // TODO add '\u00000
                } else {
                    throw new ParserError(ptr, "Unexpected escape character '" + c + "'");
                }
                s = 1;
            } else if (s == 3) {
                if (c == 'r') {
                    s = 4;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 't" + c + "'");
                }
            } else if (s == 4) {
                if (c == 'u') {
                    s = 5;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 'tr" + c + "'");
                }
            } else if (s == 5) {
                if (c == 'e') {
                    s = 0;
                    return TOKEN_TRUE;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 'tru" + c + "'");
                }
            } else if (s == 6) {
                if (c == 'a') {
                    s = 7;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 'f" + c + "'");
                }
            } else if (s == 7) {
                if (c == 'l') {
                    s = 8;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 'fa" + c + "'");
                }
            } else if (s == 8) {
                if (c == 's') {
                    s = 9;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 'fal" + c + "'");
                }
            } else if (s == 9) {
                if (c == 'e') {
                    s = 0;
                    return TOKEN_FALSE;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 'fals" + c + "'");
                }
            } else if (s == 10) {
                if (Character.isDigit(c)) {
                    tokenValue.append(c);
                } else if (c == '.') {
                    s = 11;
                    tokenValue.append(c);
                } else {
                    ptr--;
                    s = 0;
                    return TOKEN_INTEGER;
                }
            } else if (s == 11) {
                if (Character.isDigit(c)) {
                    tokenValue.append(c);
                } else {
                    ptr--;
                    s = 0;
                    return TOKEN_FLOAT;
                }
            } else if (s == 12) {
                if (c == 'u') {
                    s = 13;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 'n" + c + "'");
                }
            } else if (s == 13) {
                if (c == 'l') {
                    s = 14;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 'nu" + c + "'");
                }
            } else if (s == 14) {
                if (c == 'l') {
                    s = 0;
                    return TOKEN_NULL;
                } else {
                    throw new ParserError(ptr, "Unexpected ident 'nul" + c + "'");
                }
            }
        }
        return TOKEN_EOF;
    }
}
