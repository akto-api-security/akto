package com.akto.threat.detection.tasks;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import com.akto.proto.http_response_param.v1.HttpResponseParam;

import org.apache.kafka.common.serialization.Deserializer;

public class HttpResponseParamSerde implements Serde<HttpResponseParam> {
    private final HttpResponseParamSerializer serializer = new HttpResponseParamSerializer();
    private final HttpResponseParamDeserializer deserializer = new HttpResponseParamDeserializer();

    @Override
    public Serializer<HttpResponseParam> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<HttpResponseParam> deserializer() {
        return deserializer;
    }
}