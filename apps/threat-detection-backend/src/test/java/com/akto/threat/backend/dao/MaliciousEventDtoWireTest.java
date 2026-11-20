package com.akto.threat.backend.dao;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.MongoClientSettings;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MaliciousEventDtoWireTest {

  private static MaliciousEventDto configEvent() {
    return MaliciousEventDto.newBuilder()
        .setActor("alice")
        .setHost("laptop.claude")
        .setLatestApiEndpoint("/claude/config/skipDangerousModePermissionPrompt")
        .setCategory("MCP")
        .setDetectedAt(1700000000L)
        .setContextSource("ENDPOINT")
        .setEvalType(MaliciousEventDto.EVAL_TYPE_CONFIG)
        .setDedupeKey("laptop.claude|alice|MCP|/claude/config/skipDangerousModePermissionPrompt")
        .build();
  }

  @Test
  void evalTypeAndDedupeKeySurviveTheKafkaHop() throws Exception {
    String payload = new Gson().toJson(configEvent());
    MaliciousEventDto read = new ObjectMapper().readValue(payload, new TypeReference<MaliciousEventDto>() {});
    assertEquals("CONFIG", read.getEvalType());
    assertEquals("laptop.claude|alice|MCP|/claude/config/skipDangerousModePermissionPrompt", read.getDedupeKey());
  }

  @Test
  void untaggedEventsCarryNoEvalTypeOrDedupeKeyOnTheWire() {
    MaliciousEventDto plain = MaliciousEventDto.newBuilder().setActor("bob").setLatestApiEndpoint("/v1/messages").build();
    String payload = new Gson().toJson(plain);
    assertFalse(payload.contains("evalType"));
    assertFalse(payload.contains("dedupeKey"));
  }

  @Test
  void mongoCodecWritesEvalTypeAndDedupeKey() {
    CodecRegistry registry = fromRegistries(
        MongoClientSettings.getDefaultCodecRegistry(),
        fromProviders(PojoCodecProvider.builder().automatic(true).build()));
    BsonDocument encoded = new BsonDocument();
    registry.get(MaliciousEventDto.class).encode(new BsonDocumentWriter(encoded), configEvent(), EncoderContext.builder().build());
    assertEquals("CONFIG", encoded.getString("evalType").getValue());
    assertEquals("laptop.claude|alice|MCP|/claude/config/skipDangerousModePermissionPrompt", encoded.getString("dedupeKey").getValue());
  }
}
