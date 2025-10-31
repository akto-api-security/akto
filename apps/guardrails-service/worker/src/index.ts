import { Container, getContainer } from "@cloudflare/containers";

export class Backend extends Container {
  defaultPort = 8080; // pass requests to port 8080 in the container
  sleepAfter = "2h"; // only sleep a container if it hasn't gotten requests in 2 hours
  maxStartupTime = "60s"; // allow more time for container startup

  constructor(state: DurableObjectState, env: Env) {
    super(state, env);

    // Validate required environment variables
    const requiredVars = [
      'DATABASE_ABSTRACTOR_SERVICE_URL',
      'DATABASE_ABSTRACTOR_SERVICE_TOKEN',
      'THREAT_BACKEND_URL',
      'THREAT_BACKEND_TOKEN'
    ];

    const missing = requiredVars.filter(v => !env[v as keyof Env]);
    if (missing.length > 0) {
      throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
    }

    // Pass environment variables to the container
    this.envVars = {
      SERVER_PORT: env.SERVER_PORT || "8080",
      DATABASE_ABSTRACTOR_SERVICE_URL: env.DATABASE_ABSTRACTOR_SERVICE_URL!,
      DATABASE_ABSTRACTOR_SERVICE_TOKEN: env.DATABASE_ABSTRACTOR_SERVICE_TOKEN!,
      AGENT_GUARD_ENGINE_URL: env.AGENT_GUARD_ENGINE_URL || "",
      THREAT_BACKEND_URL: env.THREAT_BACKEND_URL!,
      THREAT_BACKEND_TOKEN: env.THREAT_BACKEND_TOKEN!,
      LOG_LEVEL: env.LOG_LEVEL || "info",
      GIN_MODE: env.GIN_MODE || "release",
    };
  }
}

export interface Env {
  BACKEND: DurableObjectNamespace<Backend>;
  // Environment variables
  SERVER_PORT?: string;
  DATABASE_ABSTRACTOR_SERVICE_URL?: string;
  DATABASE_ABSTRACTOR_SERVICE_TOKEN?: string;
  AGENT_GUARD_ENGINE_URL?: string;
  THREAT_BACKEND_URL?: string;
  THREAT_BACKEND_TOKEN?: string;
  LOG_LEVEL?: string;
  GIN_MODE?: string;
}

export default {
  async fetch(request: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
    try {
      const url = new URL(request.url);

      // Health check endpoint
      if (url.pathname === "/health") {
        return new Response("OK", { status: 200 });
      }

      // Get container instance and forward request
      const stub = getContainer(env.BACKEND, "main");
      await stub.startAndWaitForPorts();
      const response = await stub.fetch(request);
      return response;
    } catch (error) {
      console.error("Error handling request:", error);
      return new Response(
        JSON.stringify({
          error: "Internal Server Error",
          message: error instanceof Error ? error.message : "Unknown error"
        }),
        {
          status: 500,
          headers: { "Content-Type": "application/json" }
        }
      );
    }
  },
};
