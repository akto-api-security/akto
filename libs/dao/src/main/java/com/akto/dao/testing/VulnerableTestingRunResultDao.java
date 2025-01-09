package com.akto.dao.testing;

import com.akto.dao.AccountsContextDaoWithRbac;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestingRunResult;
import com.mongodb.client.model.CreateCollectionOptions;

public class VulnerableTestingRunResultDao extends AccountsContextDaoWithRbac<TestingRunResult> {

    public static final VulnerableTestingRunResultDao instance = new VulnerableTestingRunResultDao();

    public void createIndicesIfAbsent() {
        
        String dbName = Context.accountId.get()+"";

        CreateCollectionOptions createCollectionOptions = new CreateCollectionOptions();
        createCollectionIfAbsent(dbName, getCollName(), createCollectionOptions);

        
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.VULNERABLE }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{TestingRunResult.END_TIMESTAMP}, false);

        String[] fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.API_INFO_KEY+"."+ApiInfoKey.API_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.TEST_RESULTS+"."+GenericTestResult._CONFIDENCE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.TEST_SUPER_TYPE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.VULNERABLE, TestingRunResult.API_INFO_KEY, TestingRunResult.TEST_SUB_TYPE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    @Override
    public String getCollName() {
        return "vulnerable_testing_run_results";
    }

    @Override
    public Class<TestingRunResult> getClassT() {
        return TestingRunResult.class;
    }

    @Override
    public String getFilterKeyString() {
        return TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.API_COLLECTION_ID;
    }
}
