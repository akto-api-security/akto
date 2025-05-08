package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.oas.OpenApi31;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.SpecVersion.VersionFlag;

import java.io.InputStream;
import java.util.Set;

public class ConformSchemaFilter extends DataOperandsImpl {

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;
        Object data = dataOperandFilterRequest.getData();
        Object querySet = dataOperandFilterRequest.getQueryset();

        try {
            System.out.println("data is " + data);
            System.out.println("querySet is " + querySet);

            InputStream inputStream = ConformSchemaFilter.class.getClassLoader().getResourceAsStream("company-schema.json");
            if (inputStream == null) {
                throw new RuntimeException("Schema file not found");
            }
            // Load the JSON schema from the file
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode schemaNode = objectMapper.readTree(inputStream);

            // Convert data and querySet to JSON
            JsonNode dataNode = objectMapper.readTree(data.toString());

            // Create a JsonSchema instance
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V202012,
        builder -> builder.metaSchema(OpenApi31.getInstance())
                .defaultMetaSchemaIri(OpenApi31.getInstance().getIri()));
            JsonSchema schema = factory.getSchema(schemaNode);

            // Validate the data against the schema
            Set<ValidationMessage> validationMessages = schema.validate(dataNode);

            if (validationMessages.isEmpty()) {
                result = true;
            } else {
                for (ValidationMessage message : validationMessages) {
                    System.out.println("Validation error: " + message.getMessage());
                }
            }
        } catch (Exception e) {
            return new ValidationResult(false, TestEditorEnums.DataOperands.EQ.name().toLowerCase() + " validation failed due to an exception: " + e.getMessage());
        }

        return new ValidationResult(result, result ? "Validation succeeded" : TestEditorEnums.DataOperands.EQ.name().toLowerCase() + " validation failed due to schema mismatch");
    }
}