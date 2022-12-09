package com.akto.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.types.CappedSet;

public class CustomAuthUtilTest extends MongoBasedTest{

    public static SingleTypeInfo generateSingleTypeInfo(String param, Boolean isHeader) {
        SingleTypeInfo.ParamId p = new SingleTypeInfo.ParamId("/api","POST",200,isHeader,param,SingleTypeInfo.GENERIC,ACCOUNT_ID, false);
        return new SingleTypeInfo(p,new HashSet<>(),new HashSet<>(),0,0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
    }

    @Test
    public void test1(){
        ApiInfo apiInfo = new ApiInfo(ACCOUNT_ID, "/api", Method.POST);
        Set<Set<ApiInfo.AuthType>> authTypes = new HashSet<>();
        Set<ApiInfo.AuthType> types = new HashSet<>();
        types.add(ApiInfo.AuthType.UNAUTHENTICATED);
        authTypes.add(types);
        apiInfo.setAllAuthTypesFound(authTypes);
        List<CustomAuthType> customAuthTypes = new ArrayList<>();
        customAuthTypes.add(new CustomAuthType("auth1", new ArrayList<>(Collections.singletonList("authtoken")),new ArrayList<>(Collections.singletonList("authtoken")), true, ACCOUNT_ID));
        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        singleTypeInfos.add(generateSingleTypeInfo("authtoken",true));
        ApiInfoDao.instance.insertOne(apiInfo);
        CustomAuthTypeDao.instance.insertMany(customAuthTypes);
        SingleTypeInfoDao.instance.insertMany(singleTypeInfos);
        CustomAuthUtil.customAuthTypeUtil(customAuthTypes);
        apiInfo = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter("/api", "POST", ACCOUNT_ID));
        Set<ApiInfo.AuthType> customTypes = new HashSet<>();
        customTypes.add(ApiInfo.AuthType.CUSTOM);
        assertTrue(apiInfo.getAllAuthTypesFound().contains(customTypes));
    }
}