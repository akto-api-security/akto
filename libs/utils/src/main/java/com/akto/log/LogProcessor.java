package com.akto.log;
import com.akto.log.LoggerMaker;
import com.akto.dto.Log;
import com.akto.data_actor.ClientActor;
import com.akto.testing.ApiExecutor; // Added import
import com.akto.dto.OriginalHttpRequest; // Added import
import com.akto.dto.OriginalHttpResponse; // Added import

import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LogProcessor {
    private static final int MAX_LOGS = 10000; // Maximum logs in cache
    private static final int BATCH_SIZE = 100; // 100 logs per batch
    private static final int THREAD_POOL_SIZE = 100; // Number of threads
    private static final long WAIT_TIME_SECONDS = 10; // Wait 10 seconds for remaining logs
    private static final ClientActor clientActor = new ClientActor(); // Shared ClientActor instance
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);  // Instance Variable
    private final LoggerMaker.LogDb logDb; // Store LogDb type

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask = null; // Track the scheduled task

    
    // In-memory cache for logs (thread-safe)
    private final ConcurrentLinkedQueue<Log> logCache = new ConcurrentLinkedQueue<>();
    private final Object lock = new Object(); // Instance Variable, For synchronized access

    public LogProcessor(LoggerMaker.LogDb logDb) {
        this.logDb = logDb;
    }

    public int getMAX_LOGS(){
        return MAX_LOGS;
    }

    // Get the current cache size (for LoggerMaker to check)
    public int getCacheSize() {
        return logCache.size();
    }

    public void addLog(Log log){
        if(log == null) return; // Null check

        if(getCacheSize() > MAX_LOGS) return;
        
         // add lock to cache
        logCache.offer(log);
            
        // If cache has enough logs, process them immediately and cancel any scheduled task
        if(logCache.size() >= BATCH_SIZE){
            if (scheduledTask != null) {
                scheduledTask.cancel(false); // Cancel any pending task
                scheduledTask = null;
            }
            processLogs();
        } else {
            // Schedule a one-time task to process remaining logs after 10 seconds
            if (scheduledTask != null) {
                scheduledTask.cancel(false); // Cancel previous task
            }
            scheduledTask = scheduler.schedule(() -> {
                if (!logCache.isEmpty()) {
                    processLogs();
                }
                scheduledTask = null; // Clear the task after execution
            }, WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        }  
    }

    // Process logs by batching and sending via thread pool
    private void processLogs(){
        synchronized(lock){
            // Convert cache to list and sort by timestamp
            List<Log> logs = new ArrayList<>(logCache);
            logCache.clear();
            
            int totalLogs = logs.size();
            for(int i = 0; i < totalLogs; i += BATCH_SIZE ){
                int end = Math.min(i + BATCH_SIZE, totalLogs);
                List<Log> finalBatch = logs.subList(i, end);
                threadPool.submit(
                    () -> sendLogBatch(finalBatch)
                );   
            }
        }
    }

    // Send a batch of logs to the server
    private void sendLogBatch(List<Log> batch){
        if(!clientActor.transmitLogsBatch(batch, logDb)){
            System.out.println("Logs Batching failed and exiting now");
        }
    } 
    
}