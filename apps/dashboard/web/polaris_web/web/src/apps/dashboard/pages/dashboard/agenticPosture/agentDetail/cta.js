// Same shape InsightResult.Cta carries everywhere a drill builds one — route plus a flat params
// map turned into a query string, never a second URL-building convention.
//
// A CTA's own `params` (e.g. Critical alerts' "View all" -> {severity: "CRITICAL"}) has to land
// as a URL query param, not router `state` — the destination pages this app already has (e.g.
// ThreatDetectionPage.jsx's own severity filter) read their own pre-filters off `searchParams`,
// never off `location.state`. Appending here, once, is what makes a CTA's `params` do anything at
// all — passing them as `state` would have silently gone nowhere on arrival.
export function ctaHref(cta) {
    if (!cta.params) return cta.route
    const qs = new URLSearchParams(cta.params).toString()
    if (!qs) return cta.route
    return cta.route + (cta.route.includes('?') ? '&' : '?') + qs
}
