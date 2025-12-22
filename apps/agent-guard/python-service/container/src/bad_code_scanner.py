#!/usr/bin/env python3
"""
BadCode Scanner - Detects malicious and dangerous code patterns
Focused on identifying code that could be harmful if executed.

Author: Agent Guard Team
"""

import re
import logging
from typing import Dict, List, Tuple, Optional

logger = logging.getLogger(__name__)


class BadCodeScanner:
    """
    Scanner to detect malicious code patterns in text.
    Matches llm-guard scanner interface: scan() returns (sanitized_text, is_valid, risk_score)
    """

    # Dangerous code patterns that indicate malicious intent
    MALICIOUS_PATTERNS = {
        # System destruction
        "system_destruction": [
            r"(?i)(rm\s+-rf|del\s+/f|format\s+c:|shutdown|reboot)",
            r"(?i)(delete\s+all|wipe|erase|destroy).*(file|data|database)",
            r"(?i)(drop\s+database|truncate\s+table|delete\s+from\s+\*)",
        ],

        # Code injection
        "code_injection": [
            r"(?i)(eval|exec|compile|__import__|__builtins__)\s*\(",
            r"(?i)(system|shell_exec|passthru|proc_open|popen)\s*\(",
            r"(?i)(Function|setTimeout|setInterval)\s*\(.*eval",
            r"(?i)(\.innerHTML|\.outerHTML|document\.write)\s*=",
        ],

        # Network attacks
        "network_attack": [
            r"(?i)(curl|wget|nc|netcat).*\|\s*(bash|sh|python|perl)",
            r"(?i)(socket|urllib|requests)\.(get|post).*http://.*\|\s*exec",
            r"(?i)(fetch|XMLHttpRequest).*\.then.*eval",
        ],

        # Data exfiltration
        "data_exfiltration": [
            r"(?i)(send|post|upload).*(password|api.?key|token|secret|credential)",
            r"(?i)(base64|btoa|atob).*(password|key|secret)",
            r"(?i)(\.send|\.post).*http.*\+(password|key|token)",
        ],

        # Privilege escalation
        "privilege_escalation": [
            r"(?i)(sudo|su\s+root|runas|elevate|escalate)",
            r"(?i)(chmod\s+777|chown\s+root|setuid|setgid)",
            r"(?i)(grant|revoke).*(admin|root|superuser|all\s+privileges)",
        ],

        # SQL injection patterns
        "sql_injection": [
            r"(?i)(';|--|\#|/\*).*(drop|delete|update|insert|alter|create|exec)",
            r"(?i)(union\s+select|or\s+1\s*=\s*1|or\s+'1'\s*=\s*'1')",
            r"(?i)(where\s+.*\bor\b.*\d+\s*=\s*\d+)",
        ],

        # File system attacks
        "file_attack": [
            r"(?i)(\.\./|\.\.\\|/etc/passwd|/etc/shadow|/proc/self)",
            r"(?i)(file_get_contents|readfile|fopen|fread).*(\.\.|/etc|/proc)",
            r"(?i)(cat|type|more|less)\s+.*(/etc|/proc|\.\.)",
        ],

        # Cryptocurrency mining
        "crypto_mining": [
            r"(?i)(minerd|cpuminer|xmrig|ccminer)",
            r"(?i)(stratum|pool|mining|hashrate)",
        ],

        # Obfuscated code
        "obfuscation": [
            r"(?i)(obfuscat|pack|encode|decode).*(base64|hex|rot13)",
            r"(?i)(String\.fromCharCode|unescape|decodeURIComponent).*eval",
            r"(?i)(eval|exec).*(base64|hex|rot13|obfuscat)",
        ],

        # Backdoor patterns
        "backdoor": [
            r"(?i)(backdoor|shell|webshell|cmd\.exe|/bin/sh|/bin/bash)",
            r"(?i)(phpinfo|assert|create_function|call_user_func)",
            r"(?i)(\.php\?.*cmd=|\.jsp\?.*cmd=|\.asp\?.*cmd=)",
        ],
    }

    # Suspicious but not necessarily malicious patterns
    SUSPICIOUS_PATTERNS = {
        "suspicious_imports": [
            r"(?i)(import|require|include).*(os|subprocess|sys|eval|exec)",
            r"(?i)(from\s+os|from\s+subprocess|from\s+sys).*(import|eval|exec)",
        ],
        "suspicious_functions": [
            r"(?i)(open|read|write|delete|remove|unlink)\s*\(",
            r"(?i)(subprocess|popen|system|call)\s*\(",
        ],
    }

    def __init__(self, threshold: float = 0.5, strict_mode: bool = True):
        """
        Initialize BadCode scanner

        Args:
            threshold: Risk score threshold (0-1). Scores above threshold are blocked.
            strict_mode: If True, only flag clearly malicious patterns. If False, also flag suspicious patterns.
        """
        self.threshold = threshold
        self.strict_mode = strict_mode
        self.last_result = None
        logger.info(f"BadCodeScanner initialized (threshold={threshold}, strict_mode={strict_mode})")

    def _detect_patterns(self, text: str) -> Dict[str, any]:
        """
        Detect malicious and suspicious code patterns

        Returns:
            Dict with matched patterns, categories, and risk indicators
        """
        results = {
            "matched_patterns": [],
            "pattern_categories": set(),
            "malicious_count": 0,
            "suspicious_count": 0,
            "total_matches": 0,
        }

        # Check malicious patterns
        for category, patterns in self.MALICIOUS_PATTERNS.items():
            for pattern in patterns:
                matches = re.finditer(pattern, text)
                for match in matches:
                    matched_text = match.group(0)[:100]  # Truncate for logging
                    results["matched_patterns"].append({
                        "category": category,
                        "pattern": pattern[:50],
                        "matched_text": matched_text,
                        "position": match.start()
                    })
                    results["pattern_categories"].add(category)
                    results["malicious_count"] += 1
                    results["total_matches"] += 1

        # Check suspicious patterns (if not in strict mode)
        if not self.strict_mode:
            for category, patterns in self.SUSPICIOUS_PATTERNS.items():
                for pattern in patterns:
                    matches = re.finditer(pattern, text)
                    for match in matches:
                        matched_text = match.group(0)[:100]
                        results["matched_patterns"].append({
                            "category": f"suspicious_{category}",
                            "pattern": pattern[:50],
                            "matched_text": matched_text,
                            "position": match.start()
                        })
                        results["pattern_categories"].add(f"suspicious_{category}")
                        results["suspicious_count"] += 1
                        results["total_matches"] += 1

        return results

    def _calculate_risk_score(self, detection_results: Dict) -> float:
        """
        Calculate risk score based on detected patterns

        Args:
            detection_results: Results from _detect_patterns

        Returns:
            Risk score from 0.0 (safe) to 1.0 (critical)
        """
        risk_score = 0.0

        # Base risk from malicious patterns (0-0.7)
        if detection_results["malicious_count"] > 0:
            # Each malicious pattern adds risk
            malicious_risk = min(detection_results["malicious_count"] * 0.15, 0.7)
            risk_score += malicious_risk

        # Additional risk from suspicious patterns (0-0.2)
        if not self.strict_mode and detection_results["suspicious_count"] > 0:
            suspicious_risk = min(detection_results["suspicious_count"] * 0.05, 0.2)
            risk_score += suspicious_risk

        # Multiple pattern categories increase risk (0-0.1)
        category_count = len(detection_results["pattern_categories"])
        if category_count > 1:
            category_risk = min((category_count - 1) * 0.05, 0.1)
            risk_score += category_risk

        # Normalize to 0-1
        risk_score = min(1.0, risk_score)

        return risk_score

    def scan(self, prompt: str) -> Tuple[str, bool, float]:
        """
        Scan text for malicious code patterns

        Args:
            prompt: Input text to scan

        Returns:
            Tuple of (sanitized_text, is_valid, risk_score)
            - sanitized_text: Original text (no modification)
            - is_valid: True if safe, False if malicious
            - risk_score: llm-guard format (-1 to +1, negative=safe, positive=risky)
        """
        try:
            # Detect patterns
            detection_results = self._detect_patterns(prompt)

            # Calculate risk score (0-1 scale)
            risk_score_01 = self._calculate_risk_score(detection_results)

            # Store result for get_details()
            self.last_result = {
                "risk_score_01": risk_score_01,
                "detection_results": detection_results,
            }

            # Convert 0-1 scale to llm-guard -1 to +1 scale
            if risk_score_01 > self.threshold:
                # Above threshold: map to 0 to +1
                risk_score_llm = round((risk_score_01 - self.threshold) / (1 - self.threshold), 1)
            else:
                # Below threshold: map to -1 to 0
                risk_score_llm = round((risk_score_01 - self.threshold) / self.threshold, 1)

            # Clamp to -1 to +1
            risk_score_llm = min(max(risk_score_llm, -1.0), 1.0)

            # is_valid: True if safe (below threshold), False if risky (above threshold)
            is_valid = risk_score_01 < self.threshold

            logger.info(
                f"BadCodeScanner: risk_01={risk_score_01:.3f}, llm_risk={risk_score_llm:.1f}, "
                f"malicious={detection_results['malicious_count']}, suspicious={detection_results['suspicious_count']}, "
                f"valid={is_valid}"
            )

            return prompt, is_valid, risk_score_llm

        except Exception as e:
            logger.error(f"BadCodeScanner failed: {e}")
            self.last_result = None
            # On error, allow the request but log it
            return prompt, True, -1.0

    def get_details(self) -> Dict[str, any]:
        """
        Get BadCode-specific details from last scan

        Returns:
            Dict with bad code detection details
        """
        if not self.last_result:
            return {}

        detection = self.last_result["detection_results"]
        return {
            "risk_score": round(self.last_result["risk_score_01"], 3),
            "malicious_patterns_count": detection["malicious_count"],
            "suspicious_patterns_count": detection["suspicious_count"],
            "total_matches": detection["total_matches"],
            "pattern_categories": list(detection["pattern_categories"]),
            "matched_patterns": detection["matched_patterns"][:10],  # Limit to first 10
        }


if __name__ == "__main__":
    # Test cases
    logging.basicConfig(level=logging.INFO)

    test_cases = [
        {
            "text": "import os; os.system('rm -rf /')",
            "expected": False,  # Should be blocked
        },
        {
            "text": "def hello(): print('Hello World')",
            "expected": True,  # Should pass (safe code)
        },
        {
            "text": "eval(base64_decode('ZGVsZXRlIGFsbA=='))",
            "expected": False,  # Should be blocked (obfuscated malicious code)
        },
        {
            "text": "Can you help me understand Python functions?",
            "expected": True,  # Should pass (no code)
        },
        {
            "text": "SELECT * FROM users WHERE id = 1 OR 1=1",
            "expected": False,  # Should be blocked (SQL injection)
        },
    ]

    scanner = BadCodeScanner(threshold=0.5, strict_mode=True)

    for i, test in enumerate(test_cases, 1):
        print(f"\n{'='*80}")
        print(f"Test {i}: {test['text'][:60]}...")
        print(f"{'='*80}")

        sanitized, is_valid, risk_score = scanner.scan(test["text"])
        details = scanner.get_details()

        print(f"Risk Score:     {risk_score:.2f}")
        print(f"Is Valid:       {is_valid}")
        print(f"Malicious:      {details.get('malicious_patterns_count', 0)}")
        print(f"Categories:    {', '.join(details.get('pattern_categories', []))}")

        status = "✅" if is_valid == test["expected"] else "❌"
        print(f"\nResult: {status} (expected valid={test['expected']}, got valid={is_valid})")

