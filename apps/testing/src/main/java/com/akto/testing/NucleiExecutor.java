package com.akto.testing;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.rules.FuzzingTest;
import com.akto.util.Pair;

import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import kotlin.text.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

public class NucleiExecutor {

    public static class NucleiResult {
        public ArrayList<Pair<OriginalHttpRequest, OriginalHttpResponse>> attempts;
        public List<BasicDBObject> metaData;

        public NucleiResult(ArrayList<Pair<OriginalHttpRequest, OriginalHttpResponse>> attempts, List<BasicDBObject> metaData) {
            this.attempts = attempts;
            this.metaData = metaData;
        }
    }


    public static NucleiResult execute(
        String method, String baseURL, String templatePath, String outputDir, String body, Map<String, List<String>> headers,
        String pwd
    )  {

        File outputDirFile = new File(outputDir);
        if (!baseURL.endsWith("/")) {
            baseURL += "/";
        }

        File file = new File( pwd+"/.templates-config.json");
        try {
            FileUtils.writeStringToFile(file, "{}", Charsets.UTF_8);
        } catch (IOException e) {
            ;
            return null;
        }

        String outputDirCalls = outputDirFile.getAbsolutePath()+"/calls";
        String outputDirFiles = outputDirFile.getAbsolutePath()+"/files";

        FuzzingTest.createDirPath(outputDirFiles+"/logs.txt");

        String path = baseURL;
        if (baseURL.contains("?")) path = baseURL.substring(0, baseURL.indexOf("?"));
        String fullUrl = path.startsWith("http") ? path : "https://" + path;

        String arch = System.getProperty("os.arch");
        String nucleiFileSuffix = "linux";

        if (arch != null && arch.equals("aarch64")) {
            nucleiFileSuffix = "m1";
        }

        List<String> baseCmdTokens = new ArrayList<>();
        baseCmdTokens.add("/app/nuclei_"+nucleiFileSuffix);

        baseCmdTokens.add("-u");
        baseCmdTokens.add(fullUrl);

        baseCmdTokens.add("-t");
        baseCmdTokens.add(templatePath);

        baseCmdTokens.add("-output-files-dir");
        baseCmdTokens.add(outputDirFiles);

        baseCmdTokens.add("-store-resp-dir");
        baseCmdTokens.add(outputDirCalls);

        baseCmdTokens.add("-template-dir");
        baseCmdTokens.add(pwd);

        baseCmdTokens.add("-v");
        baseCmdTokens.add("Method="+method);

        baseCmdTokens.add("-v");
        baseCmdTokens.add("Path="+path);

        if (StringUtils.isNotEmpty(body)) {
            baseCmdTokens.add("-v");
            baseCmdTokens.add("Body=\"" + body.replaceAll("\"","\\\\\"") + "\"");
        }

        for (String headerName: headers.keySet()) {
            String headerValue = StringUtils.join(headers.get(headerName), ",").replaceAll("\"","\\\\\"");
            baseCmdTokens.add("-h");
            baseCmdTokens.add(headerName + ":\"" + headerValue + "\"");
        }

        Process process;

        try {
            process = Runtime.getRuntime().exec(baseCmdTokens.toArray(new String[0]));
            // StringBuilder output = new StringBuilder();

            // BufferedReader reader = new BufferedReader(
            //         new InputStreamReader(process.getErrorStream()));

            // BufferedReader reader2 = new BufferedReader(
            //     new InputStreamReader(process.getInputStream()));
                        
            //     String line;
            //     while ((line = reader.readLine()) != null) {
            //         output.append(line + "\n");
            //     }
    
            //     String line2;
            //     while ((line2= reader2.readLine()) != null) {
            //         output.append(line2 + "\n");
            //     }

            boolean processResult = process.waitFor(5, TimeUnit.MINUTES);
        } catch (IOException | InterruptedException e) {
            ;
        }

        List<BasicDBObject> metaData;
        try {
             metaData = readMetaData(outputDirFiles);
        } catch (Exception e) {
            ;
            return null;
        }

        ArrayList<Pair<OriginalHttpRequest, OriginalHttpResponse>> attempts = readResponses(outputDirCalls);

        return new NucleiResult(attempts, metaData);
        
    }

    public static List<BasicDBObject> readMetaData(String nucleiOutputDir) throws FileNotFoundException {

        FileReader fileReader = new FileReader(nucleiOutputDir+"/main.txt");
        BufferedReader reader = new BufferedReader(fileReader);
        Gson gson = new Gson();

        List<BasicDBObject> result = new ArrayList<>();
        String line = "";

        try {
            while ((line = reader.readLine()) != null) {
                BasicDBObject value = gson.fromJson(line, BasicDBObject.class);
                result.add(value);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private final static String dumpReqRegex = "^(\\[.*\\]) Dumped \\w+ request for [^ \\n]+";
    private final static String dumpRespRegex = "^(\\[.*\\]) Dumped \\w+ response [^ \\n]+";
    private enum State {
        REQ_HEADER, REQ_PAYLOAD, RESP_HEADER, RESP_PAYLOAD
    }

    public static ArrayList<Pair<OriginalHttpRequest, OriginalHttpResponse>> readResponses(String nucleiOutputDir) {
        ArrayList<Pair<OriginalHttpRequest, OriginalHttpResponse>> ret = new ArrayList<>();

        File nucleiOutputDirFile = new File(nucleiOutputDir);
        for(File childFile: nucleiOutputDirFile.listFiles()) {
            if (childFile.isDirectory()) {
                for(File logFile: childFile.listFiles()) {
                    BufferedReader reader = null;
                    FileReader fileReader = null;
                    State state = null;
                    Pair<OriginalHttpRequest, OriginalHttpResponse> pair = new Pair<>(new OriginalHttpRequest(), new OriginalHttpResponse());
                    try {
                        fileReader = new FileReader(logFile);
                        reader = new BufferedReader(fileReader);
                        String line = "";
            
                        while (line != null) {
                            line = reader.readLine();
                            if (line == null) break;
                            State oldState = state;
                            if (line.matches(dumpReqRegex)) {
                                pair = new Pair<>(new OriginalHttpRequest(), new OriginalHttpResponse());
                                ret.add(pair);
                                line = reader.readLine();
                                line = reader.readLine();
                                line = reader.readLine();
                                
                                boolean valid = pair.getFirst().setMethodAndQP(line);
                                if (!valid) continue;
                                state = State.REQ_HEADER;
                            } else if (line.matches(dumpRespRegex)) {
                                line = reader.readLine();
                                line = reader.readLine();
                                boolean valid = pair.getSecond().setStatusFromLine(line);
                                if (!valid) {
                                    continue;
                                }
                                state = State.RESP_HEADER;
                            } else if (line.isEmpty()) {
                                if(state == State.REQ_HEADER) {
                                    state = State.REQ_PAYLOAD;
                                } else if(state == State.RESP_HEADER) {
                                    state = State.RESP_PAYLOAD;
                                }
                            }
                            
                            boolean stateChanged = (oldState != state);

                            if (!stateChanged) {
                                switch (state) {
                                    case REQ_HEADER:
                                        pair.getFirst().addHeaderFromLine(line);
                                        break;
                                    case REQ_PAYLOAD:
                                        pair.getFirst().appendToPayload(line);
                                        break;
                                    case RESP_HEADER:
                                        pair.getSecond().addHeaderFromLine(line);
                                        break;
                                    case RESP_PAYLOAD:
                                        pair.getSecond().appendToPayload(line);
                                        break;
                                    default:
                                        break;
                                }
                            }

                        }
            
                    } catch (IOException e) {
                        ;
                    } finally {

                        try {
                            if (fileReader != null) fileReader.close();
                            if (reader != null) reader.close();
                        } catch (IOException e) {

                        }
                    }
            
                }
            }
        }

        return ret;
    }
}