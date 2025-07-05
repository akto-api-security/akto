package com.akto.dto.testing;

import com.akto.dto.testing.config.TestSuites;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultTestSuites extends TestSuites {
    public static final String SUITE_TYPE = "suiteType";
    private DefaultSuitesType suiteType;

    public DefaultTestSuites() {}

    public DefaultTestSuites(int createdAt, String createdBy, int lastUpdated, String name, List<String> subCategoryList, DefaultSuitesType suiteType) {
        super(name, subCategoryList, createdBy, lastUpdated, createdAt);
        this.suiteType = suiteType;
    }

    public enum DefaultSuitesType {
        OWASP,
        TESTING_METHODS,
        SEVERITY,
        DURATION
    }

    public static final Map<String, List<String>> owaspTop10List = new HashMap<>();
    static {
        owaspTop10List.put("Broken Object Level Authorization", Arrays.asList("BOLA"));
        owaspTop10List.put("Broken Authentication", Arrays.asList("NO_AUTH"));
        owaspTop10List.put("Broken Object Property Level Authorization", Arrays.asList("EDE", "MA"));
        owaspTop10List.put("Unrestricted Resource Consumption", Arrays.asList("RL"));
        owaspTop10List.put("Broken Function Level Authorization", Arrays.asList("BFLA"));
        owaspTop10List.put("Unrestricted Access to Sensitive Business Flows", Arrays.asList("INPUT"));
        owaspTop10List.put("Server Side Request Forgery", Arrays.asList("SSRF"));
        owaspTop10List.put("Security Misconfiguration", Arrays.asList("SM", "UHM", "VEM", "MHH", "SVD", "CORS", "ILM"));
        owaspTop10List.put("Improper Inventory Management", Arrays.asList("IAM", "IIM"));
        owaspTop10List.put("Unsafe Consumption of APIs", Arrays.asList("COMMAND_INJECTION", "INJ", "CRLF", "SSTI", "LFI", "XSS", "INJECT"));
    }

    public DefaultSuitesType getSuiteType() {
        return suiteType;
    }

    public void setSuiteType(DefaultSuitesType suiteType) {
        this.suiteType = suiteType;
    }
}
