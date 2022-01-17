package com.akto.open_api;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.mongodb.ConnectionString;
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

    public static OpenAPI init(int apiCollectionId) throws Exception {
        OpenAPI openAPI = new OpenAPI();
        addPaths(openAPI, apiCollectionId);
        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id", apiCollectionId);
        if (apiCollection == null) {
            addInfo(openAPI,"Invalid apiCollectionId");
            return openAPI;
        }
        addServer(null, openAPI);
        Paths paths = PathBuilder.parameterizePath(openAPI.getPaths());
        openAPI.setPaths(paths);
        addInfo(openAPI, apiCollection.getName());
        return openAPI;
    }


    public static void main(String[] args) throws Exception{
        String mongoURI = "mongodb://avneesh:UkhcXqXc74w6K9V@cluster0-shard-00-00.4s18x.mongodb.net:27017,cluster0-shard-00-01.4s18x.mongodb.net:27017,cluster0-shard-00-02.4s18x.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-k5j1ae-shard-0&authSource=admin&retryWrites=true&w=majority";
        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(1_000_000);

        OpenAPI openAPI = init(0);
        String f = convertOpenApiToJSON(openAPI);
        System.out.println(f);
    }

    public static void addPaths(OpenAPI openAPI, int apiCollectionId) {
        Paths paths = new Paths();
        List<String> uniqueUrls = SingleTypeInfoDao.instance.getUniqueValues();
        for (String url: uniqueUrls) {
            Map<String, Map<Integer, List<SingleTypeInfo>>> stiMap = getCorrespondingSingleTypeInfo(url,apiCollectionId);
            buildPathsFromSingleTypeInfosPerUrl(stiMap, url, paths);
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

    public static Map<String, Map<Integer, List<SingleTypeInfo>>> getCorrespondingSingleTypeInfo(String url,
                                                                                                 int apiCollectionId) {
        List<SingleTypeInfo> singleTypeInfoList = SingleTypeInfoDao.instance.findAll(
                Filters.and(
                        Filters.eq("isHeader", false),
                        Filters.eq("url", url),
                        Filters.eq("apiCollectionId", apiCollectionId)
                )
        );

        Map<String, Map< Integer, List<SingleTypeInfo>>> stiMap = new HashMap<>();

        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            String method = singleTypeInfo.getMethod();
            Integer responseCode = singleTypeInfo.getResponseCode();

            if (!stiMap.containsKey(method)) {
                stiMap.put(method, new HashMap<>());
            }
            if (!stiMap.get(method).containsKey(responseCode)) {
                stiMap.get(method).put(responseCode, new ArrayList<>());
            }

            stiMap.get(method).get(responseCode).add(singleTypeInfo);
        }
        return stiMap;
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
