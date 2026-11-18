package com.akto.testing.login_flow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Node-id helpers for login flows. TestExecutor.convertToWorkflowGraph numbers login steps x1, x3, x5 ...
 * so step k (1-based) is node x(2k-1).
 */
public final class LoginFlowNodes {

    private static final Pattern NODE_ID = Pattern.compile("^x(\\d+)$");

    private LoginFlowNodes() {}

    /** @return the node number of "xN", or -1 when the id is not of that form. */
    public static int nodeNumber(String nodeId) {
        if (nodeId == null) return -1;
        Matcher m = NODE_ID.matcher(nodeId);
        if (!m.matches()) return -1;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String nodeIdForStep(int step) {
        return "x" + (2 * step - 1);
    }

    /** "x3 (step 2)" for login node ids, the id itself otherwise. */
    public static String label(String nodeId) {
        int n = nodeNumber(nodeId);
        if (n <= 0 || n % 2 == 0) return String.valueOf(nodeId);
        return nodeId + " (step " + ((n + 1) / 2) + ")";
    }
}
