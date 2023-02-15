package com.akto.open_api;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class TestAddPathItems {

    @Test
    public void happy() {
        Paths paths = new Paths();
        try {
            Main.addPathItems(200,paths, "/api/1", "GET", new ArrayList<>(), true);
            Main.addPathItems(404,paths, "/api/1", "GET", new ArrayList<>(), true);
            Main.addPathItems(200,paths, "/api/1", "POST", new ArrayList<>(), true);
            Main.addPathItems(200,paths, "/api/1", "PUT", new ArrayList<>(), true);
            Main.addPathItems(200,paths, "/api/2", "GET", new ArrayList<>(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertEquals(paths.size(), 2);
        PathItem pathItem1 = paths.get("/api/1");
        PathItem pathItem2 = paths.get("/api/2");
        assertNotNull(pathItem1);
        assertNotNull(pathItem2);

        assertNotNull(pathItem1.getGet());
        assertNotNull(pathItem1.getPost());
        assertNotNull(pathItem1.getPut());
        assertNull(pathItem1.getDelete());

        Operation getOperation = pathItem1.getGet();
        assertEquals(getOperation.getResponses().size(),2);
        assertNotNull(getOperation.getResponses().get("200"));
        assertNotNull(getOperation.getResponses().get("404"));
        assertNull(getOperation.getResponses().get("444"));

        Operation putOperation = pathItem1.getPut();
        assertEquals(putOperation.getResponses().size(),1);
    }
}
