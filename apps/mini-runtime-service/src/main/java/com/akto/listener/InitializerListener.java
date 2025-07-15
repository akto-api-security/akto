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

public class InitializerListener implements ServletContextListener {

    private ScheduledExecutorService scheduler;

    public static long lastProcessingTime = System.currentTimeMillis();
    public int processingQueueThreshold = 100; // Threshold for processing the queue

    private static final LoggerMaker loggerMaker = new LoggerMaker(InitializerListener.class, LoggerMaker.LogDb.RUNTIME);
    private static final ReentrantLock mutex = new ReentrantLock();
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                mutex.lock();
                long currentTime = System.currentTimeMillis();
                if (IngestionAction.trafficDiscoveryQueue.size() > processingQueueThreshold
                        || (currentTime - lastProcessingTime > 5000 && !IngestionAction.trafficDiscoveryQueue.isEmpty())
                        ) {
                    loggerMaker.info("Processing traffic discovery queue with size: " + IngestionAction.trafficDiscoveryQueue.size());

                    List<HttpResponseParams> toProcess = new ArrayList<>();
                    synchronized (IngestionAction.trafficDiscoveryQueue) {
                        int size = IngestionAction.trafficDiscoveryQueue.size()> processingQueueThreshold ? processingQueueThreshold : IngestionAction.trafficDiscoveryQueue.size();
                        for (int i = 0; i < size; i++) {
                            toProcess.add(IngestionAction.trafficDiscoveryQueue.poll());
                            loggerMaker.info("Processing element " + (i + 1) + " from traffic discovery queue");
                        }
                        // Remove processed elements
                        for (int i = 0; i < size; i++) {
                            IngestionAction.trafficDiscoveryQueue.remove(0);
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
                mutex.unlock();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
