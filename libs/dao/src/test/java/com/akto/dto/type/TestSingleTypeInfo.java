package com.akto.dto.type;

import com.akto.dao.context.Context;
import org.junit.Test;

import java.util.HashSet;

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

    @Test
    public void testSetMinMaxValuesForNonNumbers() {
        SingleTypeInfo singleTypeInfo = generateSTI(SingleTypeInfo.EMAIL);
        assertEquals(SingleTypeInfo.acceptedMinValue, singleTypeInfo.getMaxValue());

        singleTypeInfo.setMinMaxValues(10000000);
        singleTypeInfo.setMinMaxValues(-1000000);
        assertEquals(SingleTypeInfo.acceptedMinValue, singleTypeInfo.getMaxValue());
        assertEquals(SingleTypeInfo.acceptedMaxValue, singleTypeInfo.getMinValue());
    }

    @Test
    public void testSetMinMaxValuesForNumbers() {
        SingleTypeInfo singleTypeInfo = generateSTI(SingleTypeInfo.INTEGER_32);
        assertEquals(SingleTypeInfo.acceptedMinValue, singleTypeInfo.getMaxValue());
        assertEquals(SingleTypeInfo.acceptedMaxValue, singleTypeInfo.getMinValue());

        singleTypeInfo.setMinMaxValues(Long.MAX_VALUE);
        singleTypeInfo.setMinMaxValues(Long.MIN_VALUE);
        assertEquals(SingleTypeInfo.acceptedMaxValue, singleTypeInfo.getMaxValue());
        assertEquals(SingleTypeInfo.acceptedMinValue, singleTypeInfo.getMinValue());

        singleTypeInfo.setMinMaxValues(10000000);
        singleTypeInfo.setMinMaxValues(-1000000);
        assertEquals(SingleTypeInfo.acceptedMaxValue, singleTypeInfo.getMaxValue());
        assertEquals(SingleTypeInfo.acceptedMinValue, singleTypeInfo.getMinValue());

        singleTypeInfo = generateSTI(SingleTypeInfo.INTEGER_32);

        singleTypeInfo.setMinMaxValues(10000000);
        singleTypeInfo.setMinMaxValues(-10000000);
        assertEquals(10000000, singleTypeInfo.getMaxValue());
        assertEquals(-10000000, singleTypeInfo.getMinValue());

        singleTypeInfo.setMinMaxValues(20000000);
        singleTypeInfo.setMinMaxValues(-20000000);
        assertEquals(20000000, singleTypeInfo.getMaxValue());
        assertEquals(-20000000, singleTypeInfo.getMinValue());

        singleTypeInfo.setMinMaxValues(20000);
        singleTypeInfo.setMinMaxValues(-2000);
        assertEquals(20000000, singleTypeInfo.getMaxValue());
        assertEquals(-20000000, singleTypeInfo.getMinValue());
    }

    private SingleTypeInfo generateSTI(SingleTypeInfo.SubType subType) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET", 200, true, "param", subType, 0, false
        );
        return  new SingleTypeInfo(
                paramId, new HashSet<>(), new HashSet<>(), 100, Context.now(), 0
        );
    }



}
