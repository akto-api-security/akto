package com.akto.jobs.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.akto.log.LoggerMaker;

/**
 * Utility class for executing binaries securely.
 * Handles binary execution with environment variables, timeout, and output capture.
 */
public final class BinaryExecutor {

    private static final LoggerMaker logger = new LoggerMaker(BinaryExecutor.class);

    private BinaryExecutor() {
        // Prevent instantiation
    }

    /**
     * Executes a binary with environment variables and timeout.
     *
     * @param binaryFile The binary file to execute
     * @param envVars Environment variables to pass to the binary
     * @param timeoutSeconds Timeout in seconds (0 for no timeout)
     * @return ExecutionResult containing exit code, stdout, and stderr
     * @throws Exception if execution fails
     */
    public static ExecutionResult executeBinary(File binaryFile, Map<String, String> envVars, int timeoutSeconds) throws Exception {
        logger.info("Executing binary: {}", binaryFile.getAbsolutePath());

        // Validate binary path for security
        String basePath = binaryFile.getParent();
        String binaryName = binaryFile.getName();

        try {
            BinarySecurityValidator.validateBinaryPath(binaryFile, binaryName, basePath);
        } catch (Exception e) {
            logger.error("Binary security validation failed: {}", e.getMessage());
            throw new Exception("Binary security validation failed: " + e.getMessage(), e);
        }

        // Build process
        ProcessBuilder processBuilder = new ProcessBuilder(binaryFile.getAbsolutePath());
        processBuilder.redirectErrorStream(true); // Merge stdout and stderr

        // Set environment variables
        if (envVars != null && !envVars.isEmpty()) {
            Map<String, String> env = processBuilder.environment();
            envVars.forEach((key, value) -> {
                env.put(key, value);
                logger.debug("Setting env var: {}=***", key); // Don't log sensitive values
            });
        }

        // Start process
        Process process = null;
        StringBuilder output = new StringBuilder();
        int exitCode;

        try {
            logger.info("Starting binary execution: {}", binaryFile.getName());
            process = processBuilder.start();

            // Read output in a separate thread
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                logger.info("Binary output: {}", line);
            }

            // Wait for process to complete with timeout
            boolean completed;
            if (timeoutSeconds > 0) {
                completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroy();
                    throw new Exception("Binary execution timed out after " + timeoutSeconds + " seconds");
                }
            } else {
                exitCode = process.waitFor();
            }

            exitCode = process.exitValue();

            logger.info("Binary execution completed: exitCode={}, output length={}",
                exitCode, output.length());

            return new ExecutionResult(exitCode, output.toString(), "");

        } catch (InterruptedException e) {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
            Thread.currentThread().interrupt();
            throw new Exception("Binary execution interrupted", e);

        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
            logger.error("Binary execution failed", e);
            throw new Exception("Binary execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Result of binary execution.
     */
    public static class ExecutionResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public ExecutionResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        @Override
        public String toString() {
            return "ExecutionResult{" +
                "exitCode=" + exitCode +
                ", stdoutLength=" + (stdout != null ? stdout.length() : 0) +
                ", stderrLength=" + (stderr != null ? stderr.length() : 0) +
                '}';
        }
    }
}
