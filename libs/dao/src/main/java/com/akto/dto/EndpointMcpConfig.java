package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EndpointMcpConfig {
    public static final String COLLECTION_NAME_FIELD = "collectionName";
    public static final String TEMP_COLLECTION_NAME_FIELD = "tempCollectionName";
    public static final String DOMAIN_NAME_FIELD = "domainName";
    public static final String CREATED_DATE_FIELD = "createdDate";
    public static final String UPDATED_DATE_FIELD = "updatedDate";

    private String collectionName;
    private String tempCollectionName;
    private String domainName;
    private int createdDate;
    private int updatedDate;
}
