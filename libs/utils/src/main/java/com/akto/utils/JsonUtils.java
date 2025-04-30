package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonUtils {

    private static final LoggerMaker logger = new LoggerMaker(JsonUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            logger.error("Error while parsing JSON", e);
            return null;
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            logger.error("Error while parsing JSON", e);
            return null;
        }
    }

    public static Map<String, Object> getMap(String json) {
        return fromJson(json, new TypeReference<Map<String, Object>>() {});
    }
}
