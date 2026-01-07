package com.akto.utils.threat_detection.ip_reputation;

import java.util.Map;

import org.bson.types.ObjectId;

import com.akto.dao.context.Context;
import com.akto.dao.threat_detection.IpReputationScoreDao;
import com.akto.dto.threat_detection.IpReputationScore;
import com.akto.dto.threat_detection.IpReputationScore.ReputationSource;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class ReputationScoreAnalysis {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ReputationScoreAnalysis.class, LogDb.DASHBOARD);

    // Cache expiry: 5 days in seconds
    private static final int CACHE_EXPIRY_SECONDS = 5 * 24 * 60 * 60;

    /**
     * Get IP reputation score with caching
     * Checks database first, only fetches from API if not cached or expired
     *
     * @param ipAddress The IP address to check
     * @return IpReputationScore object or null if error
     */
    public static IpReputationScore getIpReputationScore(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            loggerMaker.debugAndAddToDb("IP address is null or empty");
            return null;
        }

        // Check if we have a cached score
        IpReputationScore cachedScore = getCachedScore(ipAddress);
        int currentTimestamp = Context.now();

        if (cachedScore != null && !isCacheExpired(cachedScore, currentTimestamp)) {
            loggerMaker.debugAndAddToDb("Using cached reputation score for IP: " + ipAddress);
            return cachedScore;
        }

        // Cache miss or expired - fetch from API
        loggerMaker.debugAndAddToDb("Cache miss or expired for IP: " + ipAddress + ", fetching from API");

        // TODO: Implement round-robin logic for multiple sources
        // Since we currently have only one source, we directly call it
        // All sources have a rate limit, which if hit, should shift to the next source in round-robin fashion
        IpReputationScore apiResult = fetchFromReputationSource(ipAddress, ReputationSource.ABUSEIPDB);

        if (apiResult == null) {
            loggerMaker.errorAndAddToDb("Failed to fetch reputation score from API");
            return null;
        }

        // Save to database
        IpReputationScoreDao.instance.updateOne(
            Filters.eq(IpReputationScore._IP, ipAddress),
            Updates.combine(
                Updates.set(IpReputationScore._METADATA, apiResult.getMetadata()),
                Updates.set(IpReputationScore._SCORE, apiResult.getScore()),
                Updates.set(IpReputationScore._SOURCE, apiResult.getSource()),
                Updates.set(IpReputationScore._TIMESTAMP, apiResult.getTimestamp())
            )
        );
        return apiResult;
    }

    /**
     * Get cached score from database
     *
     * @param ipAddress The IP address to lookup
     * @return IpReputationScore or null if not found
     */
    private static IpReputationScore getCachedScore(String ipAddress) {
        try {
            IpReputationScore score = IpReputationScoreDao.instance.findOne(Filters.eq(IpReputationScore._IP, ipAddress));
            return score;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching cached score: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if cached score has expired
     *
     * @param score The cached score
     * @param currentTimestamp Current timestamp in seconds
     * @return true if expired, false otherwise
     */
    private static boolean isCacheExpired(IpReputationScore score, int currentTimestamp) {
        int age = currentTimestamp - score.getTimestamp();
        return age > CACHE_EXPIRY_SECONDS;
    }

    /**
     * Fetch reputation score from external API
     * This method will be extended for round-robin support with multiple sources
     *
     * @param ipAddress The IP address to check
     * @param source The reputation source to use
     * @return IpReputationScore object or null if error
     */
    private static IpReputationScore fetchFromReputationSource(String ipAddress, ReputationSource source) {
        try {
            switch (source) {
                case ABUSEIPDB:
                    Map<String, Object> abuseIPDBResponse = AbuseIPDB.checkIpReputation(ipAddress);
                    if (abuseIPDBResponse != null) {
                        Map<String, Object> data = AbuseIPDB.fetchDataInResponse(abuseIPDBResponse);
                        // Extract abuse confidence score (0-100)
                        Object abuseConfidenceScore = data.get("abuseConfidenceScore");
                        int confidenceScore = 0;
                        if (abuseConfidenceScore instanceof Number) {
                            confidenceScore = ((Number) abuseConfidenceScore).intValue();
                        }
                        IpReputationScore.ReputationScore score = AbuseIPDB.getReputationScoreFromConfidence(confidenceScore);
                        int timestamp = Context.now();
                        return new IpReputationScore(new ObjectId(), ipAddress, timestamp, score, ReputationSource.ABUSEIPDB, data);
                    }
                    break;
                case OTHER:
                    // Placeholder for future reputation sources
                    break;
                default:
                    loggerMaker.errorAndAddToDb("Unknown reputation source: " + source);
                    break;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching from reputation source: " + e.getMessage());
        }

        return null;
    }
}
