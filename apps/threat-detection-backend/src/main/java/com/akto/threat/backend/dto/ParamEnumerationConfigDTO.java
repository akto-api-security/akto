package com.akto.threat.backend.dto;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ParamEnumerationConfig;
import org.bson.Document;

public class ParamEnumerationConfigDTO {

    public static final int DEFAULT_UNIQUE_PARAM_THRESHOLD = 50;
    public static final int DEFAULT_WINDOW_SIZE_MINUTES = 5;

    public static ParamEnumerationConfig parseFromDocument(Document doc) {
        ParamEnumerationConfig.Builder builder = ParamEnumerationConfig.newBuilder()
                .setUniqueParamThreshold(DEFAULT_UNIQUE_PARAM_THRESHOLD)
                .setWindowSizeMinutes(DEFAULT_WINDOW_SIZE_MINUTES);

        if (doc == null) return builder.build();

        Object paramEnumObj = doc.get("paramEnumerationConfig");
        if (!(paramEnumObj instanceof Document)) return builder.build();

        Document paramEnumDoc = (Document) paramEnumObj;

        if (paramEnumDoc.getInteger("uniqueParamThreshold") != null) {
            builder.setUniqueParamThreshold(paramEnumDoc.getInteger("uniqueParamThreshold"));
        }
        if (paramEnumDoc.getInteger("windowSizeMinutes") != null) {
            builder.setWindowSizeMinutes(paramEnumDoc.getInteger("windowSizeMinutes"));
        }

        return builder.build();
    }

    public static Document toDocument(ParamEnumerationConfig proto) {
        if (proto == null) return null;

        Document paramEnumDoc = new Document();
        int threshold = proto.getUniqueParamThreshold() > 0 ? proto.getUniqueParamThreshold() : DEFAULT_UNIQUE_PARAM_THRESHOLD;
        int windowSize = proto.getWindowSizeMinutes() > 0 ? proto.getWindowSizeMinutes() : DEFAULT_WINDOW_SIZE_MINUTES;

        paramEnumDoc.append("uniqueParamThreshold", threshold);
        paramEnumDoc.append("windowSizeMinutes", windowSize);

        Document result = new Document();
        result.append("paramEnumerationConfig", paramEnumDoc);
        return result;
    }
}
