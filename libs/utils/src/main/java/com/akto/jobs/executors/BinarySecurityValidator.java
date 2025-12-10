package com.akto.jobs.executors;

import java.io.File;
import java.nio.file.LinkOption;

/**
 * Utility class for validating binary file paths and preventing security vulnerabilities.
 * Provides comprehensive security checks to prevent:
 * - Symlink-based attacks
 * - Path traversal attacks
 * - Command injection through path manipulation
 * - Execution of binaries outside trusted directories
 */
public final class BinarySecurityValidator {

    private BinarySecurityValidator() {
        // Prevent instantiation
    }

    /**
     * Validates a binary file path with comprehensive security checks.
     *
     * @param binaryFile The binary file to validate
     * @param binaryName The expected binary name
     * @param basePath The trusted base directory path
     * @return The validated canonical path
     * @throws Exception if any security validation fails
     */
    public static String validateBinaryPath(File binaryFile, String binaryName, String basePath) throws Exception {
        // Resolve real (no-follow-symlink) path to prevent symlink-based attacks
        // toRealPath(NOFOLLOW_LINKS) resolves to absolute canonical path and rejects symlinks
        String execCanonical;
        try {
            execCanonical = binaryFile.toPath().toRealPath(LinkOption.NOFOLLOW_LINKS).toString();
        } catch (java.nio.file.NoSuchFileException e) {
            throw new Exception("Binary file does not exist: " + binaryFile.getAbsolutePath(), e);
        } catch (Exception e) {
            throw new Exception("Failed to resolve real path for binary (possible symlink): " + binaryFile.getAbsolutePath(), e);
        }

        // Enforce exact expected binary path to avoid any injection vector from manipulated paths
        String expectedBinaryCanonical = new File(basePath, binaryName).getCanonicalPath();
        if (!execCanonical.equals(expectedBinaryCanonical)) {
            throw new Exception("Binary real path mismatch. Expected: " + expectedBinaryCanonical + ", Actual: " + execCanonical);
        }

        // Get canonical base directory for containment validation
        String baseCanonical = new File(basePath).getCanonicalPath();

        // Explicit absolute path check (defense-in-depth, though toRealPath already returns absolute)
        if (!new File(execCanonical).isAbsolute()) {
            throw new Exception("Binary path is not absolute: " + execCanonical);
        }

        // Check for shell meta-characters in the path we'll execute (defense-in-depth)
        // Note: Allow backslashes for Windows paths, but block other shell meta-characters
        if (execCanonical.matches(".*[;&|<>`$].*")) {
            throw new Exception("Binary path contains illegal shell meta-characters: " + execCanonical);
        }

        // Whitelist allowed characters in canonical path to prevent injection
        // Allows: alphanumeric, slash (/), backslash (\), dot (.), underscore (_), colon (:), hyphen (-)
        // This prevents any unexpected characters that could enable command injection
        if (!execCanonical.matches("^[a-zA-Z0-9/\\\\._:\\-]+$")) {
            throw new Exception("Binary path contains illegal characters (only alphanumeric, /, \\, ., _, :, - allowed): " + execCanonical);
        }

        // Ensure execCanonical is inside trusted base directory
        if (!execCanonical.startsWith(baseCanonical + File.separator)) {
            throw new Exception("Binary path is outside allowed base path: " + execCanonical);
        }

        // Final check: Ensure the binary exists and is executable
        if (!binaryFile.exists() || !binaryFile.canExecute()) {
            throw new Exception("Binary does not exist or is not executable: " + execCanonical);
        }

        return execCanonical;
    }
}
