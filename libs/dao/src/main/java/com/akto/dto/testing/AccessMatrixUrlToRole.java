package com.akto.dto.testing;

import java.util.List;
import com.akto.dto.ApiInfo.ApiInfoKey;

import org.bson.codecs.pojo.annotations.BsonId;

public class AccessMatrixUrlToRole {

    @BsonId
    private ApiInfoKey id;
    public static final String ROLES = "roles";
    private List<String> roles;

    public AccessMatrixUrlToRole() {
    }

    public AccessMatrixUrlToRole(ApiInfoKey id, List<String> roles) {
        this.id = id;
        this.roles = roles;
    }

    public ApiInfoKey getId() {
        return this.id;
    }

    public void setId(ApiInfoKey id) {
        this.id = id;
    }

    public List<String> getRoles() {
        return this.roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", roles='" + getRoles() + "'" +
            "}";
    }
}
