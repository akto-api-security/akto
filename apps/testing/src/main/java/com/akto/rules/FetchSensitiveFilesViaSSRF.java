package com.akto.rules;

import com.akto.dto.testing.TestResult;
import com.akto.util.enums.GlobalEnums;

public class FetchSensitiveFilesViaSSRF extends BaseSSRFTest{

    public FetchSensitiveFilesViaSSRF(String testRunId, String testRunResultSummaryId) {
        super(testRunId, testRunResultSummaryId);
    }

    @Override
    protected String getTemplateUrl() {
        return "https://raw.githubusercontent.com/akto-api-security/tests-library/feature/fetch_sensitive_files_via_ssrf/SSRF/business-logic/fetch_sensitive_files.yaml";
    }

    @Override
    protected String getUrlPlaceholder() {
        return "{{filename}}";
    }

    @Override
    protected boolean isResponseStatusCodeAllowed(int statusCode) {
        return false;
    }

    @Override
    public String superTestName() {
        return GlobalEnums.TestCategory.SSRF.name();
    }

    @Override
    public String subTestName() {
        return GlobalEnums.TestSubCategory.FETCH_SENSITIVE_FILES.name();
    }

    @Override
    protected Result processResult(Result result) {
        boolean isVulnerable = false;
        for (TestResult testResult : result.testResults) {
            if(testResult.getMessage().length() - testResult.getOriginalMessage().length() > 100){
                isVulnerable = true;
                testResult.setVulnerable(true);
            } else {
                testResult.setVulnerable(false);
            }
        }
        result.isVulnerable = isVulnerable;
        return result;
    }
}
