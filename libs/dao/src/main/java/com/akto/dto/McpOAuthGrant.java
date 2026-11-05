package com.akto.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Short-lived OAuth secret (pending request, auth code or refresh token); only its SHA-256 is stored
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class McpOAuthGrant {

    public static final String ID = "_id";
    public static final String TYPE = "type";
    public static final String EXPIRES_AT = "expiresAt";

    public static final String TYPE_REQUEST = "REQUEST";
    public static final String TYPE_CODE = "CODE";
    public static final String TYPE_REFRESH = "REFRESH";

    // hex SHA-256 of the secret, stored as _id
    private String id;
    private String type;
    private String clientId;
    private String redirectUri;
    private String state;
    private String codeChallenge;
    private String resource;
    private String username;
    private int accountId;
    private String contextSource;
    private Date expiresAt;
}
