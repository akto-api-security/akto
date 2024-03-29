package com.akto.utils;

import com.akto.dao.context.Context;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.open_api.parser.Parser;
import com.akto.open_api.parser.ParserResult;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.util.ArrayList;
import java.util.List;

public class SwaggerYmlProcessUtil {
    public List<String> processSwaggerYml(String swaggerSchema) {
        List<SwaggerUploadLog> swaggerToSwaggerLogsList = swaggerToSwaggerLogs(swaggerSchema);
        return swaggerLogsToAktoMsg(swaggerToSwaggerLogsList);
    }

    private List<SwaggerUploadLog> swaggerToSwaggerLogs(String swaggerSchema) {
        Context.accountId.set(1000000);

        String fileUploadId = "swaggerFileUploadId";
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(swaggerSchema, null, options);
        OpenAPI openAPI = result.getOpenAPI();

        if(openAPI == null) return null;

        ParserResult parsedSwagger = Parser.convertOpenApiToAkto(openAPI, fileUploadId, false);

        return parsedSwagger.getUploadLogs();
    }

    private List<String> swaggerLogsToAktoMsg(List<SwaggerUploadLog> swaggerUploadLogs) {
        List<String> aktoMessages = new ArrayList<>();
        if(swaggerUploadLogs == null) return aktoMessages;

        for(int i = 0; i < swaggerUploadLogs.size(); i++) {
            String aktoFormat = swaggerUploadLogs.get(i).getAktoFormat();
            aktoMessages.add(aktoFormat);
        }

        return aktoMessages;
    }
}
