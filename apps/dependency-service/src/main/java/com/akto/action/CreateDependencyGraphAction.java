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
import com.akto.utils.DependencyBucketS3Util;
import com.google.gson.Gson;
import com.opensymphony.xwork2.ActionSupport;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.util.*;
import java.util.concurrent.*;

import static com.opensymphony.xwork2.Action.ERROR;
import static com.opensymphony.xwork2.Action.SUCCESS;

public class CreateDependencyGraphAction extends ActionSupport {
    private String swaggerSchema;
    private Map<Integer, DependencyNode> nodes = new HashMap<>();
    private final Map<String, String> job_id = new HashMap<>();
    private final StringBuilder swaggerResultJson = new StringBuilder();
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final DependencyBucketS3Util s3Util = new DependencyBucketS3Util();

    public String createDependencyGraph() {
        String jobId = UUID.randomUUID().toString();
        job_id.put("jobId", jobId);

        if(swaggerSchema == null || swaggerSchema.isEmpty()) {
            addActionError("Invalid Swagger Schema");
            return ERROR.toUpperCase();
        }

        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                s3Util.uploadSwaggerSchema(jobId, swaggerSchema);

                List<SwaggerUploadLog> swaggerToSwaggerLogsList = swaggerToSwaggerLogs();
                List<String> aktoMsgList = swaggerLogsToAktoMsg(swaggerToSwaggerLogsList);

                List<HttpResponseParams> httpResponseParamsList = new ArrayList<>();
                Map<Integer, APICatalog> dbState = new HashMap<>();
                DependencyAnalyserHelper dependencyAnalyserHelper = new DependencyAnalyserHelper(dbState);

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
                    swaggerResultJson.append(gson.toJson(nodeList));

                    s3Util.uploadSwaggerResultJson(jobId, swaggerResultJson.toString());
                    s3Util.close();
                } catch (Exception e) {
                    System.err.println(e.getMessage());

                    addActionError(e.getMessage());
                    s3Util.uploadErrorMessages(jobId, e.getMessage());
                    s3Util.close();
                }
            }
        }, 0, TimeUnit.SECONDS);

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


    public void setSwaggerSchema(String swaggerSchema) {
        this.swaggerSchema = swaggerSchema;
    }
    public Map<String, String> getJob_id() {
        return job_id;
    }
}