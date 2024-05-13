package com.akto.dto.testing;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import org.apache.commons.lang3.NotImplementedException;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class RiskScoreTestingEndpoints extends TestingEndpoints {

    private RiskScoreGroupType riskScoreGroupType;
    private float riskScoreGroupLowerBound;
    private float riskScoreGroupUpperBound;

    @BsonIgnore
    private List<ApiInfo> filterRiskScoreGroupApis;

    public static int BATCH_SIZE = 100;

    public enum RiskScoreGroupType {
        LOW, MEDIUM, HIGH
    }

    public RiskScoreTestingEndpoints() {
        super(Type.RISK_SCORE);
        this.filterRiskScoreGroupApis = new ArrayList<ApiInfo>();
    }

    public RiskScoreTestingEndpoints(RiskScoreGroupType riskScoreGroupType) {
        super(Type.RISK_SCORE);
        this.riskScoreGroupType = riskScoreGroupType;

        switch (riskScoreGroupType) {
            case LOW:
                riskScoreGroupLowerBound = 0f;
                riskScoreGroupUpperBound = 3f;
                break;
            case MEDIUM:
                riskScoreGroupLowerBound = 3f;
                riskScoreGroupUpperBound = 4f;
                break;
            case HIGH:
                riskScoreGroupLowerBound = 4f;
                riskScoreGroupUpperBound = 5f;
                break;
        }

        this.filterRiskScoreGroupApis = new ArrayList<ApiInfo>();
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        throw new NotImplementedException();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        return false;
    }

    private static Bson createApiFilters(CollectionType type, ApiInfo api) {

        String prefix = getFilterPrefix(type);
        ApiInfoKey apiInfoKey = api.getId();

        return Filters.and(
                Filters.eq(prefix + SingleTypeInfo._URL, apiInfoKey.getUrl()),
                Filters.eq(prefix + SingleTypeInfo._METHOD, apiInfoKey.getMethod().toString()),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, apiInfoKey.getApiCollectionId()));

    }

    @Override
    public Bson createFilters(CollectionType type) {
        Set<ApiInfo> apisSet = new HashSet<>(filterRiskScoreGroupApis);
        List<Bson> apiFilters = new ArrayList<>();
        if (apisSet != null && !apisSet.isEmpty()) {
            for (ApiInfo api : apisSet) {
                apiFilters.add(createApiFilters(type, api));
            }
            return Filters.or(apiFilters);
        }
        
        return MCollection.noMatchFilter;
    }

    public List<ApiInfo> fetchRiskScoreGroupApis() {
        List<ApiInfo> riskScoreGroupApis = ApiInfoDao.instance.findAll(
                Filters.and(
                        Filters.gte("riskScore", riskScoreGroupLowerBound),
                        Filters.lt("riskScore", riskScoreGroupUpperBound))
                );
        return riskScoreGroupApis;
    }

    public static RiskScoreGroupType calculateRiskScoreGroup(float riskScore) {
        if (riskScore >= 0f && riskScore < 3f) {
            return RiskScoreGroupType.LOW;
        } else if (riskScore >= 3f && riskScore < 4f) {
            return RiskScoreGroupType.MEDIUM;
        } else if (riskScore >= 4f && riskScore <= 5f) {
            return RiskScoreGroupType.HIGH;
        }
        
        throw new IllegalArgumentException("Risk score is out of expected range: " + riskScore);
    }

    public static int getApiCollectionId(RiskScoreGroupType riskScoreGroupType) {
        switch (riskScoreGroupType) {
            case LOW:
                return 111_111_148;
            case MEDIUM:
                return 111_111_149;
            case HIGH:
                return 111_111_150;
            default:
                return -1;
        }
    }

    public RiskScoreGroupType getRiskScoreGroupType() {
        return riskScoreGroupType;
    }

    public void setRiskScoreGroupType(RiskScoreGroupType riskScoreGroupType) {
        this.riskScoreGroupType = riskScoreGroupType;
    }

    public float getRiskScoreGroupLowerBound() {
        return riskScoreGroupLowerBound;
    }

    public void setRiskScoreGroupLowerBound(float riskScoreGroupLowerBound) {
        this.riskScoreGroupLowerBound = riskScoreGroupLowerBound;
    }

    public float getRiskScoreGroupUpperBound() {
        return riskScoreGroupUpperBound;
    }

    public void setRiskScoreGroupUpperBound(float riskScoreGroupUpperBound) {
        this.riskScoreGroupUpperBound = riskScoreGroupUpperBound;
    }

    public List<ApiInfo> getFilterRiskScoreGroupApis() {
        return filterRiskScoreGroupApis;
    }

    public void setFilterRiskScoreGroupApis(List<ApiInfo> filterRiskScoreGroupApis) {
        this.filterRiskScoreGroupApis = filterRiskScoreGroupApis;
    }
}
