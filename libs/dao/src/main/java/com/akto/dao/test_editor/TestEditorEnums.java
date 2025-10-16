package com.akto.dao.test_editor;

public class TestEditorEnums {
    
    public enum DataOperands {
        CONTAINS_EITHER,
        CONTAINS_EITHER_CIDR,
        CONTAINS_ALL,
        NOT_CONTAINS,
        NOT_CONTAINS_EITHER,
        NOT_CONTAINS_CIDR,
        CONFORM_SCHEMA,
        REGEX,
        EQ,
        EQ_OBJ,
        GTE,
        GT,
        LTE,
        LT,
        NEQ,
        NEQ_OBJ,
        PARAM,
        CONTAINS_JWT,
        COOKIE_EXPIRE_FILTER,
        DATATYPE,
        BELONGS_TO_COLLECTIONS,
        VALUETYPE,
        MAGIC_VALIDATE,
        NOT_MAGIC_VALIDATE,
        CATEGORY,
        CONFIDENCE,
        ACTION,
        TYPE,
        PATTERN,
        REPLACEMENT_STRING
    }

    public enum CollectionOperands {
        FOR_ONE,
        FOR_ALL
    }

    public enum TermOperands {
        URL,
        METHOD,
        API_COLLECTION_ID,
        QUERY_PARAM,
        REQUEST_HEADERS,
        REQUEST_PAYLOAD,
        RESPONSE_HEADERS,
        RESPONSE_PAYLOAD,
        RESPONSE_CODE,
        SOURCE_IP,
        DESTINATION_IP,
        COUNTRY_CODE,
        TEST_TYPE,
    }

    public enum PredicateOperator {
        AND,
        OR,
        COMPARE_GREATER,
        SSRF_URL_HIT
    }

    public enum KeyValOperator {
        KEY,
        VALUE
    }

    public enum BodyOperator {
        LENGTH,
        PERCENTAGE_MATCH,
        PERCENTAGE_MATCH_SCHEMA,
        NLP_CLASSIFICATION
    }

    public enum ExtractOperator {
        EXTRACT,
        EXTRACTMULTIPLE
    }

    public enum OperandTypes {
        Data,
        Pred,
        Term,
        Collection,
        Payload,
        Body,
        Extract,
        Context
    }

    public enum ContextOperator {
        PRIVATE_VARIABLE_CONTEXT,
        PARAM_CONTEXT,
        ENDPOINT_IN_TRAFFIC_CONTEXT,
        INCLUDE_ROLES_ACCESS,
        EXCLUDE_ROLES_ACCESS,
        API_ACCESS_TYPE
    }


    public String getOperandValue(String key) {

        for (DataOperands operand: DataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (CollectionOperands operand: CollectionOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (TermOperands operand: TermOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (PredicateOperator operand: PredicateOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (KeyValOperator operand: KeyValOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (BodyOperator operand: BodyOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (ExtractOperator operand: ExtractOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (ContextOperator operand: ContextOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        return null;
    }

    public String getOperandType(String key) {

        for (DataOperands operand: DataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "data";
            }
        }
        
        for (CollectionOperands operand: CollectionOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "collection";
            }
        }

        for (TermOperands operand: TermOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "term";
            }
        }

        for (PredicateOperator operand: PredicateOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "pred";
            }
        }
        
        for (KeyValOperator operand: KeyValOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "payload";
            }
        }

        for (BodyOperator operand: BodyOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "body";
            }
        }

        for (ExtractOperator operand: ExtractOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "extract";
            }
        }

        for (ContextOperator operand: ContextOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "context";
            }
        }

        return null;
    }


    public enum ExecutorParentOperands {
        TYPE,
        REQUESTS
    }

    public enum RequestParentOperand {
        REQ
    }

    public enum TerminalExecutorDataOperands {
        API,
        DELETE_HEADER,
        DELETE_BODY_PARAM,
        DELETE_QUERY_PARAM,
        MODIFY_URL,
        MODIFY_METHOD,
        FOLLOW_REDIRECT,
        REMOVE_AUTH_HEADER,
        REPLACE_AUTH_HEADER,
        REPLACE_BODY,
        DELETE_GRAPHQL_FIELD,
        JWT_REPLACE_BODY,
        ATTACH_FILE,
        SEND_SSRF_REQ,
        FOR_EACH_COMBINATION,
        ACTION
    }

    public enum NonTerminalExecutorDataOperands {
        ADD_HEADER,
        ADD_BODY_PARAM,
        ADD_QUERY_PARAM,
        MODIFY_HEADER,
        MODIFY_BODY_PARAM,
        MODIFY_QUERY_PARAM,
        ADD_GRAPHQL_FIELD,
        ADD_UNIQUE_GRAPHQL_FIELD,
        MODIFY_GRAPHQL_FIELD,
        CONVERSATIONS_LIST
    }

    public enum TerminalNonExecutableDataOperands {
        TYPE,
        TEST_NAME,
        LABEL,
        SUCCESS,
        FAILURE,
        WAIT
    }

    public enum ValidateExecutorDataOperands {
        Validate
    }

    public enum LoopExecutorOperands {
        FOR_ALL,
        FOR_ONE
    }

    public enum ExecutorOperandTypes {
        Parent,
        Req,
        Terminal,
        NonTerminal,
        Data,
        TerminalNonExecutable,
        Validate,
        Loop,
        Dynamic
    }

    public String getExecutorOperandValue(String key) {

        for (ExecutorParentOperands operand: ExecutorParentOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (TerminalExecutorDataOperands operand: TerminalExecutorDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (NonTerminalExecutorDataOperands operand: NonTerminalExecutorDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (TerminalNonExecutableDataOperands operand: TerminalNonExecutableDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        for (ValidateExecutorDataOperands operand: ValidateExecutorDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return operand.toString();
            }
        }

        return key;
    }

    public String getExecutorOperandType(String key, String parentNodeType) {

        for (ExecutorParentOperands operand: ExecutorParentOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return ExecutorOperandTypes.Parent.toString().toLowerCase();
            }
        }

        for (RequestParentOperand operand: RequestParentOperand.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return ExecutorOperandTypes.Req.toString().toLowerCase();
            }
        }

        for (TerminalExecutorDataOperands operand: TerminalExecutorDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return ExecutorOperandTypes.Terminal.toString().toLowerCase();
            }
        }

        for (NonTerminalExecutorDataOperands operand: NonTerminalExecutorDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return ExecutorOperandTypes.NonTerminal.toString().toLowerCase();
            }
        }

        for (TerminalNonExecutableDataOperands operand: TerminalNonExecutableDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return ExecutorOperandTypes.TerminalNonExecutable.toString().toLowerCase();
            }
        }

        for (ValidateExecutorDataOperands operand: ValidateExecutorDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return ExecutorOperandTypes.Validate.toString().toLowerCase();
            }
        }

        for (LoopExecutorOperands operand: LoopExecutorOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return ExecutorOperandTypes.Loop.toString().toLowerCase();
            }
        }

        for (RequestParentOperand operand: RequestParentOperand.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return ExecutorOperandTypes.Req.toString().toLowerCase();
            }
        }

        if (key.startsWith("${") && key.endsWith("}")) {
            if (parentNodeType.equalsIgnoreCase(ExecutorOperandTypes.Req.toString())) {
                return ExecutorOperandTypes.Dynamic.toString().toLowerCase();
            } else {
                return ExecutorOperandTypes.Data.toString().toLowerCase();
            }
        }

        return ExecutorOperandTypes.Data.toString().toLowerCase();
    }

    public static boolean isTestTypeValid(String testType) {
        return testType.equalsIgnoreCase("AGENTIC") || testType.equalsIgnoreCase("MANUAL");
    }

}
