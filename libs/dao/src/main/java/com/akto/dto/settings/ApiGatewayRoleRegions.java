package com.akto.dto.settings;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * One cross-account role the API Gateway connector assumes, and the AWS
 * regions it reads logs and specs from with that role. An empty regions list
 * means "the connector's own region", which is how roles behaved before
 * regions could be set.
 */
@Getter
@Setter
public class ApiGatewayRoleRegions {

    public static final String ROLE_ARN = "roleArn";
    private String roleArn;

    public static final String REGIONS = "regions";
    private List<String> regions = new ArrayList<>();

    public ApiGatewayRoleRegions() {
    }

    public ApiGatewayRoleRegions(String roleArn, List<String> regions) {
        this.roleArn = roleArn;
        this.regions = regions;
    }
}
