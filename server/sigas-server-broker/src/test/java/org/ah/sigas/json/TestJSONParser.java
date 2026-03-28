package org.ah.sigas.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.ah.sigas.json.JSONParser.ParserError;
import org.junit.Test;

public class TestJSONParser {

    private class Obj {
        private String str;
        private int integer;
        private double flt;
        private boolean true_;
        private boolean false_;
        private Object null_;

        public String getStr() { return str; }
        public int getInteger() { return integer; }
        public double getFlt() { return flt; }
        public boolean getTrue_() { return true_; }
        public boolean getFalse_() { return false_; }
        public Object getNull_() { return null_; }
    }

    @Test public void simpleObjectMapTest() throws ParserError {
        Map<String, Object> res = new HashMap<>();

        JSONParser parser = new JSONParser(
"""
{
  "str": "str",
  "int": 5,
  "float": 1.5,
  "true": true,
  "false": false,
  "null": null
}
""");

        parser.parse(res);

        assertEquals("str", res.get("str"));
        assertEquals(5, res.get("int"));
        assertEquals(1.5, res.get("float"));
        assertEquals(true, res.get("true"));
        assertEquals(false, res.get("false"));
        assertNull(res.get("null"));
    }

    @SuppressWarnings("unchecked")
    @Test public void nestedObjectMapTest() throws ParserError {
        Map<String, Object> res = new HashMap<>();

        JSONParser parser = new JSONParser(
"""
{
  "master_token": "AAAAAAAASe_iF4MA",
  "client_id": "01",
  "alias": "main_alias",
  "float": 1.2,
  "bool_true": true,
  "bool_false": false,
  "options": {
    "min_players": 2,
    "max_players": 4,
    "allow_late_join": false
  }
}
""");

        parser.parse(res);

        assertEquals("AAAAAAAASe_iF4MA", res.get("master_token"));
        assertEquals("01", res.get("client_id"));
        assertEquals("main_alias", res.get("alias"));
        assertEquals(1.2, res.get("float"));
        assertEquals(true, res.get("bool_true"));
        assertEquals(false, res.get("bool_false"));
        assertEquals(7, res.size());

        Map<String, Object> options = (Map<String, Object>)res.get("options");

        assertEquals(2, options.get("min_players"));
        assertEquals(4, options.get("max_players"));
        assertEquals(false, options.get("allow_late_join"));
        assertEquals(3, options.size());
    }

    @Test public void javaObjectMapTest() throws ParserError {
        Obj res = new Obj();

        JSONParser parser = new JSONParser(
"""
{
  "str": "str",
  "integer": 5,
  "flt": 1.5,
  "true_": true,
  "false_": false,
  "null_": null
}
""");

        parser.parse(res);

        assertEquals("str", res.getStr());
        assertEquals(5, res.getInteger());
        assertEquals(1.5, res.getFlt(), 0.0);
        assertEquals(true, res.getTrue_());
        assertEquals(false, res.getFalse_());
        assertNull(res.getNull_());
    }


    @Test public void arrayObjectMapTest() throws ParserError {
        Map<String, Object> res = new HashMap<>();

        JSONParser parser = new JSONParser(
"""
{
  "a": [1, "2", 3],
  "int": 5
}
""");

        parser.parse(res);

        assertEquals(5, res.get("int"));

        @SuppressWarnings("unchecked")
        ArrayList<Object> a = (ArrayList<Object>)res.get("a");
        assertEquals(3, a.size());
        assertEquals(1, a.get(0));
        assertEquals("2", a.get(1));
        assertEquals(3, a.get(2));


    }
}
