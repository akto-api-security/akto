package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;

public class McpMethodAnalyzer extends TestExecutorModifier {

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        return
            "You are an expert API method analyzer for MCP (Model Context Protocol) requests. "
                + "Your task is to analyze the provided MCP request and determine the most appropriate HTTP method (GET, POST, PUT, DELETE, PATCH) based on the tool name and arguments.\n\n"
                + "ANALYSIS RULES:\n"
                + "1. Examine the tool name and arguments to understand the intended operation\n"
                + "2. Consider RESTful conventions and semantic meaning\n"
                + "3. Look for indicators in the tool name and arguments that suggest the operation type\n"
                + "4. Default to POST if the operation is unclear\n\n"
                + "METHOD INDICATORS:\n"
                + "- GET: Read operations, fetching data, queries, 'get', 'fetch', 'read', 'list', 'search'\n"
                + "- POST: Create operations, sending data, 'create', 'add', 'send', 'submit', 'process'\n"
                + "- PUT: Update/replace operations, 'update', 'replace', 'set', 'modify'\n"
                + "- DELETE: Remove operations, 'delete', 'remove', 'destroy', 'clear'\n"
                + "- PATCH: Partial updates, 'patch', 'partial', 'incremental'\n\n"
                + "OUTPUT FORMAT:\n"
                + "Return a single valid JSON object with the following structure:\n"
                + "{\"method\": \"<HTTP_METHOD>\", \"confidence\": <0.0-1.0>, \"reasoning\": \"<brief explanation>\"}\n\n"
                + "INPUTS:\n"
                + "MCP REQUEST: \n"
                + queryData.get(TestExecutorModifier._REQUEST) + "\n\n"
                + "API INFO KEY: \n"
                + queryData.get("apiInfoKey") + "\n\n"
                + "Analyze the MCP request and API INFO KEY and return the JSON response with the determined HTTP method.";
    }
}
