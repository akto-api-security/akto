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
        OR,
        COMPARE_GREATER
    }

    public enum KeyValOperator {
        KEY,
        VALUE
    }

    public enum BodyOperator {
        LENGTH,
        PERCENTAGE_MATCH,
        PERCENTAGE_MATCH_SCHEMA
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
        JWT_REPLACE_BODY
    }

    public enum NonTerminalExecutorDataOperands {
        ADD_HEADER,
        ADD_BODY_PARAM,
        ADD_QUERY_PARAM,
        MODIFY_HEADER,
        MODIFY_BODY_PARAM,
        MODIFY_QUERY_PARAM
    }

    public enum TerminalNonExecutableDataOperands {
        TYPE,
        TEST_NAME,
        LABEL,
        SUCCESS,
        FAILURE
    }

    public enum ValidateExecutorDataOperands {
        Validate
    }

    public enum ExecutorOperandTypes {
        Parent,
        Req,
        Terminal,
        NonTerminal,
        Data,
        TerminalNonExecutable,
        Validate
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

    public String getExecutorOperandType(String key) {

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

        return ExecutorOperandTypes.Data.toString().toLowerCase();
    }

}
