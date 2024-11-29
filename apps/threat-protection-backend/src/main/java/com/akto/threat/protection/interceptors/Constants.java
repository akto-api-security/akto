package com.akto.threat.protection.interceptors;

import io.grpc.Context;
import io.grpc.Metadata;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class Constants {
  public static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
      Metadata.Key.of("Authorization", ASCII_STRING_MARSHALLER);
  public static final Context.Key<Integer> ACCOUNT_ID_CONTEXT_KEY = Context.key("accountId");

  private Constants() {
    throw new AssertionError();
  }
}
