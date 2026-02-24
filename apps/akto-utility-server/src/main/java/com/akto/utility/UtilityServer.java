package com.akto.utility;

import com.akto.agent.ApiExecutionJobStore;
import com.akto.agent.ApiExecutionJobStoreRegistry;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight HTTP server for akto-utility-server APIs (execute API, get result).
 * Can be started inside another process (e.g. mini-testing) or run standalone via {@link Main}.
 */
public class UtilityServer {

    private static final LoggerMaker logger = new LoggerMaker(UtilityServer.class, LogDb.TESTING);

    private static final String ENV_PORT = "UTILITY_SERVER_PORT";

    private static HttpServer server;
    private static ExecutorService executorService;
    private static ApiExecutionJobStore jobStore;
    private static DataActor dataActor;
    private static volatile boolean started = false;

    public static void start() {
        int port = getPort();
        if (port <= 0) {
            logger.info("akto-utility-server disabled (UTILITY_SERVER_PORT <= 0)");
            return;
        }
        if (started) {
            logger.info("akto-utility-server already started");
            return;
        }
        try {
            jobStore = new ApiExecutionJobStore();
            ApiExecutionJobStoreRegistry.set(jobStore);
            dataActor = DataActorFactory.fetchInstance();
            executorService = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "akto-utility-executor");
                t.setDaemon(true);
                return t;
            });
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/utility/execute", new ExecuteApiHandler(jobStore, executorService));
            server.createContext("/utility/result", new GetResultHandler(jobStore));
            server.createContext("/utility/sendHealthCheck", new SendHealthCheckHandler(dataActor));
            server.createContext("/utility/sendLogs", new SendLogsHandler(dataActor));
            server.setExecutor(null);
            server.start();
            started = true;
            logger.info("akto-utility-server started on port " + port);
        } catch (IOException e) {
            logger.error("Failed to start akto-utility-server: " + e.getMessage(), e);
        }
    }

    /**
     * Stop the utility server and executor.
     */
    public static void stop() {
        if (!started) {
            return;
        }
        try {
            if (server != null && server.getAddress() != null) {
                server.stop(1);
                logger.info("akto-utility-server stopped");
            }
            if (executorService != null) {
                executorService.shutdown();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            }
        } catch (Exception e) {
            logger.error("Error stopping akto-utility-server: " + e.getMessage(), e);
        } finally {
            ApiExecutionJobStoreRegistry.set(null);
            server = null;
            executorService = null;
            jobStore = null;
            dataActor = null;
            started = false;
        }
    }

    public static boolean isStarted() {
        return started;
    }

    public static int getPort() {
        String env = System.getenv(ENV_PORT);
        if (env == null || env.trim().isEmpty()) {
            return 8001;
        }
        try {
            return Integer.parseInt(env.trim());
        } catch (NumberFormatException e) {
            logger.error("Invalid UTILITY_SERVER_PORT: " + env);
            return 8001;
        }
    }
}
