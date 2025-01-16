package com.akto.threat.detection.cache;

import io.lettuce.core.codec.RedisCodec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class LongValueCodec implements RedisCodec<String, Long> {

  @Override
  public String decodeKey(ByteBuffer bytes) {
    return StandardCharsets.UTF_8.decode(bytes).toString();
  }

  @Override
  public Long decodeValue(ByteBuffer bytes) {
    String valueAsString = StandardCharsets.UTF_8.decode(bytes).toString();
    return Long.parseLong(valueAsString);
  }

  @Override
  public ByteBuffer encodeKey(String key) {
    return StandardCharsets.UTF_8.encode(key);
  }

  @Override
  public ByteBuffer encodeValue(Long value) {
    String valueAsString = value.toString();
    return StandardCharsets.UTF_8.encode(valueAsString);
  }
}
