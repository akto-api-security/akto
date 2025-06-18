package com.akto.dto.jobs;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@ToString
@BsonDiscriminator
@NoArgsConstructor
public class McpSyncToolsJobParams extends JobParams {
    @Override
    public JobType getJobType() {
        return JobType.MCP_TOOLS_SYNC;
    }

    @Override
    @BsonIgnore
    public Class<? extends JobParams> getParamsClass() {
        return McpSyncToolsJobParams.class;
    }
} 