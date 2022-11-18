package com.akto.util.enums;

public class GlobalEnums {
    /* * * * * * * *  Enums for Testing run issues * * * * * * * * * * * *  */

    public enum TestErrorSource { // Whether issue came from runtime or automated testing via dashboard
        AUTOMATED_TESTING,
        RUNTIME
    }

    /* Category of tests perfomred */
    public enum TestCategory {
        BOLA ("BOLA", Severity.HIGH),
        ADD_USER_ID("ADD_USER_ID", Severity.HIGH),
        PRIVILEGE_ESCALATION("PRIVILEGE_ESCALATION", Severity.HIGH),
        NO_AUTH("NO_AUTH", Severity.HIGH);
        private final String name;
        private final Severity severity;

        private static final TestCategory[] valuesArray = values();
        TestCategory(String name, Severity severity) {
            this.name = name;
            this.severity = severity;
        }

        public static TestCategory[] getValuesArray () {
            return valuesArray;
        }

        public static TestCategory getTestCategory (String category) {
            for (TestCategory testCategory : valuesArray) {
                if (testCategory.name.equalsIgnoreCase(category)) {
                    return testCategory;
                }
            }
            throw new IllegalStateException("Unknown TestCategory passed :- " + category);
        }

        public String getName() {
            return name;
        }

        public Severity getSeverity () {
            return severity;
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
