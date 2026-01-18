"""
Utility functions for prompt processing and pattern extraction.
"""

from typing import List
import logging

logger = logging.getLogger(__name__)


def longest_common_prefix_tokenwise(prompt_list: List[str]) -> str:
    """
    Finds longest common prefix across prompts within a cluster (token-wise).
    This prefix corresponds to the base prompt.
    Handles multiple placeholders by finding common token sequences.
    """
    if not prompt_list:
        return ""
    
    if len(prompt_list) == 1:
        return prompt_list[0]
    
    # Split all prompts into tokens (words)
    split_prompts = [p.split() for p in prompt_list]
    
    if not split_prompts or not split_prompts[0]:
        return prompt_list[0]
    
    prefix = []
    
    # Find common prefix token-by-token
    for tokens in zip(*split_prompts):
        if all(t == tokens[0] for t in tokens):  # all equal
            prefix.append(tokens[0])
        else:
            break
    
    return " ".join(prefix)


def extract_user_input_v2(full_prompt: str, prefix: str) -> str:
    """Extract user input by removing the shared prefix."""
    if not prefix:
        return full_prompt
    tok_prompt = full_prompt.split()
    tok_prefix = prefix.split()
    return " ".join(tok_prompt[len(tok_prefix):]).strip()


def extract_variables_by_position(prompt: str, base_prompt: str) -> List[str]:
    """
    Extract variables per placeholder position from a prompt matching a base pattern.
    
    Args:
        prompt: Full prompt text
        base_prompt: Base prompt pattern with {} placeholders
        
    Returns:
        List of variables, one per placeholder position
        Example: ["Boston", "London", "2024-03-15"] for pattern "Book flight from {} to {} on {}"
    """
    prompt_tokens = prompt.split()
    pattern_tokens = base_prompt.split()
    
    var_list = []
    i = 0
    j = 0
    
    while i < len(prompt_tokens) and j < len(pattern_tokens):
        if pattern_tokens[j] == "{}":
            # Placeholder - collect tokens until next pattern token
            placeholder_tokens = []
            j += 1  # Move past the {}
            
            # Collect tokens until we match next pattern token or end
            # Skip over any remaining {} placeholders in pattern
            while j < len(pattern_tokens) and pattern_tokens[j] == "{}":
                j += 1  # Skip consecutive placeholders
            
            # Now collect tokens until we match the next non-placeholder pattern token
            while i < len(prompt_tokens) and j < len(pattern_tokens):
                if pattern_tokens[j] == "{}":
                    # Next placeholder in pattern - stop collecting for current variable
                    break
                if prompt_tokens[i] == pattern_tokens[j]:
                    # Matched next pattern token - stop collecting
                    break
                placeholder_tokens.append(prompt_tokens[i])
                i += 1
            
            # Store variable for this placeholder position (only if non-empty)
            var_text = " ".join(placeholder_tokens) if placeholder_tokens else ""
            if var_text:  # Only add non-empty variables
                var_list.append(var_text)
        elif j < len(pattern_tokens) and i < len(prompt_tokens) and prompt_tokens[i] == pattern_tokens[j]:
            i += 1
            j += 1
        else:
            # Mismatch - try to skip and continue (more lenient)
            # This handles cases where prompt has extra words
            if i < len(prompt_tokens):
                i += 1
            else:
                break
    
    # Handle trailing placeholder (pattern ends with {})
    if pattern_tokens and pattern_tokens[-1] == "{}" and i < len(prompt_tokens):
        remaining_tokens = prompt_tokens[i:]
        if remaining_tokens:
            var_text = " ".join(remaining_tokens)
            # Check if we already extracted a variable for the last placeholder
            # Count placeholders in pattern
            placeholder_count = pattern_tokens.count("{}")
            if len(var_list) < placeholder_count:
                # Missing variable for last placeholder - add it
                var_list.append(var_text)
            elif var_list and var_list[-1]:
                # Already have last variable - append to it
                var_list[-1] = var_list[-1] + " " + var_text
    
    return var_list


def normalize_base_prompt(base_prompt: str) -> str:
    """Normalize base_prompt string for consistent matching (remove extra whitespace)."""
    if not base_prompt:
        return ""
    # Normalize whitespace: multiple spaces to single space, strip
    normalized = " ".join(base_prompt.split())
    return normalized

