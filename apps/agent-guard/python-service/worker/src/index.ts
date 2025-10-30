import { Container, getContainer } from "@cloudflare/containers";
/**
 * Akto Agent Guard Executor Cloudflare Worker
 *
 * This worker handles Python-based security scanning and execution at the edge.
 * It uses Durable Objects with Container bindings to manage the Python service.
 */

export interface Env {
  // Durable Objects with Container binding
  AKTO_AGENT_GUARD_EXECUTOR_CONTAINER: DurableObjectNamespace<AktoAgentGuardExecutorContainer>;
}

/**
 * Durable Object with Container binding
 * The container runs the Python service defined in the Dockerfile
 */
export class AktoAgentGuardExecutorContainer extends Container {
  defaultPort = 8092; // pass requests to port 8092 in the container
  sleepAfter = "168h"; // Keep container alive for 1 week (168 hours)
  maxStartupTime = "120s"; // allow more time for Python container startup (model loading)

  constructor(state: DurableObjectState, env: Env) {
    super(state, env);
    console.log(`[Container Init] Akto Agent Guard Executor Container created - ID: ${state.id.toString()}, Time: ${new Date().toISOString()}`);
    // Pass environment variables to the container
    this.envVars = {
      PYTHONUNBUFFERED: "1",
      PORT: "8092",
      HF_HOME: "/app/.cache/huggingface"
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
        return handleExecute(request, env);
    }
  },
};

/**
 * Handle root endpoint
 */
async function handleRoot(request: Request, env: Env): Promise<Response> {
  return new Response(JSON.stringify({
    service: 'Akto Agent Guard Executor Worker',
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
 * Handle execution endpoint
 * Routes the request to the container Durable Object based on scanner_name
 */
async function handleExecute(request: Request, env: Env): Promise<Response> {
  try {
    // Try to extract scanner_name from request body for instance routing
    let scannerName = 'default-executor';

    try {
      if (request.method === 'POST' && request.headers.get('content-type')?.includes('application/json')) {
        const body = await request.clone().json() as any;
        scannerName = body.scanner_name || scannerName;
      }
    } catch (e) {
      // If body parsing fails, use default scanner name
    }

    // Use scanner_name as the instance ID for consistent routing
    // This ensures requests for the same scanner go to the same container instance
    const stub = getContainer(env.AKTO_AGENT_GUARD_EXECUTOR_CONTAINER, scannerName);

    // Start container and wait for it to be ready before forwarding requests
    // This ensures sleepAfter behavior works correctly
    await stub.startAndWaitForPorts();

    // Forward the request to the Durable Object (which will forward to the container)
    const response = await stub.fetch(request);
    return response;
  } catch (error) {
    return new Response(JSON.stringify({
      error: 'Failed to process execution request',
      message: error instanceof Error ? error.message : 'Unknown error',
    }), {
      status: 500,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }
}
