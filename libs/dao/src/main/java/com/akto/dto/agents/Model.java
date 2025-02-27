package com.akto.dto.agents;

public enum Model {

    // TODO: use this enum in agent code.
    OPEN_AI_GPT_4o_mini("OpenAI GPT-4o mini");

    private final String modelName;

    Model(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }

}
