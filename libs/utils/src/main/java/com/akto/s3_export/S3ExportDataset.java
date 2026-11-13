package com.akto.s3_export;

import java.util.ArrayList;
import java.util.List;

/** Exportable datasets. A new dataset is one value here plus one {@link S3ExportSource}. */
public enum S3ExportDataset {
    ATLAS_TRACES("atlas/traces", new AtlasTracesExportSource()),
    ATLAS_GUARDRAIL_EVENTS("atlas/guardrail_events", new GuardrailEventsExportSource());

    private final String pathSegment;
    private final S3ExportSource source;

    S3ExportDataset(String pathSegment, S3ExportSource source) {
        this.pathSegment = pathSegment;
        this.source = source;
    }

    public String getPathSegment() {
        return pathSegment;
    }

    public S3ExportSource getSource() {
        return source;
    }

    /** The dataset with this name, or null. */
    public static S3ExportDataset fromName(String name) {
        for (S3ExportDataset dataset : values()) {
            if (dataset.name().equals(name)) return dataset;
        }
        return null;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (S3ExportDataset dataset : values()) names.add(dataset.name());
        return names;
    }
}
