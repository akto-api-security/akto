package com.akto.dto.test_editor;

import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonIgnore;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ExecutorNode {
    
    private String nodeType;
    
    private List<ExecutorNode> childNodes;

    @BsonIgnore
    private Object values;

    private String operationType;

    public ExecutorNode(String nodeType, List<ExecutorNode> childNodes, Object values, String operationType) {
        this.nodeType = nodeType;
        this.childNodes = childNodes;
        this.values = values;
        this.operationType = operationType;
    }

    public ExecutorNode() { }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public List<ExecutorNode> getChildNodes() {
        return childNodes;
    }

    public void setChildNodes(List<ExecutorNode> childNodes) {
        this.childNodes = childNodes;
    }
    
    public Object getValues() {
        return values;
    }

    public void setValues(Object values) {
        this.values = values;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String fetchConditionalString(String key) {
        ObjectMapper m = new ObjectMapper();
        String str = "";
        Object obj = null;
        try {
            Map<String,Object> mapValues = m.convertValue(this.getValues(), Map.class);
            str = (String) mapValues.get(key);
        } catch (Exception e) {
            try {
                List<Object> listValues = (List<Object>) this.getValues();
                for (int i = 0; i < listValues.size(); i++) {
                    Map<String,Object> mapValues = m.convertValue(listValues.get(i), Map.class);
                    obj = mapValues.get(key);
                    if (obj != null) {
                        str = obj.toString();
                    }
                }
            } catch (Exception er) {
                // TODO: handle exception
            }
            // TODO: handle exception
        }
        return str;
    }
    
}
