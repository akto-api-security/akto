package com.akto.dao;

import com.akto.dto.RecordedLoginFlowScreenshot;

public class RecordedLoginScreenshotDao extends AccountsContextDao<RecordedLoginFlowScreenshot> {

    public static final RecordedLoginScreenshotDao instance = new RecordedLoginScreenshotDao();

    @Override
    public String getCollName() {
        return "recorded_login_flow_screenshots";
    }

    @Override
    public Class<RecordedLoginFlowScreenshot> getClassT() {
        return RecordedLoginFlowScreenshot.class;
    }
}
