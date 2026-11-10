package com.akto.dto;

import java.util.Date;
import java.util.List;
import com.akto.dao.context.Context;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiSequences {

    int apiCollectionId;
    public static final String API_COLLECTION_ID = "apiCollectionId";

    // Path should be be ApiInfoKey.toString()
    List<String> paths;
    public static final String PATHS = "paths";

    // Example: No of times transition from a->b was seen
    int transitionCount;
    public static final String TRANSITION_COUNT = "transitionCount";

    // Example: In a->b No of times the path "a" was seen
    int prevStateCount;
    public static final String PREV_STATE_COUNT = "prevStateCount";

    // No of times the last endpoint in the sequence was seen
    // In a->b , no of time b was seen
    int lastStateCount;
    public static final String LAST_STATE_COUNT = "lastStateCount";

    /*
     * Precedence Score = occurrences of the sequence / occurrences of the last endpoint in the sequence
     * A score close to 1.0 means: "almost every time endpoint Z is called, 
     * it's preceded by this exact sequence.
     */
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

    // Paths joined by "|", unique per row
    String seqKey;
    public static final String SEQ_KEY = "seqKey";

    // Previous APIs joined by "|" ("" when order is 0) and the API that followed
    String contextKey;
    public static final String CONTEXT_KEY = "contextKey";

    String next;
    public static final String NEXT = "next";

    // Number of previous APIs: 0, 1 or 2
    int order;
    public static final String ORDER = "order";

    // Decayed count, halves every day; transitionCount is its rounded value
    double weight;
    public static final String WEIGHT = "weight";

    // Lower bound of precedenceScore
    double precedenceLow;
    public static final String PRECEDENCE_LOW = "precedenceLow";

    // weight * precedenceLow, used to rank sequences
    double score;
    public static final String SCORE = "score";

    // Event time the weight was last decayed to
    int lastTs;
    public static final String LAST_TS = "lastTs";

    Date lastSeen;
    public static final String LAST_SEEN = "lastSeen";

    public ApiSequences(int apiCollectionId, List<String> paths, int transitionCount,
                       int prevStateCount, int lastStateCount, float precedenceScore, float probability) {
        this.apiCollectionId = apiCollectionId;
        this.paths = paths;
        this.transitionCount = transitionCount;
        this.prevStateCount = prevStateCount;
        this.lastStateCount = lastStateCount;
        this.precedenceScore = precedenceScore;
        this.probability = probability;
        this.createdAt = Context.now();
        this.lastUpdatedAt = Context.now();
    }

    @Override
    public String toString() {
        return "{" +
            " apiCollectionId='" + getApiCollectionId() + "'" +
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

