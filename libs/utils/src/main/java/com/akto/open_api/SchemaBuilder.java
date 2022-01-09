package com.akto.open_api;

import com.akto.dto.type.SingleTypeInfo;
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
        if (schemaClass == EmailSchema.class) {
            return new EmailSchema();
        } else if (schemaClass == StringSchema.class) {
            return new StringSchema();
        } else if (schemaClass == NumberSchema.class) {
            return new NumberSchema();
        } else if (schemaClass == IntegerSchema.class) {
            return new IntegerSchema();
        } else if (schemaClass == ObjectSchema.class) {
            return new ObjectSchema();
        } else if (schemaClass == ArraySchema.class) {
            return new ArraySchema();
        } else if (schemaClass == BooleanSchema.class) {
            return new BooleanSchema();
        } else if (schemaClass == MapSchema.class) {
            return new MapSchema();
        } else
            throw new Exception("Invalid schema class " + schemaClass);
    }

    public static class CustomSchema {
        public Class type;
        public String name;

        public CustomSchema(Class type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + "-" + type;
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
                customSchemas.add(customSchemaFromSubType(singleTypeInfo.getSubType(),name));
                break;
            }
            if (!Objects.equals(x, "$")) {
                if (Objects.equals(paramList[idx + 1], "$")) {
                    customSchemas.add(new CustomSchema(ArraySchema.class, x));
                    if (idx != paramList.length -2) {
                        customSchemas.add(new CustomSchema(ObjectSchema.class, null));
                    }
                } else {
                    customSchemas.add(new CustomSchema(ObjectSchema.class, x));
                }
            }

            idx += 1;
        }


        return customSchemas;
    }

    public static CustomSchema customSchemaFromSubType(SingleTypeInfo.SubType subType, String name) throws Exception {
        Class<?> type;
        switch (subType) {
            case TRUE:
            case FALSE:
                type = BooleanSchema.class;
                break;
            case INTEGER_32:
            case INTEGER_64:
                type = IntegerSchema.class;
                break;
            case FLOAT:
                type = NumberSchema.class;
                break;
            case NULL: // TODO:
            case OTHER:
                type = StringSchema.class;
                break;
            case EMAIL:
                type = EmailSchema.class;
                break;
            case URL:
                type = StringSchema.class;
                break;
            case ADDRESS:
                type = StringSchema.class;
                break;
            case SSN:
                type = StringSchema.class;
                break;
            case CREDIT_CARD:
                type = StringSchema.class;
                break;
            case PHONE_NUMBER:
                type = StringSchema.class;
                break;
            case UUID:
                type = StringSchema.class;
                break;
            case GENERIC:
                type = StringSchema.class;
                break;
            case DICT:
                type = MapSchema.class;
                break;
            default:
                throw new Exception("Invalid subtype");
        }

        return new CustomSchema(type,name);
    }

}
