package com.akto.test_editor.filter.data_operands_impl;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ApiCollectionFilter extends DataOperandsImpl {

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        Boolean result = false;
        Boolean res;
        List<String> querySet = new ArrayList<>();
        String data;
        try {
            querySet = (List<String>) dataOperandFilterRequest.getQueryset();
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return result;
        }

        List<ApiCollection> apiCollections = dataActor.findApiCollections(querySet);
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

        result = dataActor.apiInfoExists(apiCollectionIds, urls);
        return result;
    }
}
