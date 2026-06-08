import { Container, getContainer } from "@cloudflare/containers";

// One thin JS Worker that owns BOTH agent-guard containers and routes by path:
//   /embed*  -> embedder-container   (sentence-transformers, port 8094)
//   else     -> anonymizer-container (Presidio, port 8093)
// Each container is a separate Durable Object class, so they start/sleep/scale
// independently — we only share the routing Worker. We override `fetch` to start
// the container and wait for its port before proxying; the SDK's default surfaces
// a "container is not running, consider calling start()" error when an instance
// is asleep or cold-starting.

export class AnonymizerContainer extends Container {
	defaultPort = 8093;
	sleepAfter = "5m";

	override async fetch(request: Request): Promise<Response> {
		await this.startAndWaitForPorts();
		return this.containerFetch(request);
	}
}

export class EmbedderContainer extends Container {
	defaultPort = 8094;
	sleepAfter = "5m";

	override async fetch(request: Request): Promise<Response> {
		await this.startAndWaitForPorts();
		return this.containerFetch(request);
	}
}

interface Env {
	ANONYMIZER: DurableObjectNamespace<AnonymizerContainer>;
	EMBEDDER: DurableObjectNamespace<EmbedderContainer>;
}

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		// Sticky routing ("default") keeps each container's model warm on one
		// instance. If we ever need more throughput, switch to per-request
		// random instance picking.
		const { pathname } = new URL(request.url);
		if (pathname.startsWith("/embed")) {
			return getContainer(env.EMBEDDER, "default").fetch(request);
		}
		return getContainer(env.ANONYMIZER, "default").fetch(request);
	},
};
