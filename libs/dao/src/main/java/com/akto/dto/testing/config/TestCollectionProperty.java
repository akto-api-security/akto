package com.akto.dto.testing.config;

import com.akto.util.enums.GlobalEnums;

import java.util.Collections;
import java.util.List;

public class TestCollectionProperty {
    public enum Status {
        PENDING, DONE
    }
    public enum Type {
        CUSTOM_AUTH, TEST_YAML_KEYWORD, ROLE
    }
    public enum Id {
//        CSRF_TOKEN_HEADER("Csrf token header", Type.CUSTOM_AUTH, Collections.singletonList(GlobalEnums.TestCategory.NO_AUTH)),
        PASSWORD_RESET_ENDPOINT("Password reset endpoint", Type.TEST_YAML_KEYWORD, Collections.singletonList(GlobalEnums.TestCategory.NO_AUTH)),
        SIGNUP_ENDPOINT("User registration endpoint", Type.TEST_YAML_KEYWORD, Collections.singletonList(GlobalEnums.TestCategory.NO_AUTH)),
        LOGIN_ENDPOINT("Login endpoint", Type.TEST_YAML_KEYWORD, Collections.singletonList(GlobalEnums.TestCategory.NO_AUTH)),
        LOCKED_ACCOUNT_SYSTEM_ROLE("Locked account role", Type.ROLE, Collections.singletonList(GlobalEnums.TestCategory.NO_AUTH)),
        LOGGED_OUT_SYSTEM_ROLE("Logged out account role", Type.ROLE, Collections.singletonList(GlobalEnums.TestCategory.NO_AUTH)),
        ATTACKER_TOKEN("Attacker account role", Type.ROLE, Collections.singletonList(GlobalEnums.TestCategory.BOLA)),
//        SESSION_TOKEN_HEADER_KEY("Session token header key", Type.CUSTOM_AUTH, Collections.singletonList(GlobalEnums.TestCategory.NO_AUTH)),
        AUTH_TOKEN("Authentication token header key", Type.CUSTOM_AUTH, Collections.singletonList(GlobalEnums.TestCategory.NO_AUTH)),
        ADMIN("Admin role token", Type.ROLE,Collections.singletonList(GlobalEnums.TestCategory.BFLA)),
        MEMBER("Member role token", Type.ROLE,Collections.singletonList(GlobalEnums.TestCategory.BFLA)),
        LOGIN_2FA_INCOMPLETE_SYSTEM_ROLE("Incomplete MFA user token", Type.ROLE,Collections.singletonList(GlobalEnums.TestCategory.BFLA));


        final String title;
        final Type type;
        final List<GlobalEnums.TestCategory> impactingCategories;

        Id(String title, Type type, List<GlobalEnums.TestCategory> impactingCategories) {
            this.title = title;
            this.type = type;
            this.impactingCategories = impactingCategories;
        }

        public String getTitle() {
            return title;
        }

        public Type getType() {
            return this.type;
        }

        public List<GlobalEnums.TestCategory> getImpactingCategories() {
            return impactingCategories;
        }
    }

    String name;
    public static final String NAME = "name";

    String lastUpdatedUser;
    int lastUpdatedEpoch;
    List<String> values;
    int apiCollectionId;
    public static final String API_COLLECTION_ID = "apiCollectionId";

    public TestCollectionProperty() {}
    public TestCollectionProperty(String name, String lastUpdatedUser, int lastUpdatedEpoch, List<String> values, List<GlobalEnums.TestCategory> impactingCategories, int apiCollectionId) {
        this.name = name;
        this.lastUpdatedUser = lastUpdatedUser;
        this.lastUpdatedEpoch = lastUpdatedEpoch;
        this.values = values;
        this.apiCollectionId = apiCollectionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastUpdatedUser() {
        return lastUpdatedUser;
    }

    public void setLastUpdatedUser(String lastUpdatedUser) {
        this.lastUpdatedUser = lastUpdatedUser;
    }

    public int getLastUpdatedEpoch() {
        return lastUpdatedEpoch;
    }

    public void setLastUpdatedEpoch(int lastUpdatedEpoch) {
        this.lastUpdatedEpoch = lastUpdatedEpoch;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
}
