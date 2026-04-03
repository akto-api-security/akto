package com.akto.util;

import java.util.List;

/**
 * Persists replay screenshots for JSON-recorded login flows.
 * Mini-runtime modules without direct DB register an implementation that calls the database abstractor.
 */
@FunctionalInterface
public interface RecordedLoginScreenshotsPersistence {

    void persist(String roleName, int userId, List<String> screenshotsBase64);
}
