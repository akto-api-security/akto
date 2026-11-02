package com.akto.dto.testing;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// The MongoDB POJO codec discriminates GenericTestResult subtypes via BSON's "_t" field, but that's
// invisible once these objects are re-serialized to plain JSON over HTTP (e.g. database-abstractor's
// responses) - there's no type marker left for Jackson to key off. MultiExecTestResult is the only
// subtype with an "executionOrder" field (its getter never returns null, so it's always present),
// so its presence is a reliable structural discriminator; everything else is a TestResult.
public class GenericTestResultDeserializer extends JsonDeserializer<GenericTestResult> {

    @Override
    public GenericTestResult deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode node = mapper.readTree(parser);
        if (node.has("executionOrder")) {
            return mapper.treeToValue(node, MultiExecTestResult.class);
        }
        return mapper.treeToValue(node, TestResult.class);
    }
}
