package com.akto.dto.graph;

public abstract class SvcToSvcGraphParams {

    public enum Type {
        K8S
    }

    public abstract Type getType();
}
