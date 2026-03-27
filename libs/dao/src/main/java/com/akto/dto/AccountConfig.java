package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;

@Getter
@Setter
@NoArgsConstructor
public class AccountConfig {

    @BsonId
    private String id;                          // orgId + "_" + aktoAccountId
    public static final String ID = "_id";

    private String orgId;
    public static final String ORG_ID = "orgId";

    private String adminEmail;
    public static final String ADMIN_EMAIL = "adminEmail";

    private int adminAccountId;
    public static final String ADMIN_ACCOUNT_ID = "adminAccountId";

    private int aktoAccountId;
    public static final String AKTO_ACCOUNT_ID = "aktoAccountId";

    private String awsAccountId;
    public static final String AWS_ACCOUNT_ID = "awsAccountId";
}
