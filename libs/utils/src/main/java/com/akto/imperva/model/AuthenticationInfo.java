package com.akto.imperva.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthenticationInfo {
    private AuthParameterLocation[] authParameterLocations;
    private String status;
}