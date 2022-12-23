package com.akto.dao;

import com.akto.dto.testing.OtpTestData;

public class OtpTestDataDao extends CommonContextDao<OtpTestData> {

    public static OtpTestDataDao instance = new OtpTestDataDao();

    @Override
    public String getCollName() {
        return "otp_test_data";
    }

    @Override
    public Class<OtpTestData> getClassT() {
        return OtpTestData.class;
    }
}
