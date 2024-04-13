package com.akto.dto.auth;

import com.akto.dao.context.Context;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class APIAuthBasic extends APIAuth {
    String username;
    String password;

    public APIAuthBasic() {
    }

    public APIAuthBasic(String username, String password) {
        this.username = username;
        this.password = password;
        this.type = Type.BASIC;
        this.id = Context.now();
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public void setType(Type type) {
        this.type = Type.BASIC;
    }

}
