package com.akto.devrev;

import com.akto.dao.DevRevIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.devrev_integration.DevRevIntegration;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.conversions.Bson;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DevRevIntegrationManager {

    private static final LoggerMaker logger = new LoggerMaker(DevRevIntegrationManager.class, LoggerMaker.LogDb.DASHBOARD);

    private String orgUrl;
    private String personalAccessToken;

    public DevRevIntegration addIntegration() throws Exception {
        if (orgUrl == null || orgUrl.isEmpty()) {
            throw new Exception("Please enter a valid organization URL.");
        }

        if (orgUrl.endsWith("/")) {
            orgUrl = orgUrl.substring(0, orgUrl.length() - 1);
        }

        String actualToken;
        if (personalAccessToken != null && !personalAccessToken.isEmpty() && !personalAccessToken.equals(
            Constants.ASTERISK)) {
            actualToken = personalAccessToken;
        } else {
            DevRevIntegration existingIntegration = DevRevIntegrationDao.instance.findOne(new BasicDBObject());
            if (existingIntegration == null || existingIntegration.getPersonalAccessToken() == null || existingIntegration.getPersonalAccessToken().isEmpty()) {
                throw new Exception("Please enter a valid personal access token.");
            }
            actualToken = existingIntegration.getPersonalAccessToken();
        }

        Map<String, String> partsIdToNameMap = fetchAllPartsFromDevRev(actualToken);

        if (partsIdToNameMap.isEmpty()) {
            throw new Exception("Something went wrong. Please verify your configurations and try again.");
        }

        int currTimeStamp = Context.now();
        Bson combineUpdates = Updates.combine(
                Updates.set(DevRevIntegration.ORG_URL, orgUrl),
                Updates.set(DevRevIntegration.PARTS_ID_TO_NAME_MAP, partsIdToNameMap),
                Updates.set(DevRevIntegration.PERSONAL_ACCESS_TOKEN, actualToken),
                Updates.setOnInsert(DevRevIntegration.CREATED_TS, currTimeStamp),
                Updates.set(DevRevIntegration.UPDATED_TS, currTimeStamp)
        );

        DevRevIntegration updatedIntegration = DevRevIntegrationDao.instance.getMCollection().findOneAndUpdate(
                new BasicDBObject(),
                combineUpdates,
                new FindOneAndUpdateOptions()
                        .upsert(true)
                        .returnDocument(ReturnDocument.AFTER)
        );

        logger.infoAndAddToDb("DevRev integration added successfully", LoggerMaker.LogDb.DASHBOARD);

        if (updatedIntegration != null) {
            updatedIntegration.setPersonalAccessToken(Constants.ASTERISK);
        }

        return updatedIntegration;
    }

    private Map<String, String> fetchAllPartsFromDevRev(String token) {
        Map<String, String> partsIdToNameMap = new HashMap<>();

        String url = DevRevIntegration.API_BASE_URL + "/parts.list";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + token));
        headers.put("Content-Type", Collections.singletonList("application/json"));

        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "GET", null, headers, "");

        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            logger.infoAndAddToDb("Status and Response from DevRev parts.list API: " + response.getStatusCode() + " | " + response.getBody(), LoggerMaker.LogDb.DASHBOARD);

            if (response.getStatusCode() > 201) {
                logger.errorAndAddToDb("Failed to fetch parts from DevRev: " + response.getBody(), LoggerMaker.LogDb.DASHBOARD);
                return partsIdToNameMap;
            }

            String responsePayload = response.getBody();
            BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
            BasicDBList partsListObj = (BasicDBList) respPayloadObj.get("parts");

            if (partsListObj != null) {
                for (Object partObj : partsListObj) {
                    BasicDBObject part = (BasicDBObject) partObj;
                    String partId = part.getString("id");
                    String partName = part.getString("name");
                    partsIdToNameMap.put(partId, partName);
                }
            }

        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching parts from DevRev: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }

        return partsIdToNameMap;
    }

    public DevRevIntegration fetchIntegration() {
        DevRevIntegration integration = DevRevIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration != null) {
            integration.setPersonalAccessToken(Constants.ASTERISK);
        }
        return integration;
    }

    public Map<String, String> fetchDevrevProjects() throws Exception {
        if (personalAccessToken == null || personalAccessToken.isEmpty()) {
            throw new Exception("Please enter a valid personal access token.");
        }

        Map<String, String> partsIdToNameMap = fetchAllPartsFromDevRev(personalAccessToken);

        if (partsIdToNameMap.isEmpty()) {
            throw new Exception("Failed to fetch projects from DevRev. Please verify your personal access token and try again.");
        }

        return partsIdToNameMap;
    }

}