package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class McpServerCollectionInfo {
    private String collectionName;
    private String tempCollectionName;
    private String domainName;
}
