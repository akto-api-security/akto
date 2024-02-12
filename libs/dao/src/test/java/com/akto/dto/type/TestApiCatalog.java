package com.akto.dto.type;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestApiCatalog {

    @Test
    public void testIsTemplateUrl() {
        boolean templateUrl = APICatalog.isTemplateUrl("api/books/INTEGER");
        assertTrue(templateUrl);

        templateUrl = APICatalog.isTemplateUrl("api/BOOKS/STRING");
        assertTrue(templateUrl);

        templateUrl = APICatalog.isTemplateUrl("api/books/OBJECT_ID");
        assertTrue(templateUrl);

        templateUrl = APICatalog.isTemplateUrl("api/books/1");
        assertFalse(templateUrl);

        templateUrl = APICatalog.isTemplateUrl("api/books/bestsellers");
        assertFalse(templateUrl);

        templateUrl = APICatalog.isTemplateUrl("api/books/wefwe23423fawef");
        assertFalse(templateUrl);
    }
}
