package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ApiCollectionFilter extends DataOperandsImpl {

    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;
        Boolean res;
        List<String> querySet = new ArrayList<>();
        String data;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return new ValidationResult(result, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }

        Bson fQuery = Filters.or(
            Filters.in(ApiCollection.NAME, querySet),
            Filters.in(ApiCollection.HOST_NAME, querySet)
        );
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(fQuery);
        List<Integer> apiCollectionIds = new ArrayList<>();
        for(ApiCollection apiCollection: apiCollections) {
            apiCollectionIds.add(apiCollection.getId());
        }

        List<String> urls = new ArrayList<>();
        urls.add(data);

        try {
            URL url = new URL(data);
            urls.add(url.getPath());
        } catch (MalformedURLException e) {
            // eat it
        }

        Bson urlInCollectionQuery = Filters.and(
            Filters.in(ApiInfo.COLLECTION_IDS, apiCollectionIds),
            Filters.in(ApiInfo.ID_URL, urls)
        );


        result = ApiInfoDao.instance.findOne(urlInCollectionQuery) != null;
        if (result) {
            return new ValidationResult(result, "");
        }
        return new ValidationResult(result, "Could not find given urls: "+ data +", in list of API collections");
    }
}
