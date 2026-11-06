package com.akto.hybrid_runtime;

import com.akto.dto.HttpResponseParams;
import com.akto.mcp.McpSchema;
import com.akto.utils.McpServer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public class McpReconSyncJobExecutorTest {

    private static McpServer server() {
        McpServer server = new McpServer();
        server.setUrl("mcp.example.com");
        server.setTools(Collections.singletonList(new McpSchema.Tool("get_weather", "Get the weather", null)));
        server.setResources(Collections.singletonList(new McpSchema.Resource("file:///notes.txt", "notes", null, null, null)));
        return server;
    }

    @SuppressWarnings("unchecked")
    private static List<HttpResponseParams> discover(String method) throws Exception {
        Method m = McpReconSyncJobExecutor.class.getDeclaredMethod(method, McpServer.class);
        m.setAccessible(true);
        return (List<HttpResponseParams>) m.invoke(McpReconSyncJobExecutor.INSTANCE, server());
    }

    @Test
    public void madeUpToolCallsAreMarkedSynthetic() throws Exception {
        // these tools/call samples are never sent, so auth detection must skip them
        List<HttpResponseParams> calls = discover("toolsDiscovery");
        Assertions.assertEquals(1, calls.size());
        Assertions.assertTrue(calls.get(0).syntheticMcpCall);
    }

    @Test
    public void madeUpResourceReadsAreMarkedSynthetic() throws Exception {
        List<HttpResponseParams> reads = discover("resourcesDiscovery");
        Assertions.assertEquals(1, reads.size());
        Assertions.assertTrue(reads.get(0).syntheticMcpCall);
    }
}
