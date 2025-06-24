package com.akto.dto.testing;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.common.AuthPolicy;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dto.*;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import lombok.extern.slf4j.Slf4j;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.stream.Collectors;

import static com.akto.util.Constants.ID;
import static com.akto.util.Constants.default_token;
import static org.apache.commons.collections.CollectionUtils.collect;


@Slf4j
public class TestRoles {
    private ObjectId id;
    @BsonIgnore
    private String hexId;
    public static final String NAME = "name";
    private String name;
    private ObjectId endpointLogicalGroupId;
    public static final String AUTH_WITH_COND_LIST = "authWithCondList";
    private List<AuthWithCond> authWithCondList;
    @BsonIgnore
    private EndpointLogicalGroup endpointLogicalGroup;

    private boolean defaultPresetAuth = false;
    public static final String CREATED_BY = "createdBy";
    private String createdBy;
    private int createdTs;
    public static final String LAST_UPDATED_TS = "lastUpdatedTs";
    private int lastUpdatedTs;
    private List<Integer> apiCollectionIds;

    public static final String SCOPE_ROLES = "scopeRoles";
    private List<String> scopeRoles;

    public static final String LAST_UPDATED_BY = "lastUpdatedBy";
    private String lastUpdatedBy;
    
    public TestRoles(){}
    
    public TestRoles(ObjectId id, String name, ObjectId endpointLogicalGroupId, List<AuthWithCond> authWithCondList, String createdBy, int createdTs, int lastUpdatedTs, List<Integer> apiCollectionIds, String lastUpdatedBy) {
        this.id = id;
        this.name = name;
        this.endpointLogicalGroupId = endpointLogicalGroupId;
        this.authWithCondList = authWithCondList;
        this.createdBy = createdBy;
        this.createdTs = createdTs;
        this.lastUpdatedTs = lastUpdatedTs;
        this.apiCollectionIds = apiCollectionIds;
        this.lastUpdatedBy = lastUpdatedBy;
	}
    
    public EndpointLogicalGroup fetchEndpointLogicalGroup() {
        if (this.endpointLogicalGroup == null) {
            this.endpointLogicalGroup = EndpointLogicalGroupDao.instance.findOne(Filters.eq(ID, this.endpointLogicalGroupId));
        }
        return this.endpointLogicalGroup;
    }

    public AuthMechanism findDefaultAuthMechanism() {
        try {
            for(AuthWithCond authWithCond: this.getAuthWithCondList()) {
                if (authWithCond.getHeaderKVPairs().isEmpty()) {
                    AuthMechanism ret = authWithCond.getAuthMechanism();
                    if(authWithCond.getRecordedLoginFlowInput()!=null){
                        ret.setRecordedLoginFlowInput(authWithCond.getRecordedLoginFlowInput());
                    }

                    return ret;
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    public AuthMechanism findMatchingAuthMechanism(RawApi rawApi) {
        if (rawApi == null) {
            return findDefaultAuthMechanism();
        }

        String deafaultAuthHeader = "";
        List<AuthWithCond> authWithConds = this.getAuthWithCondList();
        for(AuthWithCond authWithCond: authWithConds) {
            List<AuthParam> params = authWithCond.getAuthMechanism().getAuthParams();
              for(AuthParam param: params) {
                  if(param.getKey().equals("Authorization") && param.getValue() != null) {
                      deafaultAuthHeader  = param.getValue();
                      break;
                  }
              }
        }
        if (this.defaultPresetAuth || deafaultAuthHeader.equals(default_token)) {

             try {

                HttpResponseParams httpResponseParams = createResponseParamsFromRawApi(rawApi);

                Set<Set<ApiInfo.AuthType>> allAuthTypesFound = new HashSet<>();
                ApiInfo apiInfo= new ApiInfo(ApiInfo.ApiType.REST, allAuthTypesFound);
                //ApiInfo apiInfo = createApiInfoFromRawApi(httpResponseParams);

                Map<String, List<String>> headers = rawApi.fetchReqHeaders();
                List<String> headerKeys = new ArrayList<>(headers.keySet());
                List<CustomAuthType> customAuthTypes = new ArrayList<>();
                for (String headerKey : headerKeys) {
                    CustomAuthType customAuthType = new CustomAuthType();
                    customAuthType.setHeaderKeys(Collections.singletonList(headerKey));
                    customAuthTypes.add(customAuthType);
                }

                AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes, rawApi);

                Map<String, String> headersMap = AuthPolicy.headersMap;
                List<String> authHeaders = AuthPolicy.authHeaders;
                if (authHeaders == null || authHeaders.isEmpty()) {
                    return findDefaultAuthMechanism();
                }


                 ApiInfo apiInfoLatest = ApiInfoDao.fetchLatestAuthenticatedByApiCollectionId(rawApi.getRawApiMetadata().getApiCollectionId());
                 String latestUrl = rawApi.getRequest().getUrl();
                 if( apiInfoLatest != null)
                     latestUrl = apiInfoLatest.getId().getUrl();

                 SampleData sampleData = SampleDataDao.instance.fetchSampleDataForApiURL(rawApi.getRawApiMetadata().getApiCollectionId(), latestUrl);

                 if (sampleData == null || sampleData.getSamples() == null || sampleData.getSamples().isEmpty()) {
                     return findDefaultAuthMechanism();
                 }

                 List<String> sampleTokens = new ArrayList<>();
                 List<String> samples = sampleData.getSamples();
                 if (samples != null && !samples.isEmpty()) {
                     for(String sample : samples){
                         HttpResponseParams sampleResponse = AuthPolicy.parseSampleData(sample);
                         if (sampleResponse.getRequestParams() == null) {
                             continue;
                         }else{
                             String token = sampleResponse.getRequestParams().getHeaders().getOrDefault(authHeaders.get(0), new ArrayList<>()).stream()
                                     .findFirst()
                                     .orElse(null);

                             sampleTokens.add(token);
                         }
                     }
                 }

                List<AuthParam> authParams = new ArrayList<>();

                for(String authHeader: authHeaders) {
                    String tokenToBeUsed = headersMap.get(authHeader);
                    if(!sampleTokens.isEmpty()) {
                       String rawApiToken = headersMap.get(authHeader);
                        // Check if the token is in the sample tokens
                        boolean isSampleToken = sampleTokens.stream().anyMatch(token -> token.equals(rawApiToken));
                        if (!isSampleToken) {
                            continue;
                        }else {
                            sampleTokens.remove(rawApiToken);
                            List<String> updatedSampleTokens = new ArrayList<>(sampleTokens);
                            tokenToBeUsed = updatedSampleTokens.isEmpty() ? rawApiToken : updatedSampleTokens.get(0);
                        }
                    }else {
                        tokenToBeUsed = headersMap.get(authHeader);
                    }

                        log.info("Using token: {} for auth header: {}", tokenToBeUsed, authHeader);
                        HardcodedAuthParam hardcodedAuthParam = new HardcodedAuthParam(AuthParam.Location.HEADER, authHeader, tokenToBeUsed, true);
                        authParams.add(hardcodedAuthParam);

                }

                AuthMechanism mechanism = new AuthMechanism();
                mechanism.setAuthParams(authParams);
                if (mechanism != null) {
                    this.setDefaultPresetAuth(false);
                    return mechanism;
                }

            } catch (Exception e) {
            }
        } else {

            for (AuthWithCond authWithCond : this.getAuthWithCondList()) {

                try {
                    boolean allSatisfied = true;

                    if (authWithCond.getHeaderKVPairs().isEmpty()) {
                        continue;
                    }

                    for (String headerKey : authWithCond.getHeaderKVPairs().keySet()) {
                        String headerVal = authWithCond.getHeaderKVPairs().get(headerKey);
                        List<String> rawHeaderValue = rawApi.getRequest().getHeaders().getOrDefault(headerKey.toLowerCase(), new ArrayList<>());
                        if (!rawHeaderValue.contains(headerVal)) {
                            allSatisfied = false;
                            break;
                        }
                    }

                    if (allSatisfied) {
                        AuthMechanism ret = authWithCond.getAuthMechanism();
                        if (authWithCond.getRecordedLoginFlowInput() != null) {
                            ret.setRecordedLoginFlowInput(authWithCond.getRecordedLoginFlowInput());
                        }
                        return ret;
                    }
                } catch (Exception e) {
                    // Handle exception if needed
                }
            }
        }
        
        return findDefaultAuthMechanism();
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ObjectId getEndpointLogicalGroupId() {
        return endpointLogicalGroupId;
    }

    public void setEndpointLogicalGroupId(ObjectId endpointLogicalGroupId) {
        this.endpointLogicalGroupId = endpointLogicalGroupId;
    }

    public EndpointLogicalGroup getEndpointLogicalGroup() {
        return endpointLogicalGroup;
    }

    public void setEndpointLogicalGroup(EndpointLogicalGroup endpointLogicalGroup) {
        this.endpointLogicalGroup = endpointLogicalGroup;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public int getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(int createdTs) {
        this.createdTs = createdTs;
    }

    public int getLastUpdatedTs() {
        return lastUpdatedTs;
    }

    public void setLastUpdatedTs(int lastUpdatedTs) {
        this.lastUpdatedTs = lastUpdatedTs;
    }

    public List<AuthWithCond> getAuthWithCondList() {
        return authWithCondList;
    }

    public void setAuthWithCondList(List<AuthWithCond> authWithCondList) {
        this.authWithCondList = authWithCondList;
    }

    public String getHexId() {
        if (hexId == null) return this.id.toHexString();
        return this.hexId;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }
    public List<Integer> getApiCollectionIds() {
        return apiCollectionIds;
    }

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    public List<String> getScopeRoles() {
        return scopeRoles;
    }   

    public void setScopeRoles(List<String> scopeRoles) {
        this.scopeRoles = scopeRoles;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public boolean isDefaultPresetAuth() {
        return defaultPresetAuth;
    }

    public void setDefaultPresetAuth(boolean defaultPresetAuth) {
        this.defaultPresetAuth = defaultPresetAuth;
    }

    public static HttpResponseParams createResponseParamsFromRawApi(RawApi rawApi) {
        if (rawApi == null || rawApi.getResponse() == null) {
            return null;
        }
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setStatusCode(rawApi.getResponse().getStatusCode());
        responseParams.setHeaders(rawApi.getResponse().getHeaders());
        responseParams.setPayload(rawApi.getResponse().getBody());
        return responseParams;

    }

    public static ApiInfo createApiInfoFromRawApi(HttpResponseParams httpResponseParams) {
        if (httpResponseParams == null || httpResponseParams.requestParams == null) {
            return null;
        }
        return new ApiInfo(httpResponseParams);
    }
}
