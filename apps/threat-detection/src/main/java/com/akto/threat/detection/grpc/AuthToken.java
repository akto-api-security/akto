package com.akto.threat.detection.grpc;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.concurrent.Executor;

public class AuthToken extends CallCredentials {

  private final String token;
  public static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
      Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER);

  public AuthToken(String token) {
    this.token = token;
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
}
