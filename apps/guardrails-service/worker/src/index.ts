import { Container, getContainer } from "@cloudflare/containers";

export class Backend extends Container {
  defaultPort = 8080; // pass requests to port 8080 in the container
  sleepAfter = "2h"; // only sleep a container if it hasn't gotten requests in 2 hours
  maxStartupTime = "60s"; // allow more time for container startup
  
  constructor(state: DurableObjectState, env: Env) {
    super(state, env);
    // Pass environment variables to the container
    this.envVars = {
      SERVER_PORT: "8080",
      DATABASE_ABSTRACTOR_SERVICE_URL: "https://cyborg.akto.io",
      DATABASE_ABSTRACTOR_SERVICE_TOKEN: "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoiaW52aXRlX3VzZXIiLCJhY2NvdW50SWQiOjE3MjY2MTU0NzAsImlhdCI6MTc1MDM0NDU5MSwiZXhwIjoxNzY2MTU1NzkxfQ.gV7r0q5q7DhRuerRS9XwbrnITtxfxHqnY-9MIVfji67JWwbjdWUF5igFU5DQDNdDsmLFCNyixBW21Mf99tVEZPSlxcHZfPDKH-Epa3TLy_0Z3Ale82_kowkOH1-WFo9-2CZJ59vs1sue7HoflXxbiag4Yx8nKnjD92tcJq-YVByezV8MTSTKYzojyxlykNzO-6OLDdbU9DqofkGuD8ct6x47erawsvpzcLmyR0UttTNVETLE-ULwkVS0YipKVisckrRjy-BqY_dVBXSnA_Yo9_fUBKi__tdNGcNvgTf3d40ISwq58kcjujAq4VNRJeP6nyNtHQa5oFmo2SCOI1pIdA",
      AGENT_GUARD_ENGINE_URL: "https://akto-agent-guard-engine.billing-53a.workers.dev",
      THREAT_BACKEND_URL: "https://tbs.akto.io",
      THREAT_BACKEND_TOKEN: "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoiaW52aXRlX3VzZXIiLCJhY2NvdW50SWQiOjE3MjY2MTU0NzAsImlhdCI6MTc1MDM0NDU5MSwiZXhwIjoxNzY2MTU1NzkxfQ.gV7r0q5q7DhRuerRS9XwbrnITtxfxHqnY-9MIVfji67JWwbjdWUF5igFU5DQDNdDsmLFCNyixBW21Mf99tVEZPSlxcHZfPDKH-Epa3TLy_0Z3Ale82_kowkOH1-WFo9-2CZJ59vs1sue7HoflXxbiag4Yx8nKnjD92tcJq-YVByezV8MTSTKYzojyxlykNzO-6OLDdbU9DqofkGuD8ct6x47erawsvpzcLmyR0UttTNVETLE-ULwkVS0YipKVisckrRjy-BqY_dVBXSnA_Yo9_fUBKi__tdNGcNvgTf3d40ISwq58kcjujAq4VNRJeP6nyNtHQa5oFmo2SCOI1pIdA",
      LOG_LEVEL: "info",
      GIN_MODE: "release",
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
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/health") {
      return new Response("OK", { status: 200 });
    }
    
    const stub = getContainer(env.BACKEND, "main");
    await stub.startAndWaitForPorts();
    const response = await stub.fetch(request);
    return response;
  },
};
