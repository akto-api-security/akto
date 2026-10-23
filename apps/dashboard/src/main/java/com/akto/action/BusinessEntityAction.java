package com.akto.action;

import com.akto.dao.BusinessEntityApiDao;
import com.akto.dao.BusinessEntityDao;
import com.akto.dao.BusinessFlowDao;
import com.akto.dto.BusinessEntity;
import com.akto.dto.BusinessEntityApi;
import com.akto.dto.BusinessFlow;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class BusinessEntityAction extends UserAction {

    private String scopeKey = "chargepoint";

    private List<BusinessEntity> businessEntities = new ArrayList<>();

    // APIs that operate on the returned entities (create / read / list / update / delete / action)
    private List<BusinessEntityApi> entityOperations = new ArrayList<>();

    // Entity lifecycles and cross-entity journeys found from API sequences
    private List<BusinessFlow> businessFlows = new ArrayList<>();

    public String fetchBusinessEntities() {
        List<BusinessEntity> confirmed = BusinessEntityDao.instance.findConfirmed(scopeKey);
        List<String> slugs = confirmed.stream().map(BusinessEntity::getSlug).collect(Collectors.toList());
        this.entityOperations = BusinessEntityApiDao.instance.findOperations(scopeKey, slugs);
        Set<String> withOperations = entityOperations.stream().map(BusinessEntityApi::getEntityId).collect(Collectors.toSet());
        this.businessEntities = confirmed.stream().filter(e -> withOperations.contains(e.getSlug())).collect(Collectors.toList());
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchBusinessFlows() {
        this.businessFlows = BusinessFlowDao.instance.findFlows(scopeKey);
        return Action.SUCCESS.toUpperCase();
    }
}
