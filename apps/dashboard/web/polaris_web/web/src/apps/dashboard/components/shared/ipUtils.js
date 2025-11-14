import { isIP } from "is-ip";
import isCidr from "is-cidr";

/**
 * Validates IP or CIDR input
 */
export const validateIpInput = (localVal, localType) => {
    localVal = localVal.replace(/\s+/g, '');
    if (localVal.length === 0) return false;
    
    const values = localVal.split(",");
    let valid = true;
    
    for (let v of values) {
        if (v.length === 0) {
            return true; // Invalid if empty value found
        }
        if (localType === "cidr") {
            valid = valid && (isCidr(v) !== 0);
        } else if (localType === "ip") {
            valid = valid && (isIP(v));
        } else {
            valid = valid && (isIP(v));
        }
    }
    
    return !valid; // Return true if invalid
};

/**
 * Generic IP/CIDR list handler utility
 * Processes IP or CIDR additions and removals for array-based state management
 */
export const handleIpsChange = (ip, isAdded, currentList, setterCallback) => {
    let ipList = ip.split(",");
    ipList = ipList.map((x) => x.replace(/\s+/g, ''));
    
    let updatedIps = [];
    if (isAdded) {
        updatedIps = [...currentList, ...ipList];
    } else {
        updatedIps = currentList.filter(item => item !== ip);
    }
    
    // Remove duplicates
    updatedIps = Array.from(new Set(updatedIps));
    
    // Call the setter function
    setterCallback(updatedIps);
    
    return updatedIps;
};

/**
 * Creates a handler function for specific IP/CIDR list with additional side effects
 * Used in About.jsx for API calls and toast notifications
 */
export const createIpsChangeHandler = (
    currentList,
    setterCallback,
    apiCallbackForAdded = null,
    apiCallbackForRemoved = null,
    successMessage = null
) => {
    return async (ip, isAdded, type) => {
        const updatedIps = handleIpsChange(ip, isAdded, currentList, setterCallback);
        
        // Execute API callback if provided
        if (isAdded && apiCallbackForAdded) {
            await apiCallbackForAdded(updatedIps);
        } else if (!isAdded && apiCallbackForRemoved) {
            await apiCallbackForRemoved(updatedIps);
        }
        
        // Show success message if provided
        if (successMessage && typeof func !== 'undefined') {
            func.setToast(true, false, successMessage);
        }
    };
};