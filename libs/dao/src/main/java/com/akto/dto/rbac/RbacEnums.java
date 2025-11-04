package com.akto.dto.rbac;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RbacEnums {

    public enum AccessGroups {
        INVENTORY,
        TESTING,
        TEST_LIBRARY,
        SETTINGS,
        ADMIN,
        DEBUG_INFO,
        USER,
        AI,
        THREAT_PROTECTION,
        PII_DATA;
    
        public static AccessGroups[] getAccessGroups() {
            return values();
        }
    }

    public enum Feature {
        API_COLLECTIONS(AccessGroups.INVENTORY),
        SENSITIVE_DATA(AccessGroups.PII_DATA),
        TRAFFIC_FILTERS(AccessGroups.INVENTORY),
        DEFAULT_PAYLOADS(AccessGroups.INVENTORY),
        SAMPLE_DATA(AccessGroups.PII_DATA),
        TAGS(AccessGroups.INVENTORY),
        ASK_GPT(AccessGroups.INVENTORY),
        START_TEST_RUN(AccessGroups.TESTING),
        TEST_RESULTS(AccessGroups.TESTING),
        TEST_RUN_RESULTS(AccessGroups.PII_DATA),
        TEST_ROLES(AccessGroups.TESTING),
        USER_CONFIG(AccessGroups.TESTING),
        AUTH_TYPE(AccessGroups.TESTING),
        ISSUES(AccessGroups.TESTING),
        TEST_EDITOR(AccessGroups.TEST_LIBRARY),
        TEST_SUITE(AccessGroups.TEST_LIBRARY),
        EXTERNAL_TEST_LIBRARY(AccessGroups.TEST_LIBRARY),
        INTEGRATIONS(AccessGroups.SETTINGS),
        METRICS(AccessGroups.DEBUG_INFO),
        LOGS(AccessGroups.DEBUG_INFO),
        BILLING(AccessGroups.SETTINGS),
        INVITE_MEMBERS(AccessGroups.DEBUG_INFO),
        ADMIN_ACTIONS(AccessGroups.ADMIN),
        USER_ACTIONS(AccessGroups.USER),
        AI_AGENTS(AccessGroups.AI),
        THREAT_PROTECTION(AccessGroups.THREAT_PROTECTION);

        private final AccessGroups accessGroup;

        Feature(AccessGroups accessGroup) {
            this.accessGroup = accessGroup;
        }

        public AccessGroups getAccessGroup() {
            return accessGroup;
        }

        public static List<Feature> getFeaturesForAccessGroup(AccessGroups accessGroup) {
            return Arrays.stream(values())
                    .filter(feature -> feature.getAccessGroup() == accessGroup)
                    .collect(Collectors.toList());
        }
    }

    public enum ReadWriteAccess {
        READ,
        READ_WRITE,
        NO_ACCESS
    }

    public static void mergeUserFeaturesAccess (Map<Feature, ReadWriteAccess> accessMap){
        for(Feature feature: Feature.getFeaturesForAccessGroup(AccessGroups.USER)){
            accessMap.put(feature, ReadWriteAccess.READ_WRITE);
        }
    }
}
