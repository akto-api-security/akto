package com.akto.threat.protection;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.akto.DaoInit;
import com.akto.threat.protection.db.DBService;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;

public class BackendServer {
    private final int port;
    private final Server server;

    public BackendServer(int port) {
        this.port = port;

        MongoClient mongoClient = DaoInit.createMongoClient(
                new ConnectionString(System.getenv("AKTO_THREAT_PROTECTION_MONGO")),
                ReadPreference.secondary());

        DBService dbService = new DBService(mongoClient);

        this.server = ServerBuilder.forPort(port).addService(new ConsumerMaliciousEventService(dbService)).build();
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
                                    System.err.println(
                                            "*** shutting down gRPC server since JVM is shutting down");
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
