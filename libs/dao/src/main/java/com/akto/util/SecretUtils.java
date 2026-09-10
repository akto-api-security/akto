package com.akto.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a secret that may be delivered either directly in an environment
 * variable, or from a file whose path is given in a companion
 * "&lt;NAME&gt;_FILE" environment variable.
 *
 * File-based delivery keeps the secret out of the process environment, so it
 * cannot be recovered from the Kubernetes Deployment spec, "helm get values",
 * /proc/&lt;pid&gt;/environ, or a JVM heap dump. That is what allows a secret
 * manager (Vault Agent Injector, Vault CSI, or a platform's own secret sidecar)
 * to hand the value to the service over a tmpfs mount instead.
 *
 * Resolution order for readSecret("PRIVATE_KEY"):
 *
 *   1. If PRIVATE_KEY_FILE is set, read the secret from it. Two forms are
 *      accepted:
 *
 *        /path/to/file           the whole file contents are the secret
 *        /path/to/file:FIELD     the file holds several fields; return FIELD
 *
 *      The second form matches secret managers that materialise one file per
 *      Vault secret, with each field of that secret inside it. Supported file
 *      formats for that form are JSON, "FIELD=value" lines and "FIELD: value"
 *      lines.
 *
 *      If the file cannot be read, or the requested field is absent, log an
 *      error and return null. We deliberately do NOT fall back to PRIVATE_KEY
 *      here: a broken secret mount must stay visible rather than be silently
 *      papered over by the less secure delivery path.
 *
 *   2. Otherwise return PRIVATE_KEY (legacy delivery, unchanged behaviour).
 *
 * The file is read on every call rather than cached, so a secret manager can
 * rotate the value in place. Note that some callers cache the result in a
 * static field, which defeats that - see PayloadEncodeUtil's callers.
 */
public class SecretUtils {

    private static final Logger logger = LoggerFactory.getLogger(SecretUtils.class);

    public static final String FILE_ENV_SUFFIX = "_FILE";

    private SecretUtils() {}

    /**
     * @param envName name of the environment variable holding the secret,
     *                e.g. "PRIVATE_KEY"
     * @return the secret value, or null if it is not configured, a configured
     *         secret file could not be read, or a requested field is missing
     */
    public static String readSecret(String envName) {
        if (envName == null || envName.isEmpty()) {
            return null;
        }

        String pathEnvName = envName + FILE_ENV_SUFFIX;
        String spec = System.getenv(pathEnvName);

        if (spec == null || spec.trim().isEmpty()) {
            return System.getenv(envName);
        }
        spec = spec.trim();

        String path = spec;
        String field = null;

        // Split a trailing ":FIELD" off the path. Only treat it as a field when
        // the left-hand side actually resolves to a readable file, so ordinary
        // paths - including the unusual ones containing a colon - still work.
        int sep = spec.lastIndexOf(':');
        if (sep > 0 && sep < spec.length() - 1) {
            String maybePath = spec.substring(0, sep);
            String maybeField = spec.substring(sep + 1);
            if (!maybeField.contains("/") && Files.isReadable(Paths.get(maybePath))) {
                path = maybePath;
                field = maybeField;
            }
        }

        String contents;
        try {
            byte[] raw = Files.readAllBytes(Paths.get(path));
            contents = new String(raw, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // No fallback to the plain env var here - see the class javadoc.
            logger.error("{} is set to {} but that file could not be read: {}",
                    pathEnvName, path, e.getMessage());
            return null;
        }

        if (field == null) {
            // Secret managers routinely append a trailing newline when
            // templating a value into a file. Strip trailing whitespace so
            // callers get exactly the secret; leading characters are preserved
            // in case they are part of the value.
            String secret = stripTrailing(contents);
            if (secret.isEmpty()) {
                logger.error("{} points at {} but that file is empty", pathEnvName, path);
                return null;
            }
            return secret;
        }

        String secret = extractField(contents, field);
        if (secret == null) {
            logger.error("{} asked for field {} of {} but it was not found in that file",
                    pathEnvName, field, path);
            return null;
        }
        secret = stripTrailing(secret);
        if (secret.isEmpty()) {
            logger.error("{} asked for field {} of {} but it is empty",
                    pathEnvName, field, path);
            return null;
        }
        return secret;
    }

    /**
     * Pull one field out of a multi-field secret file. Understands JSON,
     * "FIELD=value" lines and "FIELD: value" lines.
     *
     * @return the field's value, or null when the field is not present
     */
    private static String extractField(String contents, String field) {
        String trimmed = contents.trim();

        if (trimmed.startsWith("{")) {
            try {
                JSONObject obj = new JSONObject(trimmed);
                if (obj.has(field)) {
                    return String.valueOf(obj.get(field));
                }
                return null;
            } catch (RuntimeException e) {
                // Not valid JSON after all - fall through to line parsing.
                logger.warn("secret file starts with '{{' but did not parse as JSON, "
                        + "falling back to line parsing: {}", e.getMessage());
            }
        }

        for (String line : contents.split("\\R")) {
            String candidate = line.trim();
            if (candidate.isEmpty() || candidate.startsWith("#")) {
                continue;
            }
            int eq = candidate.indexOf('=');
            int colon = candidate.indexOf(':');
            int at;
            if (eq < 0) {
                at = colon;
            } else if (colon < 0) {
                at = eq;
            } else {
                at = Math.min(eq, colon);
            }
            if (at <= 0) {
                continue;
            }
            if (candidate.substring(0, at).trim().equals(field)) {
                return candidate.substring(at + 1).trim();
            }
        }
        return null;
    }

    private static String stripTrailing(String s) {
        return s.replaceAll("\\s+$", "");
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
