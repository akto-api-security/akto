package com.akto.dao.testing_run_findings;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.test_run_findings.SourceCodeVulnerabilities;

public class SourceCodeVulnerabilitiesDao extends AccountsContextDao<SourceCodeVulnerabilities> {
    public static final SourceCodeVulnerabilitiesDao instance = new SourceCodeVulnerabilitiesDao();

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {SourceCodeVulnerabilities.AGENT_PROCESS_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

    @Override
    public String getCollName() {
        return "source_code_vulnerabilities";
    }

    @Override
    public Class<SourceCodeVulnerabilities> getClassT() {
        return SourceCodeVulnerabilities.class;
    }
}
