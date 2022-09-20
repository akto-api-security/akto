package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.open_api.Main;
import com.akto.utils.SampleDataToSTI;
import com.mongodb.client.model.Filters;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class OpenApiAction extends UserAction{

    private static final Logger logger = LoggerFactory.getLogger(OpenApiAction.class);
    private int apiCollectionId;
    private String openAPIString = null;
    @Override
    public String execute() {
        try {
            
            List<SampleData> sampleData = SampleDataDao.instance.findAll(
                Filters.eq("_id.apiCollectionId", apiCollectionId)
            );
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id", apiCollectionId);
            if (apiCollection == null) {
                return ERROR.toUpperCase();
            }
            SampleDataToSTI sampleDataToSTI = new SampleDataToSTI();
            sampleDataToSTI.setSampleDataToSTI(sampleData);
            Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList = sampleDataToSTI.getSingleTypeInfoMap();
            OpenAPI openAPI = Main.init(apiCollection.getDisplayName(),stiList);
            openAPIString = Main.convertOpenApiToJSON(openAPI);
        } catch (Exception e) {
            logger.error("ERROR while downloading openApi file " + e);
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getOpenAPIString() {
        return openAPIString;
    }
}
