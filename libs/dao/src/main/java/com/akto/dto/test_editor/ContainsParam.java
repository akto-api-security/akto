package com.akto.dto.test_editor;

import java.util.List;

public class ContainsParam {
    
    private String param;
    private List<String> paramRegex;

    private List<String> searchIn;

    public ContainsParam(String param, List<String> paramRegex, List<String> searchIn) {
        this.param = param;
        this.paramRegex = paramRegex;
        this.searchIn = searchIn;
    }

    public ContainsParam() { }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public List<String> getParamRegex() {
        return paramRegex;
    }

    public void setParamRegex(List<String> paramRegex) {
        this.paramRegex = paramRegex;
    }
    
    public List<String> getSearchIn() {
        return searchIn;
    }

    public void setSearchIn(List<String> searchIn) {
        this.searchIn = searchIn;
    }
    
}
