package com.akto.oas;

import com.akto.oas.Issue.Type;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.media.*;

import java.math.BigDecimal;
import java.util.*;

public class SchemaValidator {
    public static List<Issue> validate(Schema schema, List<String> path) {
        // Reason we are not putting path.add("schema") here is that we will recurse and don't want to print "schema" everytime

        List<Issue> issues = new ArrayList<>();
        if (schema == null) {
            issues.add(Issue.generateNoSchemaIssue(path));
            return issues;
        }

        String type = schema.getType();
        Class<?> schemaClass = schema.getClass();
        boolean refFlag = schema.get$ref()!=null;

        if (refFlag) {
            // if $ref is defined then any other param becomes illegal. We also don't validate any other fields in schema if refFlag true
            issues.addAll(issuesDueToRefFlag(schema, path));
            return issues;
        }

        // composed schemas consist list of possible schemas.
        // Composed schemas come into play when type is null and anyOf, allOf and oneOf are present
        if (schemaClass == ComposedSchema.class) {
            ComposedSchema composedSchema = (ComposedSchema) schema;
            List<Schema> schemasList = new ArrayList<>();
            if (composedSchema.getAnyOf() !=null) {
                path.add(PathComponent.ANY_OF);
                schemasList = composedSchema.getAnyOf();
            } else if (composedSchema.getAllOf() !=null) {
                path.add(PathComponent.ALL_OF);
                schemasList = composedSchema.getAllOf();
            } else if (composedSchema.getOneOf() !=null) {
                path.add(PathComponent.ONE_OF);
                schemasList = composedSchema.getOneOf();
            }
            // TODO: check if all null possible
            int nested = 0;
            for (Schema<?> nestedSchema: schemasList) {
                path.add(nested+"");
                issues.addAll(validate(nestedSchema, path));
                path.remove(path.size()-1);
                nested += 1;
            }
            path.remove(path.size()-1);
        }

        if (type == null && schemaClass != ComposedSchema.class) {
            issues.add(Issue.generateSchemaTypeNeededIssue(path));
            return issues;
        }

        if (type != null) {
            if (schemaClass == ArraySchema.class) {
                path.add(PathComponent.ITEMS);
                ArraySchema arraySchema = (ArraySchema) schema;
                Schema<?> itemSchema = arraySchema.getItems();
                issues.addAll(validate(itemSchema,path));
                path.remove(path.size()-1);
            }

            issues.addAll(validateFormat(schema.getFormat(),path, type));
            issues.addAll(validateMaximumOrMinimum(schema.getMinimum(),path, PathComponent.MINIMUM, type));
            issues.addAll(validateMaximumOrMinimum(schema.getMaximum(),path, PathComponent.MAXIMUM, type));
            issues.addAll(validateMaxLength(schema.getMaxLength(),path, type));
            issues.addAll(validatePattern(schema.getPattern(),path, type));
            issues.addAll(validateMaxItems(schema.getMaxItems(),path, type));
            issues.addAll(validateProperties(schema.getProperties(),path, type));
        }


        return issues;
    }

    private static List<Issue> issuesDueToRefFlag(Schema schema, List<String> path) {
        List<Issue> issues = new ArrayList<>();
        Set<String> blackList = new HashSet<>();
        blackList.add(PathComponent.REF);
        blackList.add(PathComponent.EXAMPLE_SET_FLAG);
        ObjectMapper m = new ObjectMapper();
        Map<String,Object> map = m.convertValue(schema, Map.class);
        for (String field: map.keySet()) {
            if (blackList.contains(field)) continue;
            if (map.get(field) != null) {
                path.add(field);
                issues.add(Issue.generateRefFlagIssue(field, path));
                path.remove(path.size()-1);
            }
        }
        return issues;
    }

    private static List<Issue> validateFormat(String format,List<String> path, String type) {
        List<Issue> issues = new ArrayList<>();
        boolean formatRequired = Arrays.asList(SchemaType.NUMBER, SchemaType.INTEGER).contains(type);

        if (format == null) {
            if (formatRequired) {
                issues.add(Issue.generateSchemaFormatIssue(path));
            }
            return issues;
        }

        path.add(PathComponent.FORMAT);

        if (type.equals(SchemaType.NUMBER) && !Arrays.asList("float", "double").contains(format)) {
            issues.add(Issue.generateUnknownNumberFormat(path));
        }

        if (type.equals(SchemaType.INTEGER) && !Arrays.asList("int32", "int64").contains(format)) {
            issues.add(Issue.generateUnknownIntegerFormat(path));
        }

        path.remove(path.size()-1);
        return issues;
    }

    private static List<Issue> validateMaxLength(Integer maxLength,List<String> path, String type) {
        List<Issue> issues = new ArrayList<>();
        boolean maxLengthAllowed = type.equals(SchemaType.STRING);
        if (maxLength == null) {
            if (maxLengthAllowed) {
                issues.add( Issue.generateSchemaMaxLengthIssue(path));
            }
            return issues;
        }

        path.add(PathComponent.MAX_LENGTH);
        if (!maxLengthAllowed) {
            issues.add(Issue.generateSchemaMaxLengthNotDefinedIssue(type,path));
        }
        path.remove(path.size()-1);
        return issues;
    }

    private static List<Issue> validatePattern(String pattern,List<String> path, String type) {
        // TODO: make sure there are strict pattern
        List<Issue> issues = new ArrayList<>();
        boolean patternAllowed = type.equals(SchemaType.STRING);
        if (pattern == null) {
            if (patternAllowed) {
                issues.add(Issue.generatePatternRequiredIssue(path));
            }
            return issues;
        }

        path.add(PathComponent.PATTERN);
        if (!patternAllowed) {
            issues.add(Issue.generatePatternNotRequiredIssue(type,path));
        }
        path.remove(path.size()-1);
        return issues;
    }

    private static List<Issue> validateMaxItems(Integer maxItems,List<String> path, String type) {
        List<Issue> issues = new ArrayList<>();
        boolean maxItemsAllowed = type.equals(SchemaType.ARRAY);
        if (maxItems ==null) {
            if (maxItemsAllowed) {
                issues.add(Issue.generateMaxItemsRequiredIssue(path));
            }
            return issues;
        }

        path.add(PathComponent.MAX_ITEMS);
        if (!maxItemsAllowed) {
            issues.add(Issue.generateMaxItemsNotRequiredIssue(type, path));
        }
        path.remove(path.size()-1);
        return issues;
    }

    public static List<Issue> validateProperties(Map<String, Schema<?>> propertiesMap, List<String> path, String type){
        List<Issue> issues = new ArrayList<>();
        boolean propertiesAllowed = type.equals(SchemaType.OBJECT);
        if (propertiesMap == null){
            if (propertiesAllowed) {
                issues.add(Issue.generatePropertiesRequired(path));
            }
            return issues;
        }

        path.add(PathComponent.PROPERTIES);

        if (!propertiesAllowed) {
            issues.add(Issue.generatePropertiesNotRequired(type,path));
        }

        for (String propertyName: propertiesMap.keySet()) {
            Schema<?> propertySchema = propertiesMap.get(propertyName);
            path.add(propertyName);
            issues.addAll(validate(propertySchema, path));
            path.remove(path.size()-1);
        }

        path.remove(path.size()-1);
        return issues;
    }

    private static List<Issue> validateMaximumOrMinimum(BigDecimal decimal, List<String> path, String maxOrMin, String type) {
        List<Issue> issues = new ArrayList<>();

        boolean isRequired = Arrays.asList(SchemaType.INTEGER,  SchemaType.NUMBER).contains(type);
        if (decimal == null && isRequired) {
            issues.add(Issue.generateMaxMinRequiredIssue(type, maxOrMin, path));
            return issues;
        }

        path.add(maxOrMin);

        if (decimal != null && !isRequired){
            issues.add(Issue.generateMaxMinNotRequiredIssue(maxOrMin, type, path));
        }

        path.remove(path.size()-1);
        return issues;
    }

}
