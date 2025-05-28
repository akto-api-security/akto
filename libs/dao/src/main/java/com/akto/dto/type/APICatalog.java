package com.akto.dto.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class APICatalog {

    @BsonId
    int id;
    Map<URLStatic, RequestTemplate> strictURLToMethods;
    Map<URLTemplate, RequestTemplate> templateURLToMethods;
    List<SingleTypeInfo> deletedInfo = new ArrayList<>();

    public APICatalog() {
    }

    public APICatalog(
        int id, 
        Map<URLStatic, RequestTemplate> strictURLToMethods, 
        Map<URLTemplate,RequestTemplate> templateURLToMethods
    ) {
        this.id = id;
        this.strictURLToMethods = strictURLToMethods;
        this.templateURLToMethods = templateURLToMethods;
    }

    public List<SingleTypeInfo> getAllTypeInfo() {
        List<SingleTypeInfo> ret = new ArrayList<>();
        for(RequestTemplate requestTemplate: strictURLToMethods.values()) {
            ret.addAll(requestTemplate.getAllTypeInfo());
        }

        for(Map.Entry<URLTemplate, RequestTemplate> urlTemplateAndMethods: templateURLToMethods.entrySet()) {
            List<SingleTypeInfo> singleTypeInfos = urlTemplateAndMethods.getValue().getAllTypeInfo();
            for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
                singleTypeInfo.setUrl(urlTemplateAndMethods.getKey().getTemplateString());
            }
            ret.addAll(singleTypeInfos);
        }

        return ret;
    }

    public APICatalog(int id, Map<URLStatic, RequestTemplate> strictURLToMethods, Map<URLTemplate, RequestTemplate> templateURLToMethods, List<SingleTypeInfo> deletedInfo) {
        this.id = id;
        this.strictURLToMethods = strictURLToMethods;
        this.templateURLToMethods = templateURLToMethods;
        this.deletedInfo = deletedInfo;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Map<URLStatic, RequestTemplate> getStrictURLToMethods() {
        return this.strictURLToMethods;
    }

    public void setStrictURLToMethods(Map<URLStatic, RequestTemplate> strictURLToMethods) {
        this.strictURLToMethods = strictURLToMethods;
    }

    public Map<URLTemplate, RequestTemplate> getTemplateURLToMethods() {
        return this.templateURLToMethods;
    }

    public void setTemplateURLToMethods(Map<URLTemplate, RequestTemplate> templateURLToMethods) {
        this.templateURLToMethods = templateURLToMethods;
    }

    public List<SingleTypeInfo> getDeletedInfo() {
        return this.deletedInfo;
    }

    public void setDeletedInfo(List<SingleTypeInfo> deletedInfo) {
        this.deletedInfo = deletedInfo;
    }

    public APICatalog id(int id) {
        setId(id);
        return this;
    }

    public static boolean isTemplateUrl(String url) {
        return url.contains(SingleTypeInfo.SuperType.STRING.name()) ||
                url.contains(SingleTypeInfo.SuperType.INTEGER.name()) ||
                url.contains(SingleTypeInfo.SuperType.FLOAT.name()) ||
                url.contains(SingleTypeInfo.SuperType.OBJECT_ID.name()) ||
                url.contains(SingleTypeInfo.SuperType.VERSIONED.name())
                ;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", strictURLToMethods='" + getStrictURLToMethods() + "'" +
            ", templateURLToMethods='" + getTemplateURLToMethods() + "'" +
            ", deletedInfo='" + getDeletedInfo() + "'" +
            "}";
    }

}
