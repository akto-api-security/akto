import { Container, getContainer } from "@cloudflare/containers";
import { Hono } from "hono";

/**
 * Akto Agent Guard Engine Cloudflare Worker
 *
 * This worker handles security scanning and threat detection at the edge.
 * It uses Durable Objects with Container bindings to manage the Go service.
 */

type Environment = {
  readonly AKTO_AGENT_GUARD_ENGINE_CONTAINER_DEV: DurableObjectNamespace<AktoAgentGuardEngineContainerDev>;
}

/**
 * Durable Object with Container binding
 * The container runs the Go service defined in the Dockerfile
 */
export class AktoAgentGuardEngineContainerDev extends Container {
  defaultPort = 8091; // pass requests to port 8091 in the container
  sleepAfter = "2h"; // Keep container alive for 2 hours

  // Pass environment variables to the container
  envVars = {
    PORT: "8091",
    GIN_MODE: "release",
    PYTHON_SERVICE_URL: "https://akto-agent-guard-executor-dev.billing-53a.workers.dev",
  };

  override onStart() {
    console.log(`[Container Init] Akto Agent Guard Engine Container started - Time: ${new Date().toISOString()}`);
  }

  override onStop() {
    console.log(`[Container Stop] Akto Agent Guard Engine Container stopped - Time: ${new Date().toISOString()}`);
  }

  override onError(error: unknown) {
    console.error(`[Container Error] Akto Agent Guard Engine Container error:`, error);
  }
}

// Create Hono app with proper typing for Cloudflare Workers
const app = new Hono<{
  Bindings: Environment;
}>();

// Home route
app.get("/", (c) => {
  return c.json({
    service: 'Akto Agent Guard Engine Worker',
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
      console.log('[Scan Request] Received scan request');

      // Get or create a Durable Object ID for the container
      const stub = getContainer(env.AKTO_AGENT_GUARD_ENGINE_CONTAINER_DEV, "main");
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
  },
};
