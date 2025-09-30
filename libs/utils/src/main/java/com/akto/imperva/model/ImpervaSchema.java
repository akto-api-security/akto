package com.akto.imperva.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

/**
 * Root Imperva schema model representing the complete EndpointDrillDown structure
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImpervaSchema {
    private AuthenticationInfo authenticationInfo;
    private String hostName;
    private String method;
    private PathParamSegment[] pathParamSegments;
    private RequestDrillDown request;
    private String resource;
    private Map<String, ResponseDrillDown> responses;
}