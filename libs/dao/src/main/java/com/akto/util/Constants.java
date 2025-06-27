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
    public static final int MAX_WAIT_FOR_SLEEP = StringUtils.hasLength(System.getenv("MAX_WAIT_FOR_SLEEP")) ? Integer.parseInt(System.getenv("MAX_WAIT_FOR_SLEEP")) : 3 * 60 ;
    public static final String UNDERSCORE = "_";

    public final static String _AKTO = "AKTO";
    public static final String AKTO_DECRYPT_HEADER= "x-akto-decode";

    public final static String default_token = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjIsInVzZXJuYW1lIjoiYXR0YWNrZXIiLCJlbWFpbCI6ImF0dGFja2VyQGdtYWlsLmNvbSIsInBhc3N3b3JkIjoiMTY2YzU1YjUxZmQ1ZmJkYTg3NWFiNmMzMTg0NDIyNTUiLCJyb2xlIjoiY3VzdG9tZXIiLCJkZWx1eGVUb2tlbiI6IiIsImxhc3RMb2dpbklwIjoiIiwicHJvZmlsZUltYWdlIjoiYXNzZXRzL3B1YmxpYy9pbWFnZXMvdXBsb2Fkcy9kZWZhdWx0LnN2ZyIsInRvdHBTZWNyZXQiOiIiLCJpc0FjdGl2ZSI6dHJ1ZSwiY3JlYXRlZEF0IjoiMjAyMy0wMy0xMCAwNTozOToxOC4yOTkgKzAwOjAwIiwidXBkYXRlZEF0IjoiMjAyMy0wMy0xMCAwNTozOToxOC4yOTkgKzAwOjAwIiwiZGVsZXRlZEF0IjpudWxsfSwiaWF0IjoxNjc4NDI3Mjg1LCJleHAiOjE5OTM3ODcyODV9.hBAPAJm1FZIpDb7fm4nT3GY_u3R0KeyjqK-Ns5pcz22RN5_qhWt-K98y8DdELjUsRKVodAFPOki0QBmAqdhp5umgJB1ZPk4uEKLg2AI6ztr5729UezMbQozbIOu8UFmVm2crJn5YZKCbPKCcDwRUpisICbjDtJ5PD41RhZfLut8";
}