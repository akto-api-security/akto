package com.akto.dto.test_editor;

import java.util.List;

public class Auth {

    private boolean authenticated;

    private List<String> headers;

    public Auth() { }

    public Auth(boolean authenticated, List<String> headers) {
        this.authenticated = authenticated;
        this.headers = headers;
    }

    public boolean getAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }
    
}
