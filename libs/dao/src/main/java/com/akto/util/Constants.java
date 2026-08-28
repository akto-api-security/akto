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
    public static final String AKTO_COVERAGE_FLAG = "x-akto-coverage-check";
    public static final String AKTO_ATTACH_FILE = "x-akto-attach-file";
    public static final String AKTO_TOKEN_KEY = "x-akto-key";
    public static final String AKTO_NODE_ID = "x-akto-node";
    public static final String AKTO_REMOVE_AUTH= "x-akto-remove-auth";
    public static final String AKTO_AGENT_CONVERSATIONS= "x-agent-conversations";
    public static final String AKTO_MESSAGE_ID_HEADER = "x-akto-message-id";
    public static final String AKTO_SOURCE_HEADER = "x-akto-source";
    public static final String AKTO_SOURCE_RED_TEAMING = "red-teaming";

    public static final String LOCAL_KAFKA_BROKER_URL = System.getenv("KAFKA_BROKER_URL") != null ? System.getenv("KAFKA_BROKER_URL") : "localhost:29092"; // run kafka process with name kafka1 in docker
    // Optional per-instance namespace so multiple mini-testing instances can share one Kafka
    // broker, each with its own topic + consumer group. Unset/empty => legacy names (backwards compatible).
    public static final String AKTO_TOPIC_PREFIX = System.getenv("AKTO_TOPIC_PREFIX");
    private static String withTopicPrefix(String base) {
        return (AKTO_TOPIC_PREFIX != null && !AKTO_TOPIC_PREFIX.isEmpty())
                ? AKTO_TOPIC_PREFIX + "." + base
                : base;
    }
    public static final String TEST_RESULTS_TOPIC_NAME = withTopicPrefix("akto.test.messages");
    public static final String AKTO_KAFKA_GROUP_ID_CONFIG = withTopicPrefix("testing-group");

    // For k8s horizontal scaling: identical replicas get identical env, so there's no per-replica
    // value to namespace on (unlike AKTO_TOPIC_PREFIX). When true, derive the topic/group name from
    // the run's own summaryId instead - Mongo's atomic run-claiming already guarantees no two
    // replicas ever process the same summaryId, so this isolates concurrent runs with zero
    // per-replica config. Falls back to the plain constants above when unset (no behavior change).
    public static final boolean CONCURRENT_TESTING = (StringUtils.hasLength(System.getenv("CONCURRENT_TESTING")) && System.getenv("CONCURRENT_TESTING").equals("true"));

    public static String getTestResultsTopicName(String runIdentifier) {
        if (CONCURRENT_TESTING && runIdentifier != null && !runIdentifier.isEmpty()) {
            return withTopicPrefix(runIdentifier + ".akto.test.messages");
        }
        return TEST_RESULTS_TOPIC_NAME;
    }

    public static String getKafkaGroupIdConfig(String runIdentifier) {
        if (CONCURRENT_TESTING && runIdentifier != null && !runIdentifier.isEmpty()) {
            return withTopicPrefix(runIdentifier + ".testing-group");
        }
        return AKTO_KAFKA_GROUP_ID_CONFIG;
    }
    public static final int AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG = 1; // read one message at a time
    public static final String TESTING_STATE_FOLDER_PATH = System.getenv("TESTING_STATE_FOLDER_PATH") != null ? System.getenv("TESTING_STATE_FOLDER_PATH") : "testing-info";
    public static final String TESTING_STATE_FILE_NAME = "testing-state.json";
    public static final boolean IS_NEW_TESTING_ENABLED = (StringUtils.hasLength(System.getenv("NEW_TESTING_ENABLED")) && System.getenv("NEW_TESTING_ENABLED").equals("true"));
    public static final boolean KAFKA_DEBUG_MODE = (StringUtils.hasLength(System.getenv("KAFKA_DEBUG_MODE")) && System.getenv("KAFKA_DEBUG_MODE").equals("true"));
    public static final int MAX_REQUEST_TIMEOUT = StringUtils.hasLength(System.getenv("MAX_REQUEST_TIMEOUT")) ? Integer.parseInt(System.getenv("MAX_REQUEST_TIMEOUT")) : 15000;
    public static final int LINGER_MS_KAFKA = StringUtils.hasLength(System.getenv("LINGER_MS_KAFKA")) ?  Integer.parseInt(System.getenv("LINGER_MS_KAFKA")) : 5000;
    public static final int MAX_POLL_INTERVAL_MS = StringUtils.hasLength(System.getenv("MAX_POLL_INTERVAL_MS")) ? Integer.parseInt(System.getenv("MAX_POLL_INTERVAL_MS")) : 300000;
    public static final int MAX_WAIT_FOR_SLEEP = StringUtils.hasLength(System.getenv("MAX_WAIT_FOR_SLEEP")) ? Integer.parseInt(System.getenv("MAX_WAIT_FOR_SLEEP")) : 60 ;
    public static final boolean sendLogsForTesting = (StringUtils.hasLength(System.getenv("SEND_LOGS_FOR_TESTING")) && System.getenv("SEND_LOGS_FOR_TESTING").equals("true"));
    public static final String UNDERSCORE = "_";

    public static final String AGENT_BASE_URL = StringUtils.hasLength(System.getenv("AGENT_BASE_URL")) ? System.getenv("AGENT_BASE_URL") : "http://localhost:5500";
    public static final String AUTOMATED_AGENT_BASE_URL = StringUtils.hasLength(System.getenv("AUTOMATED_AGENT_BASE_URL")) ? System.getenv("AUTOMATED_AGENT_BASE_URL") : "http://localhost:8000";

    public static final String AGENT_QUERY_LOGS_SERVICE_URL = StringUtils.hasLength(System.getenv("AGENT_QUERY_LOGS_SERVICE_URL")) ? System.getenv("AGENT_QUERY_LOGS_SERVICE_URL") : "https://observer.akto.io";


    public final static String _AKTO = "AKTO";
    public static final String AKTO_MCP_SERVER_TAG = "mcp-server";
    public static final String AKTO_GEN_AI_TAG = "gen-ai";
    public static final String AKTO_BROWSER_LLM_TAG = "browser-llm";
    public static final String AKTO_GUARD_RAIL_TAG = "guard-rail";
    public static final String AKTO_MCP_TOOLS_TAG = "mcp-tool";
    public static final String AKTO_MCP_RESOURCES_TAG = "mcp-resource";
    public static final String AKTO_MCP_PROMPTS_TAG = "mcp-prompt";
    public static final String HOST_HEADER = "Host";
    public static final String X_TRANSPORT_HEADER = "x-transport";
    public static final String STDIO_TRANSPORT = "STDIO";
    public static final String HTTP_TRANSPORT = "HTTP";

    // Protocol types
    public static final String WEBSOCKET_PROTOCOL = "WEBSOCKET";
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
    public static final String AI_AGENT_SOURCE_VERTEX = "VERTEX_AI";
    public static final String AI_AGENT_SOURCE_SNOWFLAKE = "SNOWFLAKE";
    public static final String AI_AGENT_SOURCE_ARCADE_DEV = "ARCADE_DEV";
    public static final String AI_AGENT_SOURCE_MICROSOFT_DEFENDER = "DEFENDER";
    public static final String AI_AGENT_SOURCE_AWS_BEDROCK="AWS_BEDROCK";
    public static final String AI_AGENT_SOURCE_ENDPOINT = "ENDPOINT";
    public static final String AI_AGENT_TAG_BOT_NAME = "bot-name";
    public static final String AI_AGENT_TAG_BOT_SCHEMA_NAME = "bot-schemaname";
    public static final String AI_AGENT_TAG_SOURCE = "source";
    public static final String AI_AGENT_TAG_CONNECTOR = "connector";
    public static final String AI_AGENT_CONNECTOR_MICROSOFT_DEFENDER = "MICROSOFT_DEFENDER";
    public static final String AI_AGENT_CONNECTOR_SENTINEL = "SENTINELONE";
    public static final String AI_AGENT_CONNECTOR_CROWDSTRIKE = "CROWDSTRIKE";
    public static final String AI_AGENT_APP_NAME = "ai-agent";
    public static final String COPILOT_STUDIO_AI_AGENT_NAME = "copilot-studio";
    public static final String SAAS_AGENT_TAG_NAME = "saas-agent";


    public static final String AKTO_ENDPOINT_SOURCE_TAG = "source";
    public static final String AKTO_COPILOT_SOURCE_VALUE = "COPILOT_STUDIO";
    public static final String AKTO_COPILOT_BOT_NAME_TAG = "bot-name";
    public static final String AKTO_COPILOT_BOT_SCHEMA_TAG = "bot-schemaname";
    public static final String AKTO_COPILOT_BOT_ENVIRONMENT_TAG = "bot-environment-id";
    public static final String AKTO_COPILOT_CONVERSATION_URL_PREFIX = "/copilot/conversation";
    public static final String AKTO_COPILOT_INVENTORY_TAG = "copilot-inventory";

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_FAILED = "Failed";
}