package com.akto.action;

import com.akto.DependencyAnalyserHelper;
import com.akto.DependencyFlowHelper;
import com.akto.dto.DependencyNode;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.type.APICatalog;
import com.akto.util.parsers.HttpCallParserHelper;
import com.akto.utils.DataDetector;
import com.akto.utils.DependencyBucketS3Util;
import com.akto.utils.SwaggerYmlProcessUtil;
import com.google.gson.Gson;
import com.opensymphony.xwork2.ActionSupport;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CreateDependencyGraphAction extends ActionSupport {
    private String apiData;
    private final Map<String, String> job_id = new HashMap<>();
    private final StringBuilder apiResultJson = new StringBuilder();
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final DependencyBucketS3Util s3Util = new DependencyBucketS3Util();

    public String createDependencyGraph() {
        if(apiData == null || apiData.isEmpty()) {
            addActionError("Invalid Schema");
            return ERROR.toUpperCase();
        }

        String jobId = UUID.randomUUID().toString();
        job_id.put("jobId", jobId);

        executorService.schedule(new Runnable() {
            @Override
            public void run() {

                List<String> aktoMessages = null;

                boolean isJson = DataDetector.isJSON(apiData);
                boolean isYaml = DataDetector.isYAML(apiData);

                if(isJson || isYaml) {
                    s3Util.uploadSwaggerSchema(jobId, apiData, isJson);
                    SwaggerYmlProcessUtil swaggerYmlProcessUtil = new SwaggerYmlProcessUtil();
                    aktoMessages = swaggerYmlProcessUtil.processSwaggerYml(apiData);
                } else {
                    s3Util.uploadErrorMessages(jobId, "For now, we only accept Swagger JSON and YAML schema.");
                    s3Util.close();
                }

                if(aktoMessages == null || aktoMessages.isEmpty()) {
                    s3Util.uploadErrorMessages(jobId, "Invalid schema");
                    s3Util.close();
                    return;
                }

                processAktoFormat(aktoMessages);
                s3Util.close();
            }
        }, 0, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    private void processAktoFormat(List<String> aktoMsgList) {
        String jobId = job_id.get("jobId");
        List<HttpResponseParams> httpResponseParamsList = new ArrayList<>();
        Map<Integer, APICatalog> dbState = new HashMap<>();
        DependencyAnalyserHelper dependencyAnalyserHelper = new DependencyAnalyserHelper(dbState);

        try {
            for (String aktoMsg : aktoMsgList) {
                httpResponseParamsList.add(HttpCallParserHelper.parseKafkaMessage(aktoMsg));
            }

            for (int i = 0; i < httpResponseParamsList.size(); i++) {
                dependencyAnalyserHelper.analyse(httpResponseParamsList.get(i), i);
            }

            Map<Integer, DependencyNode> nodes = dependencyAnalyserHelper.getNodes();
            List<DependencyNode> dependencyNodeList = new ArrayList<>(nodes.values());
            DependencyFlowHelper dependencyFlowHelper = new DependencyFlowHelper(dependencyNodeList);
            dependencyFlowHelper.run();

            Map<Integer, Node> resultNodes = dependencyFlowHelper.resultNodes;
            List<Node> nodeList = new ArrayList<>(resultNodes.values());
            Gson gson = new Gson();
            apiResultJson.append(gson.toJson(nodeList));

            s3Util.uploadApiResultJson(jobId, apiResultJson.toString());
        } catch(Exception e) {
            System.out.println(e.getMessage());
            s3Util.uploadErrorMessages(jobId, e.getMessage());
        }
    }

    public void setApiData(String apiData) {
        this.apiData = apiData;
    }
    public Map<String, String> getJob_id() {
        return job_id;
    }
}
