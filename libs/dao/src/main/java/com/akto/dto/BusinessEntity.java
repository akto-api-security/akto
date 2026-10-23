package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessEntity {

    @BsonId
    private String id;

    private String scopeKey;
    public static final String SCOPE_KEY = "scopeKey";

    private String slug;
    private String name;

    public static final String TIER = "tier";
    public static final String TIER_CONFIRMED = "confirmed";

    private Stats stats;
    public static final String API_COUNT = "stats.apiCount";

    private List<NamedCount> aliases;
    private List<NamedCount> roles;
    private List<NamedCount> hosts;
    private List<NamedCount> parentContexts;
    private List<String> attributes;
    private Evidence evidence;
    private List<SampleApi> sampleApis;

    private Date createdAt;
    private Date updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private int apiCount;
        private int collectionCount;
        private int hostCount;
        private int requestApis;
        private int responseApis;
        private int pathApis;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NamedCount {
        private String name;
        private int apiCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private List<String> statefulAttributes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleApi {
        private int apiCollectionId;
        private String hostName;
        private String method;
        private String url;
        private String location;
        private String param;
    }
}
