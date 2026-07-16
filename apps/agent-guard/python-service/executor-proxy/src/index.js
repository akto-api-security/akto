/**
 * akto-agent-guard-executor — reverse-proxy Worker.
 *
 * Installed devices hard-code
 *   https://akto-agent-guard-executor.billing-53a.workers.dev
 * and cannot be updated in the field. This Worker keeps that URL alive but
 * transparently forwards every request to the agent-guard service now running
 * on Azure. Deploying it under the name "akto-agent-guard-executor" (see
 * wrangler.jsonc) replaces the old Python Worker at the same URL, so callers
 * see no change.
 *
 * The Azure target is read from env.AZURE_ORIGIN (a var/secret) so it can be
 * repointed without a code edit. It must be an absolute origin, e.g.
 *   https://agent-guard.example.com
 */
export default {
  async fetch(request, env) {
    const target = env.AZURE_ORIGIN;
    if (!target) {
      return json({ error: "proxy misconfigured: AZURE_ORIGIN is not set" }, 500);
    }

    let origin;
    try {
      origin = new URL(target);
    } catch {
      return json({ error: `proxy misconfigured: AZURE_ORIGIN is not a valid URL: ${target}` }, 500);
    }

    const incoming = new URL(request.url);

    // Preserve the path + query exactly (/health, /scanners, /scan, /scan/batch,
    // ...); swap only scheme + host to the Azure origin. Ignore any path on
    // AZURE_ORIGIN itself so it stays a pure origin.
    origin.pathname = incoming.pathname;
    origin.search = incoming.search;

    // Rebuild the request against Azure, carrying over method, headers, and the
    // (possibly streamed) body. Cloudflare rewrites the Host header to the Azure
    // hostname automatically, which Azure's ingress / Application Gateway needs
    // to route the request correctly.
    const proxied = new Request(origin.toString(), request);

    // redirect: "manual" so any 3xx Location from Azure is passed straight back
    // to the caller instead of being followed inside the Worker.
    //
    // Guard the upstream call: installed devices expect the agent-guard JSON
    // contract, so on an unreachable/failing origin return a clean 502 JSON
    // rather than letting the Worker throw (which serves Cloudflare's HTML
    // error page and breaks client-side response parsing).
    try {
      return await fetch(proxied, { redirect: "manual" });
    } catch (err) {
      return json({ error: "upstream unavailable", detail: String(err) }, 502);
    }
  },
};

function json(obj, status) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json" },
  });
}
