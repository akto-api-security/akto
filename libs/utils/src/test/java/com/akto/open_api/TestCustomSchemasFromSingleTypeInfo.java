package com.akto.open_api;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import io.swagger.v3.oas.models.media.*;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.List;

public class TestCustomSchemasFromSingleTypeInfo {

    public static SingleTypeInfo generateSingleTypeInfo(String param, SingleTypeInfo.SubType subType) {
        SingleTypeInfo.ParamId p = new SingleTypeInfo.ParamId("/api","GET",200,false,param,subType,0, false);
        return new SingleTypeInfo(p,new HashSet<>(),new HashSet<>(),0,0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
    }
    @Test
    public void testSimple() throws Exception {
        List<SchemaBuilder.CustomSchema> customSchemaList;

        SingleTypeInfo s = generateSingleTypeInfo("id", SingleTypeInfo.GENERIC);
        customSchemaList = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(s);
        assertEquals(1, customSchemaList.size());
        assertEquals("id", customSchemaList.get(0).name);
        assertEquals(StringSchema.class, customSchemaList.get(0).type);

    }

    @Test
    public void testSimpleObject() throws Exception {
        List<SchemaBuilder.CustomSchema> customSchemaList;

        SingleTypeInfo s = generateSingleTypeInfo("user#name#first", SingleTypeInfo.GENERIC);
        customSchemaList = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(s);
        assertEquals(3, customSchemaList.size());
        assertEquals("user", customSchemaList.get(0).name);
        assertEquals(ObjectSchema.class, customSchemaList.get(0).type);
        assertEquals("name", customSchemaList.get(1).name);
        assertEquals(ObjectSchema.class, customSchemaList.get(1).type);
        assertEquals("first", customSchemaList.get(2).name);
        assertEquals(StringSchema.class, customSchemaList.get(2).type);
    }

    @Test
    public void testObjectInArray() throws Exception {
        List<SchemaBuilder.CustomSchema> customSchemaList;

        SingleTypeInfo s = generateSingleTypeInfo("cards#$#id", SingleTypeInfo.INTEGER_32);
        customSchemaList = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(s);
        assertEquals(3, customSchemaList.size());
        assertEquals("cards", customSchemaList.get(0).name);
        assertEquals(ArraySchema.class, customSchemaList.get(0).type);
        assertNull(customSchemaList.get(1).name);
        assertEquals(ObjectSchema.class, customSchemaList.get(1).type);
        assertEquals("id", customSchemaList.get(2).name);
        assertEquals(IntegerSchema.class, customSchemaList.get(2).type);
    }

    @Test
    public void testNestedObjectInArray() throws Exception {
        List<SchemaBuilder.CustomSchema> customSchemaList;

        SingleTypeInfo s = generateSingleTypeInfo("cards#$#user#name", SingleTypeInfo.GENERIC);
        customSchemaList = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(s);
        assertEquals(4, customSchemaList.size());
        assertEquals("cards", customSchemaList.get(0).name);
        assertEquals(ArraySchema.class, customSchemaList.get(0).type);
        assertNull(customSchemaList.get(1).name);
        assertEquals(ObjectSchema.class, customSchemaList.get(1).type);
        assertEquals("user", customSchemaList.get(2).name);
        assertEquals(ObjectSchema.class, customSchemaList.get(2).type);
        assertEquals("name", customSchemaList.get(3).name);
        assertEquals(StringSchema.class, customSchemaList.get(3).type);
    }

    @Test
    public void testIntegerArray() throws Exception {
        List<SchemaBuilder.CustomSchema> customSchemaList;

        SingleTypeInfo s = generateSingleTypeInfo("cards#$", SingleTypeInfo.INTEGER_32);
        customSchemaList = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(s);
        assertEquals(2, customSchemaList.size());
        assertEquals("cards", customSchemaList.get(0).name);
        assertEquals(ArraySchema.class, customSchemaList.get(0).type);
        assertNull(customSchemaList.get(1).name);
        assertEquals(IntegerSchema.class, customSchemaList.get(1).type);
    }

    @Test
    public void testBareIntegerArray() throws Exception {
        List<SchemaBuilder.CustomSchema> customSchemaList;

        SingleTypeInfo s = generateSingleTypeInfo("json#$#id", SingleTypeInfo.FLOAT);
        customSchemaList = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(s);
        assertEquals(3, customSchemaList.size());
        assertEquals("json", customSchemaList.get(0).name);
        assertEquals(ArraySchema.class, customSchemaList.get(0).type);
        assertNull(customSchemaList.get(1).name);
        assertEquals(ObjectSchema.class, customSchemaList.get(1).type);
        assertEquals("id", customSchemaList.get(2).name);
        assertEquals(NumberSchema.class, customSchemaList.get(2).type);
    }

    @Test
    public void testArrayInArray() throws Exception {
        List<SchemaBuilder.CustomSchema> customSchemaList;

        SingleTypeInfo s = generateSingleTypeInfo("data#apiInfoList#$#allAuthTypesFound#$#$", SingleTypeInfo.GENERIC);
        customSchemaList = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(s);

        assertEquals(6, customSchemaList.size());

        assertEquals("data", customSchemaList.get(0).name);
        assertEquals(ObjectSchema.class, customSchemaList.get(0).type);

        assertEquals("apiInfoList", customSchemaList.get(1).name);
        assertEquals(ArraySchema.class, customSchemaList.get(1).type);

        assertEquals(ObjectSchema.class, customSchemaList.get(2).type);

        assertEquals("allAuthTypesFound", customSchemaList.get(3).name);
        assertEquals(ArraySchema.class, customSchemaList.get(3).type);

        // assertEquals("allAuthTypesFound", customSchemaList.get(2).name);
        assertEquals(ArraySchema.class, customSchemaList.get(4).type);

        assertEquals(null, customSchemaList.get(5).name);
        assertEquals(StringSchema.class, customSchemaList.get(5).type);
    }

}
