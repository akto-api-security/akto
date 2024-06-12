package com.akto.dto;


import org.bson.types.ObjectId;

import java.util.*;

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
        final AccessGroups[] allowedAccessForRole;


        Role(String name) {
            this.name = name;
            this.roleHierarchy = Role.roleHierarchy(this);
            this.allowedAccessForRole = Role.createAccessGroupsForRole(this);
        }

        private static AccessGroups[] createAccessGroupsForRole(Role role) {
            switch (role) {
                case ADMIN:
                    return new AccessGroups[]{
                            AccessGroups.INVENTORY
                                    .addFeature(Feature.API_COLLECTIONS.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.SENSITIVE_DATA.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.TRAFFIC_FILTERS.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.DEFAULT_PAYLOADS.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.TAGS.setWriteType(WriteType.READ_WRITE)),
                            AccessGroups.TESTING
                                    .addFeature(Feature.START_TEST_RUN.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.TEST_RESULTS.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.TEST_ROLES.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.USER_CONFIG.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.AUTH_TYPE.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.ISSUES.setWriteType(WriteType.READ_WRITE)),
                            AccessGroups.TEST_LIBRARY
                                    .addFeature(Feature.TEST_EDITOR.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.EXTERNAL_TEST_LIBRARY.setWriteType(WriteType.READ_WRITE)),
                            AccessGroups.SETTINGS
                                    .addFeature(Feature.INTEGRATIONS.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.METRICS.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.LOGS.setWriteType(WriteType.READ_WRITE))
                                    .addFeature(Feature.BILLING.setWriteType(WriteType.READ_WRITE))

                    };
                case MEMBER:
                case DEVELOPER:
                case GUEST:
            }
            return null;
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

        final private Set<Feature> features;

        AccessGroups() {
            features = new HashSet<>();
        }

        public AccessGroups setFeatures(Feature[] features) {
            this.features.addAll(Arrays.asList(features));
            return this;
        }

        public AccessGroups addFeature(Feature feature) {
            this.features.add(feature);
            return this;
        }
    }

    public enum Feature {
        //Inventory Features
        API_COLLECTIONS,
        SENSITIVE_DATA,
        TRAFFIC_FILTERS,
        DEFAULT_PAYLOADS,
        TAGS,

        //Testrun features
        START_TEST_RUN,
        TEST_RESULTS,
        TEST_ROLES,
        USER_CONFIG,
        AUTH_TYPE,
        ISSUES,

        //Test Library features
        TEST_EDITOR,
        EXTERNAL_TEST_LIBRARY,

        //Settings features
        INTEGRATIONS,
        METRICS,
        LOGS,

        //Billing Features
        BILLING;


        private WriteType writeType;
        public Feature setWriteType(WriteType writeType) {
            this.writeType = writeType;
            return this;
        }
    }

    public enum WriteType {
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
