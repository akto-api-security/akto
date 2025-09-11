package guardrails

// getDefaultSensitivePatterns returns default patterns for detecting sensitive data
func getDefaultSensitivePatterns() []SensitiveDataPattern {
	return []SensitiveDataPattern{
		{
			Name:        "credit_card",
			Pattern:     `\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b`,
			Replacement: "***REDACTED_CREDIT_CARD***",
			Description: "Credit card numbers",
		},
		{
			Name:        "ssn",
			Pattern:     `\b\d{3}-\d{2}-\d{4}\b`,
			Replacement: "***REDACTED_SSN***",
			Description: "Social Security Numbers",
		},
		{
			Name:        "email",
			Pattern:     `\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b`,
			Replacement: "***REDACTED_EMAIL***",
			Description: "Email addresses",
		},
		{
			Name:        "phone",
			Pattern:     `\b\d{3}[-.]?\d{3}[-.]?\d{4}\b`,
			Replacement: "***REDACTED_PHONE***",
			Description: "Phone numbers",
		},
		{
			Name:        "api_key",
			Pattern:     `(api[_-]?key|access[_-]?token|secret[_-]?key)\s*[:=]\s*["']?[A-Za-z0-9]{20,}["']?`,
			Replacement: "***REDACTED_API_KEY***",
			Description: "API keys and tokens",
		},
		{
			Name:        "password",
			Pattern:     `"password"\s*:\s*"[^"]*"`,
			Replacement: `"password": "***REDACTED_PASSWORD***"`,
			Description: "Passwords",
		},
		{
			Name:        "ip_address",
			Pattern:     `\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b`,
			Replacement: "***REDACTED_IP***",
			Description: "IP addresses",
		},
		{
			Name:        "aws_access_key",
			Pattern:     `AKIA[0-9A-Z]{16}`,
			Replacement: "***REDACTED_AWS_KEY***",
			Description: "AWS access keys",
		},
		{
			Name:        "private_key",
			Pattern:     `-----BEGIN\s+(?:RSA\s+)?PRIVATE\s+KEY-----`,
			Replacement: "***REDACTED_PRIVATE_KEY***",
			Description: "Private keys",
		},
	}
}

// getDefaultContentFilters returns default content filtering rules
func getDefaultContentFilters() []ContentFilter {
	return []ContentFilter{
		{
			Type:        "keyword",
			Pattern:     "password",
			Action:      "warn",
			Description: "Password field detected",
		},
		{
			Type:        "keyword",
			Pattern:     "secret",
			Action:      "warn",
			Description: "Secret field detected",
		},
		{
			Type:        "keyword",
			Pattern:     "token",
			Action:      "warn",
			Description: "Token field detected",
		},
		{
			Type:        "regex",
			Pattern:     `(eval|exec|system|shell_exec)`,
			Action:      "block",
			Description: "Potentially dangerous code execution",
		},
		{
			Type:        "regex",
			Pattern:     `(union\s+select|drop\s+table|delete\s+from)`,
			Action:      "block",
			Description: "SQL injection attempt",
		},
		{
			Type:        "regex",
			Pattern:     `<script[^>]*>.*?</script>`,
			Action:      "block",
			Description: "XSS attempt",
		},
		{
			Type:        "keyword",
			Pattern:     "admin",
			Action:      "warn",
			Description: "Admin-related content",
		},
		{
			Type:        "keyword",
			Pattern:     "root",
			Action:      "warn",
			Description: "Root access content",
		},
	}
} 