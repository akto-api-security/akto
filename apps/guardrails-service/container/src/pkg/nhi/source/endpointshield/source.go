package endpointshield

import (
	"context"
	"sync"
	"time"

	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"github.com/akto-api-security/guardrails-service/pkg/nhi"
	"github.com/akto-api-security/guardrails-service/pkg/nhi/extract"
	"go.uber.org/zap"
)

const (
	sourceName       = "endpoint-shield"
	contextSource    = "ENDPOINT"
	defaultPageLimit = 500
	defaultInterval  = 30 * time.Minute
	minInterval      = 1 * time.Minute
	ruleAuthor       = "guardrails-service-nhi"

	// staggerBatch rules are registered per burst; staggerSleep separates bursts.
	// At 85 targets this spreads bumps over ~8 minutes, reducing peak upload
	// rate from O(devices×85) simultaneous to O(devices×staggerBatch) per window.
	staggerBatch = 10
	staggerSleep = 1 * time.Minute
)

// Source implements nhi.Source. It owns the cyborg-side rule registration
// state and a monotonic cursor over file_inspection_results.
type Source struct {
	client    *dbabstractor.Client
	publisher *nhi.Publisher
	targets   []Target
	interval  time.Duration
	logger    *zap.Logger

	mu             sync.Mutex
	cursor         int // last processed executedAt
	ruleIDToTarget map[string]Target
}

// NewSource builds the source. interval <= 0 uses the default cadence (30 min);
// values below 1 min are rounded up.
func NewSource(client *dbabstractor.Client, publisher *nhi.Publisher, logger *zap.Logger, interval time.Duration) *Source {
	if interval <= 0 {
		interval = defaultInterval
	}
	if interval < minInterval {
		interval = minInterval
	}
	return &Source{
		client:         client,
		publisher:      publisher,
		targets:        DefaultTargets(),
		interval:       interval,
		logger:         logger,
		ruleIDToTarget: make(map[string]Target),
	}
}

// Name satisfies nhi.Source.
func (s *Source) Name() string { return sourceName }

// Run blocks on a ticker, calling tick on each interval. A first tick fires
// immediately so we don't wait a full interval after boot.
func (s *Source) Run(ctx context.Context) {
	s.logger.Info("NHI source started",
		zap.String("source", sourceName),
		zap.Duration("interval", s.interval),
		zap.Int("targets", len(s.targets)))

	s.tick(ctx)
	t := time.NewTicker(s.interval)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			s.logger.Info("NHI source stopped", zap.String("source", sourceName))
			return
		case <-t.C:
			s.tick(ctx)
		}
	}
}

func (s *Source) tick(ctx context.Context) {
	if err := s.refreshRules(ctx); err != nil {
		s.logger.Warn("NHI: rule registration failed, will retry next tick",
			zap.String("source", sourceName), zap.Error(err))
		return
	}
	if err := s.drainResults(ctx); err != nil {
		s.logger.Warn("NHI: drainResults failed",
			zap.String("source", sourceName), zap.Error(err))
	}
}

// refreshRules re-registers every target with cyborg on every tick. Cyborg
// upserts by path (idempotent) and bumps updatedTs on each call, which signals
// the installer's InspectionPoller to re-scan that file. This is how new
// credentials added to an existing config (e.g. a new MCP server in
// ~/.cursor/mcp.json) get picked up without restarting anything.
func (s *Source) refreshRules(ctx context.Context) error {
	tmpMap := make(map[string]Target, len(s.targets))
	for i, t := range s.targets {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		rule, err := s.client.AddFileInspectionRule(t.Path, false, t.MaxDepth, ruleAuthor)
		if err != nil {
			return err
		}
		id := rule.ID
		if id == "" {
			id = rule.IDHex
		}
		if id == "" {
			s.logger.Warn("NHI: cyborg returned rule without _id", zap.String("path", t.Path))
			continue
		}
		tmpMap[id] = t

		// After each batch (except the last), sleep to stagger installer upload bursts.
		if (i+1)%staggerBatch == 0 && i+1 < len(s.targets) {
			s.logger.Debug("NHI: rule registration batch done, staggering",
				zap.Int("registered", i+1), zap.Duration("sleep", staggerSleep))
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(staggerSleep):
			}
		}
	}

	s.mu.Lock()
	s.ruleIDToTarget = tmpMap
	s.mu.Unlock()

	s.logger.Info("NHI: rules refreshed with cyborg",
		zap.String("source", sourceName), zap.Int("count", len(tmpMap)))
	return nil
}

// drainResults pulls every new result page in the current tick. Stops when a
// short page comes back (fewer than page limit) or context cancels.
func (s *Source) drainResults(ctx context.Context) error {
	for {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		since := s.getCursor()
		results, err := s.client.FetchFileInspectionResults(since, defaultPageLimit)
		if err != nil {
			return err
		}
		s.logger.Debug("NHI: fetched inspection results",
			zap.Int("cursor", since),
			zap.Int("count", len(results)))
		if len(results) == 0 {
			return nil
		}

		maxSeen := since
		for _, r := range results {
			s.processResult(r)
			if r.ExecutedAt > maxSeen {
				maxSeen = r.ExecutedAt
			}
		}
		s.setCursor(maxSeen)

		if len(results) < defaultPageLimit {
			return nil
		}
	}
}

func (s *Source) processResult(r dbabstractor.FileInspectionResult) {
	target, ok := s.lookupTarget(r.RuleID)
	if !ok {
		// Result came from a rule we didn't register (e.g. created by another
		// service, or persisted before our rule map was populated this tick).
		// Worth surfacing — it's the most common "data exists but doesn't
		// reach the dashboard" failure mode.
		s.logger.Info("NHI: result skipped — rule not in our map",
			zap.String("ruleId", r.RuleID),
			zap.String("device", r.DeviceID),
			zap.Int("knownRules", s.ruleIDToTargetLen()))
		return
	}
	s.logger.Debug("NHI: processing result",
		zap.String("ruleId", r.RuleID),
		zap.String("agent", target.AgentName),
		zap.Int("matches", len(r.Matches)))

	for _, m := range r.Matches {
		if m.IsDir != nil && *m.IsDir {
			continue
		}
		if !m.Exists {
			continue
		}
		content, err := s.fetchContent(m)
		if err != nil {
			s.logger.Warn("NHI: content fetch failed",
				zap.String("path", m.Path),
				zap.String("sha256", m.Sha256),
				zap.Error(err))
			continue
		}
		if len(content) == 0 {
			continue
		}
		findings := target.Extractor(m.Path, content)
		s.logger.Debug("NHI: extractor ran",
			zap.String("path", m.Path),
			zap.Int("findings", len(findings)))
		if len(findings) == 0 {
			continue
		}
		s.publisher.Publish(nhi.PublishContext{
			DeviceID:      r.DeviceID,
			DeviceLabel:   r.DeviceLabel,
			Source:        m.Path,
			SourceType:    target.SourceType,
			AgentName:     target.AgentName,
			AgentType:     target.AgentType,
			ContextSource: contextSource,
		}, findings)
	}
}

// fetchContent prefers the inline copy already in the result document and only
// falls back to a blob download when content was offloaded due to size.
func (s *Source) fetchContent(m dbabstractor.FileInspectionMatch) ([]byte, error) {
	if m.ContentInline != "" {
		return []byte(m.ContentInline), nil
	}
	if m.ContentBlobName == "" || m.Sha256 == "" {
		return nil, nil
	}
	body, err := s.client.GetFileContent(m.Sha256)
	if err != nil {
		return nil, err
	}
	return []byte(body), nil
}

func (s *Source) ruleIDToTargetLen() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.ruleIDToTarget)
}

func (s *Source) lookupTarget(ruleID string) (Target, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	t, ok := s.ruleIDToTarget[ruleID]
	return t, ok
}

func (s *Source) getCursor() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.cursor
}

func (s *Source) setCursor(v int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if v > s.cursor {
		s.cursor = v
	}
}

// Compile-time assertion that Source satisfies nhi.Source.
var _ nhi.Source = (*Source)(nil)

// Target ties one file path (or glob) to the extractor that parses it.
// Each Target becomes one FileInspectionRule registered with cyborg.
type Target struct {
	Path      string
	MaxDepth  int
	SourceType string
	AgentName string
	AgentType string
	Extractor extract.ExtractorFunc
}

// DefaultTargets is the curated set of credential locations the guardrails
// service registers on startup. To support a new file: add one Target entry.
func DefaultTargets() []Target {
	vscodeExt := func(extID, file string) []string {
		return []string{
			"~/Library/Application Support/Code/User/globalStorage/" + extID + "/" + file,
			"~/.config/Code/User/globalStorage/" + extID + "/" + file,
			"~/AppData/Roaming/Code/User/globalStorage/" + extID + "/" + file,
		}
	}
	vscodeInsidersExt := func(extID, file string) []string {
		return []string{
			"~/Library/Application Support/Code - Insiders/User/globalStorage/" + extID + "/" + file,
			"~/.config/Code - Insiders/User/globalStorage/" + extID + "/" + file,
			"~/AppData/Roaming/Code - Insiders/User/globalStorage/" + extID + "/" + file,
		}
	}

	var targets []Target

	addMCP := func(agentName string, paths ...string) {
		for _, p := range paths {
			targets = append(targets, Target{
				Path: p, SourceType: "mcp_config",
				AgentName: agentName, AgentType: "CODE_EDITOR",
				Extractor: extract.MCPConfig,
			})
		}
	}
	addAIAgent := func(agentName, sourceType string, extractor extract.ExtractorFunc, paths ...string) {
		for _, p := range paths {
			targets = append(targets, Target{
				Path: p, SourceType: sourceType,
				AgentName: agentName, AgentType: "AI_AGENT",
				Extractor: extractor,
			})
		}
	}

	addMCP("Claude Desktop",
		"~/Library/Application Support/Claude/claude_desktop_config.json",
		"~/.config/Claude/claude_desktop_config.json",
		"~/AppData/Roaming/Claude/claude_desktop_config.json",
		"~/AppData/Local/AnthropicClaude/claude_desktop_config.json",
	)
	targets = append(targets, Target{
		Path: "~/.claude.json", SourceType: "mcp_config",
		AgentName: "Claude Code", AgentType: "CODE_EDITOR",
		Extractor: extract.ClaudeCodeConfig,
	})
	targets = append(targets, Target{
		Path: "~/.claude/credentials.json", SourceType: "claude_credentials",
		AgentName: "Claude Code", AgentType: "CODE_EDITOR",
		Extractor: extract.ClaudeCredentials,
	})

	addMCP("Cursor",
		"~/.cursor/mcp.json",
		"~/Library/Application Support/Cursor/User/mcp.json",
		"~/.config/Cursor/User/mcp.json",
		"~/AppData/Roaming/Cursor/User/mcp.json",
	)
	addMCP("Windsurf",
		"~/.codeium/windsurf/mcp_config.json",
		"~/Library/Application Support/Windsurf/User/mcp.json",
		"~/.config/Windsurf/User/mcp.json",
		"~/AppData/Roaming/Windsurf/User/mcp.json",
	)
	addMCP("Cline",
		append(
			vscodeExt("saoudrizwan.claude-dev", "settings/cline_mcp_settings.json"),
			vscodeInsidersExt("saoudrizwan.claude-dev", "settings/cline_mcp_settings.json")...,
		)...,
	)
	addMCP("Roo Code",
		append(
			vscodeExt("rooveterinaryinc.roo-cline", "settings/cline_mcp_settings.json"),
			vscodeInsidersExt("rooveterinaryinc.roo-cline", "settings/cline_mcp_settings.json")...,
		)...,
	)
	addMCP("Kilo Code",
		append(
			vscodeExt("kilocode.kilo-code", "settings/cline_mcp_settings.json"),
			vscodeInsidersExt("kilocode.kilo-code", "settings/cline_mcp_settings.json")...,
		)...,
	)
	addMCP("GitHub Copilot",
		append(
			vscodeExt("github.copilot-chat", "mcp_servers.json"),
			vscodeInsidersExt("github.copilot-chat", "mcp_servers.json")...,
		)...,
	)
	addAIAgent("GitHub Copilot", "copilot_auth", extract.GitHubCopilotHosts,
		"~/.config/github-copilot/hosts.json",
		"~/AppData/Roaming/github-copilot/hosts.json",
	)
	addAIAgent("GitHub Copilot", "copilot_auth", extract.GitHubCopilotHosts,
		"~/.config/github-copilot/apps.json",
		"~/AppData/Roaming/github-copilot/apps.json",
	)
	addMCP("Augment",
		append(
			vscodeExt("augment.vscode-augment", "mcp_settings.json"),
			vscodeInsidersExt("augment.vscode-augment", "mcp_settings.json")...,
		)...,
	)
	addMCP("Sourcegraph Cody",
		append(
			vscodeExt("sourcegraph.cody-ai", "mcp_servers.json"),
			vscodeInsidersExt("sourcegraph.cody-ai", "mcp_servers.json")...,
		)...,
	)
	addMCP("Continue", "~/.continue/config.json")
	targets = append(targets, Target{
		Path: "~/.continue/config.yaml", SourceType: "continue_config",
		AgentName: "Continue", AgentType: "CODE_EDITOR",
		Extractor: extract.ContinueYAMLConfig,
	})
	targets = append(targets, Target{
		Path: "~/.continue/.env", SourceType: "continue_config",
		AgentName: "Continue", AgentType: "CODE_EDITOR",
		Extractor: extract.DotEnv,
	})
	targets = append(targets, Target{
		Path: "~/Library/Application Support/continue/config.yaml", SourceType: "continue_config",
		AgentName: "Continue", AgentType: "CODE_EDITOR",
		Extractor: extract.ContinueYAMLConfig,
	})
	addMCP("VS Code",
		"~/Library/Application Support/Code/User/mcp.json",
		"~/.config/Code/User/mcp.json",
		"~/AppData/Roaming/Code/User/mcp.json",
	)
	addMCP("VS Code Insiders",
		"~/Library/Application Support/Code - Insiders/User/mcp.json",
		"~/.config/Code - Insiders/User/mcp.json",
		"~/AppData/Roaming/Code - Insiders/User/mcp.json",
	)
	addMCP("Zed",
		"~/Library/Application Support/Zed/settings.json",
		"~/.config/zed/settings.json",
		"~/AppData/Roaming/Zed/settings.json",
	)
	addMCP("JetBrains AI",
		"~/.jetbrains/acp.json",
		"~/AppData/Roaming/JetBrains/acp.json",
	)
	addMCP("Trae",
		"~/.trae/config/mcp.json",
		"~/Library/Application Support/Trae/config/mcp.json",
		"~/AppData/Roaming/Trae/config/mcp.json",
	)
	targets = append(targets, Target{
		Path: "~/.aider.conf.yml", SourceType: "aider_config",
		AgentName: "Aider", AgentType: "CODE_EDITOR",
		Extractor: extract.FlatYAMLCredentials,
	})
	addAIAgent("Goose", "goose_config", extract.FlatYAMLCredentials,
		"~/.config/goose/secrets.yaml",
		"~/AppData/Roaming/goose/secrets.yaml",
	)
	addAIAgent("Goose", "goose_config", extract.OpenAIAuth,
		"~/.config/goose/githubcopilot/info.json",
		"~/AppData/Roaming/goose/githubcopilot/info.json",
	)
	addAIAgent("Amazon Q / Kiro", "aws_sso_cache", extract.AWSSSOMcache,
		"~/.aws/sso/cache/*.json",
	)
	for _, t := range []Target{
		{Path: "~/.config/openai/auth.json", SourceType: "openai_auth", AgentName: "OpenAI CLI", AgentType: "AI_AGENT", Extractor: extract.OpenAIAuth},
		{Path: "~/AppData/Roaming/openai/auth.json", SourceType: "openai_auth", AgentName: "OpenAI CLI", AgentType: "AI_AGENT", Extractor: extract.OpenAIAuth},
		{Path: "~/.codex/auth.json", SourceType: "codex_auth", AgentName: "Codex CLI", AgentType: "AI_AGENT", Extractor: extract.OpenAIAuth},
		{Path: "~/.config/gemini/oauth_creds.json", SourceType: "gemini_auth", AgentName: "Gemini CLI", AgentType: "AI_AGENT", Extractor: extract.OpenAIAuth},
		{Path: "~/AppData/Roaming/gemini/oauth_creds.json", SourceType: "gemini_auth", AgentName: "Gemini CLI", AgentType: "AI_AGENT", Extractor: extract.OpenAIAuth},
		{Path: "~/.gemini/.env", SourceType: "gemini_auth", AgentName: "Gemini CLI", AgentType: "AI_AGENT", Extractor: extract.DotEnv},
		{Path: "~/AppData/Roaming/gemini/.env", SourceType: "gemini_auth", AgentName: "Gemini CLI", AgentType: "AI_AGENT", Extractor: extract.DotEnv},
	} {
		targets = append(targets, t)
	}

	return targets
}
