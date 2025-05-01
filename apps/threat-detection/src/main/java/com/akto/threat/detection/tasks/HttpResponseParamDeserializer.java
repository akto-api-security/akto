package com.akto.threat.detection.tasks;

import org.apache.kafka.common.serialization.Deserializer;

import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.google.protobuf.InvalidProtocolBufferException;

public class HttpResponseParamDeserializer implements Deserializer<HttpResponseParam> {
    @Override
    public HttpResponseParam deserialize(String topic, byte[] data) {
        try {
            return data != null ? HttpResponseParam.parseFrom(data) : null;
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Failed to deserialize HttpResponseParam", e);
        }
    }
}