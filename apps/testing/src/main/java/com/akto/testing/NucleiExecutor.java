package com.akto.testing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.util.Pair;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

public class NucleiExecutor {
    
    public static ArrayList<Pair<OriginalHttpRequest, OriginalHttpResponse>> execute(
        String method, String baseURL, String templatePath, String outputDir, String body, Map<String, List<String>> headers
    ) {
        File outputDirFile = new File(outputDir);
        String pathEnv = System.getenv("PATH");
        if (!baseURL.endsWith("/")) {
            baseURL += "/";
        }

        String outputDirCalls = outputDirFile.getAbsolutePath()+"/calls";

        String path = baseURL;
        if (baseURL.contains("?")) {
            path = baseURL.substring(0, baseURL.indexOf("?"));
        }

        String[] baseCmdTokens = new String[]{
            "/Users/ankushjain/go/bin/nuclei",
            "-u", baseURL,
            "-templates", templatePath,
            "--store-resp", 
            "-o", outputDirFile.getAbsolutePath()+"/logs.txt",
            "-store-resp-dir", outputDirCalls,
            "-V", "\"Method="+method+"\"",
            "-V", "\"Path="+path+"\""
        };

        String baseCmd = StringUtils.join(baseCmdTokens, " ");

        if (StringUtils.isNotEmpty(body)) {
            baseCmd += " -V \"Body=" + body.replaceAll("\"","\\\\\"") + "\"";
        }

        for (String headerName: headers.keySet()) {
            String headerValue = StringUtils.join(headers.get(headerName), ",").replaceAll("\"","\\\\\"");
            baseCmd += " -H \"" + headerName + ":" + headerValue + "\"";
        }

        baseCmd += " -debug\n";

        Process process;

        File file = new File(outputDir, "script.sh");

        try {
            FileUtils.writeStringToFile(file, baseCmd, StandardCharsets.UTF_8);
        } catch (IOException e1) {
            return null;
        }

        try {
            process = Runtime.getRuntime().exec("sh " + file.getAbsolutePath(), new String[]{"PATH="+pathEnv}, new File("/Users/ankushjain/akto_code/nuclei-wrapper"));
            StringBuilder output = new StringBuilder();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));

            BufferedReader reader2 = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
                        
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line + "\n");
                }
    
                String line2;
                while ((line2= reader2.readLine()) != null) {
                    output.append(line2 + "\n");
                }
    
            int s = process.waitFor();        
            
        } catch (IOException e) {

            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        return readResponses(outputDirCalls);
        
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