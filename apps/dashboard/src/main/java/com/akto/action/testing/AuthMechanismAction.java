package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;

import java.util.ArrayList;
import java.util.List;

public class AuthMechanismAction extends UserAction {

    private AuthParam.Location location;
    private String key;
    private String value;

    @Override
    public String execute() {
        List<AuthParam> authParams = new ArrayList<>();
        if (location == null || key == null || value == null) {
            addActionError("Location, Key or Value can't be empty");
            return ERROR.toUpperCase();
        }
        authParams.add(new HardcodedAuthParam(location, key, value));
        AuthMechanism authMechanism = new AuthMechanism(authParams);

        AuthMechanismsDao.instance.insertOne(authMechanism);
        return SUCCESS.toUpperCase();
    }

    public void setLocation(AuthParam.Location location) {
        this.location = location;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
