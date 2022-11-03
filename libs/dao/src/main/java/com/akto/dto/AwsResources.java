package com.akto.dto;

import java.util.List;

public class AwsResources {
    // private EnumMap<AwsResourceType, List<AwsResource>> awsResources;
    private int id;
    private List<AwsResource> loadBalancers;

    public AwsResources() {
    }

    public AwsResources(int id, List<AwsResource> loadBalancers) {
        this.id = id;
        this.loadBalancers = loadBalancers;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<AwsResource> getLoadBalancers() {
        return this.loadBalancers;
    }

    public void setLoadBalancers(List<AwsResource> loadBalancers) {
        this.loadBalancers = loadBalancers;
    }
}

// enum AwsResourceType {
// LoadBalancer, EC2;
// }
