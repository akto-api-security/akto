package com.akto.utils;

import com.akto.dto.ApiInfo;

import java.util.List;

public class ApiInfoKeyResult {
    public final int count;
    public final List<ApiInfo> apiInfoList;
    public ApiInfoKeyResult(int count, List<ApiInfo> apiInfoList) {
        this.count = count;
        this.apiInfoList = apiInfoList;
    }
}

