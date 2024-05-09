package com.akto.dto.testing;

import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.NotImplementedException;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.regex.Pattern;

public class RiskScoreTestingEndpoints extends TestingEndpoints {

    private RiskScoreGroupType riskScoreGroupType;
    private int riskScoreGroupLowerBound;
    private int riskScoreGroupUpperBound;

    public enum RiskScoreGroupType {
        LOW, MEDIUM, HIGH
    }

    public RiskScoreTestingEndpoints() {
        super(Type.RISK_SCORE);
    }

    public RiskScoreTestingEndpoints(RiskScoreGroupType riskScoreGroupType) {
        super(Type.RISK_SCORE);
        this.riskScoreGroupType = riskScoreGroupType;

        switch (riskScoreGroupType) {
            case LOW:
                riskScoreGroupLowerBound = 0;
                riskScoreGroupUpperBound = 3;
                break;
            case MEDIUM:
                riskScoreGroupLowerBound = 3;
                riskScoreGroupUpperBound = 4;
                break;
            case HIGH:
                riskScoreGroupLowerBound = 4;
                riskScoreGroupUpperBound = 5;
                break;
        }
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        throw new NotImplementedException();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        throw new NotImplementedException();
    }

    @Override
    public Bson createFilters(ApiCollectionUsers.CollectionType type) {
        return MCollection.noMatchFilter;
    }

    public RiskScoreGroupType getRiskScoreGroupType() {
        return riskScoreGroupType;
    }

    public void setRiskScoreGroupType(RiskScoreGroupType riskScoreGroupType) {
        this.riskScoreGroupType = riskScoreGroupType;
    }

    public int getRiskScoreGroupLowerBound() {
        return riskScoreGroupLowerBound;
    }

    public void setRiskScoreGroupLowerBound(int riskScoreGroupLowerBound) {
        this.riskScoreGroupLowerBound = riskScoreGroupLowerBound;
    }

    public int getRiskScoreGroupUpperBound() {
        return riskScoreGroupUpperBound;
    }

    public void setRiskScoreGroupUpperBound(int riskScoreGroupUpperBound) {
        this.riskScoreGroupUpperBound = riskScoreGroupUpperBound;
    }
}
