package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.mongodb.client.model.Filters;

public class AllAPIsGroup extends TestingEndpoints {

    public AllAPIsGroup() {
        super(Type.ALL, Operator.OR);
    }

    @Override
    public List<ApiInfoKey> returnApis() {
        throw new UnsupportedOperationException("Unimplemented method 'returnApis'");
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        return true;
    }

    public final static int ALL_APIS_GROUP_ID = 111_111_121;

    @Override
    public Bson createFilters(CollectionType type) {
        return Filters.empty();
    }

    /** Every api is in this group, so the only work is tagging the apis with new data. */
    private static final DeltaGroupSync GROUP_SYNC = new NewDataGroupSync() {
        @Override
        protected int groupId() {
            return ALL_APIS_GROUP_ID;
        }

        @Override
        protected String groupName() {
            return "all apis group";
        }

        @Override
        protected TestingEndpoints endpointsFor(List<ApiInfoKey> keys) {
            return new CustomTestingEndpoints(new ArrayList<>(keys));
        }
    };

    public static void updateCollections() {
        GROUP_SYNC.run();
    }
}
