package com.akto.dao.test_editor;

public class TestEditorEnums {
    
    public enum DataOperands {
        CONTAINS_EITHER,
        CONTAINS_ALL,
        NOT_CONTAINS,
        NOT_CONTAINS_EITHER,
        REGEX,
        EQ,
        GTE,
        GT,
        LTE,
        LT,
        NEQ,
        PARAM,
        CONTAINS_JWT
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
        RESPONSE_CODE
    }

    public enum PredicateOperator {
        AND,
        OR
    }

    public enum KeyValOperator {
        KEY,
        VALUE
    }

    public enum BodyOperator {
        LENGTH,
        PERCENTAGE_MATCH
    }

    public enum ExtractOperator {
        EXTRACT
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
        ENDPOINT_IN_TRAFFIC_CONTEXT
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
        DELETE_HEADER,
        DELETE_BODY_PARAM,
        DELETE_QUERY_PARAM,
        MODIFY_URL,
        MODIFY_METHOD,
        FOLLOW_REDIRECT,
        REMOVE_AUTH_HEADER,
        REPLACE_AUTH_HEADER,
        REPLACE_BODY,
        DELETE_GRAPHQL_FIELD
    }

    public enum NonTerminalExecutorDataOperands {
        ADD_HEADER,
        ADD_BODY_PARAM,
        ADD_QUERY_PARAM,
        MODIFY_HEADER,
        MODIFY_BODY_PARAM,
        MODIFY_QUERY_PARAM,
        ADD_GRAPHQL_FIELD,
        MODIFY_GRAPHQL_FIELD
    }

    public enum ExecutorOperandTypes {
        Parent,
        Req,
        Terminal,
        NonTerminal,
        Data
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

        return key;
    }

    public String getExecutorOperandType(String key) {

        for (ExecutorParentOperands operand: ExecutorParentOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "parent";
            }
        }

        for (RequestParentOperand operand: RequestParentOperand.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "req";
            }
        }

        for (TerminalExecutorDataOperands operand: TerminalExecutorDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "terminal";
            }
        }

        for (NonTerminalExecutorDataOperands operand: NonTerminalExecutorDataOperands.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "nonterminal";
            }
        }

        return "data";
    }

}
