package com.akto.dto.test_editor;

import java.util.List;

public class Environment {
    
    private List<Roles> roles;

    public Environment(List<Roles> roles) {
        this.roles = roles;
    }

    public Environment() { }

    public List<Roles> getRoles() {
        return roles;
    }

    public void setRoles(List<Roles> roles) {
        this.roles = roles;
    }

}
