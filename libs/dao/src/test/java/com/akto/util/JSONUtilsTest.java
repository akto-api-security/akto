package com.akto.util;

import com.akto.dto.type.SingleTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import org.bson.types.ObjectId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JSONUtilsTest {

    private static final String ID = "65a1b2c3d4e5f60718293a4b";

    private static final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // shaped like the JSON of the database-abstractor: the ObjectId is serialized as an object, strId is its hex
    private static BasicDBObject abstractorDoc() {
        return new BasicDBObject("strId", ID)
                .append("id", new BasicDBObject("timestamp", 1))
                .append("url", "/api/users")
                .append("method", "GET")
                .append("responseCode", -1)
                .append("isHeader", true)
                .append("param", "host")
                .append("subType", new BasicDBObject("name", "GENERIC").append("sensitiveAlways", false))
                .append("apiCollectionId", 7)
                .append("count", 5)
                .append("lastSeen", 1_700_000_000L)
                .append("somethingNew", "ignored");
    }

    @Test
    public void testParseSingleTypeInfoTakesTheIdFromStrId() throws Exception {
        SingleTypeInfo singleTypeInfo = JSONUtils.parseSingleTypeInfo(abstractorDoc(), objectMapper);

        assertEquals(new ObjectId(ID), singleTypeInfo.getId());
    }

    @Test
    public void testParseSingleTypeInfoLooksTheSubTypeUpByName() throws Exception {
        SingleTypeInfo singleTypeInfo = JSONUtils.parseSingleTypeInfo(abstractorDoc(), objectMapper);

        assertEquals(SingleTypeInfo.GENERIC, singleTypeInfo.getSubType());
    }

    @Test
    public void testParseSingleTypeInfoKeepsTheOtherFieldsAndIgnoresUnknownOnes() throws Exception {
        SingleTypeInfo singleTypeInfo = JSONUtils.parseSingleTypeInfo(abstractorDoc(), objectMapper);

        assertEquals("/api/users", singleTypeInfo.getUrl());
        assertEquals("GET", singleTypeInfo.getMethod());
        assertEquals(-1, singleTypeInfo.getResponseCode());
        assertEquals(true, singleTypeInfo.getIsHeader());
        assertEquals("host", singleTypeInfo.getParam());
        assertEquals(7, singleTypeInfo.getApiCollectionId());
        assertEquals(5, singleTypeInfo.getCount());
        assertEquals(1_700_000_000L, singleTypeInfo.getLastSeen());
    }

    @Test
    public void testParseSingleTypeInfoWithAnUnknownSubTypeLeavesItUnset() throws Exception {
        BasicDBObject doc = abstractorDoc();
        doc.put("subType", new BasicDBObject("name", "NOT_A_SUB_TYPE"));

        assertNull(JSONUtils.parseSingleTypeInfo(doc, objectMapper).getSubType());
    }
}
