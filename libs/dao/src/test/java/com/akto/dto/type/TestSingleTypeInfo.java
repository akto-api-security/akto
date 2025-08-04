package com.akto.dto.type;

import com.akto.dao.context.Context;
import com.akto.types.CappedSet;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.*;

public class TestSingleTypeInfo {

    @Test
    public void testCopy() {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId("url", "GET", 200, true, "param", SingleTypeInfo.EMAIL, 100, false);
        CappedSet<String> values = new CappedSet<>();
        values.add("ankush");
        values.add("avneesh");
        values.add("ankita");
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 10,0,0,values, SingleTypeInfo.Domain.ANY, 100, 1000);
        singleTypeInfo.setUniqueCount(1000);
        singleTypeInfo.setPublicCount(9999);

        SingleTypeInfo singleTypeInfoCopy = singleTypeInfo.copy();

        assertEquals(singleTypeInfo, singleTypeInfoCopy);
        assertEquals(singleTypeInfo.getValues().getElements().size(), singleTypeInfoCopy.getValues().getElements().size());
        assertEquals(singleTypeInfo.getMinValue(), singleTypeInfoCopy.getMinValue());
        assertEquals(singleTypeInfo.getMaxValue(), singleTypeInfoCopy.getMaxValue());
        assertEquals(singleTypeInfo.getCount(), singleTypeInfoCopy.getCount());
        assertEquals(singleTypeInfo.getDomain(), singleTypeInfoCopy.getDomain());
        assertEquals(singleTypeInfo.getUniqueCount(), singleTypeInfoCopy.getUniqueCount());
        assertEquals(singleTypeInfo.getPublicCount(), singleTypeInfoCopy.getPublicCount());

    }

    @Test
    public void testMerge() {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId("url", "GET", 200, true, "param", SingleTypeInfo.EMAIL, 100, false);
        CappedSet<String> values1 = new CappedSet<>();
        values1.add("ankush");
        values1.add("avneesh");
        SingleTypeInfo singleTypeInfo1 = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 10,0,0,values1, SingleTypeInfo.Domain.ANY, 100, 1000);
        singleTypeInfo1.setUniqueCount(100);
        singleTypeInfo1.setPublicCount(200);

        CappedSet<String> values2 = new CappedSet<>();
        values2.add("ankita");
        SingleTypeInfo singleTypeInfo2 = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 3,0,0,values2, SingleTypeInfo.Domain.ANY, 200, 2000);
        singleTypeInfo2.setUniqueCount(100);
        singleTypeInfo2.setPublicCount(200);

        singleTypeInfo1.merge(singleTypeInfo2);

        assertEquals(singleTypeInfo1.getCount(),13);
        assertEquals(singleTypeInfo1.getValues().getElements().size(),3);
        assertEquals(singleTypeInfo1.getMinValue(), 100);
        assertEquals(singleTypeInfo1.getMaxValue(), 2000);
        assertEquals(singleTypeInfo1.getUniqueCount(), 200);
        assertEquals(singleTypeInfo1.getPublicCount(), 400);
    }

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
        assertEquals(SingleTypeInfo.ACCEPTED_MIN_VALUE, singleTypeInfo.getMaxValue());

        singleTypeInfo.updateMinMaxValues(10000000);
        singleTypeInfo.updateMinMaxValues(-1000000);
        assertEquals(SingleTypeInfo.ACCEPTED_MIN_VALUE, singleTypeInfo.getMaxValue());
        assertEquals(SingleTypeInfo.ACCEPTED_MAX_VALUE, singleTypeInfo.getMinValue());
    }

    @Test
    public void testSetMinMaxValuesForNumbers() {
        SingleTypeInfo singleTypeInfo = generateSTI(SingleTypeInfo.INTEGER_32);
        assertEquals(SingleTypeInfo.ACCEPTED_MIN_VALUE, singleTypeInfo.getMaxValue());
        assertEquals(SingleTypeInfo.ACCEPTED_MAX_VALUE, singleTypeInfo.getMinValue());

        singleTypeInfo.updateMinMaxValues(Long.MAX_VALUE);
        singleTypeInfo.updateMinMaxValues(Long.MIN_VALUE);
        assertEquals(SingleTypeInfo.ACCEPTED_MAX_VALUE, singleTypeInfo.getMaxValue());
        assertEquals(SingleTypeInfo.ACCEPTED_MIN_VALUE, singleTypeInfo.getMinValue());

        singleTypeInfo.updateMinMaxValues(10000000);
        singleTypeInfo.updateMinMaxValues(-1000000);
        assertEquals(SingleTypeInfo.ACCEPTED_MAX_VALUE, singleTypeInfo.getMaxValue());
        assertEquals(SingleTypeInfo.ACCEPTED_MIN_VALUE, singleTypeInfo.getMinValue());

        singleTypeInfo = generateSTI(SingleTypeInfo.INTEGER_32);

        singleTypeInfo.updateMinMaxValues(10000000);
        singleTypeInfo.updateMinMaxValues(-10000000);
        assertEquals(10000000, singleTypeInfo.getMaxValue());
        assertEquals(-10000000, singleTypeInfo.getMinValue());

        singleTypeInfo.updateMinMaxValues(20000000);
        singleTypeInfo.updateMinMaxValues(-20000000);
        assertEquals(20000000, singleTypeInfo.getMaxValue());
        assertEquals(-20000000, singleTypeInfo.getMinValue());

        singleTypeInfo.updateMinMaxValues(20000);
        singleTypeInfo.updateMinMaxValues(-2000);
        assertEquals(20000000, singleTypeInfo.getMaxValue());
        assertEquals(-20000000, singleTypeInfo.getMinValue());
    }

    private SingleTypeInfo generateSTI(SingleTypeInfo.SubType subType) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                "url", "GET", 200, true, "param", subType, 0, false
        );
        return  new SingleTypeInfo(
                paramId, new HashSet<>(), new HashSet<>(), 100, Context.now(), 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE
        );
    }

    @Test
    public void testFindLastKeyFromParam() {
        String result = SingleTypeInfo.findLastKeyFromParam("name");
        assertEquals("name", result);

        result = SingleTypeInfo.findLastKeyFromParam("user#name");
        assertEquals("name", result);

        result = SingleTypeInfo.findLastKeyFromParam("cards#$#name");
        assertEquals("name", result);

        assertNull(SingleTypeInfo.findLastKeyFromParam(null));
    }

    @Test
    public void testDoesNotStartWithSuperType_nullInput() {
        assertTrue(SingleTypeInfo.doesNotStartWithSuperType(null));
    }

    @Test
    public void testDoesNotStartWithSuperType_emptyString() {
        assertTrue(SingleTypeInfo.doesNotStartWithSuperType(""));
    }

    @Test
    public void testDoesNotStartWithSuperType_startsWithSuperType() {
        for (SingleTypeInfo.SuperType superType : SingleTypeInfo.SuperType.values()) {
            String input = superType.name() + "_SOMETHING";
            assertFalse("Failed for input: " + input, SingleTypeInfo.doesNotStartWithSuperType(input));
        }
    }

    @Test
    public void testDoesNotStartWithSuperType_doesNotStartWithSuperType() {
        assertTrue(SingleTypeInfo.doesNotStartWithSuperType("SOMETHING_BOOLEAN"));
        assertTrue(SingleTypeInfo.doesNotStartWithSuperType("randomValue"));
        assertFalse(SingleTypeInfo.doesNotStartWithSuperType("INTEGER32"));
    }

    @Test
    public void testDoesNotStartWithSuperType_exactSuperType() {
        for (SingleTypeInfo.SuperType superType : SingleTypeInfo.SuperType.values()) {
            String input = superType.name();
            assertFalse("Failed for input: " + input, SingleTypeInfo.doesNotStartWithSuperType(input));
        }
    }


}
