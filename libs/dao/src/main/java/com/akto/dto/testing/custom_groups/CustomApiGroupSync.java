package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

/**
 * Keeps a group that has rules (conditions) up to date: the apis with new data are asked against the group's own rules,
 * so who is in the group is decided exactly as the rules' database filters decide it. The rules themselves are applied
 * to everything when the group is created or edited; this only follows new data after that.
 */
public class CustomApiGroupSync extends NewDataApiGroupSync {

    private static final int EVALUATE_BATCH_SIZE = 200;

    private final ApiCollection collection;
    private Bson ruleFilter;

    public CustomApiGroupSync(ApiCollection collection) {
        this.collection = collection;
    }

    @Override
    protected int groupId() {
        return collection.getId();
    }

    @Override
    protected String groupName() {
        return "custom group " + collection.getId() + " (" + collection.getName() + ")";
    }

    @Override
    protected TestingEndpoints endpointsFor(List<ApiInfoKey> keys) {
        return new CustomTestingEndpoints(new ArrayList<>(keys));
    }

    @Override
    protected void scan(int from, int to, Predicate<Candidate> visitor) {
        List<Candidate> batch = new ArrayList<>();
        boolean[] stopped = {false};
        super.scan(from, to, candidate -> {
            batch.add(candidate);
            if (batch.size() >= EVALUATE_BATCH_SIZE && !evaluate(batch, visitor)) {
                stopped[0] = true;
                return false;
            }
            return true;
        });
        if (!stopped[0]) {
            evaluate(batch, visitor);
        }
    }

    /** Decides membership of the batch with the group's rules and reports it, then empties the batch. @return false when the visitor asked to stop */
    private boolean evaluate(List<Candidate> batch, Predicate<Candidate> visitor) {
        if (batch.isEmpty()) {
            return true;
        }
        Set<ApiInfoKey> matching = matchingApis(batch);
        boolean keepGoing = true;
        for (Candidate candidate : batch) {
            if (!visitor.test(new Candidate(candidate.key, matching.contains(candidate.key), candidate.inGroup))) {
                keepGoing = false;
                break;
            }
        }
        batch.clear();
        return keepGoing;
    }

    /** One query on api_info: the given apis that the group's rules match. A group with no rules matches nothing. */
    private Set<ApiInfoKey> matchingApis(List<Candidate> batch) {
        Set<ApiInfoKey> matching = new HashSet<>();
        List<TestingEndpoints> conditions = collection.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return matching;
        }
        if (ruleFilter == null) {
            ruleFilter = ApiCollectionUsers.getFilters(conditions, CollectionType.Id_ApiCollectionId);
        }

        List<ApiInfoKey> keys = new ArrayList<>();
        for (Candidate candidate : batch) {
            keys.add(candidate.key);
        }
        Bson filter = Filters.and(ruleFilter, ApiInfoDao.getFilter(keys));
        try (MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().find(filter)
                .projection(Projections.include("_id")).cursor()) {
            while (cursor.hasNext()) {
                matching.add(cursor.next().getId());
            }
        }
        return matching;
    }
}
