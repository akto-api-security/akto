package com.akto.listener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.akto.analyser.ResourceAnalyser;
import com.akto.dao.context.Context;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicy;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RuntimeListener implements ServletContextListener {

    public static HttpCallParser httpCallParser = null;
    public static AktoPolicy aktoPolicy = null;
    public static ResourceAnalyser resourceAnalyser = null;
    private boolean ranOnce = false;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    @Override
    public void contextInitialized(ServletContextEvent sce) {

        executorService.schedule( new Runnable() {
            public void run() {
                while (!ranOnce) {
                    if (!InitializerListener.connectedToMongo) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        continue;
                    }

                    try {
                        Context.accountId.set(1_000_000);
                        Main.initializeRuntime();
                        httpCallParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
                        aktoPolicy = new AktoPolicy(RuntimeListener.httpCallParser.apiCatalogSync, false);
                        resourceAnalyser = new ResourceAnalyser(300_000, 0.01, 100_000, 0.01);
                        ranOnce = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }, 0 , TimeUnit.SECONDS);

    }
    
}
