package com.akto.dto.runtime_filters;

import com.akto.dto.CustomFilter;
import com.akto.dto.HttpResponseParams;

import java.util.Collections;
import java.util.List;

public class RuntimeFilter {
    private int id;
    public static final String NAME = "name";
    private String name;

    private List<CustomFilter> customFilterList;
    private Operator customFiltersOperator;

    private UseCase useCase;

    private String customFieldName;

    public enum UseCase {
        AUTH_TYPE, SET_CUSTOM_FIELD, DETERMINE_API_ACCESS_TYPE
    }

    public enum Operator {
        AND, OR
    }

    public RuntimeFilter(int id,UseCase useCase, String name, List<CustomFilter> customFilterList,
                         Operator customFiltersOperator, String customFieldName) {
        this.id = id;
        this.name = name;
        this.useCase = useCase;
        this.customFilterList = customFilterList;
        this.customFiltersOperator = customFiltersOperator;
        this.customFieldName = customFieldName;
    }

    public RuntimeFilter() {
    }

    public boolean process(HttpResponseParams httpResponseParams) {
        if (this.customFilterList == null || this.customFilterList.size() == 0) return false;

        boolean result = this.customFilterList.get(0).process(httpResponseParams);
        for (int i = 1 ; i < this.customFilterList.size(); i++) {
            CustomFilter customFilter = this.customFilterList.get(i);
            switch (customFiltersOperator) {
                case AND:
                    result = result && customFilter.process(httpResponseParams);
                    break;
                case OR:
                    result = result || customFilter.process(httpResponseParams);
                    break;
            }
        }
        return result;
    }



    public static final String OPEN_ENDPOINTS_FILTER = "Open Endpoints";
    public static RuntimeFilter generateOpenEndpointsFilter(int id) {
        List<CustomFilter> customFilterList = Collections.singletonList(new ResponseCodeRuntimeFilter(200, 299));
        return new RuntimeFilter(
                id,
                RuntimeFilter.UseCase.AUTH_TYPE,
                OPEN_ENDPOINTS_FILTER,
                customFilterList,
                RuntimeFilter.Operator.AND,
                "unauthenticated"
        );
    }

    public static final String API_ACCESS_TYPE_FILTER = "API access Type";
    public static RuntimeFilter generateApiAccessTypeFilter(int id) {
        List<CustomFilter> customFilterList = Collections.singletonList(new ResponseCodeRuntimeFilter(200, 299));
        return new RuntimeFilter(
                id,
                UseCase.DETERMINE_API_ACCESS_TYPE,
                API_ACCESS_TYPE_FILTER,
                customFilterList,
                RuntimeFilter.Operator.AND,
                "access_type"
        );
    }


    public void setCustomFilterList(List<CustomFilter> customFilterList) {
        this.customFilterList = customFilterList;
    }

    public List<CustomFilter> getCustomFilterList() {
        return customFilterList;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UseCase getUseCase() {
        return useCase;
    }

    public void setUseCase(UseCase useCase) {
        this.useCase = useCase;
    }

    public Operator getCustomFiltersOperator() {
        return customFiltersOperator;
    }

    public void setCustomFiltersOperator(Operator customFiltersOperator) {
        this.customFiltersOperator = customFiltersOperator;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomFieldName() {
        return customFieldName;
    }

    public void setCustomFieldName(String customFieldName) {
        this.customFieldName = customFieldName;
    }
}
