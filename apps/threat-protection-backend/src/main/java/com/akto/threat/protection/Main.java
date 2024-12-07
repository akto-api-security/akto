package com.akto.threat.protection;

import com.akto.DaoInit;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;

public class Main {
  public static void main(String[] args) throws Exception {
    String mongoURI = System.getenv("AKTO_MONGO_CONN");

    DaoInit.init(new ConnectionString(mongoURI));

    MongoClient threatProtectionMongo =
        DaoInit.createMongoClient(
            new ConnectionString(System.getenv("AKTO_THREAT_PROTECTION_MONGO_CONN")),
            ReadPreference.secondary());

    int port =
        Integer.parseInt(
            System.getenv().getOrDefault("AKTO_THREAT_PROTECTION_BACKEND_PORT", "8980"));
    BackendServer server = new BackendServer(port, threatProtectionMongo);
    server.start();
    server.blockUntilShutdown();
  }
}
