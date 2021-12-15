package com.akto.dto.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class APICatalog {

    @BsonId
    int id;
    Map<String, URLMethods> strictURLToMethods;
    Map<URLTemplate, URLMethods> templateURLToMethods;

    public APICatalog() {
    }

    public APICatalog(int id, Map<String,URLMethods> strictURLToMethods, Map<URLTemplate,URLMethods> templateURLToMethods) {
        this.id = id;
        this.strictURLToMethods = strictURLToMethods;
        this.templateURLToMethods = templateURLToMethods;
    }

    public List<SingleTypeInfo> getAllTypeInfo() {
        List<SingleTypeInfo> ret = new ArrayList<>();
        for(URLMethods urlMethods: strictURLToMethods.values()) {
            ret.addAll(urlMethods.getAllTypeInfo());
        }

        for(Map.Entry<URLTemplate, URLMethods> urlTemplateAndMethods: templateURLToMethods.entrySet()) {
            List<SingleTypeInfo> singleTypeInfos = urlTemplateAndMethods.getValue().getAllTypeInfo();
            for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
                singleTypeInfo.setUrl(urlTemplateAndMethods.getKey().getTemplateString());
            }
            ret.addAll(singleTypeInfos);
        }

        return ret;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Map<String,URLMethods> getStrictURLToMethods() {
        return this.strictURLToMethods;
    }

    public void setStrictURLToMethods(Map<String,URLMethods> strictURLToMethods) {
        this.strictURLToMethods = strictURLToMethods;
    }

    public Map<URLTemplate,URLMethods> getTemplateURLToMethods() {
        return this.templateURLToMethods;
    }

    public void setTemplateURLToMethods(Map<URLTemplate,URLMethods> templateURLToMethods) {
        this.templateURLToMethods = templateURLToMethods;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", strictURLToMethods='" + getStrictURLToMethods() + "'" +
            ", templateURLToMethods='" + getTemplateURLToMethods() + "'" +
            "}";
    }

    public void prettyPrint() {
        for(SingleTypeInfo s: this.getAllTypeInfo()) {
            System.out.println(s.toString());
        }

    }

    
}
