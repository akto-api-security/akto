package com.akto.util.enums;

public class GlobalEnums {
    /* * * * * * * *  Enums for Testing run issues * * * * * * * * * * * *  */

    public enum TestErrorSource { // Whether issue came from runtime or automated testing via dashboard
        AUTOMATED_TESTING,
        RUNTIME
    }

    /* Category of tests perfomred */
    public enum TestCategory {
        BOLA("BOLA", Severity.HIGH),
        ADD_USER_ID("ADD_USER_ID", Severity.HIGH),
        PRIVILEGE_ESCALATION("PRIVILEGE_ESCALATION", Severity.HIGH),
        NO_AUTH("NO_AUTH", Severity.HIGH);
        private final String name;
        private final Severity severity;

        TestCategory(String name, Severity severity) {
            this.name = name;
            this.severity = severity;
        }

        public String getName() {
            return name;
        }

        public Severity getSeverity() {
            return severity;
        }
    }

    public enum TestSubCategory {
        REPLACE_AUTH_TOKEN("REPLACE_AUTH_TOKEN", TestCategory.BOLA),
        ADD_USER_ID("ADD_USER_ID", TestCategory.ADD_USER_ID),
        ADD_METHOD_IN_PARAMETER("ADD_METHOD_IN_PARAMETER", TestCategory.PRIVILEGE_ESCALATION),
        ADD_METHOD_OVERRIDE_HEADERS("ADD_METHOD_OVERRIDE_HEADERS", TestCategory.PRIVILEGE_ESCALATION),
        CHANGE_METHOD("CHANGE_METHOD", TestCategory.PRIVILEGE_ESCALATION),
        REMOVE_TOKENS("REMOVE_TOKENS", TestCategory.NO_AUTH),
        PARAMETER_POLLUTION("PARAMETER_POLLUTION", TestCategory.BOLA),
        REPLACE_AUTH_TOKEN_OLD_VERSION("REPLACE_AUTH_TOKEN_OLD_VERSION", TestCategory.BOLA),
        JWT_NONE_ALGO("JWT_NONE_ALGO", TestCategory.NO_AUTH);

        private final String name;
        private final TestCategory superCategory;
        private static final TestSubCategory[] valuesArray = values();

        TestSubCategory(String name, TestCategory superCategory) {
            this.name = name;
            this.superCategory = superCategory;
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
