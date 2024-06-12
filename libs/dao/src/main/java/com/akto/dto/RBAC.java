package com.akto.dto;


import org.bson.types.ObjectId;

import java.util.*;

import static com.akto.dto.RBAC.AccessGroups.*;

public class RBAC {

    private ObjectId id;
    private int userId;

    public static final String USER_ID = "userId";
    private Role role;
    public static final String ROLE = "role";
    private int accountId;
    public static final String ACCOUNT_ID = "accountId";

    public enum Role {
        ADMIN("ADMIN"),
        MEMBER("SECURITY ENGINEER"),
        DEVELOPER("DEVELOPER"),
        GUEST("GUEST");

        final String name;
        final Role[] roleHierarchy;//invitation
        final Map<Feature, ReadWriteAccess> featureReadWriteAccessMap;
        private static final Role[] roles = values();

        public static Role[] getRoles() {
            return roles;
        }

        Role(String name) {
            this.name = name;
            this.roleHierarchy = roleHierarchy(this);
            this.featureReadWriteAccessMap = createFeatureMap(this);
        }

        public Role[] getRoleHierarchy () {
            return this.roleHierarchy;
        }
        public ReadWriteAccess getReadWriteAccessForFeature(Feature feature) {
            return this.featureReadWriteAccessMap.get(feature);
        }

        private Map<Feature, ReadWriteAccess> createFeatureMap (Role role) {
            Map<Feature, ReadWriteAccess> featureReadWriteAccessMap = new HashMap<>();
            switch (role) {
                case ADMIN:
                    for (AccessGroups accessGroup: AccessGroups.getAccessGroups()) {
                        for (Feature feature: Feature.getFeaturesForAccessGroup(accessGroup)) {
                            featureReadWriteAccessMap.put(feature, ReadWriteAccess.READ_WRITE);
                        }
                    }
                case MEMBER:
                    for (AccessGroups accessGroup: AccessGroups.getAccessGroups()) {
                        if (accessGroup.equals(SETTINGS)) {
                            for (Feature feature: Feature.getFeaturesForAccessGroup(accessGroup)) {
                                featureReadWriteAccessMap.put(feature, ReadWriteAccess.READ);
                            }
                        } else {
                            for (Feature feature: Feature.getFeaturesForAccessGroup(accessGroup)) {
                                featureReadWriteAccessMap.put(feature, ReadWriteAccess.READ_WRITE);
                            }
                        }
                    }
                case DEVELOPER:
                    for (AccessGroups accessGroup: AccessGroups.getAccessGroups()) {
                        if (accessGroup.equals(SETTINGS)) {
                            for (Feature feature: Feature.getFeaturesForAccessGroup(accessGroup)) {
                                featureReadWriteAccessMap.put(feature, ReadWriteAccess.READ_WRITE);
                            }
                        } else {
                            for (Feature feature: Feature.getFeaturesForAccessGroup(accessGroup)) {
                                featureReadWriteAccessMap.put(feature, ReadWriteAccess.READ);
                            }
                        }
                    }
                case GUEST:
                    for (AccessGroups accessGroup: AccessGroups.getAccessGroups()) {
                        for (Feature feature: Feature.getFeaturesForAccessGroup(accessGroup)) {
                            featureReadWriteAccessMap.put(feature, ReadWriteAccess.READ);
                        }
                    }
                    break;
            }
            return featureReadWriteAccessMap;
        }
        private static Role[] roleHierarchy(Role role) {
            switch (role) {
                case ADMIN:
                    return new Role[]{ADMIN, MEMBER, DEVELOPER, GUEST};
                case MEMBER:
                    return new Role[]{MEMBER, DEVELOPER, GUEST};
                case DEVELOPER:
                    return new Role[]{DEVELOPER, GUEST};
                case GUEST:
                    return new Role[]{};
            }
            return null;
        }


    }

    public enum AccessGroups {
        INVENTORY,
        TESTING,
        TEST_LIBRARY,
        SETTINGS;

        private static final AccessGroups[] accessGroups = values();
        public static AccessGroups[] getAccessGroups() {
            return accessGroups;
        }
    }

    public enum Feature {
        //Inventory Features
        API_COLLECTIONS(INVENTORY),
        SENSITIVE_DATA(INVENTORY),
        TRAFFIC_FILTERS(INVENTORY),
        DEFAULT_PAYLOADS(INVENTORY),
        TAGS(INVENTORY),

        //Testrun features
        START_TEST_RUN(TESTING),
        TEST_RESULTS(TESTING),
        TEST_ROLES(TESTING),
        USER_CONFIG(TESTING),
        AUTH_TYPE(TESTING),
        ISSUES(TESTING),

        //Test Library features
        TEST_EDITOR(TEST_LIBRARY),
        EXTERNAL_TEST_LIBRARY(TEST_LIBRARY),

        //Settings features
        INTEGRATIONS(SETTINGS),
        METRICS(SETTINGS),
        LOGS(SETTINGS),

        //Billing Features
        BILLING(SETTINGS);


        private final AccessGroups accessGroups;

        Feature(AccessGroups accessGroups) {
            this.accessGroups = accessGroups;
        }

        private static final Feature[] features = values();

        public static List<Feature> getFeaturesForAccessGroup(AccessGroups accessGroups) {
            List<Feature> featureList = new ArrayList<>();
            for (Feature feature : getFeatures()) {
                if (feature.accessGroups.equals(accessGroups)) {
                    featureList.add(feature);
                }
            }
            return featureList;
        }

        public static Feature[] getFeatures() {
            return features;
        }
    }

    public enum ReadWriteAccess {
        READ,
        READ_WRITE
    }

    public RBAC(int userId, Role role) {
        this.userId = userId;
        this.role = role;
    }

    public RBAC(int userId, Role role, int accountId) {
        this.userId = userId;
        this.role = role;
        this.accountId = accountId;
    }

    public RBAC() {
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }


    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }
}
