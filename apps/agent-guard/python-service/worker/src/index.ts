import { Container, getContainer } from "@cloudflare/containers";
import { Hono } from "hono";

/**
 * Akto Agent Guard Executor Cloudflare Worker
 *
 * This worker handles Python-based security scanning and execution at the edge.
 * It uses Durable Objects with Container bindings to manage the Python service.
 */

type Environment = {
  readonly AKTO_AGENT_GUARD_EXECUTOR_CONTAINER_DEV: DurableObjectNamespace<AktoAgentGuardExecutorContainerDev>;
}

/**
 * Durable Object with Container binding
 * The container runs the Python service defined in the Dockerfile
 */
export class AktoAgentGuardExecutorContainerDev extends Container {
  defaultPort = 8092; // pass requests to port 8092 in the container
  sleepAfter = "2h"; // Keep container alive for 2 hours

  // Pass environment variables to the container
  envVars = {
    PYTHONUNBUFFERED: "1",
    PORT: "8092",
    HF_HOME: "/app/.cache/huggingface"
  };

  override onStart() {
    console.log(`[Container Init] Akto Agent Guard Executor Container started - Time: ${new Date().toISOString()}`);
  }

  override onStop() {
    console.log(`[Container Stop] Akto Agent Guard Executor Container stopped - Time: ${new Date().toISOString()}`);
  }

  override onError(error: unknown) {
    console.error(`[Container Error] Akto Agent Guard Executor Container error:`, error);
  }
}

// Create Hono app with proper typing for Cloudflare Workers
const app = new Hono<{
  Bindings: Environment;
}>();

// Home route
app.get("/", (c) => {
  return c.json({
    service: 'Akto Agent Guard Executor Worker',
    version: '1.0.0',
  });
});

// Health check endpoint
app.get("/health", (c) => {
  return c.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
  });
});

export default {
  async fetch(request: Request, env: Environment, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    // Handle root and health via Hono
    if (url.pathname === '/' || url.pathname === '/health') {
      return app.fetch(request, env, ctx);
    }

    // All other requests go to the container
    try {
      console.log('[Execute Request] Received execution request');

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
      const stub = getContainer(env.AKTO_AGENT_GUARD_EXECUTOR_CONTAINER_DEV, scannerName);
      console.log(`[Execute Request] Got container stub for scanner: ${scannerName}`);

      // Start container and wait for it to be ready before forwarding requests
      // This ensures sleepAfter behavior works correctly
      console.log('[Execute Request] Starting container and waiting for ports...');
      const startTime = Date.now();
      await stub.startAndWaitForPorts();
      const startDuration = Date.now() - startTime;
      console.log(`[Execute Request] Container started and ports ready in ${startDuration}ms`);

      // Forward the request to the Durable Object (which will forward to the container)
      const response = await stub.fetch(request);
      console.log(`[Execute Request] Response status: ${response.status}`);
      return response;
    } catch (error) {
      console.error('[Execute Request] Error:', error);
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
  },
};
