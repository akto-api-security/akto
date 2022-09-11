package com.akto.open_api;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.mongodb.client.model.Filters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.servers.Server;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    public static void main2(String[] args) throws URISyntaxException {
        Pattern pattern = Pattern.compile("^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:|:blank:]])?$");
        String url = "https://petstore.swagger.io/v2/user/STRING";
        URI uri1 =  new URI("https://www.bb.petstore.swagger.io/v2/user/STRING#gg");
        System.out.println(uri1.getPath());
        System.out.println(uri1.getHost());
        System.out.println();
//        Matcher a = pattern.matcher(url);
//        System.out.println(a.matches());
//        if (a.matches()) {
//            URI uri =  new URI(url);
//            System.out.println(uri.getPath());
//        }

    }


    public static OpenAPI init(String info,Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList) throws Exception {
        OpenAPI openAPI = new OpenAPI();
        addPaths(openAPI, stiList);
        addServer(null, openAPI);
        Paths paths = PathBuilder.parameterizePath(openAPI.getPaths());
        openAPI.setPaths(paths);
        addInfo(openAPI,info);
        return openAPI;
    }


    public static void addPaths(OpenAPI openAPI, Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList) {
        Paths paths = new Paths();
        for(String url : stiList.keySet()){
            buildPathsFromSingleTypeInfosPerUrl(stiList.get(url), url,paths);
        }
        openAPI.setPaths(paths);
    }

    public static void buildPathsFromSingleTypeInfosPerUrl(Map<String, Map<Integer, List<SingleTypeInfo>>> stiMap, String url, Paths paths ) {
        for (String method: stiMap.keySet()) {
            Map<Integer,List<SingleTypeInfo>> responseWiseMap = stiMap.get(method);
            for (Integer responseCode: responseWiseMap.keySet()) {
                List<SingleTypeInfo> singleTypeInfoList = responseWiseMap.get(responseCode);
                try {
                    addPathItems(responseCode, paths, url, method, singleTypeInfoList);
                } catch (Exception e) {
                    logger.error("ERROR in buildPathsFromSingleTypeInfosPerUrl  " + e);
                }
            }
        }
    }

    public static void addPathItems(int responseCode, Paths paths, String url, String method, List<SingleTypeInfo> singleTypeInfoList) throws Exception {
        Schema<?> schema = null;
        try {
            schema = buildSchema(singleTypeInfoList);
        } catch (Exception e) {
            logger.error("ERROR in addPathItems " + e);
        }
        if (schema == null) {
            schema = new ObjectSchema();
            schema.setDescription("AKTO_ERROR while building schema");
        }
        PathBuilder.addPathItem(paths, url, method, responseCode, schema);
    }


    public static Schema<?> buildSchema(List<SingleTypeInfo> singleTypeInfoList) throws Exception {
        ObjectSchema schema =new ObjectSchema();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
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
