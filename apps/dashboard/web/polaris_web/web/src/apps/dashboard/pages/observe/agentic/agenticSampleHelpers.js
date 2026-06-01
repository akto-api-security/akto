/** Fallback HTTP samples when no captured traffic exists in the DB. */

export function generateSkillSample(skill) {
    const skillPath = skill?.rawName || skill?.name || "skill";
    return {
        message: JSON.stringify({
            method: "POST",
            path: `/skills/${skillPath}`,
            requestHeaders: JSON.stringify({ "content-type": "application/json" }),
            requestPayload: JSON.stringify({ prompt: `Execute ${skill?.name || skillPath}` }),
            statusCode: 200,
            responseHeaders: JSON.stringify({ "content-type": "application/json" }),
            responsePayload: JSON.stringify({ status: "ok" }),
        }),
    };
}

export const EMPTY_HTTP_SAMPLE = {
    message: JSON.stringify({
        method: "POST",
        path: "/",
        requestHeaders: JSON.stringify({ "content-type": "application/json" }),
        requestPayload: JSON.stringify({}),
        statusCode: 200,
        responseHeaders: JSON.stringify({ "content-type": "application/json" }),
        responsePayload: JSON.stringify({}),
    }),
};

export function generateResourceSample(resource) {
    return {
        message: JSON.stringify({
            method: "GET",
            path: "/mcp/resources/read",
            requestHeaders: JSON.stringify({ "content-type": "application/json" }),
            requestPayload: JSON.stringify({ uri: resource.uri }),
            statusCode: 200,
            responseHeaders: JSON.stringify({ "content-type": "application/json" }),
            responsePayload: JSON.stringify({
                contents: [{
                    uri: resource.uri,
                    mimeType: "application/json",
                    text: JSON.stringify({ id: "obj_001", name: resource.name }),
                }],
            }),
        }),
    };
}

export function generatePromptSample(prompt) {
    const args = {};
    (prompt.args || []).forEach((a) => { args[a] = `example_${a.replace(/_/g, "-")}`; });
    return {
        message: JSON.stringify({
            method: "POST",
            path: "/mcp/prompts/get",
            requestHeaders: JSON.stringify({ "content-type": "application/json" }),
            requestPayload: JSON.stringify({ name: prompt.name, arguments: args }),
            statusCode: 200,
            responseHeaders: JSON.stringify({ "content-type": "application/json" }),
            responsePayload: JSON.stringify({
                description: prompt.description,
                messages: [{ role: "user", content: { type: "text", text: prompt.description || prompt.name } }],
            }),
        }),
    };
}

export function generateToolSample(tool) {
    const args = {};
    (tool.params || []).forEach((p) => {
        if (!p.required) return;
        if (p.type === "string") args[p.name] = `example_${p.name.replace(/_/g, "-")}`;
        else if (p.type === "number") args[p.name] = 42;
        else if (p.type === "boolean") args[p.name] = true;
        else if (p.type === "array") args[p.name] = ["item_1"];
        else if (p.type === "object") args[p.name] = { key: "value" };
    });

    const n = tool.name || "";
    let responsePayload = { success: true, result: "Operation completed successfully." };
    if (n.startsWith("list_") || n.startsWith("get_")) {
        responsePayload = { result: [{ id: "obj_001", name: "Example item", status: "active" }], total: 1 };
    } else if (n.startsWith("create_") || n.startsWith("add_")) {
        responsePayload = { id: "id_new", status: "created" };
    }

    return {
        message: JSON.stringify({
            method: "POST",
            path: "/mcp/tools/call",
            requestHeaders: JSON.stringify({ "content-type": "application/json" }),
            requestPayload: JSON.stringify({ name: tool.name, arguments: args }),
            statusCode: 200,
            responseHeaders: JSON.stringify({ "content-type": "application/json" }),
            responsePayload: JSON.stringify({
                content: [{ type: "text", text: JSON.stringify(responsePayload, null, 2) }],
                isError: false,
            }),
        }),
    };
}
