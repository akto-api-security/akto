package com.akto.imperva.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthParameterLocation {
    private String authParameterLocation;
    private Long lastModified;
    private String lastModifiedUser;
    private Long[] siteIds;
    private Boolean useForFutureWebSites;
}