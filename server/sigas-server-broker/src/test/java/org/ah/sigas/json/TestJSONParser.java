package org.ah.sigas.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Collection;
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

    @Test public void nestedObjectMapTest() throws ParserError {
        Map<String, Object> res = new HashMap<>();

        JSONParser parser = new JSONParser(
"""
{
  "str": "str",
  "int": 5,
  "float": 1.5,
  "true": true,
  "false": false,
  "null": null,
  "obj": {
    "name": "myname"
  }
}
""");

        parser.parse(res);

        assertEquals("str", res.get("str"));
        assertEquals(5, res.get("int"));
        assertEquals(1.5, res.get("float"));
        assertEquals(true, res.get("true"));
        assertEquals(false, res.get("false"));
        assertNull(res.get("null"));
        assertEquals("myname", ((Map<String, Object>)res.get("obj")).get("name"));
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
