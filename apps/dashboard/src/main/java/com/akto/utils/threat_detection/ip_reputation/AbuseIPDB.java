package com.akto.utils.threat_detection.ip_reputation;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.threat_detection.IpReputationScore.ReputationScore;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.client.model.Filters;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class AbuseIPDB {

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();
    private static final LoggerMaker loggerMaker = new LoggerMaker(AbuseIPDB.class, LogDb.DASHBOARD);

    private static final String ABUSEIPDB_CHECK_URL = "https://api.abuseipdb.com/api/v2/check";
    private static final int MAX_AGE_IN_DAYS = 90;

    // Cache for AbuseIPDB config to avoid repeated DB calls
    private static volatile Config.AbuseIPDBConfig cachedConfig = null;
    private static volatile long lastConfigFetchTime = 0;
    private static final long CONFIG_CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Get cached AbuseIPDB config or fetch from DB if cache is expired
     * @return Config.AbuseIPDBConfig or null if not configured
     */
    private static Config.AbuseIPDBConfig getConfig() {
        long currentTime = Context.now();

        // Check if cache is valid
        if (cachedConfig != null && (currentTime - lastConfigFetchTime) < CONFIG_CACHE_DURATION_MS) {
            return cachedConfig;
        }

        // Cache miss or expired - fetch from DB
        try {
            Config.AbuseIPDBConfig config = (Config.AbuseIPDBConfig) ConfigsDao.instance.findOne(
                Filters.eq("_id", Config.AbuseIPDBConfig.CONFIG_ID)
            );

            if (config != null) {
                cachedConfig = config;
                lastConfigFetchTime = currentTime;
            }

            return config;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching AbuseIPDB config: " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetch IP reputation score from AbuseIPDB API
     * @param ipAddress The IP address to check
     * @return Map containing reputation data or null if error
     */
    public static Map<String, Object> checkIpReputation(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            loggerMaker.errorAndAddToDb("IP address is null or empty");
            return null;
        }

        // Get API key from cached config
        Config.AbuseIPDBConfig config = getConfig();

        if (config == null || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            loggerMaker.errorAndAddToDb("AbuseIPDB API key not configured");
            return null;
        }

        String apiKey = config.getApiKey();

        // Build URL with query parameters
        String url = ABUSEIPDB_CHECK_URL + "?ipAddress=" + ipAddress + "&maxAgeInDays=" + MAX_AGE_IN_DAYS;

        Request request = new Request.Builder()
                .url(url)
                .header("Key", apiKey)
                .header("Accept", "application/json")
                .get()
                .build();

        Response response = null;
        try {
            loggerMaker.infoAndAddToDb("Checking IP reputation for: " + ipAddress);

            response = httpClient.newCall(request).execute();

            if (response == null) {
                loggerMaker.errorAndAddToDb("AbuseIPDB response is null");
                return null;
            }

            String jsonData = response.body().string();

            if (!response.isSuccessful()) {
                loggerMaker.errorAndAddToDb(
                    "AbuseIPDB API error: " + response.code() + " - " + response.message() + " - " + jsonData
                );
                return null;
            }

            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> responseMap = new Gson().fromJson(jsonData, type);

            loggerMaker.infoAndAddToDb("Successfully fetched reputation data from AbuseIPDB for IP: " + ipAddress);

            return responseMap;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching IP reputation from AbuseIPDB: " + e.getMessage());
            return null;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> fetchDataInResponse(Map<String, Object> response) {
        if (response == null || !response.containsKey("data")) {
            return new HashMap<>();
        }
        Object dataObj = response.get("data");
        if (!(dataObj instanceof Map)) {
            return new HashMap<>();
        }
        return (Map<String, Object>) dataObj;
    }

    /**
     * Since AbuseIPDB, gives higher confidence score for more severe threats,
     * and we give higher reputation score for less severe threats,
     * we invert the confidence score to map it to our ReputationScore enum.
     */
    public static ReputationScore getReputationScoreFromConfidence(int confidenceScore) {
        if (confidenceScore >= 70) {
            return ReputationScore.LOW;
        } else if (confidenceScore >= 25) {
            return ReputationScore.MEDIUM;
        } else {
            return ReputationScore.HIGH;
        }
    }
}
