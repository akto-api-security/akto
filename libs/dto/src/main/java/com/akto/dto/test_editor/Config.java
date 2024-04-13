package com.akto.dto.test_editor;

import java.util.Map;

public class Config {
    
    private Map<String, Object> val;

    public Config(Map<String, Object> val) {
        this.val = val;
    }

    public Config() { }

    public Map<String, Object> getVal() {
        return val;
    }

    public void setVal(Map<String, Object> val) {
        this.val = val;
    }    
    
}
