package com.akto.utility;

/**
 * Standalone entry point for akto-utility-server.
 * Set UTILITY_SERVER_PORT (e.g. 8081) and run this class to start the server as a separate process.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            UtilityServer.stop();
        }));
        UtilityServer.start();
        if (!UtilityServer.isStarted()) {
            System.err.println("akto-utility-server did not start. Set UTILITY_SERVER_PORT to a valid port (e.g. 8081).");
            System.exit(1);
        }
        Thread.currentThread().join();
    }
}
