package com.akto.dto.runtime_filters;

import com.akto.dto.CustomFilter;
import com.akto.dto.HttpResponseParams;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.*;

public class FieldExistsFilter extends CustomFilter {
    private String fieldName;

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonFactory factory = mapper.getFactory();

    public FieldExistsFilter() {
        super();
    }

    public FieldExistsFilter(String fieldName) {
        super();
        this.fieldName = fieldName;
    }


    @Override
    public boolean process(HttpResponseParams httpResponseParams) {
        JsonParser jp = null;
        JsonNode node;
        try {
            jp = factory.createParser(httpResponseParams.getPayload());
            node = mapper.readTree(jp);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return findField(node, this.fieldName);
    }

    public static boolean findField(JsonNode node, String matchFieldName) {
        if (node == null) return false;

        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                boolean result = findField(arrayElement, matchFieldName);
                if (result) return true;
            }
        } else {
            Iterator<String> fieldNames = node.fieldNames();
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (Objects.equals(fieldName, matchFieldName)) return true;
                JsonNode fieldValue = node.get(fieldName);
                boolean result= findField(fieldValue, matchFieldName);
                if (result) return true;
            }
        }


        return false;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
}
