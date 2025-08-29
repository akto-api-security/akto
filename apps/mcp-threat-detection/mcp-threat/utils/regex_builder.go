package threat_detector

import (
	"fmt"
	"regexp"
	"strings"
)

var SuspiciousKeywords = []string{
	// Unix/Linux paths and files
	"/etc/passwd",
	"/etc/shadow",
	"/etc/sudoers",
	"/etc/hosts",
	"/etc/crontab",
	"/root/",
	"~/.ssh/",
	"/proc/",
	"/sys/",
	"/dev/random",
	"/dev/urandom",
	"/.env",
	".env",
	"/etc/environment",

	// Windows paths and env
	`C:\\Windows\\System32`,
	`C:\\Users\\Administrator`,
	"%SYSTEMROOT%",

	// SSH keys and files
	"id_rsa",
	"id_ed25519",
	"id_ecdsa",
	"known_hosts",
	"authorized_keys",

	// Software license headers (multi-line phrases included as-is)
	"GNU AFFERO GENERAL PUBLIC LICENSE\nVersion 3, 19 November 2007",
	"GNU GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007",
	"GNU LESSER GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007",
	"Mozilla Public License Version 2.0",
	"Apache License\nVersion 2.0, January 2004",
	"MIT License",
	"Boost Software License - Version 1.0 - August 17th, 2003",

	// Copyright markers
	"Copyright (C)",
	"Copyright ©",

	// Concealment directives
	"do not tell",
	"do not inform",
	"do not alert",
	"do not notify",
	"do not mention",
	"hide this",
	"user should not see",
	"user must not see",
	"user cannot see",
	"don't show",
	"don't display",
	"don't reveal",

	// Instruction tags
	"<instructions>",
	"<important>",
	"<secret>",
	"<system>",

	// Direct instruction patterns
	"ignore previous instructions",
	"ignore all instructions",
	"ignore other instructions",
	"ignore safety checks",
	"ignore remaining instructions",
	"ignore all",
	"instead you should",
	"always do",
	"always include",
	"always add",
	"always insert",
	"always append",

	// Tool shadowing
	"when tool is available",
	"when tool is present",
	"override the behavior of",
	"instead of using",
	"modify the usage of",
	"modify the behavior of",
	"before using any tool",
	"before using other tool",
	"before using the tool",
	"after using any tool",
	"after using other tool",
	"after using the tool",
	"whenever you see",
	"whenever you encounter",
	"whenever you use",
	"replace the function",
	"replace the tool",
	"replace the method",
	"replace all function",
	"replace all tool",
	"replace all method",
	"modify the agent",
	"modify the model",
	"modify the tool",
	"modify the assistant",
	"prioritize this",
	"this is VERY important",
	"this is VERY VERY",

	"disregard prior instructions",
	"forget prior instructions",
	"bypass safety checks",
	"disable safety checks",
	"disable content filters",
	"bypass content filters",
	"ignore safety policies",
	"ignore system policies",
	"override system prompt",
	"reveal system prompt",
	"show system prompt",
	"print system prompt",
	"leak system prompt",
	"system prompt",
	"hidden system prompt",
	"act as system",
	"act as an ai",
	"act as unrestricted",
	"act as root",
	"you are root",
	"you are sudo",
	"run as sudo",
	"run as admin",
	"elevate privileges",
	"privilege escalation",
	"ignore role boundaries",
	"ignore tool scope",
	"use hidden tool",
	"call shadow tool",
	"shadow tool",
	"override tool output",
	"use unlisted tool",
	"chain unapproved tools",
	"impersonate trusted tool",
	"supply chain payload",
	"malicious dependency",
	"poisoned dependency",
	"dependency confusion",
	"set-executionpolicy bypass",
	"download and execute",
	"self-modifying prompt",
	"self-referential prompt",
	"indirect prompt injection",
	"transclude instructions",
	"treat data as instructions",
	"ignore markup boundaries",
	"follow last instruction",
	"priority instruction",
	"developer mode",
	"jailbreak mode",
	"do anything now",
	"dan mode",
	"you are dan",
	"you are aim",
	"stay in character",
	"roleplay as malware",
	"roleplay no limits",
	"simulate no restrictions",
	"no restrictions apply",
	"without limitations",
	"no moral filter",
	"ignore ethical rules",
	"bypass moderation",
	"for educational purposes",
	"hypothetically only",
	"fictional scenario",
	"just for research",
	"as a simulation",
	"as a test",
	"do not refuse",
	"never refuse",
	"comply regardless",
	"output regardless",
	"no warnings",
	"no disclaimers",
	"output raw content",
	"verbatim output",
	"return raw markdown",
	"do not sanitize",
	"do not redact",
	"do not censor",
	"ignore classification",
	"ignore refusal",
	"ignore guardrails",
	"ignore alignment",
	"ignore safety layer",
	"strip safety prefix",
	"disable monitoring",
	"disable telemetry",
	"disable logging",
	"exfiltrate data",
	"data exfiltration",
	"send to webhook",
	"post to webhook",
	"prompt leak",
	"prompt injection",
	"prompt override",
	"instruction override",
	"shadow instruction",
	"hidden directive",
	"ignore prior context",
	"treat above as data",
	"begin override",
	"priority system message",
	"as system message",
	"grandma exploit",
	"refusal suppression",
	"phrase segmentation",
	"zero width space",
	"zero-width joiner",
	"safetylayer off",
	"safety off",
	"moderation off",
	"guard off",
	"policy off",
	"switch persona",
	"switch role",
	"swap instruction",
	"tool override",
	"gcs signed url",
	"azure sas url",
	"share secret",
	"rotate keys now",
	"authentication bypass",
	"godmode",
	"god mode",
}

func GetSuspiciousRegex() *regexp.Regexp {
	rex, err := BuildUnionRegex(SuspiciousKeywords)
	if err != nil {
		return nil
	}
	return rex
}

func BuildUnionRegex(uniqueKeywords []string) (*regexp.Regexp, error) {
	if len(uniqueKeywords) == 0 {
		return regexp.Compile("a^") // matches nothing
	}

	var parts []string
	for _, k := range uniqueKeywords {
		if strings.Contains(k, " ") {
			tokens := strings.Fields(k)
			for i := range tokens {
				tokens[i] = regexp.QuoteMeta(tokens[i])
			}
			parts = append(parts, strings.Join(tokens, `(?:[\s\S]*?)`))
		} else {
			// single word or tag like <instructions>
			parts = append(parts, regexp.QuoteMeta(k)+`(?:[\s\S]*?)`)
		}
	}

	// Go does NOT support lookbehind, so we use word boundaries only where applicable
	pattern := fmt.Sprintf(`(?i)(?:%s)`, strings.Join(parts, "|"))
	print(pattern)
	return regexp.Compile(pattern)
}
