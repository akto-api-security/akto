package com.akto.util;

import java.util.Arrays;
import java.util.List;

public class Constants {
    private Constants() {}

    public static final String ID = "_id";

    public static final String TIMESTAMP = "timestamp";

    public static final String AWS_REGION = "AWS_REGION";

    public static final String AWS_ACCOUNT_ID = "AWS_ACCOUNT_ID";

    public static final int ONE_MONTH_TIMESTAMP = (60 * 60 * 24 * 30) ;

    public static final int ONE_DAY_TIMESTAMP = ( 60 * 60 * 24 );

    public static final String AKTO_IGNORE_FLAG = "x-akto-ignore";
    public static final String AKTO_ATTACH_FILE = "x-akto-attach-file";
    public static final String AKTO_TOKEN_KEY = "x-akto-key";
    public static final String AKTO_NODE_ID = "x-akto-node";
    public static final String AKTO_REMOVE_AUTH= "x-akto-remove-auth";

    public static final String AKTO_MCP_SERVER_TAG = "mcp-server";
    public static final String AKTO_MCP_TOOLS_TAG = "mcp-tool";
    public static final String AKTO_MCP_RESOURCES_TAG = "mcp-resource";

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_FAILED = "Failed";

    // Constants for routing tag filtering
    public static final int ROUTING_SKIP_ACCOUNT_ID = 1736798101;
    public static final List<String> ROUTING_TAG_SUFFIXES = Arrays.asList("booking-creation", "booking-query", "cashout-api", "ebe_creditcard_service_netcore", "partner-credit-card-api");
}
