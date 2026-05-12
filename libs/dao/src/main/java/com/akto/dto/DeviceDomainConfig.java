package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDomainConfig {

    public static final String DEVICE_ID = "deviceId";
    @BsonId
    private String deviceId;

    public static final String DOMAIN_LISTS = "domainLists";
    private Map<String, List<String>> domainLists;

    public static final String UPDATED_AT = "updatedAt";
    private int updatedAt;
}
