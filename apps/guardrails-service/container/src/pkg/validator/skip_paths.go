package validator

import (
	"regexp"
	"strings"

	"go.uber.org/zap"
)

// regexPrefix marks a SkipPaths spec that should be treated as a single regex
// pattern rather than a comma-separated substring list. Mirrors the FILTER_PATH
// convention used by the kafka consumer.
const regexPrefix = "regex:"

// pathSkipper decides whether a request is exempt from guardrails, driven by
// the GUARDRAILS_SKIP_PATHS env var. Matching is done against the request's
// "host+path" target (the Host header value concatenated with the path, or just
// the path when there is no Host header) so a rule can be scoped to one agent
// even when several agents expose the same path. A nil/empty spec never matches,
// so the default behaviour (guardrails apply everywhere) is unchanged.
type pathSkipper struct {
	regex     *regexp.Regexp
	substrs   []string
	rawConfig string
}

// newPathSkipper parses a GUARDRAILS_SKIP_PATHS spec. An empty spec yields a
// skipper that never matches. A "regex:" prefix compiles the remainder as a
// single regex matched against "host+path"; otherwise the spec is a
// comma-separated list, each entry matched by substring against "host+path".
// Include the host in an entry (e.g. "agent1.example.com/tool/Agent") to scope
// it to one agent, or omit it (e.g. "/tool/Agent") to match any host.
func newPathSkipper(spec string, logger *zap.Logger) *pathSkipper {
	spec = strings.TrimSpace(spec)
	ps := &pathSkipper{rawConfig: spec}
	if spec == "" {
		return ps
	}

	if strings.HasPrefix(spec, regexPrefix) {
		pattern := strings.TrimPrefix(spec, regexPrefix)
		re, err := regexp.Compile(pattern)
		if err != nil {
			logger.Error("GUARDRAILS_SKIP_PATHS: invalid regex, ignoring skip config",
				zap.String("pattern", pattern), zap.Error(err))
			return ps
		}
		ps.regex = re
		return ps
	}

	for _, part := range strings.Split(spec, ",") {
		if p := strings.TrimSpace(part); p != "" {
			ps.substrs = append(ps.substrs, p)
		}
	}
	return ps
}

// enabled reports whether any skip rule is configured.
func (ps *pathSkipper) enabled() bool {
	return ps.regex != nil || len(ps.substrs) > 0
}

// shouldSkip returns true if guardrails should be bypassed for this request.
// host is the Host header value (may be empty); path is the request path.
func (ps *pathSkipper) shouldSkip(host, path string) bool {
	target := host + path
	if ps.regex != nil {
		return ps.regex.MatchString(target)
	}
	for _, s := range ps.substrs {
		if strings.Contains(target, s) {
			return true
		}
	}
	return false
}
