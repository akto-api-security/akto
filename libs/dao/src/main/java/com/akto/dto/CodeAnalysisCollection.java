package com.akto.dto;

import java.util.Map;

import org.bson.types.ObjectId;

public class CodeAnalysisCollection {
    
    private ObjectId id;
    public static final String ID = "_id";
    private String name;
    public static final String NAME = "name";

    private String projectDir;
    public static final String PROJECT_DIR = "projectDir";

    public CodeAnalysisCollection() {
    }

    public CodeAnalysisCollection(String name, String projectDir) {
        this.name = name;
        this.projectDir = projectDir;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(String projectDir) {
        this.projectDir = projectDir;
    }
}
