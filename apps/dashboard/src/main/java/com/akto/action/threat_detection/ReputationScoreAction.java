package com.akto.action.threat_detection;

import com.akto.action.UserAction;
import com.akto.dto.threat_detection.IpReputationScore;
import com.akto.dto.type.KeyTypes;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.threat_detection.ip_reputation.ReputationScoreAnalysis;

import lombok.Getter;
import lombok.Setter;

public class ReputationScoreAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ReputationScoreAction.class, LogDb.DASHBOARD);

    @Setter
    private String ipAddress;
    @Getter
    private IpReputationScore reputationData;

    public String fetchIpReputationScore() {
        try {
            if (ipAddress == null || ipAddress.trim().isEmpty()) {
                loggerMaker.errorAndAddToDb("IP address parameter is required");
                addActionError("IP address is required");
                return ERROR.toUpperCase();
            }

            if (!KeyTypes.isIP(ipAddress)) {
                loggerMaker.errorAndAddToDb("Invalid IP address format: " + ipAddress);
                addActionError("Invalid IP address format");
                return ERROR.toUpperCase();
            }

            loggerMaker.debugAndAddToDb("Fetching reputation score for IP: " + ipAddress);

            // Get reputation score (with caching)
            this.reputationData = ReputationScoreAnalysis.getIpReputationScore(ipAddress);

            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchIpReputationScore");
            addActionError("Error fetching IP reputation score: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }
}
