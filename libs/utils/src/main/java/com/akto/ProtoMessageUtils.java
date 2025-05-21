package com.akto;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.util.Optional;

public class ProtoMessageUtils {
  public static Optional<String> toString(Message msg) {
    try {
      return Optional.of(JsonFormat.printer().print(msg));
    } catch (Exception e) {
      // Ignore
    }
    return Optional.empty();
  }

  public static <T extends Message> Optional<T> toProtoMessage(Class<T> clz, String msg) {
    try {
      T.Builder builder = (T.Builder) clz.getMethod("newBuilder").invoke(null);
      JsonFormat.parser().merge(msg, builder);
      return Optional.of((T) builder.build());
    } catch (Exception e) {
      // Ignore
    }
    return Optional.empty();
  }
}
