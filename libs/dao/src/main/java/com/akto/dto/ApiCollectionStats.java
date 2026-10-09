package com.akto.dto;

import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import org.bson.codecs.pojo.annotations.BsonId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One small row per api collection: the filterable attributes (denormalized from api_collections)
 * and the sortable metrics (riskScore, lastSeen, endpointsCount), so a page of the collections
 * table is a single indexed find instead of a fan-out over api_info / single_type_info.
 *
 * Attributes keep the field names they have on api_collections, so one pipeline can copy them over.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApiCollectionStats {

    public static final String ID = Constants.ID;
    @BsonId
    private int id;

    public static final String NAME = ApiCollection.NAME;
    private String name;

    public static final String DISPLAY_NAME = "displayName";
    private String displayName;

    public static final String HOST_NAME = ApiCollection.HOST_NAME;
    private String hostName;

    public static final String TYPE = ApiCollection._TYPE;
    private String type;

    public static final String DEACTIVATED = ApiCollection._DEACTIVATED;
    private boolean deactivated;

    public static final String START_TS = ApiCollection.START_TS;
    private int startTs;

    public static final String TAGS_LIST = ApiCollection.TAGS_STRING;
    private List<CollectionTags> tagsList;

    public static final String DESCRIPTION = ApiCollection.DESCRIPTION;
    public static final String IS_OUT_OF_TESTING_SCOPE = ApiCollection.IS_OUT_OF_TESTING_SCOPE;
    public static final String ACCESS_TYPE = ApiCollection.ACCESS_TYPE;

    public static final String TAB = "tab";
    private String tab;

    // size of api_collections.urls, the fallback input of ApiCollectionsAction.resolveUrlsCount
    public static final String URLS_FALLBACK_COUNT = "urlsFallbackCount";
    private int urlsFallbackCount;

    public static final String ENDPOINTS_COUNT = "endpointsCount";
    private int endpointsCount;

    public static final String RISK_SCORE = ApiInfo.RISK_SCORE;
    private double riskScore;

    public static final String LAST_SEEN = ApiInfo.LAST_SEEN;
    private int lastSeen;

    public static final String SENSITIVE_SUB_TYPES = "sensitiveSubTypes";
    private List<String> sensitiveSubTypes;

    public static final String ATTRS_SYNCED_AT = "attrsSyncedAt";

    // per metric write time; rows whose value predates a refresh are reset by it
    public static final String AT_SUFFIX = "At";

    public enum Tab {
        HOSTNAME, GROUP, CUSTOM, DEACTIVATED, UNTRACKED
    }
}
