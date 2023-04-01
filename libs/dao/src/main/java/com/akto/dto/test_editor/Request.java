package com.akto.dto.test_editor;

import java.util.List;

public class Request {
    
    private String type;

    private List<RequestModificationTemplate> req;

    public Request(String type, List<RequestModificationTemplate> req) {
        this.type = type;
        this.req = req;
    }

    public Request() { }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<RequestModificationTemplate> getReq() {
        return req;
    }

    public void setReq(List<RequestModificationTemplate> req) {
        this.req = req;
    }
    
}
