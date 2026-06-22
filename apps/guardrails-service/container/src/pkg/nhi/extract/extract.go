// Package extract holds source-agnostic credential extractors.
//
// An ExtractorFunc parses raw bytes in a specific format (Claude Desktop MCP
// config JSON, AWS credentials INI, gcloud OAuth JSON, ...) and returns zero
// or more Findings. It knows nothing about where the bytes came from — the
// same MCPConfig extractor works for content pulled off a file on disk
// (endpoint-shield) or pasted from a browser localStorage key (browser
// extension), as long as the bytes look like the format.
//
// Add a new credential format by writing one function that returns
// []Finding. Any source can then use it.
package extract

import (
	"bufio"
	"bytes"
	"encoding/base64"
	"encoding/json"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
)

// Finding is the output of an extractor: one secret found in one piece of raw
// material.
//
// SECURITY: Secret carries the *raw* credential value. It exists only so the
// publishing layer can compute Redact()'s outputs. NEVER log it, persist it,
// or pass it to another service. The publisher consumes it and drops it.
type Finding struct {
	// IdentityName is the display label, scoped to the source it was found in.
	// Convention: "{owner}.{field}" so two findings from different owners don't
	// collide (e.g. "github-mcp.GITHUB_TOKEN", "context7.API_KEY").
	IdentityName string

	// IdentityType: "API Key" | "Bearer Token" | "OAuth Token" |
	// "Service Account" | "Machine Identity".
	IdentityType string

	// AgentName overrides the source-level default agent name (e.g. an MCP
	// config file knows the server name per-entry, while a flat .env file
	// inherits from the source). Empty string means "use source default".
	AgentName string

	// AgentType: "MCP Server" | "LLM" | "AI Agent" | "CLI Tool".
	AgentType string

	// RiskLevel: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW".
	RiskLevel string

	// SourceType lets an extractor override the source-level default, useful
	// when one source produces findings of multiple SourceTypes (e.g. a single
	// MCP config file contains both env vars and Authorization headers).
	SourceType string

	// Secret is the raw credential value. Used only to compute Redact() outputs;
	// must never escape the publishing pipeline.
	Secret string

	// ExpiryDate is a Unix timestamp extracted from the credential itself
	// (JWT exp claim, OAuth expires_at, SSO cache expiresAt). 0 = unknown.
	ExpiryDate int

	// Metadata is extractor-specific extra context (profile name, MCP server
	// name, header name, registry URL, ...). Persisted on the NhiIdentity.
	Metadata map[string]any
}

// ExtractorFunc parses one piece of raw material and returns its findings.
// origin is a free-form locator (file path / URL / cache key) that extractors
// can use for context — most ignore it. Implementations must be defensive:
// malformed input returns nil, never panic.
type ExtractorFunc func(origin string, content []byte) []Finding

// ============================================================================
// shared helpers
// ============================================================================

// credKeyRE matches env-var / config-key names that conventionally hold secrets.
// Used as a fallback when the file format doesn't have a stable schema.
var credKeyRE = regexp.MustCompile(`(?i)(token|key|secret|password|credential|api[-_]?key|auth)`)

func isLikelyCredentialName(k string) bool {
	if strings.EqualFold(k, "type") || strings.EqualFold(k, "username") || strings.EqualFold(k, "user") {
		return false
	}
	return credKeyRE.MatchString(k)
}

func isLikelyCredentialValue(v string) bool {
	v = strings.TrimSpace(v)
	return len(v) >= 12 && !strings.ContainsAny(v, " \t\n")
}

func splitKV(line, sep string) (string, string, bool) {
	idx := strings.Index(line, sep)
	if idx <= 0 {
		return "", "", false
	}
	return strings.TrimSpace(line[:idx]), line[idx+1:], true
}

// classifyRisk is a tiny heuristic for risk level when the extractor itself
// doesn't already know better. Keep it conservative — under-classifying is
// safer than alarming on every API key.
func classifyRisk(keyName, value string) string {
	lk := strings.ToLower(keyName)
	switch {
	case strings.Contains(lk, "secret"),
		strings.Contains(lk, "private"),
		strings.HasPrefix(value, "AKIA"),
		strings.HasPrefix(value, "ASIA"):
		return "CRITICAL"
	case strings.Contains(lk, "token"),
		strings.Contains(lk, "password"):
		return "HIGH"
	default:
		return "MEDIUM"
	}
}

// classifyIdentityType returns the credential category based on the key name
// and raw value. Callers that already know the exact type (e.g. Authorization
// header → "Bearer Token") should set IdentityType directly instead.
func classifyIdentityType(keyName, value string) string {
	lk := strings.ToLower(keyName)
	switch {
	case strings.Contains(lk, "oauth") || strings.Contains(lk, "refresh_token"):
		return "OAuth Token"
	case strings.Contains(lk, "access_token") || strings.HasPrefix(value, "ya29."):
		return "OAuth Token"
	case strings.HasPrefix(value, "eyJ"):
		return "Bearer Token"
	default:
		return "API Key"
	}
}

// extractJWTExpiry decodes the exp claim from a JWT without verifying the
// signature. Returns 0 if the token is not a JWT or carries no exp.
func extractJWTExpiry(token string) int {
	parts := strings.SplitN(token, ".", 3)
	if len(parts) != 3 {
		return 0
	}
	decoded, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return 0
	}
	var claims struct {
		Exp float64 `json:"exp"`
	}
	if err := json.Unmarshal(decoded, &claims); err != nil {
		return 0
	}
	return int(claims.Exp)
}

// parseUnixExpiry tries common time formats used by OAuth/SSO systems and
// returns the value as a Unix timestamp. Returns 0 on failure.
func parseUnixExpiry(s string) int {
	if s == "" {
		return 0
	}
	for _, layout := range []string{
		time.RFC3339,
		"2006-01-02T15:04:05UTC",
		"2006-01-02T15:04:05Z07:00",
	} {
		if t, err := time.Parse(layout, s); err == nil {
			return int(t.Unix())
		}
	}
	return 0
}

// ============================================================================
// MCP server config (Claude Desktop / Claude Code / Cursor / Windsurf / Cline)
// ============================================================================

type mcpEnvelope struct {
	MCPServers map[string]mcpServerEntry `json:"mcpServers"`
}

type mcpServerEntry struct {
	Command string            `json:"command"`
	Args    []string          `json:"args"`
	Env     map[string]string `json:"env"`
	Headers map[string]string `json:"headers"`
	URL     string            `json:"url"`
}

// MCPConfig pulls secrets out of an MCP host config. For each declared
// mcpServer, every env-var value whose key looks credential-shaped becomes a
// Finding, plus any Authorization header.
//
// Works for: claude_desktop_config.json, .cursor/mcp.json,
// windsurf/mcp_config.json, cline_mcp_settings.json.
func MCPConfig(_ string, content []byte) []Finding {
	var env mcpEnvelope
	if err := json.Unmarshal(content, &env); err != nil {
		return nil
	}
	return extractMCPServers(env.MCPServers)
}

// ClaudeCodeConfig handles ~/.claude.json which stores MCP servers per-project
// under projects.<path>.mcpServers rather than at the top level.
func ClaudeCodeConfig(_ string, content []byte) []Finding {
	var cfg struct {
		MCPServers map[string]mcpServerEntry `json:"mcpServers"`
		Projects   map[string]struct {
			MCPServers map[string]mcpServerEntry `json:"mcpServers"`
		} `json:"projects"`
	}
	if err := json.Unmarshal(content, &cfg); err != nil {
		return nil
	}
	var out []Finding
	out = append(out, extractMCPServers(cfg.MCPServers)...)
	for _, proj := range cfg.Projects {
		out = append(out, extractMCPServers(proj.MCPServers)...)
	}
	return out
}

func extractMCPServers(servers map[string]mcpServerEntry) []Finding {
	var out []Finding
	for serverName, srv := range servers {
		for k, v := range srv.Env {
			if !isLikelyCredentialName(k) || !isLikelyCredentialValue(v) {
				continue
			}
			out = append(out, Finding{
				IdentityName: serverName + "." + k,
				IdentityType: "API Key",
				RiskLevel:    classifyRisk(k, v),
				Secret:       v,
				Metadata:     map[string]any{"mcpServer": serverName, "envVar": k},
			})
		}
		for k, v := range srv.Headers {
			if !strings.EqualFold(k, "authorization") {
				continue
			}
			parts := strings.Fields(v)
			tok := v
			scheme := ""
			if len(parts) == 2 {
				scheme = parts[0]
				tok = parts[1]
			}
			if !isLikelyCredentialValue(tok) {
				continue
			}
			identityType := "API Key" // raw token with no scheme prefix
			if strings.EqualFold(scheme, "bearer") {
				identityType = "Bearer Token"
			} else if strings.EqualFold(scheme, "basic") {
				identityType = "Basic Auth"
			}
			out = append(out, Finding{
				IdentityName: serverName + ".Authorization",
				IdentityType: identityType,
				RiskLevel:    "HIGH",
				Secret:       tok,
				ExpiryDate:   extractJWTExpiry(tok),
				Metadata:     map[string]any{"mcpServer": serverName, "header": "Authorization", "scheme": scheme},
			})
		}
	}
	return out
}

// ============================================================================
// AWS shared credentials (INI)
// ============================================================================

// AWSCredentials walks ~/.aws/credentials and emits one finding per
// (profile, secret-key) pair. Skips non-secret keys like region or output.
func AWSCredentials(_ string, content []byte) []Finding {
	var out []Finding
	profile := "default"
	scanner := bufio.NewScanner(bytes.NewReader(content))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, ";") {
			continue
		}
		if strings.HasPrefix(line, "[") && strings.HasSuffix(line, "]") {
			profile = strings.TrimSpace(line[1 : len(line)-1])
			profile = strings.TrimPrefix(profile, "profile ")
			continue
		}
		k, v, ok := splitKV(line, "=")
		if !ok {
			continue
		}
		k = strings.ToLower(strings.TrimSpace(k))
		v = strings.TrimSpace(v)
		switch k {
		case "aws_access_key_id":
			out = append(out, Finding{
				IdentityName: profile + ".aws_access_key_id",
				IdentityType: "API Key",
				RiskLevel:    "HIGH",
				Secret:       v,
				Metadata:     map[string]any{"profile": profile, "field": "access_key_id"},
			})
		case "aws_secret_access_key":
			out = append(out, Finding{
				IdentityName: profile + ".aws_secret_access_key",
				IdentityType: "API Key",
				RiskLevel:    "CRITICAL",
				Secret:       v,
				Metadata:     map[string]any{"profile": profile, "field": "secret_access_key"},
			})
		case "aws_session_token":
			out = append(out, Finding{
				IdentityName: profile + ".aws_session_token",
				IdentityType: "API Key",
				RiskLevel:    "MEDIUM",
				Secret:       v,
				Metadata:     map[string]any{"profile": profile, "field": "session_token"},
			})
		}
	}
	return out
}

// ============================================================================
// GCP application-default credentials (JSON)
// ============================================================================

// GCPCredentials handles both shapes the gcloud SDK can write:
//   - service_account: identity = client_email, secret = private_key
//   - authorized_user (OAuth): identity = client_id, secret = refresh_token
func GCPCredentials(_ string, content []byte) []Finding {
	var doc map[string]any
	if err := json.Unmarshal(content, &doc); err != nil {
		return nil
	}
	credType, _ := doc["type"].(string)
	switch credType {
	case "service_account":
		email, _ := doc["client_email"].(string)
		key, _ := doc["private_key"].(string)
		if email == "" || key == "" {
			return nil
		}
		project, _ := doc["project_id"].(string)
		return []Finding{{
			IdentityName: email,
			IdentityType: "Service Account",
			RiskLevel:    "CRITICAL",
			Secret:       key,
			Metadata:     map[string]any{"projectId": project, "type": "service_account"},
		}}
	case "authorized_user":
		cid, _ := doc["client_id"].(string)
		rt, _ := doc["refresh_token"].(string)
		if cid == "" || rt == "" {
			return nil
		}
		return []Finding{{
			IdentityName: cid,
			IdentityType: "API Key",
			RiskLevel:    "HIGH",
			Secret:       rt,
			Metadata:     map[string]any{"type": "authorized_user"},
		}}
	}
	return nil
}

// ============================================================================
// Azure CLI access tokens (JSON array)
// ============================================================================

func AzureTokens(_ string, content []byte) []Finding {
	var arr []map[string]any
	if err := json.Unmarshal(content, &arr); err != nil {
		return nil
	}
	var out []Finding
	for _, entry := range arr {
		access, _ := entry["accessToken"].(string)
		if access == "" {
			continue
		}
		who, _ := entry["userId"].(string)
		if who == "" {
			who, _ = entry["servicePrincipalId"].(string)
		}
		if who == "" {
			who = "azure-token"
		}
		out = append(out, Finding{
			IdentityName: who,
			IdentityType: "API Key",
			RiskLevel:    "HIGH",
			Secret:       access,
			Metadata: map[string]any{
				"tokenType": entry["tokenType"],
				"resource":  entry["resource"],
			},
		})
	}
	return out
}

// ============================================================================
// kubeconfig (YAML)
// ============================================================================

type kubeConfig struct {
	Users []struct {
		Name string `yaml:"name"`
		User struct {
			Token                 string `yaml:"token"`
			ClientCertificateData string `yaml:"client-certificate-data"`
			ClientKeyData         string `yaml:"client-key-data"`
		} `yaml:"user"`
	} `yaml:"users"`
}

// KubeConfig emits one Finding per user with a token or client-key. Cert data
// is captured separately because rotation cadence is different.
func KubeConfig(_ string, content []byte) []Finding {
	var k kubeConfig
	if err := yaml.Unmarshal(content, &k); err != nil {
		return nil
	}
	var out []Finding
	for _, u := range k.Users {
		if u.User.Token != "" {
			out = append(out, Finding{
				IdentityName: u.Name + ".token",
				IdentityType: "Service Account",
				RiskLevel:    "HIGH",
				Secret:       u.User.Token,
				Metadata:     map[string]any{"user": u.Name, "field": "token"},
			})
		}
		if u.User.ClientKeyData != "" {
			out = append(out, Finding{
				IdentityName: u.Name + ".client-key",
				IdentityType: "Machine Identity",
				RiskLevel:    "CRITICAL",
				Secret:       u.User.ClientKeyData,
				Metadata:     map[string]any{"user": u.Name, "field": "client-key-data"},
			})
		}
	}
	return out
}

// ============================================================================
// Docker config (JSON, base64 auth)
// ============================================================================

func DockerConfig(_ string, content []byte) []Finding {
	var doc struct {
		Auths map[string]struct {
			Auth          string `json:"auth"`
			IdentityToken string `json:"identitytoken"`
		} `json:"auths"`
	}
	if err := json.Unmarshal(content, &doc); err != nil {
		return nil
	}
	var out []Finding
	for registry, a := range doc.Auths {
		if a.IdentityToken != "" {
			out = append(out, Finding{
				IdentityName: registry + ".identitytoken",
				IdentityType: "API Key",
				RiskLevel:    "HIGH",
				Secret:       a.IdentityToken,
				Metadata:     map[string]any{"registry": registry, "field": "identitytoken"},
			})
			continue
		}
		if a.Auth == "" {
			continue
		}
		decoded, err := base64.StdEncoding.DecodeString(a.Auth)
		if err != nil {
			continue
		}
		parts := strings.SplitN(string(decoded), ":", 2)
		if len(parts) != 2 {
			continue
		}
		out = append(out, Finding{
			IdentityName: registry + "." + parts[0],
			IdentityType: "API Key",
			RiskLevel:    "HIGH",
			Secret:       parts[1],
			Metadata:     map[string]any{"registry": registry, "user": parts[0]},
		})
	}
	return out
}

// ============================================================================
// gh CLI hosts.yml (YAML)
// ============================================================================

type ghHosts map[string]struct {
	OAuthToken string `yaml:"oauth_token"`
	User       string `yaml:"user"`
	GitProto   string `yaml:"git_protocol"`
}

func GHHosts(_ string, content []byte) []Finding {
	var hosts ghHosts
	if err := yaml.Unmarshal(content, &hosts); err != nil {
		return nil
	}
	var out []Finding
	for host, h := range hosts {
		if h.OAuthToken == "" {
			continue
		}
		identity := host
		if h.User != "" {
			identity = host + "." + h.User
		}
		out = append(out, Finding{
			IdentityName: identity,
			IdentityType: "OAuth Token",
			RiskLevel:    "HIGH",
			Secret:       h.OAuthToken,
			ExpiryDate:   extractJWTExpiry(h.OAuthToken),
			Metadata:     map[string]any{"host": host, "user": h.User},
		})
	}
	return out
}

// ============================================================================
// git credentials file (URL-encoded)
// ============================================================================

// GitCredentials parses lines like:
//
//	https://user:token@github.com
//
// One Finding per line.
func GitCredentials(_ string, content []byte) []Finding {
	var out []Finding
	scanner := bufio.NewScanner(bytes.NewReader(content))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		u, err := url.Parse(line)
		if err != nil || u.User == nil {
			continue
		}
		pw, ok := u.User.Password()
		if !ok || pw == "" {
			continue
		}
		out = append(out, Finding{
			IdentityName: u.Host + "." + u.User.Username(),
			IdentityType: "API Key",
			RiskLevel:    "HIGH",
			Secret:       pw,
			Metadata:     map[string]any{"host": u.Host, "user": u.User.Username()},
		})
	}
	return out
}

// ============================================================================
// .npmrc (registry auth tokens)
// ============================================================================

// Npmrc captures lines like:
//
//	//registry.npmjs.org/:_authToken=abc123
//	_authToken=abc123
func Npmrc(_ string, content []byte) []Finding {
	var out []Finding
	scanner := bufio.NewScanner(bytes.NewReader(content))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, ";") {
			continue
		}
		k, v, ok := splitKV(line, "=")
		if !ok || !strings.Contains(strings.ToLower(k), "authtoken") {
			continue
		}
		registry := "default"
		if idx := strings.Index(k, "//"); idx >= 0 {
			rest := k[idx+2:]
			if end := strings.Index(rest, "/"); end > 0 {
				registry = rest[:end]
			}
		}
		out = append(out, Finding{
			IdentityName: registry + "._authToken",
			IdentityType: "API Key",
			RiskLevel:    "MEDIUM",
			Secret:       strings.TrimSpace(v),
			Metadata:     map[string]any{"registry": registry},
		})
	}
	return out
}

// ============================================================================
// HuggingFace token (raw text file)
// ============================================================================

func HuggingFaceToken(_ string, content []byte) []Finding {
	tok := strings.TrimSpace(string(content))
	if tok == "" {
		return nil
	}
	return []Finding{{
		IdentityName: "huggingface_token",
		IdentityType: "API Key",
		RiskLevel:    "MEDIUM",
		Secret:       tok,
	}}
}

// ============================================================================
// OpenAI / Codex auth.json (small JSON, one or two keys)
// ============================================================================

func OpenAIAuth(_ string, content []byte) []Finding {
	var doc map[string]any
	if err := json.Unmarshal(content, &doc); err != nil {
		return nil
	}
	var out []Finding
	for k, v := range doc {
		s, ok := v.(string)
		if !ok || !isLikelyCredentialName(k) || !isLikelyCredentialValue(s) {
			continue
		}
		out = append(out, Finding{
			IdentityName: k,
			IdentityType: classifyIdentityType(k, s),
			RiskLevel:    classifyRisk(k, s),
			Secret:       s,
			ExpiryDate:   extractJWTExpiry(s),
		})
	}
	return out
}

// ============================================================================
// .env / KEY=VALUE flat files
// ============================================================================

// DotEnv parses shell-style KEY=VALUE files (e.g. ~/.continue/.env,
// ~/.gemini/.env, any per-tool secrets file that uses this format).
// Lines starting with # are comments; quoted values are unquoted.
func DotEnv(_ string, content []byte) []Finding {
	var out []Finding
	scanner := bufio.NewScanner(bytes.NewReader(content))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		k, v, ok := splitKV(line, "=")
		if !ok {
			continue
		}
		v = strings.TrimSpace(v)
		v = strings.Trim(v, `"'`)
		if !isLikelyCredentialName(k) || !isLikelyCredentialValue(v) {
			continue
		}
		out = append(out, Finding{
			IdentityName: k,
			IdentityType: classifyIdentityType(k, v),
			RiskLevel:    classifyRisk(k, v),
			Secret:       v,
			ExpiryDate:   extractJWTExpiry(v),
			Metadata:     map[string]any{"envVar": k},
		})
	}
	return out
}

// ============================================================================
// Flat YAML credential maps (Goose secrets.yaml, Aider .aider.conf.yml)
// ============================================================================

// FlatYAMLCredentials handles YAML files whose top level is a map of
// KEY: value pairs where some keys are credential-shaped. Covers:
//   - ~/.config/goose/secrets.yaml  (OPENAI_API_KEY, ANTHROPIC_API_KEY, …)
//   - ~/.aider.conf.yml             (openai-api-key, anthropic-api-key, …)
func FlatYAMLCredentials(_ string, content []byte) []Finding {
	var doc map[string]any
	if err := yaml.Unmarshal(content, &doc); err != nil {
		return nil
	}
	var out []Finding
	for k, v := range doc {
		s, ok := v.(string)
		if !ok {
			continue
		}
		if !isLikelyCredentialName(k) || !isLikelyCredentialValue(s) {
			continue
		}
		out = append(out, Finding{
			IdentityName: k,
			IdentityType: classifyIdentityType(k, s),
			RiskLevel:    classifyRisk(k, s),
			Secret:       s,
			ExpiryDate:   extractJWTExpiry(s),
			Metadata:     map[string]any{"configKey": k},
		})
	}
	return out
}

// ============================================================================
// Claude Code credentials.json (OAuth tokens)
// ============================================================================

// ClaudeCredentials extracts the OAuth access / refresh tokens that
// Claude Code stores at ~/.claude/credentials.json after login.
func ClaudeCredentials(_ string, content []byte) []Finding {
	var doc struct {
		AccessToken  string `json:"accessToken"`
		RefreshToken string `json:"refreshToken"`
		ExpiresAt    any    `json:"expiresAt"`
		OAuthToken   string `json:"oauth_token"`
		Token        string `json:"token"`
	}
	if err := json.Unmarshal(content, &doc); err != nil {
		return nil
	}
	var out []Finding
	if doc.AccessToken != "" && isLikelyCredentialValue(doc.AccessToken) {
		out = append(out, Finding{
			IdentityName: "claude_code.accessToken",
			IdentityType: "OAuth Token",
			RiskLevel:    "HIGH",
			Secret:       doc.AccessToken,
			ExpiryDate:   extractJWTExpiry(doc.AccessToken),
			Metadata:     map[string]any{"field": "accessToken"},
		})
	}
	if doc.RefreshToken != "" && isLikelyCredentialValue(doc.RefreshToken) {
		out = append(out, Finding{
			IdentityName: "claude_code.refreshToken",
			IdentityType: "OAuth Token",
			RiskLevel:    "HIGH",
			Secret:       doc.RefreshToken,
			ExpiryDate:   extractJWTExpiry(doc.RefreshToken),
			Metadata:     map[string]any{"field": "refreshToken"},
		})
	}
	if doc.OAuthToken != "" && isLikelyCredentialValue(doc.OAuthToken) {
		out = append(out, Finding{
			IdentityName: "claude_code.oauth_token",
			IdentityType: "OAuth Token",
			RiskLevel:    "HIGH",
			Secret:       doc.OAuthToken,
			ExpiryDate:   extractJWTExpiry(doc.OAuthToken),
			Metadata:     map[string]any{"field": "oauth_token"},
		})
	}
	if doc.Token != "" && isLikelyCredentialValue(doc.Token) {
		out = append(out, Finding{
			IdentityName: "claude_code.token",
			IdentityType: "Bearer Token",
			RiskLevel:    "HIGH",
			Secret:       doc.Token,
			ExpiryDate:   extractJWTExpiry(doc.Token),
			Metadata:     map[string]any{"field": "token"},
		})
	}
	return out
}

// ============================================================================
// GitHub Copilot hosts.json (JSON, not YAML like gh CLI)
// ============================================================================

// GitHubCopilotHosts handles ~/.config/github-copilot/hosts.json whose
// top-level keys are hostnames mapping to {oauth_token, user, …}.
func GitHubCopilotHosts(_ string, content []byte) []Finding {
	var doc map[string]struct {
		OAuthToken string `json:"oauth_token"`
		User       string `json:"user"`
	}
	if err := json.Unmarshal(content, &doc); err != nil {
		return nil
	}
	var out []Finding
	for host, h := range doc {
		if h.OAuthToken == "" || !isLikelyCredentialValue(h.OAuthToken) {
			continue
		}
		name := host
		if h.User != "" {
			name = host + "." + h.User
		}
		out = append(out, Finding{
			IdentityName: name,
			IdentityType: "OAuth Token",
			RiskLevel:    "HIGH",
			Secret:       h.OAuthToken,
			ExpiryDate:   extractJWTExpiry(h.OAuthToken),
			Metadata:     map[string]any{"host": host, "user": h.User},
		})
	}
	return out
}

// ============================================================================
// AWS SSO cache (Amazon Q / Kiro CLI session tokens)
// ============================================================================

// AWSSSOMcache extracts access tokens from files under ~/.aws/sso/cache/.
// Amazon Q CLI and Kiro CLI both store SSO session tokens there.
func AWSSSOMcache(_ string, content []byte) []Finding {
	var doc map[string]any
	if err := json.Unmarshal(content, &doc); err != nil {
		return nil
	}
	expiresAtStr, _ := doc["expiresAt"].(string)
	expiry := parseUnixExpiry(expiresAtStr)
	startURL, _ := doc["startUrl"].(string)

	var out []Finding
	for _, field := range []string{"accessToken", "token", "clientSecret"} {
		v, _ := doc[field].(string)
		if v == "" || !isLikelyCredentialValue(v) {
			continue
		}
		name := field
		if startURL != "" {
			name = startURL + "." + field
		}
		idType := "OAuth Token"
		if field == "clientSecret" {
			idType = "API Key"
		}
		out = append(out, Finding{
			IdentityName: name,
			IdentityType: idType,
			RiskLevel:    "HIGH",
			Secret:       v,
			ExpiryDate:   expiry,
			Metadata:     map[string]any{"field": field, "startUrl": startURL},
		})
	}
	return out
}

// ============================================================================
// Continue.dev config.yaml (nested YAML with apiKey fields)
// ============================================================================

// ContinueYAMLConfig scans ~/.continue/config.yaml for credential-shaped
// values. Continue nests API keys under models[].apiKey,
// embeddingsProvider.apiKey, reranker.apiKey, and similar fields.
// Rather than matching a rigid schema, we walk all YAML nodes recursively.
func ContinueYAMLConfig(_ string, content []byte) []Finding {
	var root yaml.Node
	if err := yaml.Unmarshal(content, &root); err != nil {
		return nil
	}
	if root.Kind == 0 {
		return nil
	}
	var out []Finding
	walkYAMLNode(&root, "", &out)
	return out
}

func walkYAMLNode(node *yaml.Node, parentKey string, out *[]Finding) {
	if node == nil {
		return
	}
	switch node.Kind {
	case yaml.DocumentNode:
		for _, child := range node.Content {
			walkYAMLNode(child, parentKey, out)
		}
	case yaml.MappingNode:
		for i := 0; i+1 < len(node.Content); i += 2 {
			keyNode := node.Content[i]
			valNode := node.Content[i+1]
			key := keyNode.Value
			if valNode.Kind == yaml.ScalarNode {
				v := valNode.Value
				if isLikelyCredentialName(key) && isLikelyCredentialValue(v) {
					label := key
					if parentKey != "" {
						label = parentKey + "." + key
					}
					*out = append(*out, Finding{
						IdentityName: label,
						IdentityType: classifyIdentityType(key, v),
						RiskLevel:    classifyRisk(key, v),
						Secret:       v,
						ExpiryDate:   extractJWTExpiry(v),
						Metadata:     map[string]any{"configKey": label},
					})
				}
			} else {
				prefix := key
				if parentKey != "" {
					prefix = parentKey + "." + key
				}
				walkYAMLNode(valNode, prefix, out)
			}
		}
	case yaml.SequenceNode:
		for i, child := range node.Content {
			prefix := parentKey + "[" + strconv.Itoa(i) + "]"
			walkYAMLNode(child, prefix, out)
		}
	}
}
