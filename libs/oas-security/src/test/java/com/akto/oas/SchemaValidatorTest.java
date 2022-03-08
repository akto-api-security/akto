package com.akto.oas;

import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.Assertions;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.*;

import com.akto.oas.Issue;
import com.akto.oas.PathComponent;
import com.akto.oas.SchemaType;
import com.akto.oas.SchemaValidator;

import static org.junit.jupiter.api.Assertions.*;

class SchemaValidatorTest {

    @Test
    public void testNullSchema() {
        List<String> path = new ArrayList<>();
        List<Issue> issues =  SchemaValidator.validate(null, path);
        Assertions.assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getMessage(), Issue.generateNoSchemaIssue(path).getMessage());
        assertEquals(issues.get(0).getPath(), path);
    }

    @Test
    public void testWithRef() {
        // any field other $ref not allowed
        StringSchema schema = new StringSchema();
        schema.set$ref("#/components/schemas/User");
        schema.setType("string");
        schema.setPattern("*asd");

        List<Issue> issues = SchemaValidator.validate(schema, new ArrayList<>());
        assertEquals(issues.size(), 2);
        for (Issue i: issues){
            assertEquals(i.getPath().size(),1);
        }
    }

    @Test
    public void testNullType() {
        // type error will be raised if specifically not mentioned that it's composed schema
        Schema<?> schema = new Schema();
        List<Issue> issues = SchemaValidator.validate(schema, new ArrayList<>());
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath().size(), 0);
    }

    @Test
    public void testNullTypeInComposedSchema() {
        // type error won't be raised for composed schema
        ComposedSchema schema = new ComposedSchema();
        schema.setAnyOf(Arrays.asList(null,null));
        List<Issue> issues = SchemaValidator.validate(schema, new ArrayList<>());
        assertEquals(issues.size(), 2);
        assertEquals(issues.get(0).getPath(), Arrays.asList(PathComponent.ANY_OF, "0"));
        assertEquals(issues.get(1).getPath(), Arrays.asList(PathComponent.ANY_OF, "1"));
    }

    public StringSchema getValidStringSchema() {
        StringSchema schema = new StringSchema();
        schema.setPattern("^[a-zA-Z]+$");
        schema.setFormat("email");
        schema.setMaxLength(18);
        return schema;
    }

    @Test
    public void testValidStringSchema() {
        List<Issue> issues = SchemaValidator.validate(getValidStringSchema(), new ArrayList<>());
        assertEquals(issues.size(), 0);
    }

    public IntegerSchema getValidIntegerSchema() {
        IntegerSchema schema = new IntegerSchema();
        schema.setFormat("int32");
        schema.setMinimum(new BigDecimal(0));
        schema.setMaximum(new BigDecimal(10000));
        return schema;
    }

    @Test
    public void testValidIntegerSchema() {
        List<Issue> issues = SchemaValidator.validate(getValidIntegerSchema(), new ArrayList<>());
        assertEquals(issues.size(), 0);
    }

    @Test
    public void testArraySchemaItemsNull() {
        ArraySchema schema = new ArraySchema();
        schema.setItems(null);
        schema.setMaxItems(4);
        List<Issue> issues = SchemaValidator.validate(schema, new ArrayList<>());
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), Collections.singletonList(PathComponent.ITEMS));
    }

    public ArraySchema getValidArraySchema() {
        ArraySchema schema = new ArraySchema();
        schema.setItems(getValidStringSchema());
        schema.setMaxItems(4);
        return schema;
    }

    @Test
    public void testArraySchemaItemsValidItems() {
        List<Issue> issues = SchemaValidator.validate(getValidArraySchema(), new ArrayList<>());
        assertEquals(issues.size(), 0);
    }

    @Test
    public void testInvalidIntegerFormat() {
        List<String> path = new ArrayList<>();
        IntegerSchema schema = getValidIntegerSchema();
        schema.setFormat("asdf");
        List<Issue> issues = SchemaValidator.validate(schema, path);
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), Collections.singletonList(PathComponent.FORMAT));
        assertEquals(issues.get(0).getMessage(), Issue.generateUnknownIntegerFormat(path).getMessage());
    }

    @Test
    public void testMaximumString() {
        List<String> path = new ArrayList<>();
        StringSchema schema = getValidStringSchema();
        schema.setMaximum(new BigDecimal(23));
        List<Issue> issues = SchemaValidator.validate(schema, path);
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), Collections.singletonList(PathComponent.MAXIMUM));
        assertEquals(issues.get(0).getMessage(), Issue.generateMaxMinNotRequiredIssue("maximum", SchemaType.STRING, path).getMessage());
    }

    @Test
    public void testMaximumInteger() {
        List<String> path = new ArrayList<>();
        IntegerSchema schema = getValidIntegerSchema();
        schema.setMaximum(null);
        List<Issue> issues = SchemaValidator.validate(schema, path);
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), path);
        assertEquals(issues.get(0).getMessage(), Issue.generateMaxMinRequiredIssue(SchemaType.INTEGER, "maximum", path).getMessage());
    }

    @Test
    public void testMaximumLengthString() {
        List<String> path = new ArrayList<>();
        StringSchema schema = getValidStringSchema();
        schema.setMaxLength(null);
        List<Issue> issues = SchemaValidator.validate(schema, new ArrayList<>());
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), path);
        assertEquals(issues.get(0).getMessage(), Issue.generateSchemaMaxLengthIssue(path).getMessage());
    }

    @Test
    public void testMaximumLengthInteger() {
        List<String> path = new ArrayList<>();
        IntegerSchema schema = getValidIntegerSchema();
        schema.setMaxLength(23);
        List<Issue> issues = SchemaValidator.validate(schema, path);
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), Collections.singletonList(PathComponent.MAX_LENGTH));
        assertEquals(issues.get(0).getMessage(), Issue.generateSchemaMaxLengthNotDefinedIssue(SchemaType.INTEGER,path).getMessage());
    }

    @Test
    public void testValidateNullPattern() {
        StringSchema schema = getValidStringSchema();
        schema.setPattern(null);
        List<Issue> issues = SchemaValidator.validate(schema, new ArrayList<>());
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), new ArrayList<>());
        assertEquals(issues.get(0).getMessage(), "String schema need pattern field");
    }

    @Test
    public void testValidateIntegerPattern() {
        IntegerSchema schema = getValidIntegerSchema();
        schema.setPattern("adfs");
        List<Issue> issues = SchemaValidator.validate(schema, new ArrayList<>());
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), Collections.singletonList(PathComponent.PATTERN));
        assertEquals(issues.get(0).getMessage(), "Pattern is not defined for integer");
    }

    @Test
    public void testValidateArrayMaxItemsNull() {
        ArraySchema schema = getValidArraySchema();
        schema.setMaxItems(null);
        List<Issue> issues = SchemaValidator.validate(schema, new ArrayList<>());
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath().size(), 0);
        assertEquals(issues.get(0).getMessage(), "Array schema need max items field");
    }

    @Test
    public void testValidateStringMaxItemsNull() {
        StringSchema schema = getValidStringSchema();
        schema.setMaxItems(23);
        List<Issue> issues = SchemaValidator.validate(schema, new ArrayList<>());
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), Collections.singletonList(PathComponent.MAX_ITEMS));
        assertEquals(issues.get(0).getMessage(), "maxItems is not defined for string");
    }

    @Test
    public void testValidateNullPropertiesMap() {
        List<Issue> issues = SchemaValidator.validateProperties(null,new ArrayList<>(),SchemaType.OBJECT);
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath().size(), 0);
        assertEquals(issues.get(0).getMessage(), "Schema of a JSON object has no properties defined");
    }

    @Test
    public void testValidatePropertiesForStringSchema() {
        Map<String, Schema<?>> propertiesMap = new HashMap<>();
        propertiesMap.put("something", getValidStringSchema());
        List<Issue> issues = SchemaValidator.validateProperties(propertiesMap,new ArrayList<>(),SchemaType.STRING);
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getPath(), Collections.singletonList(PathComponent.PROPERTIES));
        assertEquals(issues.get(0).getMessage(), "Properties is not defined for string");
    }

    @Test
    public void testValidatePropertiesWithIssues() {
        Map<String, Schema<?>> propertiesMap = new HashMap<>();
        StringSchema stringSchema = getValidStringSchema();
        stringSchema.setMaxLength(null);
        propertiesMap.put("string_prop", stringSchema);

        IntegerSchema integerSchema = getValidIntegerSchema();
        integerSchema.setMaximum(null);
        propertiesMap.put("integer_prop", integerSchema);

        List<Issue> issues = SchemaValidator.validateProperties(propertiesMap,new ArrayList<>(),SchemaType.OBJECT);
        assertEquals(issues.size(), 2);
        Map<String, List<String>> expectedIssues = new HashMap<>();
        expectedIssues.put("String schema needs maxLength field", Arrays.asList(PathComponent.PROPERTIES, "string_prop"));
        expectedIssues.put("integer needs maximum", Arrays.asList(PathComponent.PROPERTIES, "integer_prop"));
        for (Issue issue: issues) {
            List<String> p = expectedIssues.get(issue.getMessage());
            if (p != null) {
                assertEquals(issue.getPath(), p);
                expectedIssues.remove(issue.getMessage());
            }
        }
        assertEquals(expectedIssues.size(),0);
    }

}