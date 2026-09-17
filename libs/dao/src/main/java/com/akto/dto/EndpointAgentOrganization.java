package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EndpointAgentOrganization {

    // The uuid is the _id, so mongo's own unique index is what keeps one row per org.
    public static final String ORGANIZATION_UUID = "_id";
    @BsonId
    private String organizationUuid;

    public static final String SEPARATOR = "__";

    // "<organizationName>__<organizationType>", eg "Acme's Organization__claude_max".
    public static final String ORGANIZATION_INFO = "organizationInfo";
    private String organizationInfo;

    // Which endpoint agent reported the org, eg "claude-cli".
    public static final String AGENT_TYPE = "agentType";
    private String agentType;

    public static final String CREATED_AT = "createdAt";
    private int createdAt;
}
