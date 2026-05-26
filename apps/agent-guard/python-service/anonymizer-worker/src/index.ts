import { Container, getContainer } from "@cloudflare/containers";

// Durable Object class for the anonymizer container. The class itself does no
// work — it just declares the container's listening port. All requests to this
// worker are forwarded to the container as-is.
export class AnonymizerContainer extends Container {
	defaultPort = 8093;
	sleepAfter = "5m";
}

interface Env {
	ANONYMIZER: DurableObjectNamespace<AnonymizerContainer>;
}

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		// Sticky routing per session keeps spaCy model warm on one instance.
		// "default" here means a single shared instance; if we ever need more
		// throughput, switch to per-request random instance picking.
		const container = getContainer(env.ANONYMIZER, "default");
		return container.fetch(request);
	},
};
