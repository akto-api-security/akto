package com.akto.util;

import org.springframework.util.StringUtils;

public class Constants {
    private Constants() {}

    public static final String ID = "_id";

    public static final String TIMESTAMP = "timestamp";

    public static final String AWS_REGION = "AWS_REGION";

    public static final String AWS_ACCOUNT_ID = "AWS_ACCOUNT_ID";

    public static final int ONE_MONTH_TIMESTAMP = (60 * 60 * 24 * 30) ;

    public static final int ONE_DAY_TIMESTAMP = ( 60 * 60 * 24 );

    public static final int TWO_HOURS_TIMESTAMP = ( 60 * 60 * 2 );

    public static final String AKTO_IGNORE_FLAG = "x-akto-ignore";
    public static final String AKTO_ATTACH_FILE = "x-akto-attach-file";
    public static final String AKTO_TOKEN_KEY = "x-akto-key";
    public static final String AKTO_NODE_ID = "x-akto-node";
    public static final String AKTO_REMOVE_AUTH= "x-akto-remove-auth";
    public static final String AKTO_AGENT_CONVERSATIONS= "x-agent-conversations";

    public static final String LOCAL_KAFKA_BROKER_URL = System.getenv("KAFKA_BROKER_URL") != null ? System.getenv("KAFKA_BROKER_URL") : "localhost:29092"; // run kafka process with name kafka1 in docker
    public static final String TEST_RESULTS_TOPIC_NAME = "akto.test.messages";
    public static final String AKTO_KAFKA_GROUP_ID_CONFIG =  "testing-group";
    public static final int AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG = 1; // read one message at a time
    public static final String TESTING_STATE_FOLDER_PATH = System.getenv("TESTING_STATE_FOLDER_PATH") != null ? System.getenv("TESTING_STATE_FOLDER_PATH") : "testing-info";
    public static final String TESTING_STATE_FILE_NAME = "testing-state.json";
    public static final boolean IS_NEW_TESTING_ENABLED = (StringUtils.hasLength(System.getenv("NEW_TESTING_ENABLED")) && System.getenv("NEW_TESTING_ENABLED").equals("true"));
    public static final boolean KAFKA_DEBUG_MODE = (StringUtils.hasLength(System.getenv("KAFKA_DEBUG_MODE")) && System.getenv("KAFKA_DEBUG_MODE").equals("true"));
    public static final int MAX_REQUEST_TIMEOUT = StringUtils.hasLength(System.getenv("MAX_REQUEST_TIMEOUT")) ? Integer.parseInt(System.getenv("MAX_REQUEST_TIMEOUT")) : 15000;
    public static final int LINGER_MS_KAFKA = StringUtils.hasLength(System.getenv("LINGER_MS_KAFKA")) ?  Integer.parseInt(System.getenv("LINGER_MS_KAFKA")) : 5000;
    public static final int MAX_WAIT_FOR_SLEEP = StringUtils.hasLength(System.getenv("MAX_WAIT_FOR_SLEEP")) ? Integer.parseInt(System.getenv("MAX_WAIT_FOR_SLEEP")) : 60 ;
    public static final boolean sendLogsForTesting = (StringUtils.hasLength(System.getenv("SEND_LOGS_FOR_TESTING")) && System.getenv("SEND_LOGS_FOR_TESTING").equals("true"));
    public static final String UNDERSCORE = "_";

    public static final String AGENT_BASE_URL = StringUtils.hasLength(System.getenv("AGENT_BASE_URL")) ? System.getenv("AGENT_BASE_URL") : "http://localhost:5500";


    public final static String _AKTO = "AKTO";
    public static final String AKTO_MCP_SERVER_TAG = "mcp-server";
    public static final String AKTO_GEN_AI_TAG = "gen-ai";
    public static final String AKTO_GUARD_RAIL_TAG = "guard-rail";
    public static final String AKTO_MCP_TOOLS_TAG = "mcp-tool";
    public static final String AKTO_MCP_RESOURCES_TAG = "mcp-resource";
    public static final String AKTO_MCP_PROMPTS_TAG = "mcp-prompt";
    public static final String HOST_HEADER = "Host";
    public static final String X_TRANSPORT_HEADER = "x-transport";
    public static final String STDIO_TRANSPORT = "STDIO";
    public static final String HTTP_TRANSPORT = "HTTP";

    // RAG and Vector Database Tags
    public static final String AKTO_RAG_DATABASE_TAG = "rag-database";
    public static final String AKTO_VECTOR_SEARCH_TAG = "vector-search";
    public static final String AKTO_EMBEDDING_TAG = "embedding";
    public static final String AKTO_SIMILARITY_SEARCH_TAG = "similarity-search";
    public static final String AKTO_RAG_COLLECTION_TAG = "rag-collection";

    // AI Agent source type constants
    public static final String AI_AGENT_SOURCE_N8N = "N8N";
    public static final String AI_AGENT_SOURCE_LANGCHAIN = "LANGCHAIN";
    public static final String AI_AGENT_SOURCE_COPILOT_STUDIO = "COPILOT_STUDIO";
    public static final String AI_AGENT_SOURCE_DATABRICS = "DATABRICKS";
    public static final String AI_AGENT_SOURCE_SNOWFLAKE = "SNOWFLAKE";
    public static final String AI_AGENT_TAG_BOT_NAME = "bot-name";
    public static final String AI_AGENT_TAG_SOURCE = "source";

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_FAILED = "Failed";
}