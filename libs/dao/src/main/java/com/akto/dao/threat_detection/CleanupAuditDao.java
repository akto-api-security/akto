package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.threat_detection.CleanupAudit;
import com.mongodb.BasicDBObject;

import java.util.Optional;

public class CleanupAuditDao extends AccountsContextDao<CleanupAudit> {

    public static final CleanupAuditDao instance = new CleanupAuditDao();

    @Override
    public String getCollName() {
        return "cleanup_malicious_requests_audit";
    }

    @Override
    public Class<CleanupAudit> getClassT() {
        return CleanupAudit.class;
    }

    public Optional<CleanupAudit> getLatestEntry() {
        return Optional.ofNullable(
                findOne(
                        new BasicDBObject(),
                        new BasicDBObject("sort", new BasicDBObject("alertWindowEnd", -1))));
    }
}
