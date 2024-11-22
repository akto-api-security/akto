package com.akto.threat.protection;

import com.akto.DaoInit;
import com.mongodb.ConnectionString;

public class Main {
    public static void main(String[] args) throws Exception {
        String mongoURI = System.getenv("AKTO_THREAT_DETECTION_MONGO_CONN");
        DaoInit.init(new ConnectionString(mongoURI));

        int port = Integer.parseInt(System.getenv().getOrDefault("AKTO_THREAT_PROTECTION_BACKEND_PORT", "8980"));
        BackendServer server = new BackendServer(port);
        server.start();
        server.blockUntilShutdown();
    }
}
