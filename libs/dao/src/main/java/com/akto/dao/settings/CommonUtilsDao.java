package com.akto.dao.settings;

import com.akto.dao.CommonContextDao;
import com.akto.dto.settings.CommonUtils;

public class CommonUtilsDao extends CommonContextDao<CommonUtils> {

    public static final CommonUtilsDao instance = new CommonUtilsDao();

    @Override
    public String getCollName() {
        return "commonUtils";
    }

    @Override
    public Class<CommonUtils> getClassT() {
        return CommonUtils.class;
    }
    
}
