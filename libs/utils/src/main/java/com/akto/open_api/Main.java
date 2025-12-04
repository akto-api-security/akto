package com.akto.open_api;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, LogDb.DASHBOARD);
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    public static void main2(String[] args) throws URISyntaxException {
        Pattern pattern = Pattern.compile("^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:|:blank:]])?$");
        String url = "https://petstore.swagger.io/v2/user/STRING";
        URI uri1 =  new URI("https://www.bb.petstore.swagger.io/v2/user/STRING#gg");
        logger.info(uri1.getPath());
        logger.info(uri1.getHost());
//        Matcher a = pattern.matcher(url);
//        System.out.println(a.matches());
//        if (a.matches()) {
//            URI uri =  new URI(url);
//            System.out.println(uri.getPath());
//        }

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
            headerParameters = buildParams(singleTypeInfoList, ParamLocation.HEADER);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("ERROR in building headers in addPathItems " + e, LogDb.DASHBOARD);
        }

        List<Parameter> queryParameters = new ArrayList<>();
        try{
            queryParameters = buildParams(singleTypeInfoList, ParamLocation.QUERY);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("ERROR in building query params in addPathItems " + e, LogDb.DASHBOARD);
        }
        
        PathBuilder.addPathItem(paths, url, method, responseCode, schema, headerParameters, queryParameters, includeHeaders);
    }

    // Ref: https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#parameter-locations
    public enum ParamLocation {
        HEADER, QUERY, PATH, COOKIE
    }

    public static List<Parameter> buildParams(List<SingleTypeInfo> singleTypeInfoList, ParamLocation location) throws Exception{
        List<Parameter> parameters = new ArrayList<>();
        ObjectSchema schema =new ObjectSchema();
        for (SingleTypeInfo singleTypeInfo : singleTypeInfoList) {
            switch (location) {
                case HEADER:
                    if (singleTypeInfo.isIsHeader()) {
                        List<SchemaBuilder.CustomSchema> cc = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(singleTypeInfo);
                        SchemaBuilder.build(schema, cc);
                    }
                    break;
                case QUERY:
                    if (singleTypeInfo.isQueryParam()) {
                        List<SchemaBuilder.CustomSchema> cc = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(singleTypeInfo);
                        SchemaBuilder.build(schema, cc);
                    }
                    break;
                default:
                    break;
            }
        }

        if (schema.getProperties() == null) return parameters;
        for(String param:schema.getProperties().keySet()){
            Parameter parameter = new Parameter();
            // Strip the _queryParam suffix if present to get clean parameter name
            String cleanParamName = param;
            if (param.endsWith("_queryParam")) {
                cleanParamName = param.substring(0, param.length() - "_queryParam".length());
            }
            parameter.setName(cleanParamName);
            parameter.setIn(location.name().toLowerCase());
            
            Schema<?> paramSchema = schema.getProperties().get(param);
            parameter.setSchema(paramSchema);
            
            // Set example on parameter if available in schema
            if (paramSchema != null && paramSchema.getExample() != null) {
                parameter.setExample(paramSchema.getExample());
            }            
            // Set required to false by default for query and header parameters
            // Path parameters should be required, but they're handled separately in parameterizePath
            parameter.setRequired(location == ParamLocation.PATH);
            parameters.add(parameter);
        }
        return parameters;
    }


    public static Set<SingleTypeInfo> removeStiWithObjectListConflict(List<SingleTypeInfo> allPayloadSti) {
        Set<String> allPayloadObjectPaths = new HashSet<>();
        Set<String> allPayloadListPaths = new HashSet<>();
        Set<String> allPayloadBlacklistPaths = new HashSet<>();

        Set<SingleTypeInfo> ret = new HashSet<>();

        for (SingleTypeInfo singleTypeInfo : allPayloadSti) {
            String fullParam = singleTypeInfo.getParam();
            String[] params = fullParam.split("#");


            if (params.length == 0) {
                break;
            } 

            allPayloadObjectPaths.add("#"+fullParam);

            String prefix = "";
            for (int i = 0; i < params.length-1; i++) {
                String curr = params[i];
                String next = params[i+1];
                prefix += ("#"+curr);

                if (next.equals("$")) {

                    if (allPayloadObjectPaths.contains(prefix)) {
                        allPayloadBlacklistPaths.add(prefix + "#$");
                    } else {
                        allPayloadListPaths.add(prefix);
                    }

                } else {

                    if (allPayloadListPaths.contains(prefix)) {
                        allPayloadBlacklistPaths.add(prefix + "#" + next);
                    } else {
                        allPayloadObjectPaths.add(prefix);
                    }
                    
                }
            }
        }

        for (SingleTypeInfo singleTypeInfo: allPayloadSti) {
            String _p = "#"+singleTypeInfo.getParam();

            boolean skipThisSti = false;
            for(String blacklist: allPayloadBlacklistPaths) {
                if (_p.startsWith(blacklist)) {
                    skipThisSti = true;
                    break;
                }
            }

            if (skipThisSti) {
                continue;
            } else {
                ret.add(singleTypeInfo);
            }

        }

        return ret;
    }    

    public static Schema<?> buildSchema(List<SingleTypeInfo> singleTypeInfoList) throws Exception {
        ObjectSchema schema =new ObjectSchema();
        List<SingleTypeInfo> allPayloadSti = new ArrayList<>();
        
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            if(singleTypeInfo.isIsHeader() || singleTypeInfo.isQueryParam() || singleTypeInfo.getIsUrlParam()){
                continue;
            }

            allPayloadSti.add(singleTypeInfo);
        }

        for (SingleTypeInfo singleTypeInfo: removeStiWithObjectListConflict(allPayloadSti)) {
            
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
                List<String> exclusions = Arrays.asList("exampleSetFlag", "types");
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
