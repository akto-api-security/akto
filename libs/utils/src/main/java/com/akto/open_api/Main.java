package com.akto.open_api;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.dao.common.LoggerMaker;
import com.akto.dao.common.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    public static void main2(String[] args) throws URISyntaxException {
        Pattern pattern = Pattern.compile("^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:|:blank:]])?$");
        String url = "https://petstore.swagger.io/v2/user/STRING";
        URI uri1 =  new URI("https://www.bb.petstore.swagger.io/v2/user/STRING#gg");
        loggerMaker.info(uri1.getPath());
        loggerMaker.info(uri1.getHost());

    }


    public static OpenAPI init(String info,Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList, boolean includeHeaders, String serverUrl) throws Exception {
        OpenAPI openAPI = new OpenAPI();
        addPaths(openAPI, stiList, includeHeaders);
        if (serverUrl !=null && !serverUrl.startsWith("http")) serverUrl = "https://" + serverUrl;
        addServer(serverUrl, openAPI);
        Paths paths = PathBuilder.parameterizePath(openAPI.getPaths());
        openAPI.setPaths(paths);
        addInfo(openAPI,info);
        return openAPI;
    }


    public static void addPaths(OpenAPI openAPI, Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList, boolean includeHeaders) {
        Paths paths = new Paths();
        for(String url : stiList.keySet()){
            if (url.endsWith(".js") || url.endsWith(".css") || url.endsWith(".svg") || url.endsWith(".jpg")  || url.endsWith(".png")) {
                continue;
            }
            buildPathsFromSingleTypeInfosPerUrl(stiList.get(url), url,paths, includeHeaders);
        }
        openAPI.setPaths(paths);
    }

    public static void buildPathsFromSingleTypeInfosPerUrl(Map<String, Map<Integer, List<SingleTypeInfo>>> stiMap, String url, Paths paths, boolean includeHeaders) {
        for (String method: stiMap.keySet()) {
            Map<Integer,List<SingleTypeInfo>> responseWiseMap = stiMap.get(method);
            for (Integer responseCode: responseWiseMap.keySet()) {
                List<SingleTypeInfo> singleTypeInfoList = responseWiseMap.get(responseCode);
                try {
                    addPathItems(responseCode, paths, url, method, singleTypeInfoList, includeHeaders);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("ERROR in buildPathsFromSingleTypeInfosPerUrl  " + e, LogDb.DASHBOARD);
                }
            }
        }
    }

    public static void addPathItems(int responseCode, Paths paths, String url, String method, List<SingleTypeInfo> singleTypeInfoList, boolean includeHeaders) throws Exception {
        Schema<?> schema = null;
        try {
            schema = buildSchema(singleTypeInfoList);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("ERROR in building schema in addPathItems " + e, LogDb.DASHBOARD);
        }
        if (schema == null) {
            schema = new ObjectSchema();
            schema.setDescription("AKTO_ERROR while building schema");
        }
        List<Parameter> headerParameters = new ArrayList<>();
        try{
            headerParameters = buildHeaders(singleTypeInfoList);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("ERROR in building headers in addPathItems " + e, LogDb.DASHBOARD);
        }
        
        PathBuilder.addPathItem(paths, url, method, responseCode, schema, headerParameters, includeHeaders);
    }

    public static List<Parameter> buildHeaders(List<SingleTypeInfo> singleTypeInfoList) throws Exception{
        List<Parameter> headerParameters = new ArrayList<>();
        ObjectSchema schema =new ObjectSchema();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            if(singleTypeInfo.isIsHeader()){
                List<SchemaBuilder.CustomSchema> cc = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(singleTypeInfo);
                SchemaBuilder.build(schema, cc);
            }
        }
        if (schema.getProperties() == null) return headerParameters;
        for(String header:schema.getProperties().keySet()){
            Parameter head = new Parameter();
            head.setName(header);
            head.setIn("header");
            head.setSchema(schema.getProperties().get(header));
            headerParameters.add(head);
        }
        return headerParameters;
    }

    public static Schema<?> buildSchema(List<SingleTypeInfo> singleTypeInfoList) throws Exception {
        ObjectSchema schema =new ObjectSchema();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            if(singleTypeInfo.isIsHeader()){
                continue;
            }
            List<SchemaBuilder.CustomSchema> cc = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(singleTypeInfo);
            SchemaBuilder.build(schema, cc);
        }
        schema.setDescription("Sample description");
        return schema;
    }


    public static void addServer(String url, OpenAPI openAPI) {
        if (url != null) {
            Server server = new Server();
            server.setUrl(url);
            openAPI.setServers(Collections.singletonList(server));
        }

        Paths paths = openAPI.getPaths();
        Paths newPaths = new Paths();
        for (String path: paths.keySet()) {
            PathItem pathItem = paths.get(path);
            if (path.startsWith("http")) {
                try {
                    URI uri = new URI(path);
                    String pathUrl = uri.getScheme() + "://"+uri.getAuthority();
                    Server pathServer = new Server();
                    pathServer.setUrl(pathUrl);
                    String newPath = uri.getPath();
                    newPaths.put(newPath, pathItem);
                    pathItem.setServers(Collections.singletonList(pathServer));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            } else {
                newPaths.put(path, pathItem);
            }

        }

        openAPI.setPaths(newPaths);


    }


    public static String convertOpenApiToJSON(OpenAPI openAPI) throws Exception {
        mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector(){
            @Override
            public boolean hasIgnoreMarker(final AnnotatedMember m) {
                List<String> exclusions = Collections.singletonList("exampleSetFlag");
                return exclusions.contains(m.getName())|| super.hasIgnoreMarker(m);
            }
        });

        mapper.setSerializationInclusion(NON_NULL);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(openAPI);
    }

    public static void addInfo(OpenAPI openAPI, String collectionName) {
        Info info = new Info();
        info.setDescription("Akto generated openAPI file");
        info.setTitle(collectionName);
        info.setVersion("1.0.0");
        openAPI.setInfo(info);
    }
}
