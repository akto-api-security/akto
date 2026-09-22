package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.threat_detection.ComplianceClauseCoverage;

import java.util.List;

public class ComplianceClauseCoverageDao extends AccountsContextDao<ComplianceClauseCoverage> {

    public static final String COLLECTION_NAME = "compliance_clause_coverage";
    public static final ComplianceClauseCoverageDao instance = new ComplianceClauseCoverageDao();

    private ComplianceClauseCoverageDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<ComplianceClauseCoverage> getClassT() {
        return ComplianceClauseCoverage.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{ComplianceClauseCoverage.LAST_SCANNED_AT}, false);
    }

    /** One doc per framework — PostureService#frameworkReadiness reads every row it has. */
    public List<ComplianceClauseCoverage> findAllCoverage() {
        return findAll(new org.bson.Document());
    }
}
