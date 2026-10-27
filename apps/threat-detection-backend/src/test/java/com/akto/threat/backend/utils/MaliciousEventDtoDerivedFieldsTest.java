package com.akto.threat.backend.utils;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.dto.type.URLMethods;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClientSettings;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

public class MaliciousEventDtoDerivedFieldsTest {

    private static final CodecRegistry REGISTRY = fromRegistries(
        MongoClientSettings.getDefaultCodecRegistry(),
        fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    private static MaliciousEventDto event(String contextSource) {
        MaliciousEventDto event = MaliciousEventDto.newBuilder()
            .setContextSource(contextSource)
            .setFilterId("filter-1")
            .setLatestApiEndpoint("/claude/guardrail/tool-call")
            .setLatestApiMethod(URLMethods.Method.POST)
            .setMetadata("behaviour: \"block\"\n")
            .setStatus(MaliciousEventDto.Status.ACTIVE)
            .build();
        FilterFields.apply(event);
        return event;
    }

    private static BsonDocument encode(MaliciousEventDto dto) {
        BsonDocument doc = new BsonDocument();
        Codec<MaliciousEventDto> codec = REGISTRY.get(MaliciousEventDto.class);
        codec.encode(new BsonDocumentWriter(doc), dto, EncoderContext.builder().build());
        return doc;
    }

    @Test
    public void derivedFieldsAreNeverWrittenToTheKafkaJson() throws Exception {
        String json = new ObjectMapper().writeValueAsString(event("ENDPOINT"));
        assertFalse(json.contains("agenticType"));
        assertFalse(json.contains("endpointKind"));
        assertFalse(json.contains("\"behaviour\""));
        assertTrue(json.contains("filter-1"));
    }

    @Test
    public void jsonThatCarriesTheDerivedKeysStillDeserializes() throws Exception {
        String json = "{\"filterId\":\"f\",\"agenticType\":\"Tool\",\"endpointKind\":\"TOOL\",\"behaviour\":\"block\"}";
        MaliciousEventDto dto = new ObjectMapper().readValue(json, MaliciousEventDto.class);
        assertEquals("f", dto.getFilterId());
        assertNull(dto.getAgenticType());
    }

    @Test
    public void mongoCodecStoresTheDerivedFields() {
        BsonDocument doc = encode(event("ENDPOINT"));
        assertEquals("Tool", doc.getString("agenticType").getValue());
        assertEquals("TOOL", doc.getString("endpointKind").getValue());
        assertEquals("block", doc.getString("behaviour").getValue());
    }

    @Test
    public void mongoCodecWritesNothingNewForApiSecurityEvents() {
        BsonDocument doc = encode(event("API"));
        assertFalse(doc.containsKey("agenticType"));
        assertFalse(doc.containsKey("endpointKind"));
        assertFalse(doc.containsKey("behaviour"));
        assertEquals("/claude/guardrail/tool-call", doc.getString("latestApiEndpoint").getValue());
        assertEquals("POST", doc.getString("latestApiMethod").getValue());
    }

    @Test
    public void mongoCodecOmitsBehaviourWhenTheEventHasNone() {
        MaliciousEventDto dto = MaliciousEventDto.newBuilder()
            .setContextSource("ENDPOINT")
            .setLatestApiEndpoint("/claude/guardrail")
            .setLatestApiMethod(URLMethods.Method.POST)
            .setMetadata("policy_name: \"P\"\n")
            .setStatus(MaliciousEventDto.Status.ACTIVE)
            .build();
        FilterFields.apply(dto);
        BsonDocument doc = encode(dto);
        assertEquals("Prompt", doc.getString("agenticType").getValue());
        assertFalse(doc.containsKey("behaviour"));
    }

    @Test
    public void mongoCodecReadsOldDocumentsWithoutTheFieldsAndNewOnesWithThem() {
        BsonDocument oldDoc = encode(event("API"));
        MaliciousEventDto oldEvent = REGISTRY.get(MaliciousEventDto.class)
            .decode(new BsonDocumentReader(oldDoc), DecoderContext.builder().build());
        assertNull(oldEvent.getAgenticType());
        assertEquals(URLMethods.Method.POST, oldEvent.getLatestApiMethod());

        BsonDocument newDoc = encode(event("ENDPOINT"));
        MaliciousEventDto newEvent = REGISTRY.get(MaliciousEventDto.class)
            .decode(new BsonDocumentReader(newDoc), DecoderContext.builder().build());
        assertEquals("Tool", newEvent.getAgenticType());
        assertEquals("TOOL", newEvent.getEndpointKind());
        assertEquals("block", newEvent.getBehaviour());
        assertEquals(URLMethods.Method.POST, newEvent.getLatestApiMethod());
    }

    @Test
    public void theHttpMethodFieldStaysTheRealVerbForEveryContext() {
        for (String context : new String[] {"ENDPOINT", "AGENTIC", "MCP", "API", "DAST"}) {
            assertEquals(URLMethods.Method.POST, event(context).getLatestApiMethod(), context);
        }
    }
}
