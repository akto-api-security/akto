package com.akto.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.codec.RedisCodec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class TypeValueCode<V> implements RedisCodec<String, V> {
    private final Charset charset = StandardCharsets.UTF_8;
    private final ObjectMapper mapper = new ObjectMapper();

    public String decodeKey(ByteBuffer bytes) {
        return charset.decode(bytes).toString();
    }

    public V decodeValue(ByteBuffer byteBuffer) {
        try {
            // Generate byte array from ByteBuffer
            String jsonString = charset.decode(byteBuffer).toString();
            return this.mapper.readValue(jsonString, new TypeReference<V>() {
            });
        } catch (Exception e) {
            System.out.println("Error decoding value: " + e);
            return null;
        }
    }

    public ByteBuffer encodeKey(String key) {
        return ByteBuffer.wrap(key.getBytes(charset));
    }

    public ByteBuffer encodeValue(V value) {
        try {
            byte[] bytes = this.mapper.writeValueAsBytes(value);
            return ByteBuffer.wrap(bytes);
        } catch (IOException e) {
            return null;
        }
    }
}