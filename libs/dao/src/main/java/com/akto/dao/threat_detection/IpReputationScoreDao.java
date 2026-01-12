package com.akto.dao.threat_detection;

import com.akto.dao.CommonContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.threat_detection.IpReputationScore;

public class IpReputationScoreDao extends CommonContextDao<IpReputationScore> {

    public static final IpReputationScoreDao instance = new IpReputationScoreDao();

    private IpReputationScoreDao() {}

    public void createIndicesIfAbsent() {
        // Compound index on IP and timestamp for efficient cache expiry queries
        String[] fieldNames = new String[]{IpReputationScore._IP, IpReputationScore._TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    @Override
    public String getCollName() {
        return "ip_reputation_scores";
    }

    @Override
    public Class<IpReputationScore> getClassT() {
        return IpReputationScore.class;
    }

}
