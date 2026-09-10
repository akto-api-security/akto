package com.akto.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a secret that may be delivered either directly in an environment
 * variable, or as a file whose path is given in a companion
 * "&lt;NAME&gt;_FILE" environment variable.
 *
 * File-based delivery keeps the secret out of the process environment, so it
 * cannot be recovered from the Kubernetes Deployment spec, "helm get values",
 * /proc/&lt;pid&gt;/environ, or a JVM heap dump. That is what allows a secret
 * manager (Vault Agent Injector, Vault CSI / VaultFS, the Secrets Store CSI
 * driver) to hand the value to the service over a tmpfs mount instead.
 *
 * Resolution order for readSecret("PRIVATE_KEY"):
 *
 *   1. If PRIVATE_KEY_FILE is set, read the secret from that path. If the file
 *      cannot be read, log an error and return null. We deliberately do NOT
 *      fall back to PRIVATE_KEY in this case: a broken secret mount must stay
 *      visible rather than be silently papered over by the less secure
 *      delivery path.
 *   2. Otherwise return PRIVATE_KEY (legacy delivery, unchanged behaviour).
 *
 * The file is read on every call rather than cached, so a secret manager can
 * rotate the value in place without the pod being restarted.
 */
public class SecretUtils {

    private static final Logger logger = LoggerFactory.getLogger(SecretUtils.class);

    public static final String FILE_ENV_SUFFIX = "_FILE";

    private SecretUtils() {}

    /**
     * @param envName name of the environment variable holding the secret,
     *                e.g. "PRIVATE_KEY"
     * @return the secret value, or null if it is not configured or a
     *         configured secret file could not be read
     */
    public static String readSecret(String envName) {
        if (envName == null || envName.isEmpty()) {
            return null;
        }

        String pathEnvName = envName + FILE_ENV_SUFFIX;
        String path = System.getenv(pathEnvName);

        if (path == null || path.trim().isEmpty()) {
            return System.getenv(envName);
        }
        path = path.trim();

        try {
            byte[] raw = Files.readAllBytes(Paths.get(path));
            // Secret managers routinely append a trailing newline when
            // templating a value into a file. Strip trailing whitespace so
            // callers get exactly the secret; leading characters are preserved
            // in case they are part of the value.
            String secret = new String(raw, StandardCharsets.UTF_8).replaceAll("\\s+$", "");
            if (secret.isEmpty()) {
                logger.error("{} points at {} but that file is empty", pathEnvName, path);
                return null;
            }
            return secret;
        } catch (IOException | RuntimeException e) {
            // No fallback to the plain env var here - see the class javadoc.
            logger.error("{} is set to {} but that file could not be read: {}",
                    pathEnvName, path, e.getMessage());
            return null;
        }
    }

    /**
     * @return true when envName is being delivered by file rather than by
     *         value. Useful for logging which delivery mode is in effect
     *         without logging the secret itself.
     */
    public static boolean isFileBacked(String envName) {
        String path = System.getenv(envName + FILE_ENV_SUFFIX);
        return path != null && !path.trim().isEmpty();
    }
}
