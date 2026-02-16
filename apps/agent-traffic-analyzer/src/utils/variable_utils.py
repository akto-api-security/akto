"""
Variable store utility functions.
"""

import logging
from src.utils.utils import normalize_base_prompt
from src.core.stores import VariableStore

logger = logging.getLogger(__name__)


def get_total_variables_for_pattern(base_prompt: str, variable_store: VariableStore) -> int:
    """Get total count of variables stored for a base prompt across all positions."""
    if not variable_store:
        return 0
    
    # Normalize the base_prompt for consistent matching
    normalized_base_prompt = normalize_base_prompt(base_prompt)
    
    num_placeholders = normalized_base_prompt.count('{}')
    total = 0
    
    # Try exact match first
    for idx in range(num_placeholders):
        count = variable_store.get_variable_count(normalized_base_prompt, idx)
        total += count
    
    # If no exact match found, try to find similar patterns
    if total == 0:
        try:
            all_data = variable_store.collection.get()
            if all_data and all_data.get('metadatas'):
                stored_patterns = {}
                for metadata in all_data['metadatas']:
                    stored_bp = metadata.get('base_prompt', '')
                    if stored_bp:
                        normalized_stored = normalize_base_prompt(stored_bp)
                        if normalized_stored not in stored_patterns:
                            stored_patterns[normalized_stored] = 0
                        stored_patterns[normalized_stored] += 1
                
                # Find patterns with same number of placeholders
                matching_patterns = []
                for stored_bp, count in stored_patterns.items():
                    if stored_bp.count('{}') == num_placeholders:
                        # Check if they start similarly
                        query_prefix = normalized_base_prompt.split('{}')[0].strip() if '{}' in normalized_base_prompt else normalized_base_prompt
                        stored_prefix = stored_bp.split('{}')[0].strip() if '{}' in stored_bp else stored_bp
                        if query_prefix and stored_prefix and query_prefix.lower() == stored_prefix.lower():
                            matching_patterns.append((stored_bp, count))
                
                if matching_patterns:
                    # Use the pattern with most variables
                    best_match = max(matching_patterns, key=lambda x: x[1])
                    # Try querying with the stored pattern
                    for idx in range(num_placeholders):
                        count = variable_store.get_variable_count(best_match[0], idx)
                        total += count
        except Exception as e:
            logger.warning(f"Error finding similar patterns: {str(e)}")
    
    return total

