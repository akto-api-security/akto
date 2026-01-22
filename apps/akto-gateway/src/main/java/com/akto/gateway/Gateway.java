package com.akto.gateway;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class Gateway {

    private static final Logger logger = LogManager.getLogger(Gateway.class);
    private static Gateway instance;

    private Gateway() {
        logger.info("Gateway instance initialized");
    }

    public static synchronized Gateway getInstance() {
        if (instance == null) {
            instance = new Gateway();
        }
        return instance;
    }

    public Map<String, Object> processRequest(Map<String, Object> request) {
        logger.info("Processing request through gateway");

        Map<String, Object> response = new HashMap<>();

        return response;
    }
}
