package com.akto.listener;

import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.akto.action.IngestionAction;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import org.apache.commons.lang3.StringUtils;

public class InitializerListener implements ServletContextListener {

    private ScheduledExecutorService scheduler;

    public static long lastProcessingTime = System.currentTimeMillis();
    private int processingQueueThreshold = System.getenv("AKTO_TRAFFIC_QUEUE_THRESHOLD") != null && !System.getenv("AKTO_TRAFFIC_QUEUE_THRESHOLD").isEmpty()
            ? Integer.parseInt(System.getenv("AKTO_TRAFFIC_QUEUE_THRESHOLD"))
            : 100;  // Threshold for processing the queue
    private int inactiveQueueProcessingTime = System.getenv("AKTO_INACTIVE_QUEUE_PROCESSING_TIME") != null && !System.getenv("AKTO_INACTIVE_QUEUE_PROCESSING_TIME").isEmpty()
            ? Integer.parseInt(System.getenv("AKTO_INACTIVE_QUEUE_PROCESSING_TIME"))
            : 5000; // Time after which to process the queue if inactive
    private int jobInterval = System.getenv("AKTO_TRAFFIC_PROCESSING_JOB_INTERVAL") != null && !System.getenv("AKTO_TRAFFIC_PROCESSING_JOB_INTERVAL").isEmpty()
            ? Integer.parseInt(System.getenv("AKTO_TRAFFIC_PROCESSING_JOB_INTERVAL"))
            : 1; // Interval for the scheduled job
    private final String containerVersion = StringUtils.isEmpty(System.getenv("AKTO_CONTAINER_VERSION")) ? "1.50.0" : System.getenv("AKTO_CONTAINER_VERSION");

    private static final LoggerMaker loggerMaker = new LoggerMaker(InitializerListener.class, LoggerMaker.LogDb.RUNTIME);
    private static boolean processingStarted = false;
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        loggerMaker.info("Container is running on the latest version: " + containerVersion);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if(processingStarted) {
                    loggerMaker.info("Processing already started, skipping traffic discovery queue processing.");
                    return;
                }
                processingStarted = true; // Set the flag to indicate processing has started
                long currentTime = System.currentTimeMillis();
                if (IngestionAction.trafficDiscoveryQueue.size() > processingQueueThreshold
                        || (currentTime - lastProcessingTime > inactiveQueueProcessingTime && !IngestionAction.trafficDiscoveryQueue.isEmpty())
                        ) {
                    loggerMaker.info("Processing traffic discovery queue with size: " + IngestionAction.trafficDiscoveryQueue.size());

                    List<HttpResponseParams> toProcess = new ArrayList<>();
                    synchronized (IngestionAction.trafficDiscoveryQueue) {
                        int size = IngestionAction.trafficDiscoveryQueue.size()> processingQueueThreshold ? processingQueueThreshold : IngestionAction.trafficDiscoveryQueue.size();
                        for (int i = 0; i < size; i++) {
                            HttpResponseParams poll = IngestionAction.trafficDiscoveryQueue.poll();
                            if(poll == null) continue;
                            toProcess.add(poll);
                            loggerMaker.info("Processing element " + (i + 1) + " from traffic discovery queue");
                        }
                    }
                    com.akto.hybrid_runtime.Main.processData(toProcess);
                    loggerMaker.info("Processed traffic discovery queue, and cleared it. size now: " + IngestionAction.trafficDiscoveryQueue.size());
                    lastProcessingTime = currentTime;
                }else {
                    loggerMaker.info("Traffic discovery queue size is small: " + IngestionAction.trafficDiscoveryQueue.size() +
                            ", not processing at this time.");
                }
            } catch (Exception e) {
                loggerMaker.info("Error processing traffic discovery queue: " + e.getMessage());
            } finally {
                processingStarted = false; // Reset the flag after processing
            }
        }, 0, jobInterval, TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
