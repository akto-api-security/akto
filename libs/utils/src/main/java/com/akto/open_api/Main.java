package com.akto.open_api;

import com.akto.DaoInit;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class Main {
    public static void main1(String[] args) throws URISyntaxException {
        Pattern pattern = Pattern.compile("^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:|:blank:]])?$");
        String url = "https://petstore.swagger.io/v2/user/STRING";
        Matcher a = pattern.matcher(url);
        System.out.println(a.matches());
//        if (a.matches()) {
//            Pattern pattern1 = Pattern.compile("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}");
//            System.out.println(pattern.matcher(url).group());
//        }

    }

    public static OpenAPI init(int apiCollectionId) throws Exception {
        OpenAPI openAPI = new OpenAPI();
        addPath(openAPI, apiCollectionId);
        Paths paths = PathBuilder.parameterizePath(openAPI.getPaths());
        openAPI.setPaths(paths);

        return openAPI;
    }


    public static void main(String[] args) throws Exception{
        String mongoURI = "";
        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(1_000_000);

        OpenAPI openAPI = init(30);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(NON_NULL);
        String f = mapper.writeValueAsString(openAPI);
        System.out.println(f);
    }

    public static void addPath(OpenAPI openAPI, int apiCollectionId) throws Exception {
        // TODO: handle empty collections
        // TODO: write test cases
        Paths paths = new Paths();
        List<String> uniqueUrls = SingleTypeInfoDao.instance.getUniqueValues();
        for (String url: uniqueUrls) {
            Map<String, Map<Integer, List<SingleTypeInfo>>> stiMap = getCorrespondingSingleTypeInfo(url,apiCollectionId);
            for (String method: stiMap.keySet()) {
                Map<Integer,List<SingleTypeInfo>> responseWiseMap = stiMap.get(method);
                for (Integer responseCode: responseWiseMap.keySet()) {
                    List<SingleTypeInfo> singleTypeInfoList = responseWiseMap.get(responseCode);
                    Schema<?> schema = buildSchema(singleTypeInfoList);
                    PathBuilder.addPathItem(paths, url, method, responseCode, schema);
                    openAPI.setPaths(paths);
                }
            }
        }
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
        List<Server> serversList = new ArrayList<>();
        Server server = new Server();
        server.setUrl(url);
        serversList.add(server);
        openAPI.servers(serversList);
    }

}
