package com.akto.open_api;

import com.akto.DaoInit;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class Main {
    public static void main(String[] args) throws Exception{
        String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";
        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(222_222);

        OpenAPI openAPI = new OpenAPI();
        addPath(openAPI);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(NON_NULL);
        String f = mapper.writeValueAsString(openAPI);
        System.out.println(f);
    }

    public static void addPath(OpenAPI openAPI) throws Exception {
        Paths paths = new Paths();
        List<String> uniqueUrls = SingleTypeInfoDao.instance.getUniqueValues();
        for (String url: uniqueUrls) {
            int responseCode = 200;
            String method = "POST";
            List<SingleTypeInfo> singleTypeInfoList = getCorrespondingSingleTypeInfo(url, responseCode, method);
            Schema<?> schema = buildSchema(singleTypeInfoList);
            PathBuilder.addPathItem(paths, url, method, responseCode, schema);
            openAPI.setPaths(paths);
        }
    }

    public static List<SingleTypeInfo> getCorrespondingSingleTypeInfo(String url, int responseCode, String method) {
        return SingleTypeInfoDao.instance.findAll(
                Filters.and(
                        Filters.eq("isHeader", false),
                        Filters.eq("url", url),
                        Filters.eq("responseCode", responseCode),
                        Filters.eq("method",method)
                )
        );
    }

    public static Schema<?> buildSchema(List<SingleTypeInfo> singleTypeInfoList) throws Exception {
        ObjectSchema schema =new ObjectSchema();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            List<SchemaBuilder.CustomSchema> cc = SchemaBuilder.getCustomSchemasFromSingleTypeInfo(singleTypeInfo);
            SchemaBuilder.build(schema, cc);
        }
        schema.setDescription("Avneesh studdddd");
        return schema;
    }


    public static String convertSchemaToString(Schema<?> schema,int tab) {
        String s = "";
        s +=  getTabs(tab) + "type: " + schema.getType() + "\n";
        if (schema instanceof ObjectSchema) {
            s += getTabs(tab)+ "properties: \n" ;
            tab += 1;
            for (String k: schema.getProperties().keySet()) {
                s += getTabs(tab) + k + "\n";
                s += convertSchemaToString(schema.getProperties().get(k), tab+1);
            }
        } else if (schema instanceof ArraySchema) {
            ArraySchema arraySchema = (ArraySchema) schema;
            s += getTabs(tab)+ "items: \n" ;
            tab += 1;
            s += convertSchemaToString(arraySchema.getItems(), tab + 2);
        }
        return s;
    }

    public static String getTabs(int tab) {
        return StringUtils.repeat(" ", tab);
    }

    public static void addServer(String url, OpenAPI openAPI) {
        List<Server> serversList = new ArrayList<>();
        Server server = new Server();
        server.setUrl(url);
        serversList.add(server);
        openAPI.servers(serversList);
    }
}
