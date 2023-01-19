package com.akto.listener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.akto.dao.context.Context;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicy;

public class RuntimeListener implements ServletContextListener {

    public static HttpCallParser httpCallParser = null;
    public static AktoPolicy aktoPolicy = null;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Context.accountId.set(1_000_000);
        Main.initializeRuntime();
        httpCallParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
        aktoPolicy = new AktoPolicy(RuntimeListener.httpCallParser.apiCatalogSync, false);
    }
    
}
