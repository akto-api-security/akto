package com.akto.util.enums;

public class GlobalEnums {
    /* * * * * * * *  Enums for Testing run issues * * * * * * * * * * * *  */

    public enum TestErrorSource { // Whether issue came from runtime or automated testing via dashboard
        AUTOMATED_TESTING("testing"),
        RUNTIME("runtime");

        private final String name;

        TestErrorSource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /* Category of tests perfomred */
    public enum TestCategory {
        BOLA("BOLA", Severity.HIGH, "Broken Object Level Authorization (BOLA)"),
        NO_AUTH("NO_AUTH", Severity.HIGH, "Broken User Authentication (BUA)"),
        BFLA("BFLA", Severity.HIGH, "Broken Function Level Authorization (BFLA)");
        private final String name;
        private final Severity severity;
        private final String displayName;

        TestCategory(String name, Severity severity, String displayName) {
            this.name = name;
            this.severity = severity;
            this.displayName = displayName;
        }

        public String getName() {
            return name;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum TestSubCategory {
        REPLACE_AUTH_TOKEN("REPLACE_AUTH_TOKEN", TestCategory.BOLA, "Attacker can access resources of any user by changing the auth token in request."),
        ADD_USER_ID("ADD_USER_ID", TestCategory.BOLA, "Attacker can access resources of any user by adding user_id in URL."),
        ADD_METHOD_IN_PARAMETER("ADD_METHOD_IN_PARAMETER", TestCategory.BOLA, "Attacker can access resources of any user by replacing method of the endpoint (eg: changemethod from get to post). This way attacker can get access to unauthorized endpoints."),
        ADD_METHOD_OVERRIDE_HEADERS("ADD_METHOD_OVERRIDE_HEADERS", TestCategory.BOLA, "Attacker can access resources of any user by replacing method of the endpoint (eg: changemethod from get to post). This way attacker can get access to unauthorized endpoints."),
        CHANGE_METHOD("CHANGE_METHOD", TestCategory.BOLA, "Attacker can access resources of any user by replacing method of the endpoint (eg: changemethod from get to post). This way attacker can get access to unauthorized endpoints."),
        REMOVE_TOKENS("REMOVE_TOKENS", TestCategory.NO_AUTH, "API doesn't validate the authenticity of token. Attacker can remove the auth token and access the endpoint."),
        PARAMETER_POLLUTION("PARAMETER_POLLUTION", TestCategory.BOLA, "Attacker can access resources of any user by introducing multiple parameters with same name."),
        REPLACE_AUTH_TOKEN_OLD_VERSION("REPLACE_AUTH_TOKEN_OLD_VERSION", TestCategory.BOLA, "Attacker can access resources of any user by changing the auth token in request and using older version of an API"),
        JWT_NONE_ALGO("JWT_NONE_ALGO", TestCategory.NO_AUTH, "Since NONE Algorithm JWT is accepted by the server the attacker can tamper with the payload of JWT and access protected resources."),
        BFLA("BFLA", TestCategory.BFLA, "Less privileged attacker can access admin resources"),
        JWT_INVALID_SIGNATURE("JWT_INVALID_SIGNATURE", TestCategory.NO_AUTH, "Since server is not validating the JWT signature the attacker can tamper with the payload of JWT and access protected resources"),
        ADD_JKU_TO_JWT("ADD_JKU_TO_JWT", TestCategory.NO_AUTH, "Since Host server is using the JKU field of the JWT without validating, attacker can tamper with the payload of JWT and access protected resources.");

        private final String name;
        private final TestCategory superCategory;
        private final String issueDescription;
        private static final TestSubCategory[] valuesArray = values();

        TestSubCategory(String name, TestCategory superCategory, String issueDescription) {
            this.name = name;
            this.superCategory = superCategory;
            this.issueDescription = issueDescription;
        }

        public static TestSubCategory[] getValuesArray() {
            return valuesArray;
        }

        public static TestSubCategory getTestCategory(String category) {
            for (TestSubCategory testSubCategory : valuesArray) {
                if (testSubCategory.name.equalsIgnoreCase(category)) {
                    return testSubCategory;
                }
            }
            throw new IllegalStateException("Unknown TestCategory passed :- " + category);
        }

        public String getName() {
            return name;
        }

        public TestCategory getSuperCategory() {
            return superCategory;
        }

        public String getIssueDescription() {
            return issueDescription;
        }
    }

    public enum Severity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFO
    }

    public enum TestRunIssueStatus {
        OPEN,
        IGNORED,
        FIXED
    }


    /* ********************************************************************** */
}
