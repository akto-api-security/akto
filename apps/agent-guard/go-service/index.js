import { Container, getRandom } from "@cloudflare/containers";

const INSTANCE_COUNT = 3;

class Backend extends Container {
  defaultPort = 8080;
  sleepAfter = "2h";
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    // note: "getRandom" to be replaced with latency-aware routing in the near future
    const containerInstance = await getRandom(env.BACKEND, INSTANCE_COUNT);
    return containerInstance.fetch(request);
  },
};