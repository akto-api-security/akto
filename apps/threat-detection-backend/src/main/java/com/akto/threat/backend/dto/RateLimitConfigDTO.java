package com.akto.threat.backend.dto;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.RatelimitConfig;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RateLimitConfigDTO {
    
    public static class RateLimitRule {
        private final String name;
        private final Integer period;
        private final Integer maxRequests;
        private final Integer mitigationPeriod;
        private final String action;
        private final String type;
        private final String behaviour;
        private final Float rateLimitConfidence;
        private final AutomatedThreshold autoThreshold;
        
        private RateLimitRule(Document doc) {
            this.name = doc.getString("name");
            this.period = doc.getInteger("period");
            this.maxRequests = doc.getInteger("maxRequests");
            this.mitigationPeriod = doc.getInteger("mitigationPeriod");
            this.action = doc.getString("action");
            this.type = doc.getString("type");
            this.behaviour = doc.getString("behaviour");
            
            Double confidence = doc.getDouble("rateLimitConfidence");
            this.rateLimitConfidence = confidence != null ? confidence.floatValue() : null;
            
            Object autoThresholdObj = doc.get("autoThreshold");
            this.autoThreshold = autoThresholdObj instanceof Document ? 
                AutomatedThreshold.fromDocument((Document) autoThresholdObj) : null;
        }
        
        public static RateLimitRule fromDocument(Document doc) {
            return new RateLimitRule(doc);
        }
        
        public RatelimitConfig.RatelimitConfigItem toProto() {
            RatelimitConfig.RatelimitConfigItem.Builder builder = 
                RatelimitConfig.RatelimitConfigItem.newBuilder();
            
            if (name != null) builder.setName(name);
            if (period != null) builder.setPeriod(period);
            if (maxRequests != null) builder.setMaxRequests(maxRequests);
            if (mitigationPeriod != null) builder.setMitigationPeriod(mitigationPeriod);
            if (action != null) builder.setAction(action);
            if (type != null) builder.setType(type);
            if (behaviour != null) builder.setBehaviour(behaviour);
            if (rateLimitConfidence != null) builder.setRateLimitConfidence(rateLimitConfidence);
            if (autoThreshold != null) builder.setAutoThreshold(autoThreshold.toProto());
            
            return builder.build();
        }
        
        public static RateLimitRule fromProto(RatelimitConfig.RatelimitConfigItem proto) {
            Document doc = new Document();
            if (!proto.getName().isEmpty()) doc.append("name", proto.getName());
            if (proto.getPeriod() > 0) doc.append("period", proto.getPeriod());
            if (proto.getMaxRequests() > 0) doc.append("maxRequests", proto.getMaxRequests());
            if (proto.getMitigationPeriod() > 0) doc.append("mitigationPeriod", proto.getMitigationPeriod());
            if (!proto.getAction().isEmpty()) doc.append("action", proto.getAction());
            if (!proto.getType().isEmpty()) doc.append("type", proto.getType());
            if (!proto.getBehaviour().isEmpty()) doc.append("behaviour", proto.getBehaviour());
            if (proto.getRateLimitConfidence() > 0.0) {
                double roundedConfidence = Math.round(proto.getRateLimitConfidence() * 10.0) / 10.0;
                doc.append("rateLimitConfidence", roundedConfidence);
            }
            if (proto.hasAutoThreshold()) {
                doc.append("autoThreshold", AutomatedThreshold.fromProto(proto.getAutoThreshold()).toDocument());
            }
            return new RateLimitRule(doc);
        }
        
        public Document toDocument() {
            Document doc = new Document();
            if (name != null) doc.append("name", name);
            if (period != null) doc.append("period", period);
            if (maxRequests != null) doc.append("maxRequests", maxRequests);
            if (mitigationPeriod != null) doc.append("mitigationPeriod", mitigationPeriod);
            if (action != null) doc.append("action", action);
            if (type != null) doc.append("type", type);
            if (behaviour != null) doc.append("behaviour", behaviour);
            if (rateLimitConfidence != null) {
                double roundedConfidence = Math.round(rateLimitConfidence * 10.0) / 10.0;
                doc.append("rateLimitConfidence", roundedConfidence);
            }
            if (autoThreshold != null) doc.append("autoThreshold", autoThreshold.toDocument());
            return doc;
        }
    }
    
    public static class AutomatedThreshold {
        private final String percentile;
        private final Integer overflowPercentage;
        private final Integer baselinePeriod;
        
        private AutomatedThreshold(Document doc) {
            this.percentile = doc.getString("percentile");
            this.overflowPercentage = doc.getInteger("overflowPercentage");
            this.baselinePeriod = doc.getInteger("baselinePeriod");
        }
        
        public static AutomatedThreshold fromDocument(Document doc) {
            return new AutomatedThreshold(doc);
        }
        
        public static AutomatedThreshold fromProto(RatelimitConfig.AutomatedThreshold proto) {
            Document doc = new Document();
            if (!proto.getPercentile().isEmpty()) doc.append("percentile", proto.getPercentile());
            if (proto.getOverflowPercentage() > 0) doc.append("overflowPercentage", proto.getOverflowPercentage());
            if (proto.getBaselinePeriod() > 0) doc.append("baselinePeriod", proto.getBaselinePeriod());
            return new AutomatedThreshold(doc);
        }
        
        public RatelimitConfig.AutomatedThreshold toProto() {
            RatelimitConfig.AutomatedThreshold.Builder builder = 
                RatelimitConfig.AutomatedThreshold.newBuilder();
            
            if (percentile != null) builder.setPercentile(percentile);
            if (overflowPercentage != null) builder.setOverflowPercentage(overflowPercentage);
            if (baselinePeriod != null) builder.setBaselinePeriod(baselinePeriod);
            
            return builder.build();
        }
        
        public Document toDocument() {
            Document doc = new Document();
            if (percentile != null) doc.append("percentile", percentile);
            if (overflowPercentage != null) doc.append("overflowPercentage", overflowPercentage);
            if (baselinePeriod != null) doc.append("baselinePeriod", baselinePeriod);
            return doc;
        }
    }
    
    public static RatelimitConfig parseFromDocument(Document doc) {
        if (doc == null) return null;
        
        Object ratelimitObj = doc.get("ratelimitConfig");
        if (!(ratelimitObj instanceof Document)) return null;
        
        Document ratelimitDoc = (Document) ratelimitObj;
        Object rulesObj = ratelimitDoc.get("rules");
        if (!(rulesObj instanceof List)) return null;
        
        List<?> rulesList = (List<?>) rulesObj;
        RatelimitConfig.Builder builder = RatelimitConfig.newBuilder();
        
        List<RatelimitConfig.RatelimitConfigItem> items = rulesList.stream()
            .filter(Document.class::isInstance)
            .map(Document.class::cast)
            .map(RateLimitRule::fromDocument)
            .map(RateLimitRule::toProto)
            .collect(Collectors.toList());
        
        builder.addAllRules(items);
        return builder.build();
    }
    
    public static Document toDocument(RatelimitConfig proto) {
        if (proto == null || proto.getRulesCount() == 0) return null;
        
        List<Document> ruleDocs = proto.getRulesList().stream()
            .map(RateLimitRule::fromProto)
            .map(RateLimitRule::toDocument)
            .collect(Collectors.toList());
        
        Document ratelimitDoc = new Document();
        ratelimitDoc.append("rules", ruleDocs);
        
        Document result = new Document();
        result.append("ratelimitConfig", ratelimitDoc);
        return result;
    }
}