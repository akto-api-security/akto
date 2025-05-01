package com.akto.threat.detection.tasks;

import org.apache.kafka.common.serialization.Serializer;

import com.akto.proto.http_response_param.v1.HttpResponseParam;

// Assume HttpResponseParam is in com.example.myapp.proto

public class HttpResponseParamSerializer implements Serializer<HttpResponseParam> {
    @Override
    public byte[] serialize(String topic, HttpResponseParam data) {
        return data != null ? data.toByteArray() : null;
    }
}
