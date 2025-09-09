package com.akto.dto;

import java.util.List;
import org.bson.codecs.pojo.annotations.BsonId;
import com.akto.dao.context.Context;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiSequences {

    @BsonId
    int id;
    public static final String ID = "_id";

    int apiCollectionId;
    public static final String API_COLLECTION_ID = "apiCollectionId";

    // Path should be be ApiInfoKey.toString()
    List<String> paths;
    public static final String PATHS = "paths";

    // Example: No of times transition from a->b was seen
    int transitionCount;
    public static final String TRANSITION_COUNT = "transitionCount";

    // Example: No of times the path "a" was seen
    int prevStateCount;
    public static final String PREV_STATE_COUNT = "prevStateCount";

    float precedenceScore;
    public static final String PRECEDENCE_SCORE = "precedenceScore";

    // Example: Probability of transition from a->b
    // Probability = transitionCount / prevStateCount
    float probability;
    public static final String PROBABILITY = "probability";

    int lastUpdatedAt;
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";

    int createdAt;
    public static final String CREATED_AT = "createdAt";

    // Show to user or not.
    boolean isActive;
    public static final String IS_ACTIVE = "isActive";

    public ApiSequences(int id, int apiCollectionId, List<String> paths, int transitionCount, 
                       int prevStateCount, float precedenceScore, float probability) {
        this.id = id;
        this.apiCollectionId = apiCollectionId;
        this.paths = paths;
        this.transitionCount = transitionCount;
        this.prevStateCount = prevStateCount;
        this.precedenceScore = precedenceScore;
        this.probability = probability;
        this.createdAt = Context.now();
        this.lastUpdatedAt = Context.now();
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", apiCollectionId='" + getApiCollectionId() + "'" +
            ", paths='" + getPaths() + "'" +
            ", transitionCount='" + getTransitionCount() + "'" +
            ", prevStateCount='" + getPrevStateCount() + "'" +
            ", precedenceScore='" + getPrecedenceScore() + "'" +
            ", probability='" + getProbability() + "'" +
            ", lastUpdatedAt='" + getLastUpdatedAt() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
