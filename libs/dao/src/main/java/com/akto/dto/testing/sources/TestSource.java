package com.akto.dto.testing.sources;

import org.bson.codecs.pojo.annotations.BsonId;

public class TestSource {

    @BsonId
    private String fileUrl;
    public static final String FILE_URL = "fileUrl";
    
    String testSourceConfigId;
    private String name;
    private String description;
    private String creator;
    private int addedEpoch;
    private int stars;
    private int installs;

    
}
