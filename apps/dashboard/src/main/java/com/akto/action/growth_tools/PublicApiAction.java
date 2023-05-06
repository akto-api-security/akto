package com.akto.action.growth_tools;

import com.akto.action.UserAction;
import com.akto.dao.AccountsDao;
import com.akto.dao.CommonContextDao;
import com.akto.dto.Account;

import java.util.ArrayList;
import java.util.List;

public class PublicApiAction extends UserAction {

    private String check;
    private List<Account> accounts;

    @Override
    public String execute() throws Exception{
        check = "abcd";
        accounts = AccountsDao.instance.getAllAccounts();
        //curl api/createTest
        return SUCCESS.toUpperCase();
    }

    public String getCheck() {
        return check;
    }

    public void setCheck(String check) {
        this.check = check;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }
}
