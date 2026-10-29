package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;

import java.util.List;

/**
 * A single diagnostic check.
 *
 * <p>Rules are evaluated in {@link #order()} order so specific rules can claim a
 * failure category before the generic log rule runs.
 */
public interface DiagnosticRule {

    String id();

    List<DiagnosticModels.Finding> evaluate(DiagnosticContext context);

    /** Lower runs first. Specific rules use 10-49, the generic log rule uses 90. */
    default int order() {
        return 100;
    }

    /** Short human label used by the UI when grouping findings. */
    default String category() {
        return "configuration";
    }
}
