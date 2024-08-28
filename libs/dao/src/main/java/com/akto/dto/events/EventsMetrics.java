package com.akto.dto.events;

import java.util.Map;
import io.intercom.api.User;

public class EventsMetrics {

    public static final String DEPLOYMENT_STARTED = "deploymentStarted";
    private boolean deploymentStarted;

    public static final String UNAUTHENTICATED_APIS = "unauthenticatedAPIs";
    private EventsExample unauthenticatedAPIs;

    public static final String MILESTONES = "milestones";
    private Map<String,Integer> milestones;

    public static final String API_SECURITY_POSTURE = "apiSecurityPosture";
    private Map<String, EventsExample> apiSecurityPosture;

    public static final String SECURITY_TEST_FINDINGS = "securityTestFindings";
    private Map<String, Integer> securityTestFindings;

    public static final String CUSTOM_TEMPLATE_COUNT = "customTemplatesCount";
    private int customTemplatesCount;

    public static final String CREATED_AT = "createdAt";
    private int createdAt;

    public static final String APIS_INFO_COUNT = "apiInfosCount";

    public EventsMetrics (){}


    public EventsMetrics(boolean deploymentStarted, EventsExample unauthenticatedAPIs, Map<String,Integer> milestones,
            Map<String, EventsExample> apiSecurityPosture, Map<String, Integer> securityTestFindings,
            int customTemplatesCount, int createdAt) {
        this.deploymentStarted = deploymentStarted;
        this.unauthenticatedAPIs = unauthenticatedAPIs;
        this.milestones = milestones;
        this.apiSecurityPosture = apiSecurityPosture;
        this.securityTestFindings = securityTestFindings;
        this.customTemplatesCount = customTemplatesCount;
        this.createdAt = createdAt;
    }


    
    public boolean isDeploymentStarted() {
        return deploymentStarted;
    }
    public void setDeploymentStarted(boolean deploymentStarted) {
        this.deploymentStarted = deploymentStarted;
    }

    public EventsExample getUnauthenticatedAPIs() {
        return unauthenticatedAPIs;
    }
    public void setUnauthenticatedAPIs(EventsExample unauthenticatedAPIs) {
        this.unauthenticatedAPIs = unauthenticatedAPIs;
    }

    public Map<String,Integer> getMilestones() {
        return milestones;
    }
    public void setMilestones(Map<String,Integer> milestones) {
        this.milestones = milestones;
    }

    public Map<String, EventsExample> getApiSecurityPosture() {
        return apiSecurityPosture;
    }
    public void setApiSecurityPosture(Map<String, EventsExample> apiSecurityPosture) {
        this.apiSecurityPosture = apiSecurityPosture;
    }

    public Map<String, Integer> getSecurityTestFindings() {
        return securityTestFindings;
    }
    public void setSecurityTestFindings(Map<String, Integer> securityTestFindings) {
        this.securityTestFindings = securityTestFindings;
    }

    public int getCustomTemplatesCount() {
        return customTemplatesCount;
    }
    public void setCustomTemplatesCount(int customTemplatesCount) {
        this.customTemplatesCount = customTemplatesCount;
    }

    public int getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    public static void createUserInIntercom(String email, String name, int currentTime) throws Exception{
        User user = new User().setEmail(email).setName(name).setLastRequestAt(currentTime);
        try {
            User.create(user);
        } catch (Exception e) {
           throw new Exception("Could not create new user with email: " + email + " in intercom");
        }
    }
    
}
