package com.akto.threat.protection;

import com.akto.threat.protection.interceptors.AuthenticationInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.MongoClient;

public class BackendServer {
  private final int port;
  private final Server server;

  public BackendServer(int port, MongoClient mongoClient) {
    this.port = port;
    this.server =
        ServerBuilder.forPort(port)
            .addService(new MaliciousAlertService(mongoClient))
            .intercept(new AuthenticationInterceptor())
            .build();
  }

  public void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  public void start() throws IOException {
    server.start();
    System.out.println("Server started, listening on " + port);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.err.println("*** shutting down gRPC server since JVM is shutting down");
                  try {
                    BackendServer.this.stop();
                  } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                  }
                  System.err.println("*** server shut down");
                }));
  }

  public void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }
}
