package com.akto.util;

import com.akto.dao.testing.ComplianceInfosDao;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.ComplianceInfo;
import com.akto.dto.testing.ComplianceMapping;

import java.util.HashMap;

public final class ComplianceResolutionUtils {

    private ComplianceResolutionUtils() {}

    /**
     * Prefer compliance embedded on the test (YAML / account yaml_templates). If missing or empty, resolve from
     * {@code common.compliance_infos} using the same document ids as {@link com.akto.listener.InitializerListener}
     * ({@code compliance/{stem}.conf} for test id, then category name).
     */
    public static ComplianceMapping resolveComplianceForTestConfig(TestConfig testConfig) {
        if (testConfig == null || testConfig.getInfo() == null) {
            return emptyComplianceMapping();
        }
        Info info = testConfig.getInfo();
        ComplianceMapping fromInline = info.getCompliance();
        if (fromInline != null && fromInline.hasMappedClauses()) {
            return fromInline;
        }
        ComplianceInfo fromCommon = findComplianceInCommon(testConfig.getId());
        if (fromCommon == null && info.getCategory() != null && info.getCategory().getName() != null) {
            String categoryName = info.getCategory().getName();
            if (!categoryName.equalsIgnoreCase(testConfig.getId())) {
                fromCommon = findComplianceInCommon(categoryName);
            }
        }
        if (fromCommon != null) {
            return ComplianceMapping.createFromInfo(fromCommon);
        }
        return emptyComplianceMapping();
    }

    private static ComplianceMapping emptyComplianceMapping() {
        return new ComplianceMapping(new HashMap<>(), "", "", 0);
    }

    private static ComplianceInfo findComplianceInCommon(String stem) {
        if (stem == null || stem.isEmpty()) {
            return null;
        }
        ComplianceInfo ci = ComplianceInfosDao.instance.findOne(Constants.ID, "compliance/" + stem + ".conf");
        if (ci != null) {
            return ci;
        }
        if (!stem.equals(stem.toUpperCase())) {
            return ComplianceInfosDao.instance.findOne(Constants.ID, "compliance/" + stem.toUpperCase() + ".conf");
        }
        return null;
    }
}
