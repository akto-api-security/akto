/**
 * Akto Guardrails callbacks for Google Vertex AI ADK.
 *
 * Usage:
 *   import { aktoBeforeModelCallback, aktoAfterModelCallback } from "./callbacks";
 *
 *   const agent = new LlmAgent({
 *     model: "gemini-2.0-flash",
 *     name: "my_agent",
 *     instruction: "You are a helpful assistant.",
 *     beforeModelCallback: aktoBeforeModelCallback,
 *     afterModelCallback: aktoAfterModelCallback,
 *   });
 *
 * Environment Variables:
 *   DATA_INGESTION_URL   : Base URL for Akto's data ingestion service (required).
 *   SYNC_MODE            : "true" (default) to block before the LLM call;
 *                          "false" to validate/log asynchronously after the response.
 *   TIMEOUT              : HTTP request timeout in seconds (default: 5).
 *
 * Auto-detected (Vertex AI Agent Engine injects these at runtime):
 *   GOOGLE_CLOUD_PROJECT           : GCP project ID.
 *   GOOGLE_CLOUD_LOCATION          : Region (e.g. "us-central1").
 *   GOOGLE_CLOUD_AGENT_ENGINE_ID   : Reasoning Engine resource ID.
 */

import type { CallbackContext, LlmRequest, LlmResponse } from "@google/adk";
import type { Content, Part } from "@google/genai";

// ---------------------------------------------------------------------------
// Internal types
// ---------------------------------------------------------------------------

interface AktoSnapshot {
    model: string;
    messages: Record<string, unknown>[];
    blocked: boolean;
}

interface GuardrailsResult {
    allowed: boolean;
    reason: string;
}

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const DATA_INGESTION_URL: string | undefined = process.env.DATA_INGESTION_URL;
const SYNC_MODE: boolean =
    (process.env.SYNC_MODE || "true").toLowerCase() === "true";
const TIMEOUT: number = parseFloat(process.env.TIMEOUT || "5") * 1000; // ms
const AKTO_CONNECTOR_NAME = "vertex-ai-adk";
const HTTP_PROXY_PATH = "/api/http-proxy";

// State key used to pass request data from before_model_callback to after_model_callback.
const AKTO_SNAPSHOT_KEY = "__akto_request_snapshot";

console.info(
    `Akto Guardrails ADK callbacks initialized | sync_mode=${SYNC_MODE}`
);

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

function getVertexEndpointInfo(): [string, string] {
    const project = process.env.GOOGLE_CLOUD_PROJECT;
    const location = process.env.GOOGLE_CLOUD_LOCATION;
    const engineId = process.env.GOOGLE_CLOUD_AGENT_ENGINE_ID;

    if (engineId && project && location) {
        // Agent Engine mode
        const host = `${location}-aiplatform.googleapis.com`;
        const path = `/v1/projects/${project}/locations/${location}/reasoningEngines/${engineId}:query`;
        return [host, path];
    }

    if (process.env.K_SERVICE) {
        // Cloud Run mode
        const serviceUrl = (process.env.CLOUD_RUN_SERVICE_URL || "")
            .replace(/^https?:\/\//, "")
            .replace(/\/+$/, "");
        return [serviceUrl || "run.googleapis.com", "/"];
    }

    // Local dev fallback
    return ["generativelanguage.googleapis.com", "/v1/chat/completions"];
}

function buildHttpProxyParams(
    guardrails: boolean,
    ingestData: boolean
): URLSearchParams {
    const params = new URLSearchParams();
    params.set("akto_connector", AKTO_CONNECTOR_NAME);
    if (guardrails) {
        params.set("guardrails", "true");
    }
    if (ingestData) {
        params.set("ingest_data", "true");
    }
    return params;
}

function parseGuardrailsResult(
    result: Record<string, unknown> | null
): GuardrailsResult {
    if (!result || typeof result !== "object") {
        return { allowed: true, reason: "" };
    }
    const data = result.data as Record<string, unknown> | undefined;
    if (!data || typeof data !== "object") {
        return { allowed: true, reason: "" };
    }
    const gr = data.guardrailsResult as Record<string, unknown> | undefined;
    if (!gr || typeof gr !== "object") {
        return { allowed: true, reason: "" };
    }
    const allowed = gr.Allowed !== undefined ? Boolean(gr.Allowed) : true;
    const reason = typeof gr.Reason === "string" ? gr.Reason : "";
    return { allowed, reason };
}

function contentsToMessages(
    contents: Content[] | undefined
): Record<string, unknown>[] {
    const messages: Record<string, unknown>[] = [];
    if (!contents) {
        return messages;
    }
    for (const content of contents) {
        if (!content) {
            continue;
        }
        const role = content.role || "user";
        const parts: Part[] = content.parts || [];
        const textParts = parts
            .filter((p) => p.text != null && p.text !== "")
            .map((p) => p.text!);
        messages.push({ role, content: textParts.join(" ") });
    }
    return messages;
}

function llmResponseToDict(
    llmResponse: LlmResponse | undefined
): Record<string, unknown> | null {
    if (!llmResponse || !llmResponse.content) {
        return null;
    }
    const content = llmResponse.content;
    const role = content.role || "model";
    const parts: Part[] = content.parts || [];
    const textParts = parts
        .filter((p) => p.text != null && p.text !== "")
        .map((p) => p.text!);
    return {
        choices: [{ message: { role, content: textParts.join(" ") } }],
    };
}

function makeBlockedResponse(reason: string): LlmResponse {
    const msg = reason
        ? `Blocked by Akto Guardrails: ${reason}`
        : "Blocked by Akto Guardrails";
    return {
        content: {
            role: "model",
            parts: [{ text: msg }],
        },
    };
}

function buildPayload(
    context: CallbackContext,
    model: string,
    messages: Record<string, unknown>[],
    responseBody: unknown,
    statusCode: number = 200
): Record<string, unknown> {
    const agentName = context.agentName || "unknown-agent";
    const invocationId = context.invocationId || "";
    const engineId = process.env.GOOGLE_CLOUD_AGENT_ENGINE_ID || process.env.K_SERVICE;
    const [host, path] = getVertexEndpointInfo();
    const timestamp = String(Date.now());
    const statusStr = String(statusCode);

    const modelBodyPayload = {
        model,
        messages,
    };

    const metadata = {
        call_type: "completion",
        model,
        agent_name: agentName,
        invocation_id: invocationId,
    };

    const tags = {
        "gen-ai": "Gen AI",
        "ai-agent": AKTO_CONNECTOR_NAME,
        "bot-name": engineId,
        source: "VERTEX_AI",
    };

    const requestPayload = JSON.stringify({
        body: JSON.stringify(modelBodyPayload),
    });
    const responsePayload =
        responseBody == null
            ? JSON.stringify({})
            : JSON.stringify({ body: JSON.stringify(responseBody) });

    return {
        path,
        requestHeaders: JSON.stringify({
            host,
            "content-type": "application/json",
        }),
        responseHeaders: JSON.stringify({
            "content-type": "application/json",
        }),
        method: "POST",
        requestPayload,
        responsePayload,
        ip: "0.0.0.0",
        destIp: "127.0.0.1",
        time: timestamp,
        statusCode: statusStr,
        type: "HTTP/1.1",
        status: statusStr,
        akto_account_id: "1000000",
        akto_vxlan_id: "0",
        is_pending: "false",
        source: "MIRRORING",
        direction: null,
        process_id: null,
        socket_id: null,
        daemonset_id: null,
        enabled_graph: null,
        tag: JSON.stringify(tags),
        metadata: JSON.stringify(metadata),
        contextSource: "AGENTIC",
    };
}

async function postHttpProxy(
    guardrails: boolean,
    ingestData: boolean,
    payload: Record<string, unknown>
): Promise<Response> {
    const params = buildHttpProxyParams(guardrails, ingestData);
    const endpoint = `${DATA_INGESTION_URL}${HTTP_PROXY_PATH}?${params.toString()}`;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), TIMEOUT);

    try {
        return await fetch(endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
            signal: controller.signal,
        });
    } finally {
        clearTimeout(timeoutId);
    }
}

async function callGuardrailsValidation(
    context: CallbackContext,
    model: string,
    messages: Record<string, unknown>[]
): Promise<GuardrailsResult> {
    if (!DATA_INGESTION_URL) {
        return { allowed: true, reason: "" };
    }

    const payload = buildPayload(context, model, messages, null);
    try {
        const response = await postHttpProxy(true, false, payload);
        if (response.status !== 200) {
            console.info(
                `Guardrails validation returned HTTP ${response.status} (fail-open)`
            );
            return { allowed: true, reason: "" };
        }
        const result = (await response.json()) as Record<string, unknown>;
        return parseGuardrailsResult(result);
    } catch (e) {
        console.info(`Guardrails validation failed (fail-open): ${e}`);
        return { allowed: true, reason: "" };
    }
}

async function ingestResponsePayload(
    context: CallbackContext,
    model: string,
    messages: Record<string, unknown>[],
    responseBody: unknown,
    statusCode: number,
    logHttpError: boolean = false
): Promise<void> {
    if (!DATA_INGESTION_URL) {
        return;
    }

    const payload = buildPayload(context, model, messages, responseBody, statusCode);
    try {
        const response = await postHttpProxy(false, true, payload);
        if (logHttpError && response.status !== 200) {
            console.error(`Ingestion failed: HTTP ${response.status}`);
        }
    } catch (e) {
        console.error(`Ingestion failed: ${e}`);
    }
}

async function asyncValidateAndIngest(
    context: CallbackContext,
    model: string,
    messages: Record<string, unknown>[],
    responseDict: Record<string, unknown> | null
): Promise<void> {
    if (!DATA_INGESTION_URL) {
        return;
    }

    try {
        const payload = buildPayload(context, model, messages, responseDict);
        const response = await postHttpProxy(true, true, payload);
        if (response.status === 200) {
            const result = (await response.json()) as Record<string, unknown>;
            const { allowed, reason } = parseGuardrailsResult(result);
            if (!allowed) {
                console.info(
                    `Response flagged by guardrails (async mode, logged only): ${reason}`
                );
            }
        }
    } catch (e) {
        console.error(`Guardrails async validation error: ${e}`);
    }
}

// ---------------------------------------------------------------------------
// Public callback functions
// ---------------------------------------------------------------------------

/**
 * ADK before_model_callback — integrates Akto Guardrails pre-call validation.
 *
 * Behaviour depends on SYNC_MODE:
 *
 * - SYNC_MODE=true  (default):
 *     Sends the request to Akto's guardrails endpoint before forwarding it to
 *     the LLM. If the request is denied, a blocking LlmResponse is returned
 *     and the LLM is never called. The blocked interaction is also ingested
 *     for observability.
 *
 * - SYNC_MODE=false:
 *     Snapshots the request data in session state so that
 *     aktoAfterModelCallback can perform a combined validate-and-ingest
 *     call after the LLM responds. Returns undefined so the LLM call proceeds
 *     uninterrupted.
 *
 * Fail-open: any exception during validation allows the request through.
 */
export async function aktoBeforeModelCallback({
    context,
    request,
}: {
    context: CallbackContext;
    request: LlmRequest;
}): Promise<LlmResponse | undefined> {
    const model = request?.model || "";
    const messages = contentsToMessages(request?.contents);

    // Extract only the latest user message for guardrails validation.
    let latestUserMsg: Record<string, unknown>[] = [];
    for (const m of messages) {
        if (m.role === "user") {
            latestUserMsg = [m];
        }
    }
    if (latestUserMsg.length === 0 && messages.length > 0) {
        latestUserMsg = [messages[messages.length - 1]];
    }

    // Snapshot request data for aktoAfterModelCallback.
    context.state.set(AKTO_SNAPSHOT_KEY, {
        model,
        messages: latestUserMsg,
        blocked: false,
    } as AktoSnapshot);

    if (!SYNC_MODE) {
        return undefined;
    }

    try {
        const { allowed, reason } = await callGuardrailsValidation(
            context,
            model,
            latestUserMsg
        );
        if (!allowed) {
            // Ingest the blocked interaction before surfacing the error.
            await ingestResponsePayload(
                context,
                model,
                latestUserMsg,
                { "x-blocked-by": "Akto Proxy" },
                403
            );
            // Mark snapshot so after_model_callback skips duplicate ingestion.
            context.state.set(AKTO_SNAPSHOT_KEY, {
                model,
                messages: latestUserMsg,
                blocked: true,
            } as AktoSnapshot);
            console.info(`Request blocked by Akto Guardrails: ${reason}`);
            return makeBlockedResponse(reason);
        }
    } catch (e) {
        console.error(`Guardrails before-model error (fail-open): ${e}`);
    }

    return undefined;
}

/**
 * ADK after_model_callback — ingests or validates the completed interaction.
 *
 * Behaviour depends on SYNC_MODE:
 *
 * - SYNC_MODE=true  (default):
 *     Ingests the request + LLM response pair for observability.
 *
 * - SYNC_MODE=false:
 *     Sends a combined validate-and-ingest request. If the response is
 *     flagged by guardrails it is logged but NOT blocked (the LLM response
 *     is already being returned to the caller).
 *
 * Always returns undefined so the original LlmResponse is used unchanged.
 */
export async function aktoAfterModelCallback({
    context,
    response,
}: {
    context: CallbackContext;
    response: LlmResponse;
}): Promise<LlmResponse | undefined> {
    try {
        const snapshot = (context.state.get<AktoSnapshot>(AKTO_SNAPSHOT_KEY) || {}) as AktoSnapshot;
        const model = snapshot.model || "";
        const messages = snapshot.messages || [];

        // Skip if this request was already blocked and ingested in before_model_callback.
        if (snapshot.blocked) {
            return undefined;
        }

        const responseDict = llmResponseToDict(response);

        if (SYNC_MODE) {
            await ingestResponsePayload(
                context,
                model,
                messages,
                responseDict,
                200,
                true
            );
        } else {
            await asyncValidateAndIngest(context, model, messages, responseDict);
        }
    } catch (e) {
        console.error(`Guardrails after-model error: ${e}`);
    }

    return undefined;
}
