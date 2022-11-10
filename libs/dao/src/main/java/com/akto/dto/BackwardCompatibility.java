package com.akto.dto;

public class BackwardCompatibility {
    private int id;

    public static final String DROP_FILTER_SAMPLE_DATA = "dropFilterSampleData";
    private int dropFilterSampleData;
    private int resetSingleTypeInfoCount;
    public static final String RESET_SINGLE_TYPE_INFO_COUNT = "resetSingleTypeInfoCount";

    public static final String DROP_WORKFLOW_TEST_RESULT = "dropWorkflowTestResult";
    private int dropWorkflowTestResult;

    public static final String READY_FOR_NEW_TESTING_FRAMEWORK = "readyForNewTestingFramework";
    private int readyForNewTestingFramework;

    public static final String ADD_AKTO_DATA_TYPES = "addAktoDataTypes";
    private int addAktoDataTypes;

    public BackwardCompatibility(int id, int dropFilterSampleData, int resetSingleTypeInfoCount, int dropWorkflowTestResult, int readyForNewTestingFramework,int addAktoDataTypes) {
        this.id = id;
        this.dropFilterSampleData = dropFilterSampleData;
        this.resetSingleTypeInfoCount = resetSingleTypeInfoCount;
        this.dropWorkflowTestResult = dropWorkflowTestResult;
        this.readyForNewTestingFramework = readyForNewTestingFramework;
        this.addAktoDataTypes = addAktoDataTypes;
    }

    public BackwardCompatibility() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDropFilterSampleData() {
        return dropFilterSampleData;
    }

    public void setDropFilterSampleData(int dropFilterSampleData) {
        this.dropFilterSampleData = dropFilterSampleData;
    }

    public int getResetSingleTypeInfoCount() {
        return resetSingleTypeInfoCount;
    }

    public void setResetSingleTypeInfoCount(int resetSingleTypeInfoCount) {
        this.resetSingleTypeInfoCount = resetSingleTypeInfoCount;
    }

    public int getDropWorkflowTestResult() {
        return dropWorkflowTestResult;
    }

    public void setDropWorkflowTestResult(int dropWorkflowTestResult) {
        this.dropWorkflowTestResult = dropWorkflowTestResult;
    }

    public int getReadyForNewTestingFramework() {
        return this.readyForNewTestingFramework;
    }

    public void setReadyForNewTestingFramework(int readyForNewTestingFramework) {
        this.readyForNewTestingFramework = readyForNewTestingFramework;
    }

    public int getAddAktoDataTypes() {
        return addAktoDataTypes;
    }

    public void setAddAktoDataTypes(int addAktoDataTypes) {
        this.addAktoDataTypes = addAktoDataTypes;
    }
}
