package com.akto.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.data_types.BelongsToPredicate;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.ContainsPredicate;
import com.akto.dto.data_types.NotBelongsToPredicate;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.Predicate.Type;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.LogicalGroupTestingEndpoint;
import com.akto.dto.testing.SingleTypeInfoView;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class SingleTypeInfoViewDao extends AccountsContextDao<SingleTypeInfoView>{
    
    public static final SingleTypeInfoViewDao instance = new SingleTypeInfoViewDao();
    public static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();


    @Override
    public String getCollName() {
        return "single_type_info_view";
    }

    @Override
    public Class<SingleTypeInfoView> getClassT() {
        return SingleTypeInfoView.class;
    }

    public List<String> calculateAuthTypes(SingleTypeInfoView singleTypeInfoView) {

        List<String> result = new ArrayList<>();

        Set<String> uniqueAuthTypes = new HashSet<>();

        List<String> authTypes = singleTypeInfoView.getAllAuthTypes();

        if (authTypes == null) {
            return new ArrayList<>();
        }

        if (authTypes.contains("UNAUTHENTICATED")) {
            result.add("UNAUTHENTICATED");
            return result;
        }

        for (String authType: singleTypeInfoView.getAllAuthTypes()) {
            if (uniqueAuthTypes.contains(authType)) {
                continue;
            }
            uniqueAuthTypes.add(authType);
            result.add(authType);
        }

        return result;
    }

    public int getUrlCount(int apiCollectionId) {
        Bson filters = Filters.eq("_id.apiCollectionId", apiCollectionId);
        return (int) SingleTypeInfoViewDao.instance.findCount(filters);
    }
    
    public int getUrlCountLogicalGroup(int groupId) {
        Bson filters = Filters.in("logicalGroups", groupId);
        return (int) SingleTypeInfoViewDao.instance.findCount(filters);
    }

    public List<String> calculateSensitiveTypes(SingleTypeInfoView singleTypeInfoView) {

        Set<String> sensitiveParams = new HashSet<>();
        List<String> result = new ArrayList<>();
        
        for (String param: singleTypeInfoView.getReqSubTypes()) {
            if (isSensitive(param, true)) {
                sensitiveParams.add(param);
            }
        }

        for (String param: singleTypeInfoView.getRespSubTypes()) {
            if (isSensitive(param, false)) {
                sensitiveParams.add(param);
            }
        }

        for (String param: sensitiveParams) {
            result.add(param);
        }

        return result;
    }

    public Boolean isSensitive(String param, Boolean isReqSubtype) {
        
        List<String> alwaysSensitiveSubTypes = SingleTypeInfoDao.instance.sensitiveSubTypeNames();

        List<String> sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        List<String> sensitiveInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();

        if (isReqSubtype) {
            if (sensitiveInRequest.contains(param) || alwaysSensitiveSubTypes.contains(param)) {
                return true;
            }
        } else {
            if (sensitiveInResponse.contains(param) || alwaysSensitiveSubTypes.contains(param)) {
                return true;
            }
        }

        return false;
    }

    public void buildLogicalGroup(EndpointLogicalGroup endpointLogicalGroup) {

        LogicalGroupTestingEndpoint logicalGroup = (LogicalGroupTestingEndpoint) endpointLogicalGroup.getTestingEndpoints();
        Conditions andConditions = logicalGroup.getAndConditions();
        Conditions orConditions = logicalGroup.getOrConditions();

        ArrayList<Bson> andConditionsfilterList = new ArrayList<>();
        ArrayList<Bson> orConditionsfilterList = new ArrayList<>();

        List<Predicate> andPredicates = new ArrayList<>();
        List<Predicate> orPredicates = new ArrayList<>();

        if (andConditions != null) {
            andPredicates = andConditions.getPredicates();
        }
        if (orConditions != null) {
            orPredicates = orConditions.getPredicates();
        }

        List<String> queryOrder = Arrays.asList("BELONGS_TO", "NOT_BELONGS_TO", "CONTAINS");

        for (String key: queryOrder) {

            for (Predicate predicate: andPredicates) {
                
                if (!key.equals(predicate.getType().toString())) {
                    continue;
                }

                if (predicate.getType().equals(Type.CONTAINS)) {
                    ContainsPredicate pre = (ContainsPredicate) predicate;
                    String value = pre.getValue();
                    andConditionsfilterList.add(Filters.regex("_id.url", value, "i"));
    
                } else if (predicate.getType().equals(Type.NOT_BELONGS_TO)) {
                    NotBelongsToPredicate pre = (NotBelongsToPredicate) predicate;
                    Set<ApiInfo.ApiInfoKey> value = pre.getValue();
                    andConditionsfilterList.add(Filters.nin("_id.url", value));
    
                }
    
            }

            for (Predicate predicate: orPredicates) {
                if (!key.equals(predicate.getType().toString())) {
                    continue;
                }

                if (predicate.getType().equals(Type.CONTAINS)) {
                    ContainsPredicate pre = (ContainsPredicate) predicate;
                    String value = pre.getValue();    
                    orConditionsfilterList.add(Filters.regex("_id.url", value, "i"));
    
                } else if (predicate.getType().equals(Type.BELONGS_TO)) {
                    BelongsToPredicate pre = (BelongsToPredicate) predicate;
                    Set<ApiInfo.ApiInfoKey> value = pre.getValue();
                    orConditionsfilterList.add(Filters.in("_id.url", value));
    
                }
    
            }

        }

        Bson andFilters = null;
        if (andConditionsfilterList.size() > 0) {
            andFilters = Filters.and(andConditionsfilterList);
        }

        Bson orFilters = null;
        if (orConditionsfilterList.size() > 0) {
            orFilters = Filters.and(orConditionsfilterList);
        }

        Bson filters = null;
        if (andFilters != null && orFilters != null) {
            filters = Filters.and(andFilters, orFilters);
        } else if (andFilters != null) {
            filters = Filters.and(andFilters);
        } else if (orFilters != null) {
            filters = Filters.and(orFilters);
        }

        List<SingleTypeInfoView> singleTypeInfoViewList = SingleTypeInfoViewDao.instance.findAll(filters);

        List<WriteModel<SingleTypeInfoView>> stiViewUpdates = new ArrayList<>();

        for (SingleTypeInfoView singleTypeInfoView: singleTypeInfoViewList) {
            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.addToSet("logicalGroups", endpointLogicalGroup.getId()));
            updates.add(Updates.addToSet("combinedData", "logicalGroup_" + endpointLogicalGroup.getId()));
            stiViewUpdates.add(
                new UpdateOneModel<>(
                    Filters.and(
                        Filters.eq("_id.apiCollectionId", singleTypeInfoView.getId().getApiCollectionId()),
                        Filters.eq("_id.method", singleTypeInfoView.getId().getMethod()),
                        Filters.eq("_id.url", singleTypeInfoView.getId().getUrl())
                    ),
                Updates.combine(updates),
                new UpdateOptions().upsert(false)
            )
            );
        }

        if (stiViewUpdates.size() > 0) {
            BulkWriteResult res = SingleTypeInfoViewDao.instance.getMCollection().bulkWrite(stiViewUpdates);
            System.out.println(res.getInsertedCount() + " " + res.getUpserts());
        }

        EndpointLogicalGroupDao.instance.getMCollection().updateOne(
                Filters.eq("_id", endpointLogicalGroup.getId()),
                Updates.combine(
                        Updates.set("endpointRefreshTs", Context.now())
                )
        );
                
    }
    
    public void triggerLogicalGroupUpdate(int logicalGroupId) {
        EndpointLogicalGroup endpointLogicalGroup = EndpointLogicalGroupDao.instance.findOne(Filters.eq("_id", logicalGroupId));

        if (Context.now() - endpointLogicalGroup.getEndpointRefreshTs() > 300) {

            executorService.schedule( new Runnable() {
                public void run() {
                    Context.accountId.set(1_000_000);
                    buildLogicalGroup(endpointLogicalGroup);
                }
            }, 1 , TimeUnit.SECONDS);
        }   
    }

    public void removeLogicalGroup(int logicalGroupId) {

        Bson filters = Filters.in("combinedData", "logicalGroup_" + logicalGroupId);
        List<SingleTypeInfoView> singleTypeInfoViewList = SingleTypeInfoViewDao.instance.findAll(filters);

        List<WriteModel<SingleTypeInfoView>> stiViewUpdates = new ArrayList<>();

        for (SingleTypeInfoView singleTypeInfoView: singleTypeInfoViewList) {
            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.pull("logicalGroups", logicalGroupId));
            updates.add(Updates.pull("combinedData", "logicalGroup_" + logicalGroupId));
            stiViewUpdates.add(
                new UpdateOneModel<>(
                    Filters.and(
                        Filters.eq("_id.apiCollectionId", singleTypeInfoView.getId().getApiCollectionId()),
                        Filters.eq("_id.method", singleTypeInfoView.getId().getMethod()),
                        Filters.eq("_id.url", singleTypeInfoView.getId().getUrl())
                    ),
                Updates.combine(updates),
                new UpdateOptions().upsert(false)
            )
            );
        }

        if (stiViewUpdates.size() > 0) {
            BulkWriteResult res = SingleTypeInfoViewDao.instance.getMCollection().bulkWrite(stiViewUpdates);
            System.out.println(res.getInsertedCount() + " " + res.getUpserts());
        }

        EndpointLogicalGroupDao.instance.deleteAll(Filters.eq("_id", logicalGroupId));

    }

}
