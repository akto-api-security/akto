package com.akto.action;

import com.akto.action.observe.InventoryAction;
import com.mongodb.BasicDBObject;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.servers.Server;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class TestSwaggerData {

    private Server generateServer(String url) {
        Server server = new Server();
        server.setUrl(url);
        return server;
    }

    private Paths generatePaths(List<List<String>> values) {
        Paths paths = new Paths();

        for (List<String> v: values) {
            PathItem pathItem = paths.get(v.get(0));
            if (pathItem == null) pathItem = new PathItem();
            if (Objects.equals(v.get(1), "GET"))  {
                pathItem.setGet(new Operation());
            } else if (Objects.equals(v.get(1), "POST")) {
                pathItem.setPost(new Operation());
            } else if (Objects.equals(v.get(1), "OPTIONS")) {
                pathItem.setOptions(new Operation());
            } else {
                pathItem.setPatch(new Operation());
            }
            paths.addPathItem(v.get(0), pathItem);
        }

        return paths;
    }

    @Test
    public void happy() {
        OpenAPI openAPI = new OpenAPI();

        openAPI.setServers(new ArrayList<>());
        openAPI.getServers().add(generateServer("https://www.akto.io/"));
        openAPI.getServers().add(generateServer("https://www.akto.io/v1"));

        List<List<String>> values = new ArrayList<>();
        values.add(Arrays.asList("/api/books", "GET"));
        values.add(Arrays.asList("/api/books", "POST"));
        values.add(Arrays.asList("/api/books", "OPTIONS")); // unused
        values.add(Arrays.asList("/api/books/{book_Id}", "GET"));
        values.add(Arrays.asList("/api/books/{book_Id}", "OPTIONS")); // unused
        values.add(Arrays.asList("/api/cars", "GET"));
        values.add(Arrays.asList("/api/bus", "GET"));
        values.add(Arrays.asList("/api/unused1", "GET")); // unused
        values.add(Arrays.asList("/api/unused1/{random}", "GET")); // unused
        values.add(Arrays.asList("/api/used2", "GET"));

        openAPI.setPaths(generatePaths(values));

        List<BasicDBObject> endpoints = new ArrayList<>();
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/api/books").append("method", "GET")));
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/api/books").append("method", "POST")));
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/api/books").append("method", "PATCH"))); // shadow
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/api/somethingRandom").append("method", "GET"))); //shadow
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/v1/api/books").append("method", "GET")));
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/api/books/INTEGER").append("method", "GET")));
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/v1/api/books/INTEGER").append("method", "GET")));
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/api/cars").append("method", "GET")));
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/api/cars/STRING").append("method", "GET"))); //shadow
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","https://www.akto.io/api/bus").append("method", "GET")));
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","https://www.akto.io/v1/api/bus").append("method", "GET")));
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","https://www.akto.io/xyz/api/bus").append("method", "GET"))); // shadow
        endpoints.add(new BasicDBObject().append("_id",new BasicDBObject().append("url","/v1/api/used2").append("method", "GET")));


        Set<String> unused = InventoryAction.fetchSwaggerData(endpoints, openAPI);
        // 2*10 endpoints in swagger file (since 2 servers)
        // Then subtract the non-shadow endpoints from total
        assertEquals(unused.size(), 11);

        int counter = 0;
        for (BasicDBObject endpoint: endpoints) {
            if (endpoint.get("shadow") != null && (boolean) endpoint.get("shadow")) {
                counter ++;
            }
        }
        assertEquals(counter, 4);

    }

    @Test
    public void testRetrievePath() {
        String path = InventoryAction.retrievePath("/");
        assertEquals(path, "");

        path = InventoryAction.retrievePath("https://www.google.com/avneesh");
        assertEquals(path, "/avneesh");

        path = InventoryAction.retrievePath("https://www.google.com/");
        assertEquals(path, "");

        path = InventoryAction.retrievePath("https://www.google.com");
        assertEquals(path, "");
    }
}
