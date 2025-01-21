package com.akto.utils;


import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.akto.dto.test_editor.Util;

public class TestModifyPayload {
    
    // modifying string in root and nested payload
    @Test
    public void testModifyNestedPayload() {
        BasicDBObject root = new BasicDBObject();
        root.put("name", "aryan@akto.io");
        root.put("details", new BasicDBObject()
                .append("name", "aryan@akto.io")
                .append("address", new BasicDBObject()
                        .append("city", "San Francisco")
                        .append("zip", "94105")
                        .append("name", "CA"))
                .append("hobbies", new BasicDBList() {{
                    add("reading");
                    add("coding");
                }}));

        boolean result = Util.modifyValueInPayload(root, null, "name", "hello-world");
        assertEquals(result, true);
        assertEquals(root.getString("name"), "hello-world");
        BasicDBObject detailsObj = (BasicDBObject) root.get("details");
        BasicDBObject addressObj = (BasicDBObject) detailsObj.get("address");
        assertEquals(detailsObj.getString("name"), "hello-world");
        assertEquals(addressObj.getString("name"), "hello-world");  
    }

    // replace allowed only for same type in object.
    @Test
    public void testReplaceWithTypeChange(){
        BasicDBObject root = new BasicDBObject();
        root.put("name", new BasicDBObject()
                .append("first", "Aryan")
                .append("last", "Gupta"));
        boolean result = Util.modifyValueInPayload(root, null, "name", "hello-world");
        assertEquals(result, false);
    }

    // replace allowed only for same type in list.
    @Test
    public void testModifyNestedPayload2() {
        BasicDBObject root = new BasicDBObject();
        root.put("details", new BasicDBObject()
                .append("name", new BasicDBList() {{
                    add("reading");
                    add("coding");
                }}));

        boolean result = Util.modifyValueInPayload(root, null, "name", Arrays.asList("hello-world"));
        assertEquals(result, true);
        BasicDBObject detailsObj = (BasicDBObject) root.get("details");
        assertEquals(detailsObj.get("name"), Arrays.asList("hello-world"));
    }

    // replace allowed only for same type in list, negative case
    @Test
    public void testModifyNestedPayload3() {
        BasicDBObject root = new BasicDBObject();
        root.put("details", new BasicDBObject()
                .append("name", new BasicDBList() {{
                    add("reading");
                    add("coding");
                }}));
        boolean result = Util.modifyValueInPayload(root, null, "name", "hello-world");
        assertEquals(result, false);
    }
}
