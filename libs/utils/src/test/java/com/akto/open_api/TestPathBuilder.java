package com.akto.open_api;

import io.swagger.models.parameters.Parameter;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class TestPathBuilder {

    @Test
    public void testFixPathBaseCase() {
        Paths paths = new Paths();
        PathItem pathItem = new PathItem();
        String oldUrl = "/api/books/integer/author/string/something/ii";
        paths.addPathItem(oldUrl, pathItem);

        paths = PathBuilder.parameterizePath(paths);

        PathItem newPathItem = paths.get(oldUrl);
        assertNotNull(newPathItem);
        assertEquals(newPathItem.getParameters().size(), 0);
    }

    @Test
    public void testFixPathMultipleParams() {
        Paths paths = new Paths();
        PathItem pathItem = new PathItem();
        String oldUrl = "/api/books/INTEGER/author/STRING/string/INTEGER";
        paths.addPathItem(oldUrl, pathItem);

        paths = PathBuilder.parameterizePath(paths);

        String newUrl = "/api/books/{param1}/author/{param2}/string/{param3}";
        PathItem newPathItem = paths.get(newUrl);
        assertNotNull(newPathItem);
        assertNull(paths.get(oldUrl));
        assertEquals(newPathItem.getParameters().size(),3);
        assertEquals(newPathItem.getParameters().get(0).getSchema().getClass(), IntegerSchema.class);
        assertEquals(newPathItem.getParameters().get(1).getSchema().getClass(), StringSchema.class);
        assertEquals(newPathItem.getParameters().get(2).getSchema().getClass(), IntegerSchema.class);

    }
}
