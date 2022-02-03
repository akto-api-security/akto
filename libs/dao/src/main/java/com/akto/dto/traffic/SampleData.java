package com.akto.dto.traffic;

import java.util.List;

public class SampleData {
    Key id;
    List<String> samples;


    public SampleData() {
    }

    public SampleData(Key id, List<String> samples) {
        this.id = id;
        this.samples = samples;
    }

    public Key getId() {
        return this.id;
    }

    public void setId(Key id) {
        this.id = id;
    }

    public List<String> getSamples() {
        return this.samples;
    }

    public void setSamples(List<String> samples) {
        this.samples = samples;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", samples='" + getSamples() + "'" +
            "}";
    }


}
