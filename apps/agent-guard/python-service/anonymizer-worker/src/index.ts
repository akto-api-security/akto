import { Container, getContainer } from "@cloudflare/containers";

// Durable Object class for the anonymizer container. We override `fetch` to
// explicitly start the container and wait for its port before proxying — the
// SDK's default behavior surfaces the "container is not running, consider
// calling start()" error if the instance is asleep or cold-starting.
export class AnonymizerContainer extends Container {
	defaultPort = 8093;
	sleepAfter = "5m";

	override async fetch(request: Request): Promise<Response> {
		await this.startAndWaitForPorts();
		return this.containerFetch(request);
	}
}

interface Env {
	ANONYMIZER: DurableObjectNamespace<AnonymizerContainer>;
}

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		// Sticky routing keeps the spaCy model warm on one instance. If we ever
		// need more throughput, switch to per-request random instance picking.
		const container = getContainer(env.ANONYMIZER, "default");
		return container.fetch(request);
	},
};
