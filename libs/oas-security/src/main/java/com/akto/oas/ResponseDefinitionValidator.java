package com.akto.oas;

import io.swagger.v3.oas.models.PathItem;

import java.util.*;

public class ResponseDefinitionValidator {

    private static boolean commonElementExists(Collection<String> list1, Collection<String> list2) {
        for (String a: list1) {
            if (list2.contains(a)) return true;
        }
        return false;
    }

    public static List<Issue> validateResponseDefinitions(PathItem.HttpMethod httpMethod, Set<String> responseCodes,
                                                     List<String> path, boolean securityDefined) {
        List<Issue> issues = new ArrayList<>();

        if (securityDefined && !responseCodes.contains("401")) {
            issues.add(Issue.generate401Issue(path));
        }

        if (securityDefined && !responseCodes.contains("403")) {
            issues.add(Issue.generate403Issue(path));
        }


        if (!responseCodes.contains("400")) {
            issues.add(Issue.generate400Issue(path));
        }

        if (!responseCodes.contains("429")) {
            issues.add(Issue.generate429Issue(path));
        }

        if (!responseCodes.contains("500")) {
            issues.add(Issue.generate500Issue(path));
        }

        switch (httpMethod) {
            case POST:
                if (commonElementExists(Arrays.asList("200", "201", "202", "204"), responseCodes)){
                    issues.add(Issue.generatePOST20xIssue(path));
                }
                if (!responseCodes.contains("406")) {
                    issues.add(Issue.generatePost406Issue(path));
                }
                if (!responseCodes.contains("415")) {
                    issues.add(Issue.generatePost415Issue(path));
                }
                break;
            case GET:
                if (commonElementExists(Arrays.asList("200", "202"), responseCodes)){
                    issues.add(Issue.generateGet20xIssue(path));
                }
                if (!responseCodes.contains("404")) {
                    issues.add(Issue.generateGet404Issue(path));
                }
                if (!responseCodes.contains("406")) {
                    issues.add(Issue.generateGet406Issue(path));
                }
                break;
            case PUT:
                if (commonElementExists(Arrays.asList("200", "201", "202", "204"), responseCodes)){
                    issues.add(Issue.generatePut20xIssue(path));
                }
                if (!responseCodes.contains("404")) {
                    issues.add(Issue.generatePut404Issue(path));
                }
                if (!responseCodes.contains("415")) {
                    issues.add(Issue.generatePut415Issue(path));
                }
                break;
            case PATCH:
                if (commonElementExists(Arrays.asList("200", "201", "202", "204"), responseCodes)){
                    issues.add(Issue.generatePatch20xIssue(path));
                }

                if (!responseCodes.contains("406")) {
                    issues.add(Issue.generatePatch406Issue(path));
                }
                if (!responseCodes.contains("415")) {
                    issues.add(Issue.generatePatch415Issue(path));
                }
                break;
            case DELETE:
                if (commonElementExists(Arrays.asList("200", "201", "202", "204"), responseCodes)){
                    issues.add(Issue.generateDelete20xIssue(path));
                }
                if (!responseCodes.contains("404")) {
                    issues.add(Issue.generateDelete404Issue(path));
                }
                if (!responseCodes.contains("406")) {
                    issues.add(Issue.generateDelete406Issue(path));
                }
                break;
            case HEAD:
                if (!responseCodes.contains("404")) {
                    issues.add(Issue.generateHead404Issue(path));
                }

                if (commonElementExists(Arrays.asList("200", "202"), responseCodes)){
                    issues.add(Issue.generateHead20xIssue(path));
                }
                break;
            case OPTIONS:
                if (!responseCodes.contains("200")) {
                    issues.add(Issue.generateOptions200Issue(path));
                }
                break;
            case TRACE:
                if (!responseCodes.contains("200")) {
                    issues.add(Issue.generateTrace200Issue(path));
                }
                break;
        }


        return issues;
    }


}
