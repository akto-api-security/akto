package com.akto.dto.bulk_updates;
import com.google.gson.Gson;

public class UpdatePayload {
    private String field;
    private Object val;
    private String op;

    private static final Gson gson = new Gson();

    public UpdatePayload() {
    }

    public UpdatePayload(String field, Object val, String op) {
        this.field = field;
        this.val = val;
        this.op = op;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Object getVal() {
        return val;
    }

    public void setVal(Object val) {
        this.val = val;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    @Override
    public String toString() {
        return gson.toJson(this);
    }
    
}
