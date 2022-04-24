package com.akto.dto.type;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestSingleTypeInfo {

    @Test
    public void testSubTypeIsSensitive() {
        boolean result = SingleTypeInfo.EMAIL.isSensitive(null);
        assertTrue(result);
        result = SingleTypeInfo.EMAIL.isSensitive(SingleTypeInfo.Position.RESPONSE_PAYLOAD);
        assertTrue(result);

        result = SingleTypeInfo.GENERIC.isSensitive(null);
        assertFalse(result);
        result = SingleTypeInfo.GENERIC.isSensitive(SingleTypeInfo.Position.RESPONSE_PAYLOAD);
        assertFalse(result);


        result = SingleTypeInfo.JWT.isSensitive(SingleTypeInfo.Position.REQUEST_HEADER);
        assertFalse(result);
        result = SingleTypeInfo.EMAIL.isSensitive(SingleTypeInfo.Position.RESPONSE_PAYLOAD);
        assertTrue(result);

    }

    @Test
    public void testFindSubTypeFunction() {
        SingleTypeInfo.Position position = SingleTypeInfo.findPosition(200, false);
        assertEquals(SingleTypeInfo.Position.RESPONSE_PAYLOAD, position);

        position = SingleTypeInfo.findPosition(200,true);
        assertEquals(SingleTypeInfo.Position.RESPONSE_HEADER, position);

        position = SingleTypeInfo.findPosition(-1, false);
        assertEquals(SingleTypeInfo.Position.REQUEST_PAYLOAD, position);

        position = SingleTypeInfo.findPosition(-1,true);
        assertEquals(SingleTypeInfo.Position.REQUEST_HEADER, position);
    }



}
