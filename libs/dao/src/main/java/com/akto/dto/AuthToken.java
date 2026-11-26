package com.akto.dto;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Server-side record of a JWT; the token is never stored, only its id (sha256 of its signature) and a hint.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthToken {

    public static final String ID = "_id";
    private String id;
    public static final String NAME = "name";
    private String name;
    public static final String HINT = "hint";
    private String hint;
    public static final String SCOPE = "scope";
    private List<String> scope;
    public static final String CREATED_BY = "createdBy";
    private int createdBy;
    public static final String CREATED_BY_EMAIL = "createdByEmail";
    private String createdByEmail;
    public static final String CREATED_AT = "createdAt";
    private int createdAt;
    // 0 means the token never expires
    public static final String EXPIRES_AT = "expiresAt";
    private int expiresAt;
    public static final String STATUS = "status";
    private String status;
    public static final String REVOKED_AT = "revokedAt";
    private int revokedAt;
    public static final String REVOKED_BY = "revokedBy";
    private String revokedBy;
    public static final String REVOKE_REASON = "revokeReason";
    private String revokeReason;
    public static final String LAST_USED_AT = "lastUsedAt";
    private int lastUsedAt;
    public static final String LAST_USED_MODULE_TYPE = "lastUsedModuleType";
    private String lastUsedModuleType;
    public static final String SOURCE = "source";
    private String source;
    // TTL index field: the record is deleted 30 days after the token expires; unset for tokens that never expire
    public static final String DELETE_AT = "deleteAt";
    private Date deleteAt;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    // GENERATED: minted through the dashboard; DISCOVERED: legacy token first seen by cyborg
    public static final String SOURCE_GENERATED = "GENERATED";
    public static final String SOURCE_DISCOVERED = "DISCOVERED";
}
