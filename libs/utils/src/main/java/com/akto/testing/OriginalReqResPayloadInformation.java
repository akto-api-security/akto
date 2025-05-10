package com.akto.testing;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OriginalReqResPayloadInformation {
    private static final OriginalReqResPayloadInformation instance = new OriginalReqResPayloadInformation();

    Map<String, String> originalReqPayloadMap = new HashMap<>();

    private OriginalReqResPayloadInformation() {
    }

    public static OriginalReqResPayloadInformation getInstance() {
        return instance;
    }

    public synchronized void init(Map<String, String> originalReqPayloadMap) {
        this.originalReqPayloadMap = originalReqPayloadMap;
    }
}

