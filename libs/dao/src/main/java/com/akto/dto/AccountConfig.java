package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class AccountConfig {

    @BsonId
    private String id;                          // orgId
    public static final String ID = "_id";

    private String adminEmail;
    public static final String ADMIN_EMAIL = "adminEmail";

    private int adminAccountId;
    public static final String ADMIN_ACCOUNT_ID = "adminAccountId";

    private Map<Integer, AccountEntry> accounts;  // aktoAccountId → AccountEntry
    public static final String ACCOUNTS = "accounts";

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AccountEntry {
        private String awsAccountId;
        public static final String AWS_ACCOUNT_ID = "awsAccountId";

        private long createdTimestamp;
        public static final String CREATED_TIMESTAMP = "createdTimestamp";

        private long lastUpdatedTimestamp;
        public static final String LAST_UPDATED_TIMESTAMP = "lastUpdatedTimestamp";

        public AccountEntry(String awsAccountId) {
            this.awsAccountId = awsAccountId;
        }
    }
}
