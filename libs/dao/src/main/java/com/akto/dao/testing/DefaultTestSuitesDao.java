package com.akto.dao.testing;

import com.akto.dao.CommonContextDao;
import com.akto.dto.testing.DefaultTestSuites;
public class DefaultTestSuitesDao extends CommonContextDao<DefaultTestSuites> {

    public static final DefaultTestSuitesDao instance = new DefaultTestSuitesDao();

    @Override
    public String getCollName() {
        return "default_test_suites";
    }

    @Override
    public Class<DefaultTestSuites> getClassT() {
        return DefaultTestSuites.class;
    }
}