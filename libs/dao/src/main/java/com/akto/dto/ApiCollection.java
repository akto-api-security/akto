package com.akto.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import com.akto.dao.context.Context;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;

public class ApiCollection {

    @BsonId
    int id;
    public static final String ID = "_id";
    public static final String NAME = "name";
    String name;
    int startTs;
    public static final String _URLS = "urls";
    public static final String START_TS = "startTs";
    Set<String> urls;
    public static final String URLS_STRING = "urls";
    String hostName;
    public static final String HOST_NAME = "hostName";
    int vxlanId;

    boolean redact;
    public static final String REDACT = "redact";

    boolean sampleCollectionsDropped;

    public static final String SAMPLE_COLLECTIONS_DROPPED = "sampleCollectionsDropped";

    @BsonIgnore
    int urlsCount;

    public static final String VXLAN_ID = "vxlanId";

    public static final String _DEACTIVATED = "deactivated";
    boolean deactivated;

    public static final String AUTOMATED = "automated";
    boolean automated;

    private boolean runDependencyAnalyser;
    public static final String RUN_DEPENDENCY_ANALYSER = "runDependencyAnalyser";

    private boolean matchDependencyWithOtherCollections;
    public static final String MATCH_DEPENDENCY_WITH_OTHER_COLLECTIONS = "matchDependencyWithOtherCollections";

    public enum Type {
        API_GROUP
    }

    public enum ENV_TYPE {
        STAGING,PRODUCTION
    }

    Type type;
    public static final String _TYPE = "type";
    
    String userSetEnvType;

	public static final String USER_ENV_TYPE = "userSetEnvType";

    List<TestingEndpoints> conditions;
    public static final String CONDITIONS_STRING = "conditions";

    public ApiCollection() {
    }

    public ApiCollection(int id, String name, int startTs, Set<String> urls, String hostName, int vxlanId, boolean redact, boolean sampleCollectionsDropped) {
        this.id = id;
        this.name = name;
        this.startTs = startTs;
        this.urls = urls;
        this.hostName = hostName;
        this.vxlanId = vxlanId;
        this.redact = redact;
        this.sampleCollectionsDropped = sampleCollectionsDropped;
    }

    public ApiCollection(int id, String name, List<TestingEndpoints> conditions) {
        this.id = id;
        this.name = name;
        this.conditions = conditions;
        this.type = Type.API_GROUP;
        this.startTs = Context.now();
    }

    public static boolean useHost = true;

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStartTs() {
        return this.startTs;
    }

    public void setStartTs(int startTs) {
        this.startTs = startTs;
    }

    public Set<String> getUrls() {
        return this.urls;
    }

    public void setUrls(Set<String> urls) {
        this.urls = urls;
    }

    public String getEnvType(){
        if(this.type != null && this.type == Type.API_GROUP) return null;
        
        if(this.userSetEnvType == null){
            if(this.hostName != null && this.hostName.matches(".*(staging|preprod|qa|demo|dev|test\\.).*")){
                return "STAGING";
            }
            return null;
        }else{
            return this.userSetEnvType;
        }
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", startTs='" + getStartTs() + "'" +
            ", urls='" + getUrls() + "'" +
            "}";
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getVxlanId() {
        return vxlanId;
    }

    public void setVxlanId(int vxlanId) {
        this.vxlanId = vxlanId;
    }

    public int getUrlsCount() {
        return urlsCount;
    }

    public void setUrlsCount(int urlsCount) {
        this.urlsCount = urlsCount;
    }

    public boolean isDeactivated() {
        return deactivated;
    }

    public void setDeactivated(boolean deactivated) {
        this.deactivated = deactivated;
    }

    // to be used in front end
    public String getDisplayName() {
        String result;
        if (this.hostName != null) {
            result = this.hostName + " - " + this.name;
        } else {
            result = this.name + "";
        }

        if (this.hostName == null || this.name == null) {
            result = result.replace(" - ", "");
        }

        result = result.replace("null", "");
        return result;
    }

    // To be called if you are creating a collection that is not from mirroring
    public static ApiCollection createManualCollection(int id, String name){
        return new ApiCollection(id, name, Context.now() , new HashSet<>(),  null, 0, false, true);
    }

    public boolean getRedact() {
        return redact;
    }

    public void setRedact(boolean redact) {
        this.redact = redact;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public List<TestingEndpoints> getConditions() {
        return conditions;
    }

    public void setConditions(List<TestingEndpoints> conditions) {
        this.conditions = conditions;
    }

    private void initializeConditionsList(TestingEndpoints condition) {
        if (this.conditions == null) {
            this.conditions = new ArrayList<>();
        }
    }

    private void updateConditionList(TestingEndpoints condition, boolean isAddOperation) {
        for (TestingEndpoints it : conditions) {
            boolean sameType = it.getType() == condition.getType();
            if (sameType) {
                switch (it.getType()) {
                    case CUSTOM:
                        // Only one CUSTOM condition should exist
                        CustomTestingEndpoints.updateApiListCondition((CustomTestingEndpoints) it, condition.returnApis(), isAddOperation);
                        return;
                    default:
                        break;
                }
            }
        }

        /*
         * Since we return if we find a condition of the same type,
         * we can add the condition if we reach here
         */
        if (isAddOperation) {
            conditions.add(condition);
        }
    }

    public void addToConditions(TestingEndpoints condition) {
        initializeConditionsList(condition);
        updateConditionList(condition, true);
    }

    public void removeFromConditions(TestingEndpoints condition) {
        initializeConditionsList(condition);
        updateConditionList(condition, false);
    }

    public boolean isSampleCollectionsDropped() {
        return sampleCollectionsDropped;
    }

    public void setSampleCollectionsDropped(boolean sampleCollectionsDropped) {
        this.sampleCollectionsDropped = sampleCollectionsDropped;
    }

    public String getUserSetEnvType() {
		return userSetEnvType;
	}

	public void setUserSetEnvType(String userSetEnvType) {
		this.userSetEnvType = userSetEnvType;
	}

    public boolean getAutomated() {
        return automated;
    }

    public void setAutomated(boolean automated) {
        this.automated = automated;
    }

    public boolean isMatchDependencyWithOtherCollections() {
        return matchDependencyWithOtherCollections;
    }

    public void setMatchDependencyWithOtherCollections(boolean matchDependencyWithOtherCollections) {
        this.matchDependencyWithOtherCollections = matchDependencyWithOtherCollections;
    }

    public boolean isRunDependencyAnalyser() {
        return runDependencyAnalyser;
    }

    public boolean getRunDependencyAnalyser() {
        return runDependencyAnalyser;
    }

    public void setRunDependencyAnalyser(boolean runDependencyAnalyser) {
        this.runDependencyAnalyser = runDependencyAnalyser;
    }
}
