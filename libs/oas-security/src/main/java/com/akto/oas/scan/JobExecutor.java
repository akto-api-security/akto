package com.akto.oas.scan;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collection;

import com.akto.DaoInit;
import com.akto.dao.APISpecDao;
import com.akto.dao.AttemptsDao;
import com.akto.dao.TestEnvSettingsDao;
import com.akto.dao.TestRunDao;
import com.akto.dao.context.Context;
import com.akto.dto.APISpec;
import com.akto.dto.Attempt;
import com.akto.dto.TestEnvSettings;
import com.akto.dto.TestRun;
import com.akto.dto.Attempt.Status;
import com.akto.dto.Attempt.Success;
import com.akto.dto.TestRun.TestRunStatus;
import com.akto.oas.RequestsGenerator;
import com.akto.oas.requests.BxB;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Updates;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

public class JobExecutor {

    public TestRun execute(int apiSpecId, int testEnvId) {
        APISpec apiSpec = APISpecDao.instance.findById(apiSpecId);
        String specContent = apiSpec.getContent();

        List<Attempt> attempts = createAttempts(specContent);

        TestEnvSettings testEnvSettings = TestEnvSettingsDao.instance.get(testEnvId);

        AttemptsDao.instance.insertAttempts(attempts);

        List<String> attmeptIds = new ArrayList<>();
        for(Attempt attempt: attempts) {
            attmeptIds.add(attempt.getId());
        }

        TestRun testRun = new TestRun(apiSpecId, testEnvId, attmeptIds);
        int testRunId = TestRunDao.instance.createRun(testRun);

        TestRunDao.instance.updateOne("_id", testRunId, Updates.set("status", TestRunStatus.STARTED.toString()));

        TestRunStatus newStatus;
        try {
            fireHappyRequests(attempts, testEnvSettings);
            newStatus = TestRunStatus.COMPLETED;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            newStatus = TestRunStatus.FAILED;
        }

        TestRunDao.instance.updateOne("_id", testRunId, Updates.set("status", newStatus.toString()));

        return testRun;
    }

    private void fireHappyRequests(List<Attempt> attempts, TestEnvSettings testEnvSettings) {
        for (Attempt attempt: attempts) {
            System.out.println(attempt);
            if (!Status.CREATED.toString().equals(attempt.getStatus()) || attempt.getAttemptResult() instanceof Attempt.Err)
                continue;
                
            Success attemptResult = (Attempt.Success) (attempt.getAttemptResult());
            RequestBuilder builder;

            switch (attempt.getMethod()) {
                case "GET":
                    builder = RequestBuilder.get();
                    break;
                case "POST":
                    builder = RequestBuilder.post().setEntity(new StringEntity(attemptResult.getRequestBody(), ContentType.APPLICATION_JSON));
                    break;
                case "PUT":
                    builder = RequestBuilder.put().setEntity(new StringEntity(attemptResult.getRequestBody(), ContentType.APPLICATION_JSON));
                    break;
                case "DELETE":
                    builder = RequestBuilder.delete();
                    break;
                default: 
                    builder = RequestBuilder.get();    
            }

            for(Map.Entry<String, List<String>> headerEntry: attemptResult.getRequestHeaders().entrySet()) {
                for (String headerValue: headerEntry.getValue()) {
                    builder.addHeader(headerEntry.getKey(), headerValue);
                }
            }

            try {
                builder.setUri(new URI(attempt.getUri()));
            } catch (URISyntaxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            HttpUriRequest request = builder.build();
            RequestDispatcher.dispatch(request, attempt, Context.accountId.get());
        }
    }
    

    public List<Attempt> createAttempts(String specContent) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(specContent, null, parseOptions);

        OpenAPI openAPI = result.getOpenAPI();

        List<Attempt> attempts = new ArrayList<>();
        for (Map.Entry<String, List<BxB>> typeAndFuncs: RequestsGenerator.createRequests(openAPI).entrySet()) {
            String type = typeAndFuncs.getKey();
            List<BxB> funcs = typeAndFuncs.getValue();
            boolean isHappy = "happy".equals(type);
            int index = 0;
            for (BxB reqFunc: funcs) {
                if (!isHappy && index == 0) {
                    index++;
                    continue; //TODO: skip the first record - it is happy request - improve this later. 
                }

                if (reqFunc instanceof BxB.Err) {
                    BxB.Err err = (BxB.Err) reqFunc;
                    String message = err.message;
                    String[] array = message.split(" ");
                    attempts.add(new Attempt(array[0], array[1], new Attempt.Err(err.message), isHappy));
                } else {
                    RequestBuilder builder = reqFunc.apply(RequestBuilder.get());
                    attempts.add(ScanUtil.createAttempt(builder.build(), isHappy));
                }

                index++;
            }
        };

        return attempts;

    }

    public static void main(String [] args) throws URISyntaxException {
        String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";

        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(222222);

        new JobExecutor().execute(1633717843, 1632821744);
    }
}
