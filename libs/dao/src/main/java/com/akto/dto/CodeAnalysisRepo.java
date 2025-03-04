package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

public class CodeAnalysisRepo {

    private ObjectId id;
    private String projectName;
    public static final String PROJECT_NAME = "projectName";
    private String repoName;
    public static final String REPO_NAME = "repoName";
    private int lastRun;
    public static final String LAST_RUN = "lastRun";
    private int scheduleTime;
    public static final String SCHEDULE_TIME = "scheduleTime";
    private String apiCollectionName;
    public static final String API_COLLECTION_NAME = "apiCollectionName";

    public CodeAnalysisRunState getCodeAnalysisRunState() {
        if (codeAnalysisRunState == null) {
            if (this.lastRun > this.scheduleTime) {
                return CodeAnalysisRunState.COMPLETED;
            }
            return CodeAnalysisRunState.SCHEDULED;
        }
        return codeAnalysisRunState;
    }

    public void setCodeAnalysisRunState(CodeAnalysisRunState codeAnalysisRunState) {
        this.codeAnalysisRunState = codeAnalysisRunState;
    }

    public String getApiCollectionName() {
        return apiCollectionName;
    }

    public void setApiCollectionName(String apiCollectionName) {
        this.apiCollectionName = apiCollectionName;
    }

    public enum SourceCodeType {
        BITBUCKET, GITHUB
    }

    public enum CodeAnalysisRunState {
        SCHEDULED, STARTED, API_ENDPOINT_EXTRACTION_COMPLETED, COMPLETED, FAILED, STOPPED
    }

    private CodeAnalysisRunState codeAnalysisRunState;
    public static final String CODE_ANALYSIS_RUN_STATE = "codeAnalysisRunState";
    private SourceCodeType sourceCodeType;
    public static final String SOURCE_CODE_TYPE = "sourceCodeType";

    @BsonIgnore
    private String hexId;

    public CodeAnalysisRepo(ObjectId id, String projectName, String repoName, int lastRun, int scheduleTime, CodeAnalysisRunState codeAnalysisRunState) {
        this.id = id;
        this.projectName = projectName;
        this.repoName = repoName;
        this.lastRun = lastRun;
        this.scheduleTime = scheduleTime;
        this.codeAnalysisRunState = codeAnalysisRunState;
    }

    public CodeAnalysisRepo() {
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
        if (id != null) this.hexId = id.toHexString();
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public int getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(int scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    public int getLastRun() {
        return lastRun;
    }

    public void setLastRun(int lastRun) {
        this.lastRun = lastRun;
    }

    public String getHexId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }
    public SourceCodeType getSourceCodeType() {
        if (sourceCodeType == null) {
            return SourceCodeType.BITBUCKET;
        }
        return sourceCodeType;
    }

    public void setSourceCodeType(SourceCodeType sourceCodeType) {
        this.sourceCodeType = sourceCodeType;
    }

    @Override
    public String toString() {
        return "Project: " + this.projectName + " Repository:" + this.getRepoName();
    }

}
