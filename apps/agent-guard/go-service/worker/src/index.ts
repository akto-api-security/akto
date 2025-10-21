import { Container, getContainer } from "@cloudflare/containers";
/**
 * Akto Agent Guard Engine Cloudflare Worker
 *
 * This worker handles security scanning and threat detection at the edge.
 * It uses Durable Objects with Container bindings to manage the Go service.
 */

export interface Env {
  // Durable Objects with Container binding
  AKTO_AGENT_GUARD_ENGINE_CONTAINER: DurableObjectNamespace<AktoAgentGuardEngineContainer>;
}

/**
 * Durable Object with Container binding
 * The container runs the Go service defined in the Dockerfile
 */
export class AktoAgentGuardEngineContainer extends Container {
  defaultPort = 8091; // pass requests to port 8091 in the container
  sleepAfter = "168h"; // Keep container alive for 1 week (168 hours)
  maxStartupTime = "60s"; // allow more time for container startup
  
  constructor(state: DurableObjectState, env: Env) {
    super(state, env);
    console.log(`[Container Init] Akto Agent Guard Engine Container created - ID: ${state.id.toString()}, Time: ${new Date().toISOString()}`);
    // Pass environment variables to the container
    this.envVars = {
      PORT: "8091",
      GIN_MODE: "release",
      PYTHON_SERVICE_URL: "https://akto-agent-guard-executor.billing-53a.workers.dev",
    };
  }
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    // Route handling
    switch (url.pathname) {
      case '/':
        return handleRoot(request, env);

      case '/health':
        return handleHealth(request, env);

      default:
        return handleScan(request, env);
    }
  },
};

/**
 * Handle root endpoint
 */
async function handleRoot(request: Request, env: Env): Promise<Response> {
  return new Response(JSON.stringify({
    service: 'Akto Agent Guard Engine Worker',
    version: '1.0.0',
  }), {
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

/**
 * Handle health check endpoint
 */
async function handleHealth(request: Request, env: Env): Promise<Response> {
  return new Response(JSON.stringify({
    status: 'healthy',
    timestamp: new Date().toISOString(),
  }), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

/**
 * Handle security scan endpoint
 * Routes the request to the container Durable Object
 */
async function handleScan(request: Request, env: Env): Promise<Response> {
  try {
    console.log('[Scan Request] Received scan request');

    // Get or create a Durable Object ID for the container
    const stub = getContainer(env.AKTO_AGENT_GUARD_ENGINE_CONTAINER, "main");
    console.log('[Scan Request] Got container stub');

    // Start container and wait for it to be ready before forwarding requests
    // This ensures sleepAfter behavior works correctly
    console.log('[Scan Request] Starting container and waiting for ports...');
    const startTime = Date.now();
    await stub.startAndWaitForPorts();
    const startDuration = Date.now() - startTime;
    console.log(`[Scan Request] Container started and ports ready in ${startDuration}ms`);

    // Forward the request to the Durable Object (which will forward to the container)
    const response = await stub.fetch(request);
    console.log(`[Scan Request] Response status: ${response.status}`);
    return response;
  } catch (error) {
    console.error('[Scan Request] Error:', error);
    return new Response(JSON.stringify({
      error: 'Failed to process scan request',
      message: error instanceof Error ? error.message : 'Unknown error',
    }), {
      status: 500,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }
}
