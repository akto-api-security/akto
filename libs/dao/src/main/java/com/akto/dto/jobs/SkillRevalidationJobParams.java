package com.akto.dto.jobs;

import java.util.List;

import lombok.*;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@BsonDiscriminator
@ToString
public class SkillRevalidationJobParams extends JobParams {

    // Endpoint Shield agents (module_info ids) whose stored skills are validated again
    private List<String> agentIds;

    // progress, updated as the job runs
    private int skillsTotal;
    private int skillsRevalidated;
    private int skillsFailed;

    public SkillRevalidationJobParams(List<String> agentIds) {
        this.agentIds = agentIds;
    }

    @Override
    public JobType getJobType() {
        return JobType.SKILL_REVALIDATION;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return SkillRevalidationJobParams.class;
    }
}
