package com.akto.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a secret that is delivered either directly in an environment variable,
 * or from a file whose path is given in a companion "&lt;NAME&gt;_FILE"
 * environment variable.
 *
 * File-based delivery keeps the secret out of the process environment, so it
 * cannot be recovered from the Kubernetes Deployment spec, "helm get values",
 * /proc/&lt;pid&gt;/environ, or a JVM heap dump. That is what lets a secret
 * manager (Vault Agent Injector, Vault CSI, or a platform's own secret sidecar)
 * hand the value to the service over a tmpfs mount instead.
 *
 * Resolution order for readSecret("PRIVATE_KEY"):
 *
 *   1. If PRIVATE_KEY_FILE is set, read the secret from it. Two forms:
 *
 *        /path/to/file           the whole file contents are the secret
 *        /path/to/file:FIELD     the file holds a JSON object; return FIELD
 *
 *      The second form suits secret managers that materialise one file per
 *      vault secret with every field of that secret inside it. JSON is the only
 *      layout supported there, because line-oriented formats cannot carry a
 *      multi-line value such as a PEM key unambiguously.
 *
 *      If the file cannot be read, is not JSON, or the field is absent, log an
 *      error and return null. We deliberately do NOT fall back to PRIVATE_KEY:
 *      a broken secret mount must stay visible rather than be silently papered
 *      over by the less secure delivery path.
 *
 *   2. Otherwise return PRIVATE_KEY (legacy delivery, unchanged behaviour).
 *
 * The file is read on every call rather than cached, so a secret manager can
 * rotate the value in place. Note that some callers cache the result in a
 * static field, which defeats that - see PayloadEncodeUtil's callers.
 */
public class SecretUtils {

    private static final Logger logger = LoggerFactory.getLogger(SecretUtils.class);

    private static final String FILE_ENV_SUFFIX = "_FILE";

    private SecretUtils() {}

    /**
     * @param envName name of the environment variable holding the secret,
     *                e.g. "PRIVATE_KEY"
     * @return the secret, or null if it is not configured or a configured
     *         secret file could not be read
     */
    public static String readSecret(String envName) {
        return readSecret(envName, System.getenv());
    }

    /**
     * Overload taking the environment explicitly so the resolution rules can be
     * unit tested; production callers use {@link #readSecret(String)}.
     */
    static String readSecret(String envName, java.util.Map<String, String> env) {
        if (envName == null || envName.isEmpty()) {
            return null;
        }

        String pathEnvName = envName + FILE_ENV_SUFFIX;
        String spec = env.get(pathEnvName);

        if (spec == null || spec.trim().isEmpty()) {
            return env.get(envName);
        }
        spec = spec.trim();

        String path = spec;
        String field = null;

        // Split a trailing ":FIELD" off the path, but only when the left-hand
        // side is itself a readable file, so a path that merely contains a
        // colon still resolves as a path.
        int sep = spec.lastIndexOf(':');
        if (sep > 0 && sep < spec.length() - 1 && Files.isReadable(Paths.get(spec.substring(0, sep)))) {
            path = spec.substring(0, sep);
            field = spec.substring(sep + 1);
        }

        String contents;
        try {
            contents = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // No fallback to the plain env var here - see the class javadoc.
            logger.error("{} is set to {} but that file could not be read: {}",
                    pathEnvName, path, e.getMessage());
            return null;
        }

        String secret = contents;
        if (field != null) {
            try {
                JSONObject obj = new JSONObject(contents);
                if (!obj.has(field)) {
                    logger.error("{} asked for field {} of {} but that file has no such field",
                            pathEnvName, field, path);
                    return null;
                }
                secret = String.valueOf(obj.get(field));
            } catch (RuntimeException e) {
                logger.error("{} asked for field {} of {} but that file is not a JSON object; "
                        + "drop the \":{}\" suffix if the whole file is the secret",
                        pathEnvName, field, path, field);
                return null;
            }
        }

        // A trailing newline is the norm when a secret manager templates a value
        // into a file and is not part of the secret. Left in place it breaks
        // callers that put the value in an HTTP header: okhttp rejects 0x0a and
        // echoes the value into the exception message. Leading characters are
        // preserved in case they are genuinely part of the secret.
        secret = secret.replaceAll("\\s+$", "");

        // Normalise empty to null, because callers such as Main.createDataSource
        // only null-check: an empty string would reach the database driver and
        // fail authentication with nothing pointing back at the secret file.
        if (secret.isEmpty()) {
            logger.error("{} is set to {} but that resolved to an empty secret", pathEnvName, spec);
            return null;
        }
        return secret;
    }
}
