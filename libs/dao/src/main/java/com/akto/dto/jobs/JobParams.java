package com.akto.dto.jobs;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@ToString
@BsonDiscriminator
public abstract class JobParams {
    public abstract JobType getJobType();

    @BsonIgnore
    public abstract Class<? extends JobParams> getParamsClass();
}

