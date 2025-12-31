/**
 * Date and timestamp utility functions for chat components
 */

/**
 * Normalizes timestamp to milliseconds
 * Handles both seconds (Unix timestamp) and milliseconds
 * @param {number} timestamp - Timestamp in seconds or milliseconds
 * @returns {number} Timestamp in milliseconds
 */
export const normalizeTimestamp = (timestamp) => {
    if (!timestamp) return 0;

    // If timestamp is in seconds (< year 2286), convert to ms
    // Unix timestamp for Jan 1, 2286 is 10000000000
    return timestamp < 10000000000 ? timestamp * 1000 : timestamp;
};

/**
 * Formats timestamp for display in chat messages
 * @param {number} timestamp - Timestamp in seconds or milliseconds
 * @returns {string} Formatted date string (e.g., "12/20/21, 3:45 PM")
 */
export const formatChatTimestamp = (timestamp) => {
    if (!timestamp) return '';

    const ms = normalizeTimestamp(timestamp);
    return new Date(ms).toLocaleString('en-US', {
        month: 'numeric',
        day: 'numeric',
        year: '2-digit',
        hour: 'numeric',
        minute: 'numeric',
        hour12: true
    });
};
