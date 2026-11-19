package com.akto.dto.jobs;

import lombok.*;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@BsonDiscriminator
@ToString
public class ImpervaApiEndpointsImportJobParams extends JobParams {

    @Override
    public JobType getJobType() {
        return JobType.IMPERVA_API_ENDPOINTS_IMPORT;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return ImpervaApiEndpointsImportJobParams.class;
    }
}
