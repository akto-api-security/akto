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
        AI;
    
        public static AccessGroups[] getAccessGroups() {
            return values();
        }
    }

    public enum Feature {
        API_COLLECTIONS(AccessGroups.INVENTORY),
        SENSITIVE_DATA(AccessGroups.INVENTORY),
        TRAFFIC_FILTERS(AccessGroups.INVENTORY),
        DEFAULT_PAYLOADS(AccessGroups.INVENTORY),
        SAMPLE_DATA(AccessGroups.INVENTORY),
        TAGS(AccessGroups.INVENTORY),
        ASK_GPT(AccessGroups.INVENTORY),
        START_TEST_RUN(AccessGroups.TESTING),
        TEST_RESULTS(AccessGroups.TESTING),
        TEST_ROLES(AccessGroups.TESTING),
        USER_CONFIG(AccessGroups.TESTING),
        AUTH_TYPE(AccessGroups.TESTING),
        ISSUES(AccessGroups.TESTING),
        TEST_EDITOR(AccessGroups.TEST_LIBRARY),
        EXTERNAL_TEST_LIBRARY(AccessGroups.TEST_LIBRARY),
        INTEGRATIONS(AccessGroups.SETTINGS),
        METRICS(AccessGroups.SETTINGS),
        LOGS(AccessGroups.SETTINGS),
        BILLING(AccessGroups.SETTINGS),
        INVITE_MEMBERS(AccessGroups.SETTINGS),
        ADMIN_ACTIONS(AccessGroups.ADMIN),
        USER_ACTIONS(AccessGroups.USER),
        AI_AGENTS(AccessGroups.AI);

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
        READ_WRITE
    }

    public static void mergeUserFeaturesAccess (Map<Feature, ReadWriteAccess> accessMap){
        for(Feature feature: Feature.getFeaturesForAccessGroup(AccessGroups.USER)){
            accessMap.put(feature, ReadWriteAccess.READ_WRITE);
        }
    }
}
