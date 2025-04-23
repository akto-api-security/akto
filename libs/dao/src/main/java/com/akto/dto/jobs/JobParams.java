package com.akto.dto.jobs;

import lombok.ToString;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@BsonDiscriminator
@ToString
public abstract class JobParams {
    public abstract JobType getJobType();

    @BsonIgnore
    public abstract Class<? extends JobParams> getParamsClass();
}

