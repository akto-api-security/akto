package com.akto.oas;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Assertions;
import org.junit.Test;

import java.util.*;

import com.akto.oas.Issue;
import com.akto.oas.OpenAPIValidator;
import com.akto.oas.PathComponent;

import static org.junit.jupiter.api.Assertions.*;

public class ValidateComponentsTest {
    @Test
    public void validateNullComponents() {
        List<Issue> issues = OpenAPIValidator.validateComponents(null,null,new ArrayList<>());
        Assertions.assertEquals(issues.size(),1);
        Assertions.assertEquals(issues.get(0).getMessage(), "Components can't be null");
        Assertions.assertEquals(issues.get(0).getPath(), new ArrayList<>());
    }

    @Test
    public void validateNullSchemasAndSecuritySchemes() {
        Components components = new Components();
        components.setSchemas(null);
        components.setSecuritySchemes(null);

        List<Issue> issues = OpenAPIValidator.validateComponents(components,null,new ArrayList<>());
        assertEquals(issues.size(),1);
        assertEquals(issues.get(0).getMessage(), "Reusable security scheme is not defined");
        Assertions.assertEquals(issues.get(0).getPath(), Arrays.asList(PathComponent.COMPONENTS, PathComponent.SECURITY_SCHEMES));
    }

}