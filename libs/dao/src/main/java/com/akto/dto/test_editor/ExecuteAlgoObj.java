package com.akto.dto.test_editor;

public class ExecuteAlgoObj {
    
    private int operationCount;

    private int keyIndex;

    private int valueIndex;

    private int rawApiIndexModified;

    public ExecuteAlgoObj() { 
    }
    
    public ExecuteAlgoObj(int operationCount, int keyIndex, int valueIndex, int rawApiIndexModified) {
        this.operationCount = operationCount;
        this.keyIndex = keyIndex;
        this.valueIndex = valueIndex;
        this.rawApiIndexModified = rawApiIndexModified;
    }

    public int getOperationCount() {
        return operationCount;
    }

    public void setOperationCount(int operationCount) {
        this.operationCount = operationCount;
    }

    public int getKeyIndex() {
        return keyIndex;
    }

    public void setKeyIndex(int keyIndex) {
        this.keyIndex = keyIndex;
    }

    public int getValueIndex() {
        return valueIndex;
    }

    public void setValueIndex(int valueIndex) {
        this.valueIndex = valueIndex;
    }

    public int getRawApiIndexModified() {
        return rawApiIndexModified;
    }

    public void setRawApiIndexModified(int rawApiIndexModified) {
        this.rawApiIndexModified = rawApiIndexModified;
    }

}
