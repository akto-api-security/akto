package com.akto.dao.test_editor;

public class TestEditorEnums {
    
    public enum DataOperands {
        CONTAINS_EITHER,
        CONTAINS_ALL,
        REGEX,
        EQ,
        GTE,
        GT,
        LTE,
        LT,
        NEQ
    }

    public enum CollectionOperands {
        FOR_ONE,
        FOR_ALL
    }

    public enum TermOperands {
        URL,
        METHOD,
        API_COLLECTION_ID,
        QUERY,
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

    public enum PayloadOperator {
        KEY,
        VALUE
    }

    public enum OperandTypes {
        Data,
        Pred,
        Term,
        Collection,
        Payload
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

        for (PayloadOperator operand: PayloadOperator.values()) {
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
        
        for (PayloadOperator operand: PayloadOperator.values()) {
            if (operand.toString().toLowerCase().equals(key.toLowerCase())) {
                return "payload";
            }
        }

        return null;
    }

}
