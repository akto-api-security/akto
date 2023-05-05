package com.akto.action.growth_tools;

import com.akto.action.UserAction;

public class PublicApiAction extends UserAction {

    private String check;

    @Override
    public String execute() throws Exception{
        check = "abcd";
        return SUCCESS.toUpperCase();
    }

    public String getCheck() {
        return check;
    }

    public void setCheck(String check) {
        this.check = check;
    }
}
