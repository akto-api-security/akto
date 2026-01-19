package com.akto.dto.jobs;

import lombok.*;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@BsonDiscriminator
@ToString
public class DatadogTrafficCollectorJobParams extends JobParams {

    private long lastJobRunTimestamp;
    private String datadogApiKey;
    private String datadogAppKey;
    private String datadogSite;
    private List<String> serviceNames;
    private int limit;

    @Override
    public JobType getJobType() {
        return JobType.DATADOG_TRAFFIC_COLLECTOR;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return DatadogTrafficCollectorJobParams.class;
    }
}
