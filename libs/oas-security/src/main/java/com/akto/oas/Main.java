package com.akto.oas;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;


import com.akto.oas.requests.BxB;
import com.akto.oas.scan.ScanUtil;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        /*String petstore_local = "src/schema_example_files/petstore.json";

        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(petstore_local, null, parseOptions);

        OpenAPI openAPI = result.getOpenAPI();

        for (var reqFunc: RequestsGenerator.createRequests(openAPI)) {
            HttpRequest.Builder builder = reqFunc.apply(HttpRequest.newBuilder());

            if (builder instanceof BxB.Err) {
                System.out.println(((BxB.Err) builder));
            } else {
                System.out.println(ScanUtil.create(builder.build()));
            }
        };

        String uspto = "https://raw.githubusercontent.com/OAI/OpenAPI-Specification/main/examples/v3.0/uspto.json";
        String uspto_local = "src/schema_example_files/uspto.json";
        String petstore_2 = "https://raw.githubusercontent.com/OAI/OpenAPI-Specification/main/examples/v2.0/json/petstore.json";
        String petstore_yaml = "src/schema_example_files/a.yaml";
        String ss = "src/schema_example_files/stripe_v3.json";

        List<Issue> issues = new OpenAPIValidator().validateOpenAPI(openAPI,new ArrayList<>());
        System.out.println("Issue size: " + issues.size());
        BufferedWriter writer = null;

        try {
           writer = new BufferedWriter(new FileWriter("hello.txt"));
           for (Issue issue : issues) {
               writer.write(issue.getMessage());
               writer.write("\n");
               writer.write(issue.getPath().toString());
               writer.write("\n");
               writer.write("\n");
           }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    */}
}
