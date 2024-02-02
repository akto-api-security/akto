package com.akto.open_api.parser;

import java.net.URLEncoder;
import java.util.Map;
import java.util.Set;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Pair;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.swagger.oas.inflector.examples.ExampleBuilder;
import io.swagger.oas.inflector.examples.XmlExampleSerializer;
import io.swagger.oas.inflector.examples.models.Example;
import io.swagger.oas.inflector.processors.JsonNodeExampleSerializer;
import io.swagger.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;

public class ContentParser {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ContentParser.class, LogDb.DASHBOARD);
    private final static Gson gson = new Gson();

    public static Pair<String, String> getExampleFromContent(Content content) {
        String ret = "";
        String contentType = "";
        Set<String> contentTypes = content.keySet();

        if (contentTypes != null && !contentTypes.isEmpty()) {
            // using first content type only.
            contentType = contentTypes.iterator().next();
            MediaType mediaType = content.get(contentType);

            // add cases for encoding object for multipart, media data.

            if (mediaType.getExample() != null) {
                ret = mediaType.getExample().toString();
            } else if (mediaType.getExamples() != null) {
                for (String exampleName : mediaType.getExamples().keySet()) {
                    ret = mediaType.getExamples().get(exampleName).getValue().toString();
                }
            } else {
                Example example = ExampleBuilder.fromSchema(mediaType.getSchema(), null);

                if (example != null) {
                    
                    SimpleModule simpleModule = new SimpleModule().addSerializer(new JsonNodeExampleSerializer());
                    Json.mapper().registerModule(simpleModule);
                    Yaml.mapper().registerModule(simpleModule);

                    if(contentType.contains("xml")) {
                        ret = new XmlExampleSerializer().serialize(example);
                    } else if (contentType.contains("yaml")){
                        try {
                            ret = Yaml.pretty().writeValueAsString(example);
                        } catch (JsonProcessingException e) {
                            loggerMaker.infoAndAddToDb("Error while parsing yaml " + e.toString());
                        }
                    } else if (contentType.contains("x-www-form-urlencoded")){
                        try {
                            Map<String, Object> json = gson.fromJson(Json.pretty(example), new TypeToken<Map<String, Object>>() {}.getType());
                            StringBuilder sb = new StringBuilder();
                            for (String key : json.keySet()) {
                                if (sb.length() > 0) {
                                    sb.append("&");
                                }
                                sb.append(key).append("=").append(json.get(key));
                            }
                            ret = URLEncoder.encode(sb.toString(), "UTF-8");
                        } catch (Exception e) {
                            loggerMaker.infoAndAddToDb("Error while parsing x-www-form-urlencoded " + e.toString());
                        }
                    } else {
                        ret = Json.pretty(example);
                    }
                }
            }
        }
        return new Pair<String,String>(contentType, ret);
    }

}
