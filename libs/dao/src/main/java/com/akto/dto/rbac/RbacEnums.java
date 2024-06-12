package com.akto.dto.rbac;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RbacEnums {

    public enum AccessGroups {
        INVENTORY,
        TESTING,
        TEST_LIBRARY,
        SETTINGS;
    
        public static AccessGroups[] getAccessGroups() {
            return values();
        }
    }

    public enum Feature {
        API_COLLECTIONS(AccessGroups.INVENTORY),
        SENSITIVE_DATA(AccessGroups.INVENTORY),
        TRAFFIC_FILTERS(AccessGroups.INVENTORY),
        DEFAULT_PAYLOADS(AccessGroups.INVENTORY),
        TAGS(AccessGroups.INVENTORY),
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
        BILLING(AccessGroups.SETTINGS);

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
}
