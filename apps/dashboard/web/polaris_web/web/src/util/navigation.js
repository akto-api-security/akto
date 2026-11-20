/**
 * Smart Router - Re-exports everything from react-router-dom with enhanced useNavigate
 * 
 * This module provides a drop-in replacement for react-router-dom that automatically
 * handles ctrl+click (or cmd+click on Mac) to open links in new tabs.
 * 
 * DO NOT import from 'react-router-dom' in this file - use 'react-router-dom-original' 
 * to avoid circular dependency with webpack alias
 */

// Use the aliased path to get original react-router-dom (see webpack config)
import { useNavigate as useReactRouterNavigate } from 'react-router-dom-original';

// Re-export everything from original react-router-dom
export * from 'react-router-dom-original';

// Store the last click event to detect ctrl/cmd+click
let lastClickEvent = null;
let lastClickTime = 0;

// Global click listener to capture events (runs in capture phase before onClick handlers)
if (typeof document !== 'undefined' && !window.__smartNavigateListenerAdded) {
    document.addEventListener('click', (e) => {
        lastClickEvent = e;
        lastClickTime = Date.now();
    }, true); // true = capture phase
    window.__smartNavigateListenerAdded = true;
}

/**
 * Check if the last click was a ctrl/cmd+click (within 100ms)
 */
const isCtrlClick = () => {
    if (!lastClickEvent || Date.now() - lastClickTime > 100) {
        return false;
    }
    return lastClickEvent.ctrlKey || lastClickEvent.metaKey;
};

/**
 * Custom useNavigate hook that automatically handles ctrl/cmd+click
 * Drop-in replacement for react-router-dom's useNavigate
 * 
 * When user ctrl+clicks (or cmd+clicks on Mac), the link opens in a new tab
 * Normal clicks navigate in the same tab as usual
 */
export const useNavigate = () => {
    const reactNavigate = useReactRouterNavigate();
    
    const smartNavigate = (to, options) => {
        // If it's a string path and ctrl/cmd was pressed, open in new tab
        if (typeof to === 'string' && isCtrlClick()) {
            window.open(to, '_blank');
        } else {
            reactNavigate(to, options);
        }
    };
    
    return smartNavigate;
};
