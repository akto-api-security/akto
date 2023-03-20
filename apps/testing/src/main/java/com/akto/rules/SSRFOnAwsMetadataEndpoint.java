package com.akto.rules;

public class SSRFOnAwsMetadataEndpoint extends BaseSSRFTest {

    public SSRFOnAwsMetadataEndpoint(String testRunId, String testRunResultSummaryId) {
        super(testRunId, testRunResultSummaryId);
    }

    protected String getTemplateUrl() {
        return "https://raw.githubusercontent.com/bhavik-dand/tests-library/temp/ssrf_aws_endpoint/SSRF/ssrf_aws_metadata_endpoint.yaml";
    }

    @Override
    public String superTestName() {
        return "SSRF";
    }

    @Override
    public String subTestName() {
        return "SSRF_AWS_METADATA_EXPOSED";
    }

    protected String getUrlPlaceholder() {
        return "{{metadata_url}}";
    }

    protected boolean isResponseStatusCodeAllowed(int statusCode) {
        return statusCode == 200;
    }
}
