package com.akto.util;

import com.akto.dao.UsersDao;
import com.akto.dto.User;

public class AccountUtils {

    //This logic will work only for ON_PREM, refactor for SAAS
    public static String getAccountName(){
        User firstUser = UsersDao.instance.getFirstUser(1_000_000);
        String email = firstUser.getLogin();
        int atIndex = email.indexOf('@');
        String domain = email.substring(atIndex + 1);
        String accountName = domain.split("\\.")[0];
        return accountName.toLowerCase();
    }
}
