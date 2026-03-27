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

    private Map<Integer, Object> accounts;  // aktoAccountId → account config map
    public static final String ACCOUNTS = "accounts";

    public enum AccountType {
        AWS_ACCOUNTS("AWS-ACCOUNTS"),
        GCP_ACCOUNTS("GCP-ACCOUNTS"),
        AZURE_ACCOUNTS("AZURE-ACCOUNTS");

        private final String value;

        AccountType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static AccountType fromValue(String value) {
            for (AccountType type : AccountType.values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown account type: " + value);
        }
    }

    // Account field constants
    public static final String TYPE = "type";
    public static final String AWS_ACCOUNT_ID = "awsAccountId";
    public static final String CREATED_TIMESTAMP = "createdTimestamp";
    public static final String LAST_UPDATED_TIMESTAMP = "lastUpdatedTimestamp";
}
