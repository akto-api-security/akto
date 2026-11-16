package com.akto.action;

import com.akto.dao.ApiSequencesDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiSequences;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Getter
@Setter
public class ApiSequencesAction extends UserAction {

    // 0 means the whole account
    private int apiCollectionId;
    // The APIs clicked so far; empty means list where sessions can start
    private List<String> path = new ArrayList<>();

    // Weights are aged to now, so shares within a fan describe the same moment
    private List<ApiSequences> nextSteps = new ArrayList<>();
    private double pathWeight;

    public String fetchSessionSteps() {
        int now = Context.now();
        List<ApiSequences> rows;
        if (path == null || path.isEmpty()) {
            // Every API of the collection with its call count (order-0 rows)
            rows = findByKeyPrefix(apiCollectionId == 0 ? "" : apiCollectionId + " ");
        } else {
            // What sessions called right after the last one or two clicked APIs
            String context = String.join("|", path.subList(Math.max(0, path.size() - 2), path.size()));
            ApiSequences base = ApiSequencesDao.instance.findOne(Filters.eq(ApiSequences.SEQ_KEY, context),
                    Projections.include(ApiSequences.WEIGHT, ApiSequences.LAST_TS));
            pathWeight = base == null ? 0 : agedWeight(base, now);
            rows = findByKeyPrefix(context + "|");
        }
        for (ApiSequences row : rows) row.setWeight(agedWeight(row, now));
        rows.sort((a, b) -> Double.compare(b.getWeight(), a.getWeight()));
        nextSteps = rows;
        return Action.SUCCESS.toUpperCase();
    }

    // Rows whose seqKey is the prefix plus one more step; the range keeps the scan on the seqKey index
    private static List<ApiSequences> findByKeyPrefix(String prefix) {
        Bson range = Filters.and(Filters.gte(ApiSequences.SEQ_KEY, prefix), Filters.lt(ApiSequences.SEQ_KEY, prefix + "￿"));
        Bson oneMoreStep = Filters.regex(ApiSequences.SEQ_KEY, "^" + Pattern.quote(prefix) + "[^|]*$");
        return ApiSequencesDao.instance.findAll(Filters.and(range, oneMoreStep),
                Projections.include(ApiSequences.SEQ_KEY, ApiSequences.NEXT, ApiSequences.WEIGHT, ApiSequences.LAST_TS));
    }

    // A row's weight is decayed only when it is written; age it the rest of the way to now
    private static double agedWeight(ApiSequences row, int now) {
        return row.getWeight() * Math.pow(0.5, Math.max(0, now - row.getLastTs()) / (double) ApiSequencesDao.HALF_LIFE_SECONDS);
    }
}
