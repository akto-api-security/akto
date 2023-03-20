package com.akto.rules;

import com.akto.util.enums.GlobalEnums;

public class PortScanningViaSSRF extends BaseSSRFTest{

    public PortScanningViaSSRF(String testRunId, String testRunResultSummaryId) {
        super(testRunId, testRunResultSummaryId);
    }


    @Override
    protected String getTemplateUrl() {
        return "https://raw.githubusercontent.com/akto-api-security/tests-library/feature/port_scanning/SSRF/business-logic/port_scanning.yaml";
    }

    @Override
    public String superTestName() {
        return GlobalEnums.TestCategory.SSRF.name();
    }

    @Override
    public String subTestName() {
        return GlobalEnums.TestSubCategory.PORT_SCANNING.name();
    }

    @Override
    protected String getUrlPlaceholder() {
        return "http://localhost:{{port}}";
    }

    @Override
    protected boolean isResponseStatusCodeAllowed(int statusCode) {
        return true; //allowing all status codes
    }
}
