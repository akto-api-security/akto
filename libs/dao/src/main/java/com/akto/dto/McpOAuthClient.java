package com.akto.dto;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Dynamically registered (RFC 7591) public OAuth client of the MCP server
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class McpOAuthClient {

    public static final String CLIENT_NAME = "clientName";
    public static final String REDIRECT_URIS = "redirectUris";
    public static final String CREATED_AT = "createdAt";
    public static final String LAST_USED_AT = "lastUsedAt";

    // client_id, stored as _id
    private String id;
    private String clientName;
    private List<String> redirectUris;
    private int createdAt;
    // TTL field, must be a Date
    private Date lastUsedAt;
}
