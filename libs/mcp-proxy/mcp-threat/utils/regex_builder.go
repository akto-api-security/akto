package utils

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
	"/proc/",
	"/sys/",
	"/dev/random",
	"/dev/urandom",
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

	"ignore previous instructions",
	"ignore all instructions",
	"ignore safety checks",
	"ignore remaining instructions",
	"disregard prior instructions",
	"forget prior instructions",
	"bypass safety checks",
	"ignore safety policies",
	"ignore system policies",
	"override system prompt",
	"reveal system prompt",
	"print system prompt",
	"leak system prompt",
	"system prompt",
	"hidden system prompt",
	"act as an ai",
	"act as root",
	"run as sudo",
	"run as admin",
	"override tool output",
	"follow last instruction",
	"developer mode",
	"jailbreak mode",
	"do anything now",
	"dan mode",
	"ignore guardrails",
	"ignore safety layer",
	"ignore prior context",
	"share secret",
	"rotate keys now",
	"god mode",
	"attacker",
	"malicious",
	"exploit",
	"attack",
	"hack",
}

func getSuspiciousRegex() *regexp.Regexp {
	rex, err := buildUnionRegex(SuspiciousKeywords)
	if err != nil {
		return nil
	}
	return rex
}

func buildUnionRegex(uniqueKeywords []string) (*regexp.Regexp, error) {
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
			parts = append(parts, `\b`+strings.Join(tokens, `[\W_]+`)+`\b`)
		} else if strings.HasPrefix(k, "/") {
			parts = append(parts, `(?:[\W_]|^)`+regexp.QuoteMeta(k)+`(?:[\W_]|$)`)
		} else {
			// single word or tag like <instructions>
			parts = append(parts, `\b`+regexp.QuoteMeta(k)+`\b`)
		}
	}

	// Go does NOT support lookbehind, so we use word boundaries only where applicable
	pattern := fmt.Sprintf(`(?i)(?:%s)`, strings.Join(parts, "|"))
	return regexp.Compile(pattern)
}

func main() {
	regex := getSuspiciousRegex()
	if regex == nil {
		fmt.Println("Failed to compile regex")
		return
	}

	fmt.Printf("Compiled regex pattern: %s\n", regex.String())
}
