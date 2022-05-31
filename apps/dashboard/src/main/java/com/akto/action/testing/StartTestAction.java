package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.testing.TestingRun;

import java.util.List;

public class StartTestAction extends UserAction {

    List<ApiInfo.ApiInfoKey> apiInfoKeyList;

    @Override
    public String execute() {
        User user = getSUser();
        int testIdConfig = 0;
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) {
            return ERROR;
        }

        TestingRun testingRun = new TestingRun(
                Context.now(), user.getLogin(), apiInfoKeyList, testIdConfig
        );

        TestingRunDao.instance.insertOne(testingRun);

        return SUCCESS;
    }


}
