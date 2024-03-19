package com.akto.action;

import com.akto.DependencyAnalyserHelper;
import com.akto.DependencyFlowHelper;
import com.akto.dao.context.Context;
import com.akto.dto.DependencyNode;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.type.APICatalog;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.open_api.parser.Parser;
import com.akto.open_api.parser.ParserResult;
import com.akto.util.parsers.HttpCallParserHelper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.opensymphony.xwork2.Action.ERROR;
import static com.opensymphony.xwork2.Action.SUCCESS;

public class CreateDependencyGraphAction {

    private final BasicDBObject dependency_graph_status = new BasicDBObject();
    public BasicDBObject getDependency_graph_status() {
        return dependency_graph_status;
    }

    private String swaggerSchema;
    public void setSwaggerSchema(String swaggerSchema) {
        this.swaggerSchema = swaggerSchema;
    }
    public String getSwaggerSchema() {
        return swaggerSchema;
    }

    private Map<Integer, DependencyNode> nodes = new HashMap<>();
    public void setNodes(Map<Integer, DependencyNode> nodes) {
        this.nodes = nodes;
    }

    public String createDependencyGraph() {
        List<SwaggerUploadLog> swaggerToSwaggerLogsList = swaggerToSwaggerLogs();
        List<String> aktoMsgList = swaggerLogsToAktoMsg(swaggerToSwaggerLogsList);

        List<HttpResponseParams> httpResponseParamsList = new ArrayList<>();
        Map<Integer, APICatalog> dbState = new HashMap<>();
        DependencyAnalyserHelper dependencyAnalyserHelper = new DependencyAnalyserHelper(dbState);

        StringBuilder swaggerJson = new StringBuilder();

        try {
            for(String aktoMsg : aktoMsgList) {
                httpResponseParamsList.add(HttpCallParserHelper.parseKafkaMessage(aktoMsg));
            }

            for(int i = 0; i < httpResponseParamsList.size(); i++) {
                dependencyAnalyserHelper.analyse(httpResponseParamsList.get(i), i);
            }

            nodes = dependencyAnalyserHelper.getNodes();
            List<DependencyNode> dependencyNodeList = new ArrayList<>(nodes.values());
            DependencyFlowHelper dependencyFlowHelper = new DependencyFlowHelper(dependencyNodeList);
            dependencyFlowHelper.run();

            Map<Integer, Node> resultNodes = dependencyFlowHelper.resultNodes;
            List<Node> nodeList = new ArrayList<>(resultNodes.values());
            Gson gson = new Gson();
            swaggerJson.append(gson.toJson(nodeList));

        } catch (Exception e) {
            dependency_graph_status.put("error", e.getMessage());
            return ERROR.toUpperCase();
        }

        dependency_graph_status.put("swaggerJson", swaggerJson.toString());

        return SUCCESS.toUpperCase();
    }

    private List<SwaggerUploadLog> swaggerToSwaggerLogs() {
        Context.accountId.set(1000000);

        String fileUploadId = "demoFileUploadId";
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(swaggerSchema, null, options);
        OpenAPI openAPI = result.getOpenAPI();
        ParserResult parsedSwagger = Parser.convertOpenApiToAkto(openAPI, fileUploadId);

        return parsedSwagger.getUploadLogs();
    }

    private List<String> swaggerLogsToAktoMsg(List<SwaggerUploadLog> swaggerUploadLogs) {
        List<String> aktoMessages = new ArrayList<>();

        for(int i = 0; i < swaggerUploadLogs.size(); i++) {
            String aktoFormat = swaggerUploadLogs.get(i).getAktoFormat();
            aktoMessages.add(aktoFormat);
        }

        return aktoMessages;
    }

}