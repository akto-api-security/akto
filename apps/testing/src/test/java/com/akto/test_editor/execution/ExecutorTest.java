package com.akto.test_editor.execution;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ExecutorTest {
    // Helper to invoke the private method
    @SuppressWarnings("unchecked")
    private List<BasicDBObject> invokeParseGeneratedKeyValues(Executor executor, BasicDBObject generatedData, String operationType, Object value) {
        try {
            java.lang.reflect.Method method = Executor.class.getDeclaredMethod("parseGeneratedKeyValues", BasicDBObject.class, String.class, Object.class);
            method.setAccessible(true);
            return (List<BasicDBObject>) method.invoke(executor, generatedData, operationType, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testStringValue() {
        Executor executor = new Executor();
        BasicDBObject generatedData = new BasicDBObject();
        generatedData.put("op", "key1");
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "op", "val1");
        assertEquals(1, result.size());
        BasicDBObject dbObject = result.get(0);
        assertTrue(dbObject.containsValue("key1"));
    }

    @Test
    public void testJSONObjectValue() {
        Executor executor = new Executor();
        JSONObject obj = new JSONObject();
        obj.put("k1", "v1");
        obj.put("k2", "v2");
        BasicDBObject generatedData = new BasicDBObject();
        generatedData.put("op", obj);
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "op", "val");
        assertEquals(2, result.size());
        Set<String> keys = new HashSet<String>();
        for (BasicDBObject dbObj : result) {
            keys.addAll(dbObj.keySet());
        }
        assertTrue(keys.contains("k1"));
        assertTrue(keys.contains("k2"));
    }

    @Test
    public void testJSONArrayValue() {
        Executor executor = new Executor();
        JSONArray arr = new JSONArray();
        arr.put("k1");
        JSONObject obj = new JSONObject();
        obj.put("k2", "v2");
        arr.put(obj);
        BasicDBObject generatedData = new BasicDBObject();
        generatedData.put("op", arr);
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "op", "val");
        assertEquals(2, result.size());
        BasicDBObject dbObject1 = result.get(0);
        BasicDBObject dbObject2 = result.get(1);
        assertTrue(dbObject1.containsValue("k1"));
        assertTrue(dbObject2.containsValue("v2"));
    }

    @Test
    public void testUnexpectedType() {
        Executor executor = new Executor();
        BasicDBObject generatedData = new BasicDBObject();
        generatedData.put("op", 12345);
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "op", "val");
        assertEquals(1, result.size());
    }

    @Test
    public void testOperationNotFound() {
        Executor executor = new Executor();
        BasicDBObject generatedData = new BasicDBObject();
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "not_found", "val");
        assertEquals(0, result.size());
    }
}
