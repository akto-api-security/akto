"""
Pattern Learner Module
Manages learned base prompt patterns and provides pattern matching functionality.
"""

import logging
from typing import Dict, List, Optional, Tuple, Any
from datetime import datetime, timedelta
import re

logger = logging.getLogger(__name__)

# Constants
PATTERN_CACHE_TTL_HOURS = 24  # Cache patterns for 24 hours
MIN_PATTERN_CONFIDENCE = 0.65  # Minimum confidence to cache a pattern


class PatternInfo:
    """Stores information about a learned pattern."""
    
    def __init__(
        self,
        base_prompt: str,
        confidence: float,
        usage_count: int = 1,
        last_seen: Optional[datetime] = None
    ):
        self.base_prompt = base_prompt
        self.confidence = confidence
        self.usage_count = usage_count
        self.last_seen = last_seen or datetime.now()
    
    def update(self, confidence: float):
        """Update pattern with new confidence and increment usage."""
        self.confidence = max(self.confidence, confidence)
        self.usage_count += 1
        self.last_seen = datetime.now()


class PatternLearner:
    """Manages learned base prompt patterns for fast matching."""
    
    def __init__(self):
        """Initialize pattern cache."""
        self.patterns: Dict[str, PatternInfo] = {}
        self.pattern_tokens: Dict[str, List[str]] = {}  # Cached tokenized patterns
    
    def learn_pattern(
        self,
        base_prompt: str,
        confidence: float
    ) -> bool:
        """
        Learn and cache a base prompt pattern.
        
        Args:
            base_prompt: Base prompt pattern (may contain {} placeholders)
            confidence: Confidence score from detection
            
        Returns:
            True if pattern was cached
        """
        if confidence < MIN_PATTERN_CONFIDENCE:
            logger.debug(f"Pattern confidence too low ({confidence:.2f}), not caching")
            return False
        
        # Normalize pattern for matching (remove extra spaces)
        normalized = re.sub(r'\s+', ' ', base_prompt.strip())
        
        if normalized in self.patterns:
            # Update existing pattern
            self.patterns[normalized].update(confidence)
        else:
            # Add new pattern
            self.patterns[normalized] = PatternInfo(
                base_prompt=normalized,
                confidence=confidence
            )
            # Cache tokenized version
            self.pattern_tokens[normalized] = normalized.split()
            logger.info(f"Learned new pattern: {normalized[:50]}... (confidence: {confidence:.2f})")
        
        return True
    
    def match_pattern(
        self,
        prompt: str
    ) -> Optional[Tuple[str, List[str]]]:
        """
        Match a prompt against learned patterns and extract variables.
        
        Args:
            prompt: Input prompt to match
            
        Returns:
            Tuple of (matched_base_prompt, list_of_variables_per_position) or None if no match
            Example: ("Book flight from {} to {} on {}", ["Boston", "London", "2024-03-15"])
        """
        prompt_tokens = prompt.split()
        
        best_match = None
        best_match_score = 0.0
        
        logger.debug(f"Matching prompt against {len(self.patterns)} learned patterns")
        
        for base_prompt, pattern_info in self.patterns.items():
            # Check if pattern is expired
            if (datetime.now() - pattern_info.last_seen).total_seconds() > PATTERN_CACHE_TTL_HOURS * 3600:
                logger.debug(f"Pattern expired: {base_prompt[:50]}...")
                continue
            
            pattern_tokens = self.pattern_tokens[base_prompt]
            
            # Check if prompt matches pattern structure
            match_score, extracted_vars = self._match_tokens(prompt_tokens, pattern_tokens)
            
            logger.debug(f"Pattern '{base_prompt[:50]}...' match_score: {match_score:.3f}")
            
            if match_score > best_match_score:
                best_match_score = match_score
                best_match = (base_prompt, extracted_vars)
        
        logger.debug(f"Best match score: {best_match_score:.3f}, threshold: 0.7")
        
        # Require at least 70% token match
        if best_match_score >= 0.7:
            logger.debug(f"Pattern matched: {best_match[0] if best_match else None}")
            return best_match
        
        logger.debug("No pattern matched (score below 0.7 threshold)")
        return None
    
    def _match_tokens(
        self,
        prompt_tokens: List[str],
        pattern_tokens: List[str]
    ) -> Tuple[float, List[str]]:
        """
        Match prompt tokens against pattern tokens.
        
        Returns:
            Tuple of (match_score, list_of_variables_per_position)
            Example: (0.9, ["Boston", "London", "2024-03-15"])
        """
        if not pattern_tokens:
            return 0.0, []
        
        # Find longest matching prefix
        matched_tokens = 0
        var_list = []  # List to store variables per position
        
        i = 0
        j = 0
        
        while i < len(prompt_tokens) and j < len(pattern_tokens):
            if pattern_tokens[j] == "{}":
                # Placeholder - collect tokens until next pattern token
                placeholder_tokens = []
                j += 1
                
                # Collect tokens until we match next pattern token or end
                while i < len(prompt_tokens) and j < len(pattern_tokens):
                    if prompt_tokens[i] == pattern_tokens[j]:
                        break
                    placeholder_tokens.append(prompt_tokens[i])
                    i += 1
                
                # Store variable for this placeholder position
                var_text = " ".join(placeholder_tokens) if placeholder_tokens else ""
                var_list.append(var_text)
                matched_tokens += 1  # Count placeholder as match
            elif j < len(pattern_tokens) and i < len(prompt_tokens) and prompt_tokens[i] == pattern_tokens[j]:
                matched_tokens += 1
                i += 1
                j += 1
            else:
                # Mismatch
                break
        
        # Handle trailing placeholder (pattern ends with {})
        if pattern_tokens and pattern_tokens[-1] == "{}" and i < len(prompt_tokens):
            # Pattern ends with placeholder - all remaining tokens are variables
            remaining_tokens = prompt_tokens[i:]
            if remaining_tokens:
                var_text = " ".join(remaining_tokens)
                # If we already have a variable for the last placeholder, append to it
                if var_list and var_list[-1]:
                    var_list[-1] = var_list[-1] + " " + var_text
                else:
                    var_list.append(var_text)
            # Count the placeholder as matched
            match_score = matched_tokens / len(pattern_tokens) if len(pattern_tokens) > 0 else 0.0
        else:
            # Normal case: use max length
            max_len = max(len(prompt_tokens), len(pattern_tokens))
            match_score = matched_tokens / max_len if max_len > 0 else 0.0
        
        return match_score, var_list
    
    def get_pattern_stats(self) -> Dict[str, Any]:
        """Get statistics about learned patterns."""
        active_patterns = [
            p for p in self.patterns.values()
            if (datetime.now() - p.last_seen).total_seconds() < PATTERN_CACHE_TTL_HOURS * 3600
        ]
        
        return {
            "total_patterns": len(self.patterns),
            "active_patterns": len(active_patterns),
            "avg_confidence": sum(p.confidence for p in active_patterns) / len(active_patterns) if active_patterns else 0.0,
            "total_usage": sum(p.usage_count for p in active_patterns)
        }
    
    def clear_expired_patterns(self):
        """Remove expired patterns from cache."""
        expired = []
        cutoff_time = datetime.now() - timedelta(hours=PATTERN_CACHE_TTL_HOURS)
        
        for pattern, info in self.patterns.items():
            if info.last_seen < cutoff_time:
                expired.append(pattern)
        
        for pattern in expired:
            del self.patterns[pattern]
            if pattern in self.pattern_tokens:
                del self.pattern_tokens[pattern]
        
        if expired:
            logger.info(f"Cleared {len(expired)} expired patterns")


