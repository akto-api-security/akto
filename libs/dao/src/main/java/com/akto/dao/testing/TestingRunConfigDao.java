package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.util.enums.MongoDBEnums;

public class TestingRunConfigDao extends AccountsContextDao<TestingRunConfig> {
    @Override
    public String getCollName() {
        return MongoDBEnums.Collection.TESTING_RUN_CONFIG.getCollectionName();
    }

    public static final TestingRunConfigDao instance = new TestingRunConfigDao();

    private TestingRunConfigDao() {}

    public int countTotalTestsInitialised(TestingRunResultSummary trrs){
        TestingRun testingRun = TestingRunDao.instance.findOne("_id", trrs.getTestingRunId());
        TestingRunConfig config = TestingRunConfigDao.instance.findOne("_id", testingRun.getTestIdConfig());

        int totalCount = trrs.getTotalApis() * config.getTestSubCategoryList().size();
        return totalCount;
    }

    @Override
    public Class<TestingRunConfig> getClassT() {
        return TestingRunConfig.class;
    }
}
