package com.akto.threat.detection.cache;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.lettuce.core.codec.RedisCodec;

public class ByteArrayCodec implements RedisCodec<String, byte[]>{

  @Override
    public String decodeKey(ByteBuffer bytes) {
        return StandardCharsets.UTF_8.decode(bytes).toString();
    }

    @Override
    public byte[] decodeValue(ByteBuffer bytes) {
        byte[] array = new byte[bytes.remaining()];
        bytes.get(array);
        return array;
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        return StandardCharsets.UTF_8.encode(key);
    }

    @Override
    public ByteBuffer encodeValue(byte[] value) {
        return ByteBuffer.wrap(value);
    }
}
