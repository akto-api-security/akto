package com.akto.grpc.auth;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.Optional;
import java.util.concurrent.Executor;

public class AuthToken extends CallCredentials {

  private final String token;
  public static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
      Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER);

  public AuthToken(String token) {
    if (token == null || token.trim().isEmpty()) {
      throw new IllegalArgumentException("Token cannot be null or empty");
    }
    this.token = String.format("Bearer %s", token.trim());
  }

  @Override
  public void applyRequestMetadata(
      RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
    appExecutor.execute(
        () -> {
          try {
            Metadata headers = new Metadata();
            headers.put(AUTHORIZATION_METADATA_KEY, token);
            applier.apply(headers);
          } catch (Throwable e) {
            applier.fail(Status.UNAUTHENTICATED.withCause(e));
          }
        });
  }

  public static Optional<String> getBearerTokenFromMeta(Metadata metadata) {
    String val = metadata.get(AUTHORIZATION_METADATA_KEY);
    if (val == null || val.trim().isEmpty()) {
      return Optional.empty();
    }

    if (val.startsWith("Bearer ")) {
      return Optional.of(val.substring("Bearer ".length()).trim());
    }

    return Optional.empty();
  }
}
