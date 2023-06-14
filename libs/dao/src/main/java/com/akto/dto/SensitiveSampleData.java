package com.akto.dto;

import com.akto.dto.type.SingleTypeInfo;

import java.util.List;

public class SensitiveSampleData {
    private SingleTypeInfo.ParamId id;
    public static final String SAMPLE_DATA = "sampleData";
    private List<String> sampleData;

    private boolean invalid;
    public static final int cap = 10;

    public SensitiveSampleData() {}

    public SensitiveSampleData(SingleTypeInfo.ParamId id, List<String> sampleData) {
        this.id = id;
        this.sampleData = sampleData;
    }

    public SingleTypeInfo.ParamId getId() {
        return id;
    }

    public void setId(SingleTypeInfo.ParamId id) {
        this.id = id;
    }

    public List<String> getSampleData() {
        return sampleData;
    }

    public void setSampleData(List<String> sampleData) {
        this.sampleData = sampleData;
    }


    public boolean getInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }
}
