package com.akto.open_api;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;

import io.swagger.v3.oas.models.media.*;

import java.util.*;

public class SchemaBuilder {
    public static Schema<?> build(Schema<?> schema, List<CustomSchema> params) throws Exception {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            properties = new HashMap<>();
        }
        CustomSchema curr = params.remove(0);
        Schema<?> schema1 = getSchema(curr);


        if (params.size() == 0) {
            schema1.setExample(curr.example);
            properties.put(curr.name, schema1);
            if (schema instanceof ObjectSchema) {
                schema.setProperties(properties);
                return schema;
            } else if (schema instanceof ArraySchema) {
                ArraySchema arraySchema = (ArraySchema) schema;
                arraySchema.setItems(schema1);
                return arraySchema;
            }
            throw new Exception();
        }

        Schema<?> child;
        if (schema instanceof ObjectSchema) {
            Schema<?> next = properties.get(curr.name);
            if (next == null) {
                next = schema1;
            }
            child = build(next, params);
        } else if (schema instanceof ArraySchema) {
            ArraySchema arraySchema = (ArraySchema) schema;
            Schema<?> next = arraySchema.getItems();
            if (next == null) {
                next = schema1;
            }
            child = build(next, params);
        } else {
            throw new Exception("Schema instance of : " + schema.getClass());
        }

        if (schema instanceof ObjectSchema) {
            properties.put(curr.name,child);
            schema.setProperties(properties);
            return schema;
        } else if (schema instanceof ArraySchema) {
            ArraySchema arraySchema = (ArraySchema) schema;
            arraySchema.setItems(child);
            return arraySchema;
        }
        throw new Exception();

    }

    public static Schema<?> getSchema(CustomSchema customSchema) throws Exception {
        Class<?> schemaClass = customSchema.type;
        return (Schema<?>) schemaClass.getDeclaredConstructor().newInstance();
    }

    public static class CustomSchema {
        public Class<? extends Schema> type;
        public String name;
        public String example;

        public CustomSchema(Class<? extends Schema> type, String name, String example) {
            this.type = type;
            this.name = name;
            this.example = example;
        }

        @Override
        public String toString() {
            return name + "-" + type + "-" + example;
        }
    }

    // A#$ -> A $
    // A#$#c -> A $ c
    // C -> C
    // B#C -> B C
    public static List<CustomSchema> getCustomSchemasFromSingleTypeInfo(SingleTypeInfo singleTypeInfo) throws Exception {
        String param = singleTypeInfo.getParam();
        List<CustomSchema> customSchemas = new ArrayList<>();
        String[] paramList = param.split("#");
        int idx = 0;
        for (String x: paramList) {
            if (idx == paramList.length-1) {
                String name = x;
                if (Objects.equals(name, "$")) {
                    name = null;
                }
                CappedSet<String> values = singleTypeInfo.getValues();
                String example = values != null && values.count() > 0 ? (String) values.getElements().toArray()[0] : null;
                customSchemas.add(customSchemaFromSubType(singleTypeInfo.getSubType(), name, example));
                break;
            }
            if (!Objects.equals(x, "$")) {
                if (Objects.equals(paramList[idx + 1], "$")) {
                    customSchemas.add(new CustomSchema(ArraySchema.class, x, null));
                } else {
                    customSchemas.add(new CustomSchema(ObjectSchema.class, x, null));
                }
            } else {
                if (Objects.equals(paramList[idx + 1], "$")) {
                    customSchemas.add(new CustomSchema(ArraySchema.class, x, null));
                } else {
                    customSchemas.add(new CustomSchema(ObjectSchema.class, null, null));
                }
            }

            idx += 1;
        }


        return customSchemas;
    }

    public static CustomSchema customSchemaFromSubType(SingleTypeInfo.SubType subType, String name, String example) {
        Class<? extends Schema> type = subType.getSwaggerSchemaClass();

        return new CustomSchema(type,name, example);
    }

}
