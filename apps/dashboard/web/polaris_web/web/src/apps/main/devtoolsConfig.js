// Shared zustand devtools options. zustand's own production guard checks
// `import.meta.env.MODE`, which is a Vite-only global — this app builds with webpack, so that
// check silently evaluates to `undefined !== "production"` and the middleware stays live (and
// keeps posting full state snapshots to the extension) in production builds too. Gate it
// explicitly instead of relying on the library default.
export const DEVTOOLS_ENABLED = process.env.NODE_ENV !== "production";

// Give every store its own named connection (otherwise all unnamed stores share one connection
// and overwrite each other's state in the DevTools panel), and let callers redact large slices
// via `stateSanitizer` so they never cross the extension's serialization boundary.
export const devtoolsOptions = (name, stateSanitizer) => ({
    enabled: DEVTOOLS_ENABLED,
    name,
    ...(stateSanitizer ? { stateSanitizer } : {}),
});
