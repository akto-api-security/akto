package com.akto.action;

import com.akto.dao.ThirdPartyAccessDao;
import com.akto.dto.User;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.mongodb.client.model.Filters;

import java.util.List;

public class ThirdPartyAccessAction extends UserAction{

    private List<ThirdPartyAccess> thirdPartyAccessList;
    @Override
    public String execute() {
        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
         thirdPartyAccessList = ThirdPartyAccessDao.instance.findAll(
                Filters.eq("owner", user.getId())
        );

        return SUCCESS.toUpperCase();
    }

    public List<ThirdPartyAccess> getThirdPartyAccessList() {
        return thirdPartyAccessList;
    }
}
